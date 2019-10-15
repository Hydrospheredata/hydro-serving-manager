package io.hydrosphere.serving.manager.domain

import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.contract.model_signature.ModelSignature

/**
  * Contract with guarantee of the presence of the signature, unlike ModelContract.
  * @param predict
  */
case class Contract(predict: ModelSignature, modelName: String = "") {
  def toProto: ModelContract = {
    ModelContract(predict = Some(predict))
  }
}