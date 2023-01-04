package io.iohk.atala.castor.core.util

import io.iohk.atala.shared.models.HexStrings.*
import io.iohk.atala.castor.core.model.did.{PrismDIDOperation, SignedPrismDIDOperation, UpdateDIDAction}
import io.iohk.atala.castor.core.model.error.DIDOperationError
import io.iohk.atala.castor.core.util.DIDOperationValidator.Config
import zio.*

import java.net.URI

object DIDOperationValidator {
  final case class Config(publicKeyLimit: Int, serviceLimit: Int)

  object Config {
    val default: Config = Config(publicKeyLimit = 50, serviceLimit = 50)
  }

  def layer(config: Config = Config.default): ULayer[DIDOperationValidator] =
    ZLayer.succeed(DIDOperationValidator(config))
}

class DIDOperationValidator(config: Config) {

  private val KEY_ID_RE = "^\\w+$".r

  extension [T](xs: Seq[T]) {
    def isUnique: Boolean = xs.length == xs.distinct.length
  }

  def validate(operation: PrismDIDOperation): Either[DIDOperationError, Unit] = {
    for {
      _ <- validateMaxPublicKeysAccess(operation)
      _ <- validateMaxServiceAccess(operation)
      _ <- validateUniquePublicKeyId(operation)
      _ <- validateUniqueServiceId(operation)
      _ <- validateUniqueServiceUri(operation)
      _ <- validateKeyIdRegex(operation)
    } yield ()
  }

  private def validateMaxPublicKeysAccess(operation: PrismDIDOperation): Either[DIDOperationError, Unit] = {
    val keyCount = operation match {
      case op: PrismDIDOperation.Create => op.publicKeys.length + op.internalKeys.length
      case op: PrismDIDOperation.Update =>
        op.actions.count {
          case UpdateDIDAction.AddKey(_)         => true
          case UpdateDIDAction.AddInternalKey(_) => true
          case UpdateDIDAction.RemoveKey(_)      => true
          case _                                 => false
        }
    }
    if (keyCount <= config.publicKeyLimit) Right(())
    else Left(DIDOperationError.TooManyDidPublicKeyAccess(config.publicKeyLimit, Some(keyCount)))
  }

  private def validateMaxServiceAccess(operation: PrismDIDOperation): Either[DIDOperationError, Unit] = {
    val serviceCount = operation match {
      case op: PrismDIDOperation.Create => op.services.length
      case op: PrismDIDOperation.Update =>
        op.actions.count {
          case UpdateDIDAction.AddService(_)          => true
          case UpdateDIDAction.RemoveService(_)       => true
          case UpdateDIDAction.UpdateService(_, _, _) => true
          case _                                      => false
        }
    }
    if (serviceCount <= config.serviceLimit) Right(())
    else Left(DIDOperationError.TooManyDidServiceAccess(config.serviceLimit, Some(serviceCount)))
  }

  private def validateUniquePublicKeyId(operation: PrismDIDOperation): Either[DIDOperationError, Unit] = {
    operation match {
      case op: PrismDIDOperation.Create =>
        val ids = op.publicKeys.map(_.id) ++ op.internalKeys.map(_.id)
        if (ids.isUnique) Right(())
        else Left(DIDOperationError.InvalidArgument("id for public-keys is not unique"))
      case _: PrismDIDOperation.Update => Right(())
    }
  }

  private def validateUniqueServiceId(operation: PrismDIDOperation): Either[DIDOperationError, Unit] = {
    operation match {
      case op: PrismDIDOperation.Create =>
        val ids = op.services.map(_.id)
        if (ids.isUnique) Right(())
        else Left(DIDOperationError.InvalidArgument("id for services is not unique"))
      case _: PrismDIDOperation.Update => Right(())
    }
  }

  private def validateKeyIdRegex(operation: PrismDIDOperation): Either[DIDOperationError, Unit] = {
    val keyIds = operation match {
      case PrismDIDOperation.Create(publicKeys, internalKeys, _) => publicKeys.map(_.id) ++ internalKeys.map(_.id)
      case op: PrismDIDOperation.Update =>
        op.actions.collect {
          case UpdateDIDAction.AddKey(publicKey)         => publicKey.id
          case UpdateDIDAction.AddInternalKey(publicKey) => publicKey.id
          case UpdateDIDAction.RemoveKey(id)             => id
        }
    }
    val invalidIds = keyIds.filterNot(id => KEY_ID_RE.pattern.matcher(id).matches())
    if (invalidIds.isEmpty) Right(())
    else
      Left(DIDOperationError.InvalidArgument(s"public key id is invalid: ${invalidIds.mkString("[", ", ", "]")}"))
  }

  private def validateUniqueServiceUri(operation: PrismDIDOperation): Either[DIDOperationError, Unit] = {
    val endpoints: Seq[(String, Seq[URI])] = operation match {
      case PrismDIDOperation.Create(_, _, services) => services.map(s => s.id -> s.serviceEndpoint)
      case op: PrismDIDOperation.Update =>
        op.actions.collect {
          case UpdateDIDAction.AddService(service)             => service.id -> service.serviceEndpoint
          case UpdateDIDAction.UpdateService(id, _, endpoints) => id -> endpoints
        }
    }
    endpoints
      .find { case (_, endpoints) => !endpoints.isUnique }
      .toLeft(())
      .left
      .map { case (id, _) =>
        DIDOperationError.InvalidArgument(s"service $id does not have unique serviceEndpoint URIs")
      }
  }

}
