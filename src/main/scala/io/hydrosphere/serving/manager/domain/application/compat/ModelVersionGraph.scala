package io.hydrosphere.serving.manager.domain.application.compat

import cats.data.NonEmptyList
import io.circe.generic.JsonCodec
import io.hydrosphere.serving.manager.domain.contract.Signature
import io.hydrosphere.serving.manager.domain.servable.Servable

@JsonCodec
case class Variant[T](item: T, weight: Int)

@JsonCodec
case class Node[T](variants: NonEmptyList[Variant[T]])

@JsonCodec
case class Graph[T](nodes: NonEmptyList[Node[T]])

@JsonCodec
case class ExecutionNode(variants: NonEmptyList[Variant[Servable]], signature: Signature)
