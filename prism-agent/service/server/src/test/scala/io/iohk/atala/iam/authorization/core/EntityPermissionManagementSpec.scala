package io.iohk.atala.iam.authorization.core

import io.iohk.atala.agent.walletapi.crypto.ApolloSpecHelper
import io.iohk.atala.agent.walletapi.model.Entity
import io.iohk.atala.agent.walletapi.model.Wallet
import io.iohk.atala.agent.walletapi.service.EntityService
import io.iohk.atala.agent.walletapi.service.EntityServiceImpl
import io.iohk.atala.agent.walletapi.service.WalletManagementService
import io.iohk.atala.agent.walletapi.service.WalletManagementServiceImpl
import io.iohk.atala.agent.walletapi.sql.JdbcEntityRepository
import io.iohk.atala.agent.walletapi.sql.JdbcWalletNonSecretStorage
import io.iohk.atala.agent.walletapi.sql.JdbcWalletSecretStorage
import io.iohk.atala.iam.authorization.core.PermissionManagement.Error.ServiceError
import io.iohk.atala.iam.authorization.core.PermissionManagement.Error.WalletNotFoundById
import io.iohk.atala.shared.models.WalletAdministrationContext
import io.iohk.atala.shared.models.WalletId
import io.iohk.atala.sharedtest.containers.PostgresTestContainerSupport
import io.iohk.atala.test.container.DBTestUtils
import zio.*
import zio.test.*
import zio.test.Assertion.*

object EntityPermissionManagementSpec extends ZIOSpecDefault, PostgresTestContainerSupport, ApolloSpecHelper {

  override def spec = {
    val s = suite("EntityPermissionManagementSpec")(
      successfulCasesSuite,
      failureCasesSuite,
      multitenantSuite
    ) @@ TestAspect.before(DBTestUtils.runMigrationAgentDB)

    s.provide(
      EntityPermissionManagementService.layer,
      EntityServiceImpl.layer,
      WalletManagementServiceImpl.layer,
      JdbcEntityRepository.layer,
      JdbcWalletNonSecretStorage.layer,
      JdbcWalletSecretStorage.layer,
      contextAwareTransactorLayer,
      systemTransactorLayer,
      pgContainerLayer,
      apolloLayer
    )
  }.provide(Runtime.removeDefaultLoggers)

  private val successfulCasesSuite = suite("Successful cases")(
    test("grant wallet access to the user") {
      for {
        entityService <- ZIO.service[EntityService]
        permissionService <- ZIO.service[PermissionManagement.Service[Entity]]
        walletService <- ZIO.service[WalletManagementService]
        wallet1 <- walletService
          .createWallet(Wallet("test"))
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        wallet2 <- walletService
          .createWallet(Wallet("test2"))
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        entity <- entityService
          .create(Entity("alice", wallet1.id.toUUID))
        _ <- permissionService
          .grantWalletToUser(wallet2.id, entity)
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        entity <- entityService.getById(entity.id)
        permissions <- permissionService
          .listWalletPermissions(entity)
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
      } yield assert(permissions.head)(equalTo(wallet2.id))
    },
  )

  private val failureCasesSuite = suite("Failure Cases")(
    test("revoke wallet is not support") {
      for {
        entityService <- ZIO.service[EntityService]
        permissionService <- ZIO.service[PermissionManagement.Service[Entity]]
        walletService <- ZIO.service[WalletManagementService]
        wallet1 <- walletService
          .createWallet(Wallet("test"))
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        entity <- entityService
          .create(Entity("alice", wallet1.id.toUUID))
        exit <- permissionService
          .revokeWalletFromUser(wallet1.id, entity)
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
          .debug("revokeWallet")
          .exit
      } yield assert(exit)(fails(isSubtype[ServiceError](anything)))
    }
  )

  private val multitenantSuite = suite("multi-tenant cases")(
    test("grant wallet access to the user by self-service") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      for {
        entityService <- ZIO.service[EntityService]
        permissionService <- ZIO.service[PermissionManagement.Service[Entity]]
        walletService <- ZIO.service[WalletManagementService]
        wallet1 <- walletService
          .createWallet(Wallet("test", walletId1))
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        wallet2 <- walletService
          .createWallet(Wallet("test2", walletId2))
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        entity <- entityService.create(Entity("alice", wallet1.id.toUUID))
        _ <- permissionService
          .grantWalletToUser(wallet2.id, entity)
          .provide(ZLayer.succeed(WalletAdministrationContext.SelfService(Seq(walletId2))))
        entity <- entityService.getById(entity.id)
        permissions <- permissionService
          .listWalletPermissions(entity)
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
      } yield assert(permissions.head)(equalTo(wallet2.id))
    },
    test("grant wallet access to non-permitted wallet by self-service is not allowed") {
      val walletId1 = WalletId.random
      val walletId2 = WalletId.random
      for {
        entityService <- ZIO.service[EntityService]
        permissionService <- ZIO.service[PermissionManagement.Service[Entity]]
        walletService <- ZIO.service[WalletManagementService]
        wallet1 <- walletService
          .createWallet(Wallet("test", walletId1))
          .provide(ZLayer.succeed(WalletAdministrationContext.Admin()))
        entity <- entityService.create(Entity("alice", wallet1.id.toUUID))
        exit <- permissionService
          .grantWalletToUser(walletId2, entity)
          .provide(ZLayer.succeed(WalletAdministrationContext.SelfService(Seq(walletId1))))
          .exit
      } yield assert(exit)(fails(isSubtype[WalletNotFoundById](anything)))
    },
  )

}
