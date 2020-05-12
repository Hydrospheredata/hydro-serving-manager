package io.hydrosphere.serving.manager.domain.contract.ops

import cats.data.NonEmptyList
import cats.implicits._
import io.hydrosphere.serving.manager.domain.contract.ops.MergeError.{
  IncompatibleShapes,
  NamesAreDifferent
}
import io.hydrosphere.serving.manager.domain.contract.{DataType, Field, TensorShape}

trait ModelFieldOps {

  implicit class ModelFieldPumped(modelField: Field) {

    def insert(name: String, fieldInfo: Field): Option[Field] =
      modelField match {
        case Field.Map(_, subfields, _) =>
          subfields.find(_.name == name) match {
            case Some(_) =>
              None
            case None =>
              val newData = subfields :+ fieldInfo
              Field
                .Map(
                  modelField.name,
                  newData,
                  fieldInfo.shape
                )
                .some
          }
        case _ => None
      }

    def child(name: String): Option[Field] =
      modelField match {
        case Field.Map(_, subfields, _) => subfields.find(_.name == name)
        case _                          => None
      }

    def search(name: String): Option[Field] =
      modelField match {
        case Field.Map(_, subfields, _) =>
          subfields.find(_.name == name).orElse {
            subfields.flatMap(_.search(name)).headOption
          }
        case _ => None
      }
  }

  def mergeAll(
      inputsA: NonEmptyList[Field],
      inputsB: NonEmptyList[Field]
  ): Either[NonEmptyList[MergeError], NonEmptyList[Field]] =
    (inputsA, inputsB) match {
      case (x, y) if x == y => Right(x)
      case _ =>
        val res = inputsA.zipWith(inputsB) {
          case (in1, in2) =>
            if (in1.name == in2.name)
              merge(in1, in2).map(List(_))
            else
              Right(List(in1, in2))
        }
        val errors = res.collect {
          case Left(value) => value
        }.flatten

        val r = res.collect {
          case Right(value) => value
        }.flatten

        if (errors.nonEmpty)
          Left(NonEmptyList.fromListUnsafe(errors))
        else
          Right(NonEmptyList.fromListUnsafe(r))
    }

  def merge(first: Field, second: Field): Either[Seq[MergeError], Field] =
    if (first == second)
      Right(first)
    else if (first.name == second.name)
      for {
        mergedShape <- mergeShapes(first.shape, second.shape)
          .map(Right(_))
          .getOrElse(Left(Seq(IncompatibleShapes(first, second))))
        field <- first -> second match {
          case (fDict: Field.Map, sDict: Field.Map) =>
            mergeSubfields(fDict.subfields, sDict.subfields).map { subfields =>
              Field.Map(first.name, subfields, mergedShape)
            }

          case (fInfo: Field.Tensor, sInfo: Field.Tensor) =>
            mergeTypes(fInfo.dtype, sInfo.dtype)
              .toRight(Seq(MergeError.incompatibleTypes(first, second)))
              .map { dtype =>
                Field.Tensor(first.name, dtype, mergedShape, fInfo.profile.orElse(sInfo.profile))
              }

          case _ => Left(Seq(MergeError.incompatibleTypes(first, second)))
        }

      } yield field
    else
      Left(Seq(NamesAreDifferent(first, second)))

  def mergeShapes(first: TensorShape, second: TensorShape): Option[TensorShape] =
    first -> second match {
      case (TensorShape.Dynamic, TensorShape.Dynamic) => Some(first)
      case (TensorShape.Dynamic, TensorShape.Static(_)) =>
        Some(second) // todo maybe reconsider any with dim?
      case (TensorShape.Static(_), TensorShape.Dynamic) =>
        Some(first) // todo maybe reconsider any with dim?

      case (TensorShape.Static(fDims), TensorShape.Static(sDims)) if fDims == sDims => Some(first)
      case (TensorShape.Static(fDims), TensorShape.Static(sDims)) if fDims.length == sDims.length =>
        val res = fDims.zip(sDims).map {
          case (fDim, sDim) if fDim == sDim => Some(fDim)
          case (fDim, sDim) if fDim == -1   => Some(sDim)
          case (fDim, sDim) if sDim == -1   => Some(fDim)
          case (_, _)                       => None
        }
        if (res.exists(_.isEmpty))
          None
        else {
          val dims = res.map(_.get)
          Some(TensorShape.Static(dims))
        }
      case _ => None
    }

  def mergeTypes(first: DataType, second: DataType): Option[DataType] =
    first -> second match {
      case (fInfo, sInfo) if fInfo == sInfo => Some(fInfo)
      case _                                => None
    }

  def mergeSubfields(
      first: List[Field],
      second: List[Field]
  ): Either[Seq[MergeError], List[Field]] = {
    val fields = second.map { field =>
      val emitterField = first
        .find(_.name == field.name)
        .map(Right(_))
        .getOrElse(Left(Seq(MergeError.fieldNotFound(field.name))))
      emitterField.flatMap(merge(_, field))
    }
    val errors = fields.collect {
      case Left(value) => value
    }.flatten

    val succ = fields.collect {
      case Right(value) => value
    }

    if (errors.nonEmpty)
      Left(errors)
    else
      Right(succ)
  }

  def appendAll(
      outputs: NonEmptyList[Field],
      inputs: NonEmptyList[Field]
  ): Either[NonEmptyList[MergeError], NonEmptyList[Field]] = {
    val fields = inputs.map { input =>
      outputs
        .find(_.name == input.name)
        .map(Right(_))
        .getOrElse(Left(Seq(MergeError.fieldNotFound(input.name))))
        .flatMap(output => merge(output, input))
    }
    val errors = fields.collect {
      case Left(value) => value
    }.flatten

    val results = fields.collect {
      case Right(value) => value
    }

    if (errors.nonEmpty)
      Left(NonEmptyList.fromListUnsafe(errors))
    else
      Right(NonEmptyList.fromListUnsafe(results))
  }

}

object ModelFieldOps extends ModelFieldOps
