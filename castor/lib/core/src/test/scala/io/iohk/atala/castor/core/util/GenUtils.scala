package io.iohk.atala.castor.core.util

import io.iohk.atala.castor.core.model.did.*
import io.iohk.atala.prism.crypto.EC
import io.iohk.atala.shared.models.Base64UrlStrings.Base64UrlString
import io.lemonlabs.uri.Uri
import zio.*
import zio.test.Gen

object GenUtils {

  val uriFragment: Gen[Any, String] = Gen.stringBounded(1, 20)(Gen.asciiChar).filter(UriUtils.isValidUriFragment)

  val uri: Gen[Any, String] =
    for {
      scheme <- Gen.fromIterable(Seq("http", "https", "ftp", "ws", "wss"))
      host <- Gen.alphaNumericStringBounded(1, 10)
      path <- Gen.listOfBounded(0, 5)(Gen.alphaNumericStringBounded(1, 10)).map(_.mkString("/"))
      uri <- Gen.const(s"$scheme://$host/$path").map(UriUtils.normalizeUri).collect { case Some(uri) => uri }
    } yield uri

  val publicKeyData: Gen[Any, PublicKeyData] =
    for {
      curve <- Gen.fromIterable(EllipticCurve.values)
      pk <- Gen.fromZIO(ZIO.attempt(EC.INSTANCE.generateKeyPair().getPublicKey).orDie)
      x = Base64UrlString.fromByteArray(pk.getCurvePoint.getX.bytes())
      y = Base64UrlString.fromByteArray(pk.getCurvePoint.getY.bytes())
      compressedX = Base64UrlString.fromByteArray(pk.getEncodedCompressed)
      uncompressedGen = PublicKeyData.ECKeyData(curve, x, y)
      compressedGen = PublicKeyData.ECCompressedKeyData(curve, compressedX)
      generated <- Gen.fromIterable(Seq(uncompressedGen, compressedGen))
    } yield generated

  val publicKey: Gen[Any, PublicKey] =
    for {
      id <- uriFragment
      purpose <- Gen.fromIterable(VerificationRelationship.values)
      keyData <- publicKeyData
    } yield PublicKey(id, purpose, keyData)

  val internalPublicKey: Gen[Any, InternalPublicKey] =
    for {
      id <- uriFragment
      purpose <- Gen.fromIterable(InternalKeyPurpose.values)
      keyData <- publicKeyData
    } yield InternalPublicKey(id, purpose, keyData)

  val service: Gen[Any, Service] =
    for {
      id <- uriFragment
      serviceType <- Gen.fromIterable(ServiceType.values)
      endpoints <- Gen.listOfBounded(1, 3)(uri).map(_.map(Uri.parse))
    } yield Service(id, serviceType, endpoints).normalizeServiceEndpoint()

  val createOperation: Gen[Any, PrismDIDOperation.Create] = {
    for {
      masterKey <- internalPublicKey.map(_.copy(purpose = InternalKeyPurpose.Master))
      publicKeys <- Gen.listOfBounded(0, 5)(publicKey)
      keys: List[InternalPublicKey | PublicKey] = masterKey :: publicKeys
      services <- Gen.listOfBounded(0, 5)(service)
      contexts <- Gen.listOfBounded(0, 5)(uri)
    } yield PrismDIDOperation.Create(keys, services, contexts)
  }

  val didData: Gen[Any, DIDData] = {
    for {
      op <- createOperation
    } yield DIDData(
      id = op.did,
      publicKeys = op.publicKeys.collect { case pk: PublicKey => pk },
      services = op.services,
      internalKeys = op.publicKeys.collect { case pk: InternalPublicKey => pk },
      context = op.context
    )
  }

}
