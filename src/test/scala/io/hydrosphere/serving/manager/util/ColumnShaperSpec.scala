package io.hydrosphere.serving.manager.util

import io.circe.syntax._
import io.hydrosphere.serving.manager.GenericUnitTest
import io.hydrosphere.serving.manager.domain.contract.TensorShape
import io.hydrosphere.serving.manager.domain.tensor.json.ColumnShaper

class ColumnShaperSpec extends GenericUnitTest {
  describe("ColumnShaper") {
    val data = Seq(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).map(_.asJson)

    it("should shape any-shape tensor") {
      val shaper = ColumnShaper(TensorShape.Dynamic)
      assert(shaper.shape(data) === data.asJson)
    }
    it("should shape scalar-shape tensor") {
      val shaper = ColumnShaper(TensorShape.scalar)
      assert(shaper.shape(data) === 1.asJson)
    }
    it("should shape full-shaped vector") {
      val shaper = ColumnShaper(TensorShape.vector(10))
      assert(shaper.shape(data) === data.toVector.asJson)
    }
    it("should shape full-shaped matrix") {
      val shaper = ColumnShaper(TensorShape.mat(2, 5))
      assert(
        shaper.shape(data) === List(
          List(1, 2, 3, 4, 5),
          List(6, 7, 8, 9, 10)
        ).asJson
      )
    }
//    it("should shape partially shaped tensor") {
//      val data = Seq(1,2,3,4,5,6,7,8,9,10).map(JsNumber.apply)
//      val shaper = ColumnShaper(TensorShape.mat(-1,5))
//      assert(shaper.shape(data) === JsArray(
//        JsArray(JsNumber(1),JsNumber(2),JsNumber(3),JsNumber(4),JsNumber(5)),
//        JsArray(JsNumber(6),JsNumber(7),JsNumber(8),JsNumber(9),JsNumber(10))
//      ))
//    }
  }
}
