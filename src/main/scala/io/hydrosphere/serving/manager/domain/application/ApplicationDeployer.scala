package io.hydrosphere.serving.manager.domain.application

import cats.data.{NonEmptyList, OptionT}
import cats.effect.Concurrent
import cats.effect.implicits._
import cats.implicits._
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.discovery.ApplicationPublisher
import io.hydrosphere.serving.manager.domain.DomainError.InvalidRequest
import io.hydrosphere.serving.manager.domain.application.graph.ExecutionGraph
import io.hydrosphere.serving.manager.domain.application.requests.ExecutionGraphRequest
import io.hydrosphere.serving.manager.domain.model_version.{ModelVersion, ModelVersionRepository, ModelVersionStatus}
import io.hydrosphere.serving.manager.domain.servable.{Servable, ServableService}
import io.hydrosphere.serving.manager.domain.{Contract, DomainError}
import io.hydrosphere.serving.manager.util.UUIDGenerator
import io.hydrosphere.serving.manager.util.random.NameGenerator
import io.hydrosphere.serving.model.api.MergeError
import io.hydrosphere.serving.model.api.ops.ModelSignatureOps
import org.apache.logging.log4j.scala.Logging

trait ApplicationDeployer[F[_]] {
  def deploy(
    name: String,
    executionGraph: ExecutionGraphRequest,
    kafkaStreaming: List[Application.KafkaParams]
  ): F[Application]
}

object ApplicationDeployer extends Logging {
  def inferConcurrentNodes(
    nodes: NonEmptyList[ExecutionGraph.ModelNode]
  ): Either[DomainError, ModelSignature] = {
    ApplicationValidator.inferStageSignature(nodes.map(_.servable.modelVersion).toList)
  }

  def inferGraphSignature(
    nodes: NonEmptyList[ExecutionGraph.ExecutionNode],
    links: NonEmptyList[ExecutionGraph.DirectionalLink]
  ): Either[Seq[MergeError], Unit] = {
    val inputLinks = links.filter(ExecutionGraph.InputLink.isInput)
    val inputNodes = nodes.filter(n => inputLinks.exists(_.dest == n.id))
    val input = inputNodes
      .map(_.contract.predict)
      .foldLeft(Either.right[Seq[MergeError], ModelSignature](ModelSignature.defaultInstance)) {
        case (Right(a), b) => ModelSignatureOps.merge(a, b)
        case (Left(err), _) => Left(err)
      }
      .map(x => ExecutionGraph.InternalNode.input(Contract(x)))

    val outputLinks = links.filter(ExecutionGraph.OutputLink.isOutput)
    val outputNodes = nodes.filter(n => outputLinks.exists(_.src == n.id))
    val output = outputNodes
      .map(_.contract.predict)
      .foldLeft(Either.right[Seq[MergeError], ModelSignature](ModelSignature.defaultInstance)) {
        case (Right(a), b) => ModelSignatureOps.merge(a, b)
        case (Left(err), _) => Left(err)
      }
      .map(x => ExecutionGraph.InternalNode.input(Contract(x)))

    for {
      i <- input
      o <- output
      fullNodes = nodes ++ List(i, o)
    } yield contractCheckTraverse(i, fullNodes, links)
  }

  // TODO Ensure that it traverses graph and check contract for nodes
  // TODO Unsafe recursion. Ensure stack safety.
  def contractCheckTraverse(
    inputNode: ExecutionGraph.ExecutionNode,
    nodes: NonEmptyList[ExecutionGraph.ExecutionNode],
    links: NonEmptyList[ExecutionGraph.DirectionalLink]
  ): Unit = {
    def checkNode(node: ExecutionGraph.ExecutionNode): Either[Seq[MergeError], Unit] = {
      val nodeLinks = links.filter(l => l.src == node.id)
      val outputNodes = nodes.filter(n => nodeLinks.exists(_.dest == n.id))
      val result = outputNodes.traverse { n =>
        ModelSignatureOps.append(node.contract.predict, n.contract.predict)
      }
      val x = result.flatMap { _ =>
        outputNodes.traverse(checkNode)
      }
      x.map(_ => ())
    }
    checkNode(inputNode)
  }

  def default[F[_]]()(
    implicit
    F: Concurrent[F],
    uuidGen: UUIDGenerator[F],
    nameGenerator: NameGenerator[F],
    servableService: ServableService[F],
    versionRepository: ModelVersionRepository[F],
    applicationRepository: ApplicationRepository[F],
    discoveryHub: ApplicationPublisher[F]
  ): ApplicationDeployer[F] = {
    new ApplicationDeployer[F] {
      override def deploy(
        name: String,
        executionGraph: ExecutionGraphRequest,
        kafkaStreaming: List[Application.KafkaParams]
      ): F[Application] = {
        for {
          composedApp <- composeApp(name, executionGraph, kafkaStreaming)
          repoApp <- applicationRepository.create(composedApp)
          app = composedApp.copy(id = repoApp.id)
          _ <- discoveryHub.update(app)
          _ <- handleAppDeployment(app).start
        } yield app
      }

      def composeApp(
        name: String,
        executionGraph: ExecutionGraphRequest,
        kafkaStreaming: List[Application.KafkaParams]
      ): F[Application] = {
        for {
          _ <- checkApplicationName(name)
          nodes <- executionGraph.stages.traverse { f =>
            for {
              variants <- f.modelVariants.traverse { m =>
                for {
                  version <- OptionT(versionRepository.get(m.modelVersionId))
                    .getOrElseF(DomainError.notFound(s"Can't find ModelVersion $m").raiseError[F, ModelVersion])
                  _ <- version.status match {
                    case ModelVersionStatus.Released => F.unit
                    case x =>
                      DomainError
                        .invalidRequest(s"Can't deploy non-released ModelVersion: ${version.fullName} - $x")
                        .raiseError[F, Unit]
                  }
                  uuid <- uuidGen.generate()
                  servablePrefix = nameGenerator.getName()
                  fullPrefix = name + "-" + servablePrefix
                  servableMeta = Map(
                    "application.exec-node-id" -> uuid.toString,
                    "application.name" -> name
                  )
                  status = Servable.NotServing("Not deployed yet", None, None)
                  servable = Servable(version, fullPrefix, status, Nil, servableMeta)
                } yield ExecutionGraph.ModelNode(uuid.toString, servable) -> m.weight
              }
              uuid <- uuidGen.generate()
              nodes <- variants match {
                case NonEmptyList((head, _), Nil) =>
                  head.pure[F].widen[ExecutionGraph.ExecutionNode]
                case nel =>
                  val res = for {
                    signature <- F.fromEither(inferConcurrentNodes(nel.map(_._1)))
                  } yield ExecutionGraph.ABNode(uuid.toString, nel, Contract(signature))
                  res.widen[ExecutionGraph.ExecutionNode]
              }
            } yield nodes
          }
          inLink = ExecutionGraph.InputLink(nodes.head)
          outLink = ExecutionGraph.OutputLink(nodes.last)
          intermediateLinks = nodes.toList
            .zip(nodes.tail)
            .map((ExecutionGraph.link _).tupled)
          graphLinks = NonEmptyList.one(inLink) ::: NonEmptyList.ofInitLast(intermediateLinks, outLink)
          graph = ExecutionGraph(nodes, graphLinks, ???)
        } yield
          Application(
            id = 0,
            name = name,
            kafkaStreaming = kafkaStreaming,
            status = Application.Unhealthy("Iniitializing".some),
            graph = graph
          )
      }

      def checkApplicationName(name: String): F[String] = {
        for {
          _ <- ApplicationValidator.name(name) match {
            case Some(_) => F.unit
            case None =>
              InvalidRequest(s"Application name $name contains invalid symbols. It should only contain latin letters, numbers '-' and '_'")
                .raiseError[F, Unit]
          }
          maybeApp <- applicationRepository.get(name)
          _ <- maybeApp match {
            case Some(_) =>
              F.raiseError[Unit](InvalidRequest(s"Application with name $name already exists"))
            case None => F.unit
          }
        } yield name
      }

      def deployGraphNodes(app: Application): F[Unit] = {
        for {
          _ <- app.graph.nodes.traverse {
            case ExecutionGraph.InternalNode(_, _) => F.unit
            case ExecutionGraph.ModelNode(id, servable) =>
              for {
                _ <- servableService.deploy(servable)
              } yield ()
            case ExecutionGraph.ABNode(id, submodels, _) =>
              for {
                _ <- submodels.traverse {
                  case (ExecutionGraph.ModelNode(_, servable), _) =>
                    for {
                      _ <- servableService.deploy(servable)
                    } yield ()
                }
              } yield ()
          }
        } yield ()
      }

      def handleAppDeployment(
        app: Application
      ): F[Unit] = {
        (for {
          _ <- deployGraphNodes(app)
          _ <- F.delay(logger.debug(s"${app.name} app servables started. Ok."))
          _ <- applicationRepository.update(app)
          _ <- discoveryHub.update(app)
        } yield ())
          .handleErrorWith { x =>
            val failedApp = app.copy(status = Application.Unhealthy(Option(x.getMessage)))
            F.delay(logger.error(s"Error while buidling application ${app.name}", x)) >>
              applicationRepository.update(failedApp).void
          }
      }
    }
  }
}