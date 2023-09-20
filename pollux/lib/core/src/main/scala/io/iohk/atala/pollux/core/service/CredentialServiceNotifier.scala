package io.iohk.atala.pollux.core.service

import io.circe.Json
import io.iohk.atala.castor.core.model.did.CanonicalPrismDID
import io.iohk.atala.event.notification.*
import io.iohk.atala.mercury.model.DidId
import io.iohk.atala.mercury.protocol.issuecredential.{IssueCredential, OfferCredential, RequestCredential}
import io.iohk.atala.pollux.core.model.error.CredentialServiceError
import io.iohk.atala.pollux.core.model.{DidCommID, IssueCredentialRecord}
import io.iohk.atala.pollux.vc.jwt.{Issuer, JWT, PresentationPayload, W3cCredentialPayload}
import io.iohk.atala.shared.models.WalletAccessContext
import zio.{URLayer, ZIO, ZLayer}

import java.time.Instant
import java.util.UUID

class CredentialServiceNotifier(
    svc: CredentialService,
    eventNotificationService: EventNotificationService
) extends CredentialService {

  private val issueCredentialRecordUpdatedEvent = "IssueCredentialRecordUpdated"

  override def createJWTIssueCredentialRecord(
      pairwiseIssuerDID: DidId,
      pairwiseHolderDID: DidId,
      thid: DidCommID,
      maybeSchemaId: Option[String],
      claims: Json,
      validityPeriod: Option[Double],
      automaticIssuance: Option[Boolean],
      issuingDID: CanonicalPrismDID
  ): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
    notifyOnSuccess(
      svc.createJWTIssueCredentialRecord(
        pairwiseIssuerDID,
        pairwiseHolderDID,
        thid,
        maybeSchemaId,
        claims,
        validityPeriod,
        automaticIssuance,
        issuingDID
      )
    )

  override def createAnonCredsIssueCredentialRecord(
      pairwiseIssuerDID: DidId,
      pairwiseHolderDID: DidId,
      thid: DidCommID,
      credentialDefinitionId: UUID,
      claims: Json,
      validityPeriod: Option[Double],
      automaticIssuance: Option[Boolean],
      issuingDID: CanonicalPrismDID,
      restServiceUrl: _root_.java.lang.String
  ): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
    notifyOnSuccess(
      svc.createAnonCredsIssueCredentialRecord(
        pairwiseIssuerDID,
        pairwiseHolderDID,
        thid,
        credentialDefinitionId,
        claims,
        validityPeriod,
        automaticIssuance,
        issuingDID,
        restServiceUrl
      )
    )

  override def markOfferSent(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
    notifyOnSuccess(svc.markOfferSent(recordId))

  override def receiveCredentialOffer(
      offer: OfferCredential
  ): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
    notifyOnSuccess(svc.receiveCredentialOffer(offer))

  override def acceptCredentialOffer(
      recordId: DidCommID,
      subjectId: Option[String]
  ): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
    notifyOnSuccess(svc.acceptCredentialOffer(recordId, subjectId))

  override def generateJWTCredentialRequest(
      recordId: DidCommID,
      signedPresentation: JWT
  ): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
    notifyOnSuccess(svc.generateJWTCredentialRequest(recordId, signedPresentation))

  override def generateAnonCredsCredentialRequest(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
    notifyOnSuccess(svc.generateAnonCredsCredentialRequest(recordId))

  override def markRequestSent(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
    notifyOnSuccess(svc.markRequestSent(recordId))

  override def receiveCredentialRequest(
      request: RequestCredential
  ): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
    notifyOnSuccess(svc.receiveCredentialRequest(request))

  override def acceptCredentialRequest(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
    notifyOnSuccess(svc.acceptCredentialRequest(recordId))

  override def markCredentialGenerated(
      recordId: DidCommID,
      issueCredential: IssueCredential
  ): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
    notifyOnSuccess(svc.markCredentialGenerated(recordId, issueCredential))

  override def markCredentialSent(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
    notifyOnSuccess(svc.markCredentialSent(recordId))

  override def receiveCredentialIssue(
      issueCredential: IssueCredential
  ): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
    notifyOnSuccess(svc.receiveCredentialIssue(issueCredential))

  private[this] def notifyOnSuccess[R](effect: ZIO[R, CredentialServiceError, IssueCredentialRecord]) =
    for {
      record <- effect
      _ <- notify(record)
    } yield record

  private[this] def notify(record: IssueCredentialRecord) = {
    val result = for {
      walletId <- ZIO.serviceWith[WalletAccessContext](_.walletId)
      producer <- eventNotificationService.producer[IssueCredentialRecord]("Issue")
      _ <- producer.send(Event(issueCredentialRecordUpdatedEvent, record, walletId))
    } yield ()
    result.catchAll(e => ZIO.logError(s"Notification service error: $e"))
  }

  override def createPresentationPayload(
      recordId: DidCommID,
      subject: Issuer
  ): ZIO[WalletAccessContext, CredentialServiceError, PresentationPayload] =
    svc.createPresentationPayload(recordId, subject)

  override def createJWTCredentialPayloadFromRecord(
      record: IssueCredentialRecord,
      issuer: Issuer,
      issuanceDate: Instant
  ): ZIO[WalletAccessContext, CredentialServiceError, W3cCredentialPayload] =
    svc.createJWTCredentialPayloadFromRecord(record, issuer, issuanceDate)

  override def generateAnonCredsCredential(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, CredentialServiceError, IssueCredentialRecord] =
    svc.generateAnonCredsCredential(recordId)

  override def reportProcessingFailure(
      recordId: DidCommID,
      failReason: Option[_root_.java.lang.String]
  ): ZIO[WalletAccessContext, CredentialServiceError, Unit] =
    svc.reportProcessingFailure(recordId, failReason)

  override def getIssueCredentialRecord(
      recordId: DidCommID
  ): ZIO[WalletAccessContext, CredentialServiceError, Option[IssueCredentialRecord]] =
    svc.getIssueCredentialRecord(recordId)

  override def getIssueCredentialRecordByThreadId(
      thid: DidCommID
  ): ZIO[WalletAccessContext, CredentialServiceError, Option[IssueCredentialRecord]] =
    svc.getIssueCredentialRecordByThreadId(thid)

  override def getIssueCredentialRecords(
      offset: Option[Int] = None,
      limit: Option[Int] = None
  ): ZIO[WalletAccessContext, CredentialServiceError, (Seq[IssueCredentialRecord], Int)] =
    svc.getIssueCredentialRecords(offset, limit)

  override def getIssueCredentialRecordsByStates(
      ignoreWithZeroRetries: Boolean,
      limit: Int,
      states: IssueCredentialRecord.ProtocolState*
  ): ZIO[WalletAccessContext, CredentialServiceError, Seq[IssueCredentialRecord]] =
    svc.getIssueCredentialRecordsByStates(ignoreWithZeroRetries, limit, states: _*)
}

object CredentialServiceNotifier {
  val layer: URLayer[CredentialService & EventNotificationService, CredentialServiceNotifier] =
    ZLayer.fromFunction(CredentialServiceNotifier(_, _))
}
