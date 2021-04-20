package io.hydrosphere.serving.manager.domain.servable
import io.hydrosphere.serving.manager.domain.servable.Servable.Status

object ServableStatusComposer {
  type CombineResult = (List[String], Servable.Status);

  def combineStatuses(servables: List[Servable]): (List[String], Servable.Status) = {
    val startValue = (List[String](), Servable.Status.Starting)

    def combine(servable: Servable, res: CombineResult): CombineResult = {
      val (prevMessage, prevStatus) = res
      val currentStatus = servable.status

      def mergeMessage(s: Option[String], as: List[String]): List[String] = s match {
        case Some(value) => value :: as
        case None => as
      }

      (prevStatus, currentStatus) match {
        case (Status.NotServing, _) => (mergeMessage(servable.message, prevMessage), Status.NotServing)
        case (Status.NotAvailable, Status.NotServing) => (mergeMessage(servable.message, prevMessage), Status.NotServing)
        case (Status.NotAvailable, _) => (mergeMessage(servable.message, prevMessage), prevStatus)
        case (Status.Serving, _) => (mergeMessage(servable.message, prevMessage), currentStatus)
        case (Status.Starting, _) => (mergeMessage(servable.message, prevMessage), currentStatus)
      }
    }

    servables.foldRight[CombineResult](startValue)(combine)
  }
}
