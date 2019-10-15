package io.hydrosphere.serving.manager.domain.application.graph

import cats.data.NonEmptyList
import cats.implicits._
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.Contract
import io.hydrosphere.serving.manager.domain.application.graph.ExecutionGraph._
import io.hydrosphere.serving.manager.domain.model_version.ModelVersion
import io.hydrosphere.serving.manager.domain.servable.Servable

object ExecutionGraph {

  sealed trait ExecutionNode extends Product with Serializable {
    def id: String

    def contract: Contract
  }

  case class ModelNode(id: String, servable: Servable) extends ExecutionNode {
    override def contract: Contract = servable.modelVersion.contract
  }

  case class ABNode(id: String, submodels: NonEmptyList[(ModelNode, Int)], contract: Contract) extends ExecutionNode

  case class DirectionalLink(src: String, dest: String)

  case class InternalNode(id: String, contract: Contract) extends ExecutionNode

  object InternalNode {
    final val inputId = "<IN>"
    final val outputId = "<OUT>"

    def input(contract: Contract) = {
      InternalNode(inputId, contract)
    }

    def output(contract: Contract) = {
      InternalNode(outputId, contract)
    }
  }

  object InputLink {

    def apply(nodeId: String): DirectionalLink = DirectionalLink(InternalNode.inputId, nodeId)

    def apply(node: ExecutionNode): DirectionalLink = DirectionalLink(InternalNode.inputId, node.id)

    def unapply(arg: DirectionalLink): Option[DirectionalLink] = {
      if (isInput(arg)) {
        arg.some
      } else None
    }

    def isInput(link: DirectionalLink): Boolean = {
      link.src == InternalNode.inputId
    }
  }

  object OutputLink {

    def apply(nodeId: String): DirectionalLink = DirectionalLink(nodeId, InternalNode.outputId)

    def apply(node: ExecutionNode): DirectionalLink = DirectionalLink(node.id, InternalNode.outputId)

    def unapply(arg: DirectionalLink): Option[DirectionalLink] = {
      if (isOutput(arg)) {
        arg.some
      } else None
    }

    def isOutput(link: DirectionalLink): Boolean = {
      link.dest == InternalNode.outputId
    }
  }

  def link(node1: ExecutionNode, node2: ExecutionNode): DirectionalLink = {
    DirectionalLink(node1.id, node2.id)
  }
}

case class ExecutionGraph(nodes: NonEmptyList[ExecutionNode], links: NonEmptyList[DirectionalLink], signature: ModelSignature)