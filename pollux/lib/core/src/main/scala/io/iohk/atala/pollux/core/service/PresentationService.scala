package io.iohk.atala.pollux.core.service

import cats.*
import cats.data.*
import cats.implicits.*
import cats.syntax.all.*
import com.google.protobuf.ByteString
import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*
import io.iohk.atala.mercury.DidAgent
import io.iohk.atala.mercury.model.*
import io.iohk.atala.mercury.protocol.issuecredential.IssueCredential
import io.iohk.atala.mercury.protocol.presentproof.*
import io.iohk.atala.pollux.core.model.*
import io.iohk.atala.pollux.core.model.error.PresentationError
import io.iohk.atala.pollux.core.model.error.PresentationError.*
import io.iohk.atala.pollux.core.model.presentation.*
import io.iohk.atala.pollux.core.repository.{CredentialRepository, PresentationRepository}
import io.iohk.atala.pollux.vc.jwt.*
import zio.*

import java.rmi.UnexpectedException
import java.security.{KeyPairGenerator, PublicKey, SecureRandom}
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util as ju
import java.util.UUID

trait PresentationService {

  def extractIdFromCredential(credential: W3cCredentialPayload): Option[UUID]

  def createPresentationRecord(
      thid: DidCommID,
      subjectDid: DidId,
      connectionId: Option[String],
      proofTypes: Seq[ProofType],
      options: Option[io.iohk.atala.pollux.core.model.presentation.Options]
  ): IO[PresentationError, PresentationRecord]

  def getPresentationRecords(): IO[PresentationError, Seq[PresentationRecord]]

  def createPresentationPayloadFromRecord(
      record: DidCommID,
      issuer: Issuer,
      issuanceDate: Instant
  ): IO[PresentationError, PresentationPayload]

  def getPresentationRecordsByStates(
      ignoreWithZeroRetries: Boolean = true,
      state: PresentationRecord.ProtocolState*
  ): IO[PresentationError, Seq[PresentationRecord]]

  def getPresentationRecord(recordId: DidCommID): IO[PresentationError, Option[PresentationRecord]]

  def receiveRequestPresentation(
      connectionId: Option[String],
      request: RequestPresentation
  ): IO[PresentationError, PresentationRecord]

  def acceptRequestPresentation(
      recordId: DidCommID,
      crecentialsToUse: Seq[String]
  ): IO[PresentationError, Option[PresentationRecord]]

  def rejectRequestPresentation(recordId: DidCommID): IO[PresentationError, Option[PresentationRecord]]

  def receiveProposePresentation(request: ProposePresentation): IO[PresentationError, Option[PresentationRecord]]

  def acceptProposePresentation(recordId: DidCommID): IO[PresentationError, Option[PresentationRecord]]

  def receivePresentation(presentation: Presentation): IO[PresentationError, Option[PresentationRecord]]

  def acceptPresentation(recordId: DidCommID): IO[PresentationError, Option[PresentationRecord]]

  def rejectPresentation(recordId: DidCommID): IO[PresentationError, Option[PresentationRecord]]

  def markRequestPresentationSent(recordId: DidCommID): IO[PresentationError, Option[PresentationRecord]]

  def markRequestPresentationRejected(recordId: DidCommID): IO[PresentationError, Option[PresentationRecord]]

  def markProposePresentationSent(recordId: DidCommID): IO[PresentationError, Option[PresentationRecord]]

  def markPresentationSent(recordId: DidCommID): IO[PresentationError, Option[PresentationRecord]]

  def markPresentationGenerated(
      recordId: DidCommID,
      presentation: Presentation
  ): IO[PresentationError, Option[PresentationRecord]]

  def markPresentationVerified(recordId: DidCommID): IO[PresentationError, Option[PresentationRecord]]

  def markPresentationRejected(recordId: DidCommID): IO[PresentationError, Option[PresentationRecord]]

  def markPresentationAccepted(recordId: DidCommID): IO[PresentationError, Option[PresentationRecord]]

  def markPresentationVerificationFailed(recordId: DidCommID): IO[PresentationError, Option[PresentationRecord]]

  def reportProcessingFailure(recordId: DidCommID, failReason: Option[String]): IO[PresentationError, Unit]

}
