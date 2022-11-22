package io.iohk.atala.connect.core.service

import io.iohk.atala.connect.core.repository.ConnectionRepository
import io.iohk.atala.mercury.DidComm
import zio._
import io.iohk.atala.connect.core.model.error.ConnectionError
import io.iohk.atala.connect.core.model.error.ConnectionError._
import io.iohk.atala.connect.core.model.ConnectionRecord
import io.iohk.atala.connect.core.model.ConnectionRecord._
import io.iohk.atala.mercury.protocol.connection.ConnectionRequest
import java.util.UUID
import io.iohk.atala.mercury._
import io.iohk.atala.mercury.model.DidId
import java.time.Instant
import java.rmi.UnexpectedException
import io.iohk.atala.mercury.protocol.invitation.v2.Invitation
import io.iohk.atala.mercury.protocol.connection.ConnectionResponse

private class ConnectionServiceImpl(
    connectionRepository: ConnectionRepository[Task],
    didComm: DidComm
) extends ConnectionService {

  override def createConnectionInvitation(label: Option[String]): IO[ConnectionError, ConnectionRecord] =
    for {
      recordId <- ZIO.succeed(UUID.randomUUID)
      invitation <- ZIO.succeed(createDidCommInvitation(recordId, didComm.myDid))
      record <- ZIO.succeed(
        ConnectionRecord(
          id = recordId,
          createdAt = Instant.now,
          updatedAt = None,
          thid = None,
          label = label,
          role = ConnectionRecord.Role.Inviter,
          protocolState = ConnectionRecord.ProtocolState.InvitationGenerated,
          invitation = invitation,
          connectionRequest = None,
          connectionResponse = None
        )
      )
      count <- connectionRepository
        .createConnectionRecord(record)
        .flatMap {
          case 1 => ZIO.succeed(())
          case n => ZIO.fail(UnexpectedException(s"Invalid row count result: $n"))
        }
        .mapError(RepositoryError.apply)
    } yield record

  override def getConnectionRecords(): IO[ConnectionError, Seq[ConnectionRecord]] = {
    for {
      records <- connectionRepository
        .getConnectionRecords()
        .mapError(RepositoryError.apply)
    } yield records
  }

  override def getConnectionRecord(recordId: UUID): IO[ConnectionError, Option[ConnectionRecord]] = {
    for {
      record <- connectionRepository
        .getConnectionRecord(recordId)
        .mapError(RepositoryError.apply)
    } yield record
  }

  override def deleteConnectionRecord(recordId: UUID): IO[ConnectionError, Int] = ???

  override def receiveConnectionInvitation(invitation: Invitation): IO[ConnectionError, ConnectionRecord] =
    for {
      record <- ZIO.succeed(
        ConnectionRecord(
          id = UUID.randomUUID(),
          createdAt = Instant.now,
          updatedAt = None,
          thid = None,
          label = None,
          role = ConnectionRecord.Role.Invitee,
          protocolState = ConnectionRecord.ProtocolState.InvitationReceived,
          invitation = invitation,
          connectionRequest = None,
          connectionResponse = None
        )
      )
      count <- connectionRepository
        .createConnectionRecord(record)
        .flatMap {
          case 1 => ZIO.succeed(())
          case n => ZIO.fail(UnexpectedException(s"Invalid row count result: $n"))
        }
        .mapError(RepositoryError.apply)
    } yield record

  override def acceptConnectionInvitation(recordId: UUID): IO[ConnectionError, Option[ConnectionRecord]] =
    for {
      maybeRecord <- connectionRepository
        .getConnectionRecord(recordId)
        .mapError(RepositoryError.apply)
      record <- ZIO
        .fromOption(maybeRecord)
        .mapError(_ => RecordIdNotFound(recordId))
      request = createDidCommConnectionRequest(record.invitation)
      count <- connectionRepository
        .updateWithConnectionRequest(recordId, request, ProtocolState.ConnectionRequestPending)
        .mapError(RepositoryError.apply)
      _ <- count match
        case 1 => ZIO.succeed(())
        case n => ZIO.fail(RecordIdNotFound(recordId))
      record <- connectionRepository
        .getConnectionRecord(record.id)
        .mapError(RepositoryError.apply)
    } yield record

  override def markConnectionRequestSent(recordId: UUID): IO[ConnectionError, Option[ConnectionRecord]] =
    updateConnectionProtocolState(
      recordId,
      ProtocolState.ConnectionRequestPending,
      ProtocolState.ConnectionRequestSent
    )

  override def receiveConnectionRequest(request: ConnectionRequest): IO[ConnectionError, Option[ConnectionRecord]] =
    for {
      record <- getRecordFromThreadId(request.thid)
      _ <- connectionRepository
        .updateWithConnectionRequest(record.id, request, ProtocolState.ConnectionRequestReceived)
        .flatMap {
          case 1 => ZIO.succeed(())
          case n => ZIO.fail(UnexpectedException(s"Invalid row count result: $n"))
        }
        .mapError(RepositoryError.apply)
      record <- connectionRepository
        .getConnectionRecord(record.id)
        .mapError(RepositoryError.apply)
    } yield record

  override def markConnectionResponseSent(recordId: UUID): IO[ConnectionError, Option[ConnectionRecord]] =
    updateConnectionProtocolState(
      recordId,
      ProtocolState.ConnectionResponsePending,
      ProtocolState.ConnectionResponseSent
    )

  override def receiveConnectionResponse(response: ConnectionResponse): IO[ConnectionError, Option[ConnectionRecord]] =
    for {
      record <- getRecordFromThreadId(response.thid)
      _ <- connectionRepository
        .updateWithConnectionResponse(record.id, response, ProtocolState.ConnectionResponseReceived)
        .flatMap {
          case 1 => ZIO.succeed(())
          case n => ZIO.fail(UnexpectedException(s"Invalid row count result: $n"))
        }
        .mapError(RepositoryError.apply)
      record <- connectionRepository
        .getConnectionRecord(record.id)
        .mapError(RepositoryError.apply)
    } yield record

  private[this] def createDidCommInvitation(thid: UUID, from: DidId): Invitation = {
    Invitation(
      id = thid.toString,
      from = from,
      body = Invitation.Body(goal_code = "connect", goal = "Establish a trust connection between two peers", Nil)
    )
  }

  private[this] def createDidCommConnectionRequest(invitation: Invitation): ConnectionRequest = {
    ConnectionRequest(
      from = didComm.myDid,
      to = invitation.from,
      thid = Some(invitation.id),
      body = ConnectionRequest.Body(goal_code = Some("Connect"))
    )
  }

  private[this] def updateConnectionProtocolState(
      recordId: UUID,
      from: ProtocolState,
      to: ProtocolState
  ): IO[ConnectionError, Option[ConnectionRecord]] = {
    for {
      _ <- connectionRepository
        .updateConnectionProtocolState(recordId, from, to)
        .flatMap {
          case 1 => ZIO.succeed(())
          case n => ZIO.fail(UnexpectedException(s"Invalid row count result: $n"))
        }
        .mapError(RepositoryError.apply)
      record <- connectionRepository
        .getConnectionRecord(recordId)
        .mapError(RepositoryError.apply)
    } yield record
  }

  private[this] def getRecordFromThreadId(
      thid: Option[String]
  ): IO[ConnectionError, ConnectionRecord] = {
    for {
      thid <- ZIO
        .fromOption(thid)
        .mapError(_ => UnexpectedError("No `thid` found in credential request"))
        .map(UUID.fromString)
      maybeRecord <- connectionRepository
        .getConnectionRecordByThreadId(thid)
        .mapError(RepositoryError.apply)
      record <- ZIO
        .fromOption(maybeRecord)
        .mapError(_ => ThreadIdNotFound(thid))
    } yield record
  }

}

object ConnectionServiceImpl {
  val layer: URLayer[ConnectionRepository[Task] & DidComm, ConnectionService] =
    ZLayer.fromFunction(ConnectionServiceImpl(_, _))
}
