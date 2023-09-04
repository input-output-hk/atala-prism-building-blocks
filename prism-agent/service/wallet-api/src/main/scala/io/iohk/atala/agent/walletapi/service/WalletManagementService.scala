package io.iohk.atala.agent.walletapi.service

import io.iohk.atala.agent.walletapi.model.Wallet
import io.iohk.atala.agent.walletapi.model.WalletSeed
import io.iohk.atala.shared.models.WalletId
import zio.*
import io.iohk.atala.event.notification.EventNotificationConfig
import io.iohk.atala.shared.models.WalletAccessContext

sealed trait WalletManagementServiceError extends Throwable
object WalletManagementServiceError {
  final case class SeedGenerationError(cause: Throwable) extends WalletManagementServiceError
  final case class WalletStorageError(cause: Throwable) extends WalletManagementServiceError
}

trait WalletManagementService {
  def createWallet(wallet: Wallet, seed: Option[WalletSeed] = None): IO[WalletManagementServiceError, Wallet]

  def getWallet(walletId: WalletId): IO[WalletManagementServiceError, Option[Wallet]]

  /** @return A tuple containing a list of items and a count of total items */
  def listWallets(
      offset: Option[Int] = None,
      limit: Option[Int] = None
  ): IO[WalletManagementServiceError, (Seq[Wallet], Int)]

  def walletNotification: RIO[WalletAccessContext, Seq[EventNotificationConfig]]
}
