package org.hyperledger.identus.pollux.core.repository

import org.hyperledger.identus.mercury.protocol.presentproof._
import org.hyperledger.identus.pollux.core.model._
import org.hyperledger.identus.pollux.core.model.PresentationRecord.ProtocolState
import org.hyperledger.identus.shared.models.WalletAccessContext
import zio._

trait PresentationRepository {
  def createPresentationRecord(record: PresentationRecord): RIO[WalletAccessContext, Int]
  def getPresentationRecords(ignoreWithZeroRetries: Boolean): RIO[WalletAccessContext, Seq[PresentationRecord]]
  def getPresentationRecord(recordId: DidCommID): RIO[WalletAccessContext, Option[PresentationRecord]]
  def getPresentationRecordsByStates(
      ignoreWithZeroRetries: Boolean,
      limit: Int,
      states: PresentationRecord.ProtocolState*
  ): RIO[WalletAccessContext, Seq[PresentationRecord]]

  def getPresentationRecordsByStatesForAllWallets(
      ignoreWithZeroRetries: Boolean,
      limit: Int,
      states: PresentationRecord.ProtocolState*
  ): Task[Seq[PresentationRecord]]

  def getPresentationRecordByThreadId(thid: DidCommID): RIO[WalletAccessContext, Option[PresentationRecord]]

  def updatePresentationRecordProtocolState(
      recordId: DidCommID,
      from: PresentationRecord.ProtocolState,
      to: PresentationRecord.ProtocolState
  ): RIO[WalletAccessContext, Int]

  def updateWithRequestPresentation(
      recordId: DidCommID,
      request: RequestPresentation,
      protocolState: ProtocolState
  ): RIO[WalletAccessContext, Int]
  def updateWithProposePresentation(
      recordId: DidCommID,
      request: ProposePresentation,
      protocolState: ProtocolState
  ): RIO[WalletAccessContext, Int]
  def updateWithPresentation(
      recordId: DidCommID,
      presentation: Presentation,
      protocolState: ProtocolState
  ): RIO[WalletAccessContext, Int]
  def updatePresentationWithCredentialsToUse(
      recordId: DidCommID,
      credentialsToUse: Option[Seq[String]],
      protocolState: ProtocolState
  ): RIO[WalletAccessContext, Int]

  def updateSDJWTPresentationWithCredentialsToUse(
      recordId: DidCommID,
      credentialsToUse: Option[Seq[String]],
      sdJwtClaimsToDisclose: Option[SdJwtCredentialToDisclose],
      protocolState: ProtocolState
  ): RIO[WalletAccessContext, Int]

  def updateAnoncredPresentationWithCredentialsToUse(
      recordId: DidCommID,
      anoncredCredentialsToUseJsonSchemaId: Option[String],
      anoncredCredentialsToUse: Option[AnoncredCredentialProofs],
      protocolState: ProtocolState
  ): RIO[WalletAccessContext, Int]

  def updateAfterFail(
      recordId: DidCommID,
      failReason: Option[String]
  ): RIO[WalletAccessContext, Int]
}
