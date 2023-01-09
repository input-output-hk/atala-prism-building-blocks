package io.iohk.atala.agent.walletapi.util

import io.iohk.atala.agent.walletapi.crypto.KeyGeneratorWrapper
import io.iohk.atala.agent.walletapi.model.{DIDPublicKeyTemplate, ECKeyPair, ManagedDIDTemplate}
import io.iohk.atala.agent.walletapi.model.error.CreateManagedDIDError
import io.iohk.atala.castor.core.model.did.{
  InternalKeyPurpose,
  InternalPublicKey,
  PrismDIDOperation,
  PublicKey,
  PublicKeyData,
  EllipticCurve
}
import io.iohk.atala.shared.models.Base64UrlStrings.*
import io.iohk.atala.shared.models.HexStrings.*
import zio.*

private[walletapi] final case class CreateDIDSecret(
    keyPairs: Map[String, ECKeyPair],
    internalKeyPairs: Map[String, ECKeyPair]
)

object OperationFactory {

  def makeCreateOperation(
      masterKeyId: String,
      curve: EllipticCurve,
      keyGenerator: () => Task[ECKeyPair]
  )(
      didTemplate: ManagedDIDTemplate
  ): IO[CreateManagedDIDError, (PrismDIDOperation.Create, CreateDIDSecret)] = {
    for {
      keys <- ZIO
        .foreach(didTemplate.publicKeys.sortBy(_.id))(generateKeyPairAndPublicKey(curve, keyGenerator))
        .mapError(CreateManagedDIDError.KeyGenerationError.apply)
      masterKey <- generateKeyPairAndInternalPublicKey(curve, keyGenerator)(masterKeyId, InternalKeyPurpose.Master)
        .mapError(
          CreateManagedDIDError.KeyGenerationError.apply
        )
      operation = PrismDIDOperation.Create(
        publicKeys = keys.map(_._2),
        internalKeys = Seq(masterKey._2),
        services = didTemplate.services
      )
      secret = CreateDIDSecret(
        keyPairs = keys.map { case (keyPair, template) => template.id -> keyPair }.toMap,
        internalKeyPairs = Map(masterKey._2.id -> masterKey._1)
      )
    } yield operation -> secret
  }

  private def generateKeyPairAndPublicKey(curve: EllipticCurve, keyGenerator: () => Task[ECKeyPair])(
      template: DIDPublicKeyTemplate
  ): Task[(ECKeyPair, PublicKey)] = {
    for {
      keyPair <- keyGenerator()
      publicKey = PublicKey(template.id, template.purpose, toPublicKeyData(curve, keyPair))
    } yield (keyPair, publicKey)
  }

  private def generateKeyPairAndInternalPublicKey(curve: EllipticCurve, keyGenerator: () => Task[ECKeyPair])(
      id: String,
      purpose: InternalKeyPurpose
  ): Task[(ECKeyPair, InternalPublicKey)] = {
    for {
      keyPair <- keyGenerator()
      internalPublicKey = InternalPublicKey(id, purpose, toPublicKeyData(curve, keyPair))
    } yield (keyPair, internalPublicKey)
  }

  private def toPublicKeyData(curve: EllipticCurve, keyPair: ECKeyPair): PublicKeyData = PublicKeyData.ECKeyData(
    crv = curve,
    x = Base64UrlString.fromByteArray(keyPair.publicKey.p.x.toPaddedByteArray(curve)),
    y = Base64UrlString.fromByteArray(keyPair.publicKey.p.y.toPaddedByteArray(curve))
  )

}
