package io.hydrosphere.serving.manager.domain.application.graph

import cats.data.NonEmptyList
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.application.graph.ExecutionGraph.{DirectionalLink, ExecutionNode}
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable

object ExecutionGraph {

  sealed trait ExecutionNode extends Product with Serializable {
    def id: String
  }

  case class ModelNode(id: String, mv: ModelVersion, servable: Option[Servable]) extends ExecutionNode

  case class ABNode(id: String, submodels: NonEmptyList[(ModelNode, Int)], signature: ModelSignature) extends ExecutionNode

  case class DirectionalLink(src: String, dest: String)

}

case class ExecutionGraph(id: String, nodes: List[ExecutionNode], links: List[DirectionalLink], signature: ModelSignature) extends ExecutionNode