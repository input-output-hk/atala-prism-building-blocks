package io.iohk.atala.pollux.vc.jwt.demos

import io.circe.*
import pdi.jwt.JwtClaim
import pdi.jwt.{JwtAlgorithm, JwtCirce}

import java.security.*
import java.security.spec.*
import java.time.Instant
import net.reactivecore.cjs.Loader

import net.reactivecore.cjs.{DocumentValidator, Loader, Result}
import net.reactivecore.cjs.resolver.Downloader
import cats.implicits._
import io.circe.Json

@main def jwtDemo(): Unit =
  val keyGen = KeyPairGenerator.getInstance("EC")
  val ecSpec = ECGenParameterSpec("secp256r1")
  keyGen.initialize(ecSpec, SecureRandom())
  val keyPair = keyGen.generateKeyPair()
  val privateKey = keyPair.getPrivate
  val publicKey = keyPair.getPublic

  val Right(claimJson) : Right[io.circe.ParsingFailure, io.circe.Json] @unchecked = jawn.parse(s"""{"expires":${Instant.now.getEpochSecond}}""")

  val jwt = JwtCirce.encode(claimJson, privateKey, JwtAlgorithm.ES256)

  println(jwt)

  println(s"Is Valid: ${JwtCirce.isValid(jwt, publicKey)}")
