package io.iohk.atala.pollux.anoncreds

import org.scalatest.flatspec.AnyFlatSpec

import scala.jdk.CollectionConverters.*

/** polluxAnoncredsTest/Test/testOnly io.iohk.atala.pollux.anoncreds.PoCNewLib
  */
class PoCNewLib extends AnyFlatSpec {

  val SCHEMA_ID = "mock:uri2"
  val CRED_DEF_ID = "mock:uri3"
  val ISSUER_DID = "mock:issuer_id/path&q=bar"

  "LinkSecret" should "be able to parse back to the anoncreds lib" in {
    import scala.language.implicitConversions

    val ls1 = LinkSecret("65965334953670062552662719679603258895632947953618378932199361160021795698890")
    val ls1p = ls1: uniffi.anoncreds.LinkSecret
    assert(ls1p.getValue() == "65965334953670062552662719679603258895632947953618378932199361160021795698890")

    val ls0 = LinkSecret()
    val ls0p = ls0: uniffi.anoncreds.LinkSecret
    val ls0_ = ls0p: LinkSecret
    assert(ls0.data == ls0_.data)
  }

  "The POC New Lib script" should "run to completion" in {
    script()
  }

  def script(): Unit = {
    println(s"Version of anoncreds library")

    // ##############
    // ### ISSUER ###
    // ##############
    println("*** issuer " + ("*" * 100))
    // ############################################################################################
    val schema = AnoncredLib.createSchema(
      SCHEMA_ID,
      "0.1.0",
      Set("name", "sex", "age"),
      ISSUER_DID
    )

    // ############################################################################################
    val credentialDefinition = AnoncredLib.createCredDefinition(ISSUER_DID, schema, "tag", supportRevocation = false)

    // // ############################################################################################
    val credentialOffer = AnoncredLib.createOffer(credentialDefinition, CRED_DEF_ID)

    println("credentialOffer.schemaId: " + credentialOffer.schemaId)
    println("credentialOffer.credDefId: " + credentialOffer.credDefId)

    // ##############
    // ### HOLDER ###
    // ##############
    println("*** holder " + ("*" * 100))

    val linkSecret = LinkSecretWithId("ID_of_some_secret_1")

    val credentialRequest = AnoncredLib.createCredentialRequest(linkSecret, credentialDefinition.cd, credentialOffer)
    println("*" * 100)

    val credential = AnoncredLib.createCredential(
      credentialDefinition.cd,
      credentialDefinition.cdPrivate,
      credentialOffer,
      credentialRequest.request,
      Seq(
        ("name", "Miguel"),
        ("sex", "M"),
        ("age", "31"),
      )
    )

    println(credential)
  }

}
