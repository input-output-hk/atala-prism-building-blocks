package io.iohk.atala.connect.core.model.error

import java.util.UUID

sealed trait ConnectionServiceError

object ConnectionServiceError {
  final case class InvitationParsingError(msg: String) extends ConnectionServiceError
  final case class InvitationAlreadyReceived(msg: String) extends ConnectionServiceError
  final case class RecordIdNotFound(recordId: UUID) extends ConnectionServiceError
  final case class ThreadIdNotFound(thid: String) extends ConnectionServiceError
  final case class ThreadIdMissingInMessage() extends ConnectionServiceError
  final case class InvalidStateForOperation(msg: String) extends ConnectionServiceError
  final case class InvitationExpired(msg: String) extends ConnectionServiceError
}
