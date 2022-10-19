package io.iohk.atala.agent.keymanagement.crypto

import io.iohk.atala.agent.keymanagement.model.ECCoordinates.ECCoordinate
import io.iohk.atala.agent.keymanagement.model.{ECKeyPair, ECPoint, ECPrivateKey, ECPublicKey}
import io.iohk.atala.castor.core.model.did.EllipticCurve
import io.iohk.atala.prism.crypto.EC

import scala.collection.immutable.ArraySeq
import zio.*

object KeyGeneratorWrapper {

  def generateECKeyPair(curve: EllipticCurve): Task[ECKeyPair] = {
    curve match {
      case EllipticCurve.SECP256K1 =>
        ZIO.attempt {
          val prism14KeyPair = EC.INSTANCE.generateKeyPair()
          ECKeyPair.fromPrism14ECKeyPair(prism14KeyPair)
        }
    }
  }

}
