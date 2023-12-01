package io.iohk.atala.pollux.core.service

import io.circe.parser.decode
import io.circe.syntax.*
import io.iohk.atala.mercury.model.{AttachmentDescriptor, Base64, DidId}
import io.iohk.atala.mercury.protocol.issuecredential.{IssueCredential, IssueCredentialIssuedFormat}
import io.iohk.atala.mercury.protocol.presentproof.*
import io.iohk.atala.pollux.anoncreds.AnoncredLib
import io.iohk.atala.pollux.core.model.*
import io.iohk.atala.pollux.core.model.IssueCredentialRecord.*
import io.iohk.atala.pollux.core.model.PresentationRecord.*
import io.iohk.atala.pollux.core.model.error.PresentationError
import io.iohk.atala.pollux.core.model.error.PresentationError.*
import io.iohk.atala.pollux.core.model.presentation.Options
import io.iohk.atala.pollux.core.model.schema.CredentialDefinition.Input
import io.iohk.atala.pollux.core.repository.{CredentialRepository, PresentationRepository}
import io.iohk.atala.pollux.core.service.serdes.{
  AnoncredCredentialProofV1,
  AnoncredCredentialProofsV1,
  AnoncredPresentationRequestV1,
  AnoncredPresentationV1
}
import io.iohk.atala.pollux.vc.jwt.*
import io.iohk.atala.shared.models.{WalletAccessContext, WalletId}
import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.time.{Instant, OffsetDateTime}
import java.util.Base64 as JBase64

object PresentationServiceSpec extends ZIOSpecDefault with PresentationServiceSpecHelper {

  override def spec =
    suite("PresentationService")(singleWalletSpec, multiWalletSpec).provide(presentationServiceLayer)

  private val singleWalletSpec =
    suite("singleWalletSpec")(
      test("createPresentationRecord creates a valid JWT PresentationRecord") {
        val didGen = for {
          suffix <- Gen.stringN(10)(Gen.alphaNumericChar)
        } yield DidId("did:peer:" + suffix)

        val proofTypeGen = for {
          schemaId <- Gen.stringN(10)(Gen.alphaChar)
          requiredFields <- Gen.listOfBounded(1, 5)(Gen.stringN(10)(Gen.alphaChar)).map(Some(_))
          trustIssuers <- Gen.listOfBounded(1, 5)(didGen).map(Some(_))
        } yield ProofType(schemaId, requiredFields, trustIssuers)

        val optionsGen = for {
          challenge <- Gen.stringN(10)(Gen.alphaNumericChar)
          domain <- Gen.stringN(10)(Gen.alphaNumericChar)
        } yield Options(challenge, domain)

        check(
          Gen.uuid.map(e => DidCommID(e.toString())),
          Gen.option(Gen.string),
          Gen.listOfBounded(1, 5)(proofTypeGen),
          Gen.option(optionsGen)
        ) { (thid, connectionId, proofTypes, options) =>
          for {
            svc <- ZIO.service[PresentationService]
            pairwiseVerifierDid = DidId("did:peer:Verifier")
            pairwiseProverDid = DidId("did:peer:Prover")
            record <- svc.createJwtPresentationRecord(
              pairwiseVerifierDid,
              pairwiseProverDid,
              thid,
              connectionId,
              proofTypes,
              options
            )
          } yield {
            assertTrue(record.thid == thid) &&
            assertTrue(record.updatedAt.isEmpty) &&
            assertTrue(record.connectionId == connectionId) &&
            assertTrue(record.role == PresentationRecord.Role.Verifier) &&
            assertTrue(record.protocolState == PresentationRecord.ProtocolState.RequestPending) &&
            assertTrue(record.requestPresentationData.isDefined) &&
            assertTrue(record.requestPresentationData.get.to == pairwiseProverDid) &&
            assertTrue(record.requestPresentationData.get.thid.contains(thid.toString)) &&
            assertTrue(record.requestPresentationData.get.body.goal_code.contains("Request Proof Presentation")) &&
            assertTrue(record.requestPresentationData.get.body.proof_types == proofTypes) &&
            assertTrue(
              if (record.requestPresentationData.get.attachments.length != 0) {
                val maybePresentationOptions =
                  record.requestPresentationData.get.attachments.headOption
                    .map(attachment =>
                      decode[io.iohk.atala.mercury.model.JsonData](attachment.data.asJson.noSpaces)
                        .flatMap(data =>
                          io.iohk.atala.pollux.core.model.presentation.PresentationAttachment.given_Decoder_PresentationAttachment
                            .decodeJson(data.json.asJson)
                            .map(_.options)
                        )
                    )
                    .get
                maybePresentationOptions
                  .map(
                    _ == options
                  )
                  .getOrElse(false)
              } else true
            ) &&
            assertTrue(record.proposePresentationData.isEmpty) &&
            assertTrue(record.presentationData.isEmpty) &&
            assertTrue(record.credentialsToUse.isEmpty)
          }
        }
      },
      test("createPresentationRecord creates a valid Anoncred PresentationRecord") {
        check(
          Gen.uuid.map(e => DidCommID(e.toString())),
          Gen.option(Gen.string),
          Gen.string,
          Gen.string,
          Gen.string
        ) { (thid, connectionId, name, nonce, version) =>
          for {
            svc <- ZIO.service[PresentationService]
            pairwiseVerifierDid = DidId("did:peer:Verifier")
            pairwiseProverDid = DidId("did:peer:Prover")
            anoncredPresentationRequestV1 = AnoncredPresentationRequestV1(
              Map.empty,
              Map.empty,
              name,
              nonce,
              version,
              None
            )
            record <-
              svc.createAnoncredPresentationRecord(
                pairwiseVerifierDid,
                pairwiseProverDid,
                thid,
                connectionId,
                anoncredPresentationRequestV1
              )
          } yield {
            assertTrue(record.thid == thid) &&
            assertTrue(record.updatedAt.isEmpty) &&
            assertTrue(record.connectionId == connectionId) &&
            assertTrue(record.role == PresentationRecord.Role.Verifier) &&
            assertTrue(record.protocolState == PresentationRecord.ProtocolState.RequestPending) &&
            assertTrue(record.requestPresentationData.isDefined) &&
            assertTrue(record.requestPresentationData.get.to == pairwiseProverDid) &&
            assertTrue(record.requestPresentationData.get.thid.contains(thid.toString)) &&
            assertTrue(record.requestPresentationData.get.body.goal_code.contains("Request Proof Presentation")) &&
            assertTrue(
              record.requestPresentationData.get.attachments.map(_.media_type) == Seq(Some("application/json"))
            ) &&
            assertTrue(
              record.requestPresentationData.get.attachments.map(_.format) == Seq(
                Some(PresentCredentialRequestFormat.Anoncred.name)
              )
            ) &&
            assertTrue(
              record.requestPresentationData.get.attachments.map(_.data) ==
                Seq(
                  Base64(
                    JBase64.getUrlEncoder.encodeToString(
                      AnoncredPresentationRequestV1.schemaSerDes
                        .serializeToJsonString(anoncredPresentationRequestV1)
                        .getBytes()
                    )
                  )
                )
            ) &&
            assertTrue(record.proposePresentationData.isEmpty) &&
            assertTrue(record.presentationData.isEmpty) &&
            assertTrue(record.credentialsToUse.isEmpty)
          }
        }
      },
      test("getPresentationRecords returns created PresentationRecord") {
        for {
          svc <- ZIO.service[PresentationService]
          pairwiseProverDid = DidId("did:peer:Prover")
          record1 <- svc.createJwtRecord()
          record2 <- svc.createJwtRecord()
          records <- svc.getPresentationRecords(false)
        } yield {
          assertTrue(records.size == 2)
        }
      },
      test("getPresentationRecordsByStates returns the correct records") {
        for {
          svc <- ZIO.service[PresentationService]
          aRecord <- svc.createJwtRecord()
          records <- svc.getPresentationRecordsByStates(
            ignoreWithZeroRetries = true,
            limit = 10,
            PresentationRecord.ProtocolState.RequestPending
          )
          onePending = assertTrue(records.size == 1) && assertTrue(records.contains(aRecord))
          records <- svc.getPresentationRecordsByStates(
            ignoreWithZeroRetries = true,
            limit = 10,
            PresentationRecord.ProtocolState.RequestSent
          )
          zeroSent = assertTrue(records.isEmpty)
        } yield onePending && zeroSent
      },
      test("getPresentationRecord returns the correct record") {
        for {
          svc <- ZIO.service[PresentationService]
          aRecord <- svc.createJwtRecord()
          bRecord <- svc.createJwtRecord()
          record <- svc.getPresentationRecord(bRecord.id)
        } yield assertTrue(record.contains(bRecord))
      },
      test("getPresentationRecord returns nothing for an unknown 'recordId'") {
        for {
          svc <- ZIO.service[PresentationService]
          aRecord <- svc.createJwtRecord()
          bRecord <- svc.createJwtRecord()
          record <- svc.getPresentationRecord(DidCommID())
        } yield assertTrue(record.isEmpty)
      },
      test("createJwtPresentationPayloadFromRecord returns jwt presentation payload") {
        for {
          repo <- ZIO.service[CredentialRepository]
          aIssueCredentialRecord = issueCredentialRecord(CredentialFormat.JWT)
          _ <- repo.createIssueCredentialRecord(aIssueCredentialRecord)
          rawCredentialData =
            """{"base64":"ZXlKaGJHY2lPaUpGVXpJMU5rc2lMQ0owZVhBaU9pSktWMVFpZlEuZXlKcFlYUWlPakUyTnprek1qYzROaklzSW1GMVpDSTZJbVJ2YldGcGJpSXNJbTV2Ym1ObElqb2lZMlk1T1RJMk56Z3RPREV3TmkwME1EZzVMV0UxWXprdE5tTmhObU0wWkRBMU1HVTBJaXdpZG5BaU9uc2lRR052Ym5SbGVIUWlPbHNpYUhSMGNITTZMeTkzZDNjdWR6TXViM0puTHpJd01UZ3ZjSEpsYzJWdWRHRjBhVzl1Y3k5Mk1TSmRMQ0owZVhCbElqcGJJbFpsY21sbWFXRmliR1ZRY21WelpXNTBZWFJwYjI0aVhYMHNJbWx6Y3lJNkltUnBaRHB3Y21semJUcGhaR0psT1RJNE9XUXdZelZtWWpVMlptWmhOVEF6T0Rka01UZ3dOR0ZpTkdFeE5UYzJOVEkzWXprME5tRTFNalV5T0RFM1ptRTRaVGhoTW1OalpXUXdPa056YzBKRGMyZENSVzFKUzBSWE1XaGpNMUpzWTJsb2NHSnRVbXhsUTJ0UlFWVktVRU5uYkZSYVYwNTNUV3BWTW1GNlJWTkpSUzFNYVVkTU0xRklaRlZ1VG10d1dXSkthSE5VYTIxWVVGaEpVM0ZXZWpjMll6RlZPWGhvVURseWNFZHBSSEZXTlRselJYcEtWbEpEYWxJMGEwMHdaMGg0YkhWUU5tVk5Ta2wwZHpJMk4yWllWbEpoTUhoRE5XaEthVU5uTVhSWldFNHdXbGhKYjJGWE5XdGFXR2R3UlVGU1ExUjNiMHBWTWxacVkwUkpNVTV0YzNoRmFVSlFhVFJvYVRrd1FqTldTbnBhUzFkSGVWbGlSVFZLYkhveGVVVnhiR010TFc1T1ZsQmpXVlJmWVRaU2IyYzJiR1ZtWWtKTmVWWlZVVzh3WlVwRVRrbENPRnBpYWkxdWFrTlRUR05PZFhVek1URlZWM1JOVVhWWkluMC5CcmFpbEVXa2VlSXhWbjY3dnpkVHZGTXpBMV9oNzFoaDZsODBHRFBpbkRaVVB4ajAxSC0tUC1QZDIxTk9wRDd3am51SDkxdUNBOFZMUW9fS2FnVjlnQQo="}"""
          _ <- repo.updateWithIssuedRawCredential(
            aIssueCredentialRecord.id,
            IssueCredential.makeIssueCredentialFromRequestCredential(requestCredential.makeMessage),
            rawCredentialData,
            IssueCredentialRecord.ProtocolState.CredentialReceived
          )
          svc <- ZIO.service[PresentationService]
          aRecord <- svc.createJwtRecord()
          repo <- ZIO.service[PresentationRepository]
          _ <- repo.updatePresentationWithCredentialsToUse(
            aRecord.id,
            Some(Seq(aIssueCredentialRecord.id.value)),
            PresentationRecord.ProtocolState.RequestPending
          )
          issuer = createIssuer(DID("did:prism:issuer"))
          aPresentationPayload <- svc.createJwtPresentationPayloadFromRecord(aRecord.id, issuer, Instant.now())
        } yield {
          assertTrue(aPresentationPayload.toJwtPresentationPayload.iss == "did:prism:issuer")
        }
      },
      test("createAnoncredPresentationPayloadFromRecord returns Anoncred presentation payload") {
        for {
          svc <- ZIO.service[CredentialDefinitionService]
          issuerId = "did:prism:issuer"
          holderID = "did:prism:holder"
          schemaId = "resource:///anoncred-presentation-schema-example.json"
          credentialDefinitionDb <- svc.create(
            Input(
              name = "Credential Definition Name",
              description = "Credential Definition Description",
              version = "1.2",
              signatureType = "CL",
              tag = "tag",
              author = issuerId,
              authored = Some(OffsetDateTime.parse("2022-03-10T12:00:00Z")),
              schemaId = schemaId,
              supportRevocation = false
            )
          )
          repo <- ZIO.service[CredentialRepository]
          schema = AnoncredLib.createSchema(
            schemaId,
            "0.1.0",
            Set("name", "sex", "age"),
            issuerId
          )
          linkSecretService <- ZIO.service[LinkSecretService]
          linkSecret <- linkSecretService.fetchOrCreate()
          credentialDefinition = AnoncredLib.createCredDefinition(issuerId, schema, "tag", supportRevocation = false)
          credentialOffer = AnoncredLib.createOffer(credentialDefinition, credentialDefinitionDb.longId)
          credentialRequest = AnoncredLib.createCredentialRequest(linkSecret, credentialDefinition.cd, credentialOffer)
          credential =
            AnoncredLib
              .createCredential(
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
              .data
          issueCredential = IssueCredential(
            from = DidId(issuerId),
            to = DidId(holderID),
            body = IssueCredential.Body(),
            attachments = Seq(
              AttachmentDescriptor.buildBase64Attachment(
                mediaType = Some("application/json"),
                format = Some(IssueCredentialIssuedFormat.Anoncred.name),
                payload = credential.getBytes()
              )
            )
          )
          aIssueCredentialRecord =
            IssueCredentialRecord(
              id = DidCommID(),
              createdAt = Instant.now,
              updatedAt = None,
              thid = DidCommID(),
              schemaId = Some(schemaId),
              credentialDefinitionId = Some(credentialDefinitionDb.guid),
              credentialFormat = CredentialFormat.AnonCreds,
              role = IssueCredentialRecord.Role.Issuer,
              subjectId = None,
              validityPeriod = None,
              automaticIssuance = None,
              protocolState = IssueCredentialRecord.ProtocolState.CredentialReceived,
              offerCredentialData = None,
              requestCredentialData = None,
              anonCredsRequestMetadata = None,
              issueCredentialData = Some(issueCredential),
              issuedCredentialRaw =
                Some(issueCredential.attachments.map(_.data.asJson.noSpaces).headOption.getOrElse("???")),
              issuingDID = None,
              metaRetries = 5,
              metaNextRetry = Some(Instant.now()),
              metaLastFailure = None,
            )
          _ <- repo.createIssueCredentialRecord(aIssueCredentialRecord)
          svc <- ZIO.service[PresentationService]
          aRecord <- svc.createAnoncredRecord()
          repo <- ZIO.service[PresentationRepository]
          credentialsToUse =
            AnoncredCredentialProofsV1(
              List(
                AnoncredCredentialProofV1(
                  aIssueCredentialRecord.id.value,
                  Seq("sex"),
                  Seq("age")
                )
              )
            )
          credentialsToUseJson <- ZIO.fromEither(
            AnoncredCredentialProofsV1.schemaSerDes.serialize(credentialsToUse)
          )
          _ <- repo.updateAnoncredPresentationWithCredentialsToUse(
            aRecord.id,
            Some(AnoncredPresentationV1.version),
            Some(credentialsToUseJson),
            PresentationRecord.ProtocolState.RequestPending
          )
          issuer = createIssuer(DID("did:prism:issuer"))
          aPresentationPayload <- svc.createAnoncredPresentationPayloadFromRecord(
            aRecord.id,
            issuer,
            credentialsToUse,
            Instant.now()
          )
          validation <- AnoncredPresentationV1.schemaSerDes.validate(aPresentationPayload.data)
          presentation <- AnoncredPresentationV1.schemaSerDes.deserialize(aPresentationPayload.data)
        } yield {
          assertTrue(validation)
          assert(
            presentation.proof.proofs.headOption.flatMap(_.primary_proof.eq_proof.revealed_attrs.headOption.map(_._1))
          )(isSome(equalTo("sex")))
        }
      },
      test("markRequestPresentationSent returns updated PresentationRecord") {
        for {
          svc <- ZIO.service[PresentationService]
          pairwiseProverDid = DidId("did:peer:Prover")
          record <- svc.createJwtRecord()
          record <- svc.markRequestPresentationSent(record.id)

        } yield {
          assertTrue(record.protocolState == PresentationRecord.ProtocolState.RequestSent)
        }
      },
      test("markRequestPresentationRejected returns updated PresentationRecord") {
        for {
          svc <- ZIO.service[PresentationService]
          pairwiseProverDid = DidId("did:peer:Prover")
          record <- svc.createJwtRecord()
          repo <- ZIO.service[PresentationRepository]
          _ <- repo.updatePresentationRecordProtocolState(
            record.id,
            PresentationRecord.ProtocolState.RequestPending,
            PresentationRecord.ProtocolState.RequestReceived
          )
          record <- svc.markRequestPresentationRejected(record.id)

        } yield {
          assertTrue(record.protocolState == PresentationRecord.ProtocolState.RequestRejected)
        }
      },
      test("receiveRequestPresentation with a MissingCredentialFormat") {
        for {
          svc <- ZIO.service[PresentationService]
          connectionId = Some("connectionId")
          body = RequestPresentation.Body(goal_code = Some("Presentation Request"))
          presentationAttachmentAsJson = """{
                "challenge": "1f44d55f-f161-4938-a659-f8026467f126",
                "domain": "us.gov/DriverLicense",
                "credential_manifest": {}
            }"""
          prover = DidId("did:peer:Prover")
          verifier = DidId("did:peer:Verifier")

          attachmentDescriptor = AttachmentDescriptor.buildJsonAttachment(
            payload = presentationAttachmentAsJson,
          )
          requestPresentation = RequestPresentation(
            body = body,
            attachments = Seq(attachmentDescriptor),
            to = prover,
            from = verifier,
          )
          result <- svc.receiveRequestPresentation(connectionId, requestPresentation).exit

        } yield assert(result)(
          fails(equalTo(MissingCredentialFormat))
        )
      },
      test("receiveRequestPresentation with a UnsupportedCredentialFormat") {
        for {
          svc <- ZIO.service[PresentationService]
          connectionId = Some("connectionId")
          body = RequestPresentation.Body(goal_code = Some("Presentation Request"))
          presentationAttachmentAsJson = """{
                "challenge": "1f44d55f-f161-4938-a659-f8026467f126",
                "domain": "us.gov/DriverLicense",
                "credential_manifest": {}
            }"""
          prover = DidId("did:peer:Prover")
          verifier = DidId("did:peer:Verifier")

          attachmentDescriptor = AttachmentDescriptor.buildJsonAttachment(
            payload = presentationAttachmentAsJson,
            format = Some("Some/UnsupportedCredentialFormat")
          )
          requestPresentation = RequestPresentation(
            body = body,
            attachments = Seq(attachmentDescriptor),
            to = prover,
            from = verifier,
          )
          result <- svc.receiveRequestPresentation(connectionId, requestPresentation).exit

        } yield assert(result)(
          fails(equalTo(UnsupportedCredentialFormat(vcFormat = "Some/UnsupportedCredentialFormat")))
        )
      },
      test("receiveRequestPresentation JWT updates the RequestPresentation in PresentationRecord") {
        for {
          svc <- ZIO.service[PresentationService]
          connectionId = Some("connectionId")
          body = RequestPresentation.Body(goal_code = Some("Presentation Request"))
          presentationAttachmentAsJson = """{
                "challenge": "1f44d55f-f161-4938-a659-f8026467f126",
                "domain": "us.gov/DriverLicense",
                "credential_manifest": {}
            }"""
          prover = DidId("did:peer:Prover")
          verifier = DidId("did:peer:Verifier")

          attachmentDescriptor = AttachmentDescriptor.buildJsonAttachment(
            payload = presentationAttachmentAsJson,
            format = Some(PresentCredentialProposeFormat.JWT.name)
          )
          requestPresentation = RequestPresentation(
            body = body,
            attachments = Seq(attachmentDescriptor),
            to = prover,
            from = verifier,
          )
          aRecord <- svc.receiveRequestPresentation(connectionId, requestPresentation)

        } yield {
          assertTrue(aRecord.connectionId == connectionId) &&
          assertTrue(aRecord.protocolState == PresentationRecord.ProtocolState.RequestReceived) &&
          assertTrue(aRecord.requestPresentationData == Some(requestPresentation))
        }
      },
      test("receiveRequestPresentation Anoncred updates the RequestPresentation in PresentationRecord") {
        for {
          svc <- ZIO.service[PresentationService]
          connectionId = Some("connectionId")
          body = RequestPresentation.Body(goal_code = Some("Presentation Request"))
          prover = DidId("did:peer:Prover")
          verifier = DidId("did:peer:Verifier")
          anoncredPresentationRequestV1 = AnoncredPresentationRequestV1(
            Map.empty,
            Map.empty,
            "name",
            "nonce",
            "version",
            None
          )
          attachmentDescriptor = AttachmentDescriptor.buildBase64Attachment(
            mediaType = Some("application/json"),
            format = Some(PresentCredentialRequestFormat.Anoncred.name),
            payload =
              AnoncredPresentationRequestV1.schemaSerDes.serializeToJsonString(anoncredPresentationRequestV1).getBytes()
          )
          requestPresentation = RequestPresentation(
            body = body,
            attachments = Seq(attachmentDescriptor),
            to = prover,
            from = verifier,
          )
          aRecord <- svc.receiveRequestPresentation(connectionId, requestPresentation)

        } yield {
          assertTrue(aRecord.connectionId == connectionId) &&
          assertTrue(aRecord.protocolState == PresentationRecord.ProtocolState.RequestReceived) &&
          assertTrue(aRecord.requestPresentationData == Some(requestPresentation))
        }
      },
      test("receiveRequestPresentation Anoncred should fail given invalid attachment") {
        for {
          svc <- ZIO.service[PresentationService]
          connectionId = Some("connectionId")
          body = RequestPresentation.Body(goal_code = Some("Presentation Request"))
          presentationAttachmentAsJson =
            """{
                "challenge": "1f44d55f-f161-4938-a659-f8026467f126",
                "domain": "us.gov/DriverLicense",
                "credential_manifest": {}
            }"""
          prover = DidId("did:peer:Prover")
          verifier = DidId("did:peer:Verifier")

          attachmentDescriptor = AttachmentDescriptor.buildJsonAttachment(
            payload = presentationAttachmentAsJson,
            format = Some(PresentCredentialProposeFormat.Anoncred.name)
          )
          requestPresentation = RequestPresentation(
            body = body,
            attachments = Seq(attachmentDescriptor),
            to = prover,
            from = verifier,
          )
          result <- svc.receiveRequestPresentation(connectionId, requestPresentation).exit

        } yield assert(result)(
          fails(equalTo(InvalidAnoncredPresentationRequest("Expecting Base64-encoded data")))
        )
      },
      test("receiveRequestPresentation Anoncred should fail given invalid anoncred format") {
        for {
          svc <- ZIO.service[PresentationService]
          connectionId = Some("connectionId")
          body = RequestPresentation.Body(goal_code = Some("Presentation Request"))
          presentationAttachmentAsJson =
            """{
                "challenge": "1f44d55f-f161-4938-a659-f8026467f126",
                "domain": "us.gov/DriverLicense",
                "credential_manifest": {}
            }"""
          prover = DidId("did:peer:Prover")
          verifier = DidId("did:peer:Verifier")
          attachmentDescriptor = AttachmentDescriptor.buildBase64Attachment(
            mediaType = Some("application/json"),
            format = Some(PresentCredentialRequestFormat.Anoncred.name),
            payload = presentationAttachmentAsJson.getBytes()
          )
          requestPresentation = RequestPresentation(
            body = body,
            attachments = Seq(attachmentDescriptor),
            to = prover,
            from = verifier,
          )
          result <- svc.receiveRequestPresentation(connectionId, requestPresentation).exit

        } yield assert(result)(
          fails(isSubtype[InvalidAnoncredPresentationRequest](anything))
        )
      },
      test("acceptRequestPresentation updates the PresentationRecord JWT") {
        for {
          repo <- ZIO.service[CredentialRepository]
          aIssueCredentialRecord = issueCredentialRecord(CredentialFormat.JWT)
          _ <- repo.createIssueCredentialRecord(aIssueCredentialRecord)
          rawCredentialData =
            """{"base64":"ZXlKaGJHY2lPaUpGVXpJMU5rc2lMQ0owZVhBaU9pSktWMVFpZlEuZXlKcFlYUWlPakUyTnprek1qYzROaklzSW1GMVpDSTZJbVJ2YldGcGJpSXNJbTV2Ym1ObElqb2lZMlk1T1RJMk56Z3RPREV3TmkwME1EZzVMV0UxWXprdE5tTmhObU0wWkRBMU1HVTBJaXdpZG5BaU9uc2lRR052Ym5SbGVIUWlPbHNpYUhSMGNITTZMeTkzZDNjdWR6TXViM0puTHpJd01UZ3ZjSEpsYzJWdWRHRjBhVzl1Y3k5Mk1TSmRMQ0owZVhCbElqcGJJbFpsY21sbWFXRmliR1ZRY21WelpXNTBZWFJwYjI0aVhYMHNJbWx6Y3lJNkltUnBaRHB3Y21semJUcGhaR0psT1RJNE9XUXdZelZtWWpVMlptWmhOVEF6T0Rka01UZ3dOR0ZpTkdFeE5UYzJOVEkzWXprME5tRTFNalV5T0RFM1ptRTRaVGhoTW1OalpXUXdPa056YzBKRGMyZENSVzFKUzBSWE1XaGpNMUpzWTJsb2NHSnRVbXhsUTJ0UlFWVktVRU5uYkZSYVYwNTNUV3BWTW1GNlJWTkpSUzFNYVVkTU0xRklaRlZ1VG10d1dXSkthSE5VYTIxWVVGaEpVM0ZXZWpjMll6RlZPWGhvVURseWNFZHBSSEZXTlRselJYcEtWbEpEYWxJMGEwMHdaMGg0YkhWUU5tVk5Ta2wwZHpJMk4yWllWbEpoTUhoRE5XaEthVU5uTVhSWldFNHdXbGhKYjJGWE5XdGFXR2R3UlVGU1ExUjNiMHBWTWxacVkwUkpNVTV0YzNoRmFVSlFhVFJvYVRrd1FqTldTbnBhUzFkSGVWbGlSVFZLYkhveGVVVnhiR010TFc1T1ZsQmpXVlJmWVRaU2IyYzJiR1ZtWWtKTmVWWlZVVzh3WlVwRVRrbENPRnBpYWkxdWFrTlRUR05PZFhVek1URlZWM1JOVVhWWkluMC5CcmFpbEVXa2VlSXhWbjY3dnpkVHZGTXpBMV9oNzFoaDZsODBHRFBpbkRaVVB4ajAxSC0tUC1QZDIxTk9wRDd3am51SDkxdUNBOFZMUW9fS2FnVjlnQQo="}"""
          _ <- repo.updateWithIssuedRawCredential(
            aIssueCredentialRecord.id,
            IssueCredential.makeIssueCredentialFromRequestCredential(requestCredential.makeMessage),
            rawCredentialData,
            IssueCredentialRecord.ProtocolState.CredentialReceived
          )
          svc <- ZIO.service[PresentationService]
          connectionId = Some("connectionId")

          aRecord <- svc.receiveRequestPresentation(
            connectionId,
            requestPresentation(PresentCredentialRequestFormat.JWT)
          )
          credentialsToUse = Seq(aIssueCredentialRecord.id.value)
          updateRecord <- svc.acceptRequestPresentation(aRecord.id, credentialsToUse)

        } yield {
          assertTrue(updateRecord.connectionId == connectionId) &&
          // assertTrue(updateRecord.requestPresentationData == Some(requestPresentation)) && // FIXME: enabling them make the test fail.
          assertTrue(updateRecord.credentialsToUse.contains(credentialsToUse))
        }
      },
      test("acceptRequestPresentation updates the PresentationRecord AnonCreds") {
        for {
          repo <- ZIO.service[CredentialRepository]
          aIssueCredentialRecord = issueCredentialRecord(CredentialFormat.AnonCreds)
          _ <- repo.createIssueCredentialRecord(aIssueCredentialRecord)
          rawCredentialData =
            """{"base64":"ZXlKaGJHY2lPaUpGVXpJMU5rc2lMQ0owZVhBaU9pSktWMVFpZlEuZXlKcFlYUWlPakUyTnprek1qYzROaklzSW1GMVpDSTZJbVJ2YldGcGJpSXNJbTV2Ym1ObElqb2lZMlk1T1RJMk56Z3RPREV3TmkwME1EZzVMV0UxWXprdE5tTmhObU0wWkRBMU1HVTBJaXdpZG5BaU9uc2lRR052Ym5SbGVIUWlPbHNpYUhSMGNITTZMeTkzZDNjdWR6TXViM0puTHpJd01UZ3ZjSEpsYzJWdWRHRjBhVzl1Y3k5Mk1TSmRMQ0owZVhCbElqcGJJbFpsY21sbWFXRmliR1ZRY21WelpXNTBZWFJwYjI0aVhYMHNJbWx6Y3lJNkltUnBaRHB3Y21semJUcGhaR0psT1RJNE9XUXdZelZtWWpVMlptWmhOVEF6T0Rka01UZ3dOR0ZpTkdFeE5UYzJOVEkzWXprME5tRTFNalV5T0RFM1ptRTRaVGhoTW1OalpXUXdPa056YzBKRGMyZENSVzFKUzBSWE1XaGpNMUpzWTJsb2NHSnRVbXhsUTJ0UlFWVktVRU5uYkZSYVYwNTNUV3BWTW1GNlJWTkpSUzFNYVVkTU0xRklaRlZ1VG10d1dXSkthSE5VYTIxWVVGaEpVM0ZXZWpjMll6RlZPWGhvVURseWNFZHBSSEZXTlRselJYcEtWbEpEYWxJMGEwMHdaMGg0YkhWUU5tVk5Ta2wwZHpJMk4yWllWbEpoTUhoRE5XaEthVU5uTVhSWldFNHdXbGhKYjJGWE5XdGFXR2R3UlVGU1ExUjNiMHBWTWxacVkwUkpNVTV0YzNoRmFVSlFhVFJvYVRrd1FqTldTbnBhUzFkSGVWbGlSVFZLYkhveGVVVnhiR010TFc1T1ZsQmpXVlJmWVRaU2IyYzJiR1ZtWWtKTmVWWlZVVzh3WlVwRVRrbENPRnBpYWkxdWFrTlRUR05PZFhVek1URlZWM1JOVVhWWkluMC5CcmFpbEVXa2VlSXhWbjY3dnpkVHZGTXpBMV9oNzFoaDZsODBHRFBpbkRaVVB4ajAxSC0tUC1QZDIxTk9wRDd3am51SDkxdUNBOFZMUW9fS2FnVjlnQQo="}"""
          _ <- repo.updateWithIssuedRawCredential(
            aIssueCredentialRecord.id,
            IssueCredential.makeIssueCredentialFromRequestCredential(requestCredential.makeMessage),
            rawCredentialData,
            IssueCredentialRecord.ProtocolState.CredentialReceived
          )
          svc <- ZIO.service[PresentationService]
          connectionId = Some("connectionId")
          anoncredPresentationRequestV1 = AnoncredPresentationRequestV1(
            Map.empty,
            Map.empty,
            "name",
            "nonce",
            "version",
            None
          )
          attachmentDescriptor = AttachmentDescriptor.buildBase64Attachment(
            mediaType = Some("application/json"),
            format = Some(PresentCredentialRequestFormat.Anoncred.name),
            payload =
              AnoncredPresentationRequestV1.schemaSerDes.serializeToJsonString(anoncredPresentationRequestV1).getBytes()
          )
          requestPresentation = RequestPresentation(
            body = RequestPresentation.Body(goal_code = Some("Presentation Request")),
            attachments = Seq(attachmentDescriptor),
            to = DidId("did:peer:Prover"),
            from = DidId("did:peer:Verifier"),
          )
          aRecord <- svc.receiveRequestPresentation(connectionId, requestPresentation)
          credentialsToUse =
            AnoncredCredentialProofsV1(
              List(
                AnoncredCredentialProofV1(
                  aIssueCredentialRecord.id.value,
                  Seq("requestedAttribute"),
                  Seq("requestedPredicate")
                )
              )
            )
          anoncredCredentialProofsJson <- ZIO.fromEither(
            AnoncredCredentialProofsV1.schemaSerDes.serialize(credentialsToUse)
          )
          updateRecord <- svc.acceptAnoncredRequestPresentation(aRecord.id, credentialsToUse)

        } yield {
          assertTrue(updateRecord.connectionId == connectionId) &&
          assertTrue(updateRecord.anoncredCredentialsToUse.contains(anoncredCredentialProofsJson)) &&
          assertTrue(updateRecord.anoncredCredentialsToUseJsonSchemaId.contains(AnoncredCredentialProofsV1.version))
        }
      },
      test("acceptRequestPresentation should fail given unmatching format") {
        for {
          repo <- ZIO.service[CredentialRepository]
          aIssueCredentialRecord = issueCredentialRecord(CredentialFormat.JWT)
          _ <- repo.createIssueCredentialRecord(aIssueCredentialRecord)
          rawCredentialData =
            """{"base64":"ZXlKaGJHY2lPaUpGVXpJMU5rc2lMQ0owZVhBaU9pSktWMVFpZlEuZXlKcFlYUWlPakUyTnprek1qYzROaklzSW1GMVpDSTZJbVJ2YldGcGJpSXNJbTV2Ym1ObElqb2lZMlk1T1RJMk56Z3RPREV3TmkwME1EZzVMV0UxWXprdE5tTmhObU0wWkRBMU1HVTBJaXdpZG5BaU9uc2lRR052Ym5SbGVIUWlPbHNpYUhSMGNITTZMeTkzZDNjdWR6TXViM0puTHpJd01UZ3ZjSEpsYzJWdWRHRjBhVzl1Y3k5Mk1TSmRMQ0owZVhCbElqcGJJbFpsY21sbWFXRmliR1ZRY21WelpXNTBZWFJwYjI0aVhYMHNJbWx6Y3lJNkltUnBaRHB3Y21semJUcGhaR0psT1RJNE9XUXdZelZtWWpVMlptWmhOVEF6T0Rka01UZ3dOR0ZpTkdFeE5UYzJOVEkzWXprME5tRTFNalV5T0RFM1ptRTRaVGhoTW1OalpXUXdPa056YzBKRGMyZENSVzFKUzBSWE1XaGpNMUpzWTJsb2NHSnRVbXhsUTJ0UlFWVktVRU5uYkZSYVYwNTNUV3BWTW1GNlJWTkpSUzFNYVVkTU0xRklaRlZ1VG10d1dXSkthSE5VYTIxWVVGaEpVM0ZXZWpjMll6RlZPWGhvVURseWNFZHBSSEZXTlRselJYcEtWbEpEYWxJMGEwMHdaMGg0YkhWUU5tVk5Ta2wwZHpJMk4yWllWbEpoTUhoRE5XaEthVU5uTVhSWldFNHdXbGhKYjJGWE5XdGFXR2R3UlVGU1ExUjNiMHBWTWxacVkwUkpNVTV0YzNoRmFVSlFhVFJvYVRrd1FqTldTbnBhUzFkSGVWbGlSVFZLYkhveGVVVnhiR010TFc1T1ZsQmpXVlJmWVRaU2IyYzJiR1ZtWWtKTmVWWlZVVzh3WlVwRVRrbENPRnBpYWkxdWFrTlRUR05PZFhVek1URlZWM1JOVVhWWkluMC5CcmFpbEVXa2VlSXhWbjY3dnpkVHZGTXpBMV9oNzFoaDZsODBHRFBpbkRaVVB4ajAxSC0tUC1QZDIxTk9wRDd3am51SDkxdUNBOFZMUW9fS2FnVjlnQQo="}"""
          _ <- repo.updateWithIssuedRawCredential(
            aIssueCredentialRecord.id,
            IssueCredential.makeIssueCredentialFromRequestCredential(requestCredential.makeMessage),
            rawCredentialData,
            IssueCredentialRecord.ProtocolState.CredentialReceived
          )
          svc <- ZIO.service[PresentationService]
          connectionId = Some("connectionId")
          anoncredPresentationRequestV1 = AnoncredPresentationRequestV1(
            Map.empty,
            Map.empty,
            "name",
            "nonce",
            "version",
            None
          )
          attachmentDescriptor = AttachmentDescriptor.buildBase64Attachment(
            mediaType = Some("application/json"),
            format = Some(PresentCredentialRequestFormat.Anoncred.name),
            payload =
              AnoncredPresentationRequestV1.schemaSerDes.serializeToJsonString(anoncredPresentationRequestV1).getBytes()
          )
          requestPresentation = RequestPresentation(
            body = RequestPresentation.Body(goal_code = Some("Presentation Request")),
            attachments = Seq(attachmentDescriptor),
            to = DidId("did:peer:Prover"),
            from = DidId("did:peer:Verifier"),
          )
          aRecord <- svc.receiveRequestPresentation(connectionId, requestPresentation)
          credentialsToUse = Seq(aIssueCredentialRecord.id.value)
          result <- svc.acceptRequestPresentation(aRecord.id, credentialsToUse).exit

        } yield assert(result)(
          fails(isSubtype[NotMatchingPresentationCredentialFormat](anything))
        )
      },
      test("rejectRequestPresentation updates the PresentationRecord") {
        for {
          svc <- ZIO.service[PresentationService]
          connectionId = Some("connectionId")
          aRecord <- svc.receiveRequestPresentation(
            connectionId,
            requestPresentation(PresentCredentialRequestFormat.JWT)
          )
          updateRecord <- svc.rejectRequestPresentation(aRecord.id)

        } yield {
          assertTrue(updateRecord.connectionId == connectionId) &&
          // assertTrue(updateRecord.requestPresentationData == Some(requestPresentation)) && // FIXME: enabling them make the test fail.
          assertTrue(updateRecord.protocolState == PresentationRecord.ProtocolState.RequestRejected)
        }
      },
      test("markPresentationSent returns updated PresentationRecord") {
        for {
          svc <- ZIO.service[PresentationService]
          pairwiseProverDid = DidId("did:peer:Prover")
          record <- svc.createJwtRecord()
          repo <- ZIO.service[PresentationRepository]
          _ <- repo.updatePresentationRecordProtocolState(
            record.id,
            PresentationRecord.ProtocolState.RequestPending,
            PresentationRecord.ProtocolState.PresentationGenerated
          )
          record <- svc.markPresentationSent(record.id)

        } yield {
          assertTrue(record.protocolState == PresentationRecord.ProtocolState.PresentationSent)
        }
      },
      test("receivePresentation updates the PresentationRecord") {
        for {
          svc <- ZIO.service[PresentationService]
          aRecord <- svc.createJwtRecord()
          p = presentation(aRecord.thid.value)
          aRecordReceived <- svc.receivePresentation(p)

        } yield {
          assertTrue(aRecordReceived.id == aRecord.id) &&
          assertTrue(aRecordReceived.presentationData == Some(p))
        }
      },
      test("acceptPresentation updates the PresentationRecord") {
        for {
          svc <- ZIO.service[PresentationService]
          aRecord <- svc.createJwtRecord()
          p = presentation(aRecord.thid.value)
          aRecordReceived <- svc.receivePresentation(p)
          repo <- ZIO.service[PresentationRepository]
          _ <- repo.updatePresentationRecordProtocolState(
            aRecord.id,
            PresentationRecord.ProtocolState.PresentationReceived,
            PresentationRecord.ProtocolState.PresentationVerified
          )
          aRecordAccept <- svc.acceptPresentation(aRecord.id)
        } yield {
          assertTrue(aRecordReceived.id == aRecord.id) &&
          assertTrue(aRecordReceived.presentationData == Some(p))
        }
      },
      test("markPresentationRejected updates the PresentationRecord") {
        for {
          svc <- ZIO.service[PresentationService]
          aRecord <- svc.createJwtRecord()
          p = presentation(aRecord.thid.value)
          _ <- svc.receivePresentation(p)
          repo <- ZIO.service[PresentationRepository]
          _ <- repo.updatePresentationRecordProtocolState(
            aRecord.id,
            PresentationRecord.ProtocolState.PresentationReceived,
            PresentationRecord.ProtocolState.PresentationVerified
          )
          aRecordReject <- svc.markPresentationRejected(aRecord.id)
        } yield {
          assertTrue(aRecordReject.id == aRecord.id) &&
          assertTrue(aRecordReject.presentationData == Some(p)) &&
          assertTrue(aRecordReject.protocolState == PresentationRecord.ProtocolState.PresentationRejected)
        }
      },
      test("rejectPresentation updates the PresentationRecord") {
        for {
          svc <- ZIO.service[PresentationService]
          aRecord <- svc.createJwtRecord()
          p = presentation(aRecord.thid.value)
          aRecordReceived <- svc.receivePresentation(p)
          repo <- ZIO.service[PresentationRepository]
          _ <- repo.updatePresentationRecordProtocolState(
            aRecord.id,
            PresentationRecord.ProtocolState.PresentationReceived,
            PresentationRecord.ProtocolState.PresentationVerified
          )
          aRecordReject <- svc.rejectPresentation(aRecord.id)
        } yield {
          assertTrue(aRecordReject.id == aRecord.id) &&
          assertTrue(aRecordReject.presentationData == Some(p)) &&
          assertTrue(aRecordReject.protocolState == PresentationRecord.ProtocolState.PresentationRejected)
        }
      },
      test("markPresentationGenerated returns updated PresentationRecord") {
        for {
          svc <- ZIO.service[PresentationService]
          pairwiseProverDid = DidId("did:peer:Prover")
          record <- svc.createJwtRecord()
          p = presentation(record.thid.value)
          repo <- ZIO.service[PresentationRepository]
          _ <- repo.updatePresentationRecordProtocolState(
            record.id,
            PresentationRecord.ProtocolState.RequestPending,
            PresentationRecord.ProtocolState.PresentationPending
          )
          record <- svc.markPresentationGenerated(record.id, p)
        } yield {
          assertTrue(record.protocolState == PresentationRecord.ProtocolState.PresentationGenerated)
        }
      },
      test("markProposePresentationSent returns updated PresentationRecord") {
        for {
          svc <- ZIO.service[PresentationService]
          pairwiseProverDid = DidId("did:peer:Prover")
          record <- svc.createJwtRecord()
          repo <- ZIO.service[PresentationRepository]
          _ <- repo.updatePresentationRecordProtocolState(
            record.id,
            PresentationRecord.ProtocolState.RequestPending,
            PresentationRecord.ProtocolState.ProposalPending
          )
          record <- svc.markProposePresentationSent(record.id)
        } yield {
          assertTrue(record.protocolState == PresentationRecord.ProtocolState.ProposalSent)
        }
      },
      test("receiveProposePresentation updates the PresentationRecord") {
        for {
          svc <- ZIO.service[PresentationService]
          aRecord <- svc.createJwtRecord()
          p = proposePresentation(aRecord.thid.value)
          aRecordReceived <- svc.receiveProposePresentation(p)
        } yield {
          assertTrue(aRecordReceived.id == aRecord.id) &&
          assertTrue(aRecordReceived.proposePresentationData == Some(p))
        }
      },
      test("acceptProposePresentation updates the PresentationRecord") {
        for {
          svc <- ZIO.service[PresentationService]
          aRecord <- svc.createJwtRecord()
          p = proposePresentation(aRecord.thid.value)
          aRecordReceived <- svc.receiveProposePresentation(p)
          repo <- ZIO.service[PresentationRepository]
          _ <- repo.updatePresentationRecordProtocolState(
            aRecord.id,
            PresentationRecord.ProtocolState.ProposalPending,
            PresentationRecord.ProtocolState.ProposalReceived
          )
          aRecordAccept <- svc.acceptProposePresentation(aRecord.id)
        } yield {
          assertTrue(aRecordReceived.id == aRecord.id) &&
          assertTrue(aRecordReceived.proposePresentationData == Some(p))
        }
      },
    ).provideSomeLayer(ZLayer.succeed(WalletAccessContext(WalletId.random)))

  private val multiWalletSpec =
    suite("multi-wallet spec")(
      test("createPresentation for different wallet and isolate records") {
        val walletId1 = WalletId.random
        val walletId2 = WalletId.random
        val wallet1 = ZLayer.succeed(WalletAccessContext(walletId1))
        val wallet2 = ZLayer.succeed(WalletAccessContext(walletId2))
        for {
          svc <- ZIO.service[PresentationService]
          record1 <- svc.createJwtRecord().provide(wallet1)
          record2 <- svc.createJwtRecord().provide(wallet2)
          ownRecord1 <- svc.getPresentationRecord(record1.id).provide(wallet1)
          ownRecord2 <- svc.getPresentationRecord(record2.id).provide(wallet2)
          crossRecord1 <- svc.getPresentationRecord(record1.id).provide(wallet2)
          crossRecord2 <- svc.getPresentationRecord(record2.id).provide(wallet1)
        } yield assert(ownRecord1)(isSome(equalTo(record1))) &&
          assert(ownRecord2)(isSome(equalTo(record2))) &&
          assert(crossRecord1)(isNone) &&
          assert(crossRecord2)(isNone)
      }
    )

}
