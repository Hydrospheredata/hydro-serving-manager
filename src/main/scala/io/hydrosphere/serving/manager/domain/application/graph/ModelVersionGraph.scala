package io.hydrosphere.serving.manager.domain.application.graph

import cats.data.NonEmptyList
import io.hydrosphere.serving.contract.model_signature.ModelSignature
import io.hydrosphere.serving.manager.domain.servable.Servable.OkServable

case class Variant[T](
  item: T,
  weight: Int
)

case class Node[T](
  variants: NonEmptyList[Variant[T]]
)

case class Graph[T](
  nodes: NonEmptyList[Node[T]]
)

case class ExecutionNode(
  variants: NonEmptyList[Variant[OkServable]],
  signature: ModelSignature
)
