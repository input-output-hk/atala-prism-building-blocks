package io.iohk.atala.agent.walletapi.model.error

import io.iohk.atala.castor.core.model.did.PrismDID
import io.iohk.atala.castor.core.model.error.DIDOperationError

sealed trait UpdateManagedDIDError

object UpdateManagedDIDError {
  final case class UpdateImmutableDID(did: PrismDID) extends UpdateManagedDIDError
  final case class WalletStorageError(cause: Throwable) extends UpdateManagedDIDError
  final case class OperationError(cause: DIDOperationError) extends UpdateManagedDIDError
}
