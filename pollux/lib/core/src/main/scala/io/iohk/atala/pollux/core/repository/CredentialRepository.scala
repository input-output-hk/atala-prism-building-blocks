package io.iohk.atala.pollux.core.repository

import io.iohk.atala.pollux.core.model.EncodedJWTCredential
import io.iohk.atala.pollux.core.model.IssueCredentialRecord
import io.iohk.atala.prism.crypto.MerkleInclusionProof
import zio.*

import java.util.UUID

trait CredentialRepository[F[_]] {
  def createCredentials(batchId: String, credentials: Seq[EncodedJWTCredential]): F[Unit]
  def getCredentials(batchId: String): F[Seq[EncodedJWTCredential]]
  def createIssueCredentialRecord(record: IssueCredentialRecord): F[Int]
  def getIssueCredentialRecords(): F[Seq[IssueCredentialRecord]]
  def getIssueCredentialRecordsByState(state: IssueCredentialRecord.State): F[Seq[IssueCredentialRecord]]
  def getIssueCredentialRecord(id: UUID): F[Option[IssueCredentialRecord]]
  def updateCredentialRecordState(id: UUID, from: IssueCredentialRecord.State, to: IssueCredentialRecord.State): F[Int]
  def updateCredentialRecordStateAndProofByCredentialIdBulk(
      idsStatesAndProofs: Seq[(UUID, IssueCredentialRecord.State, MerkleInclusionProof)]
  ): F[Int]
}
