package org.hyperledger.identus.pollux.credentialdefinition

import org.hyperledger.identus.api.http.*
import org.hyperledger.identus.api.http.codec.OrderCodec.*
import org.hyperledger.identus.api.http.model.{Order, PaginationInput}
import org.hyperledger.identus.api.http.EndpointOutputs.*
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyCredentials
import org.hyperledger.identus.iam.authentication.apikey.ApiKeyEndpointSecurityLogic.apiKeyHeader
import org.hyperledger.identus.iam.authentication.oidc.JwtCredentials
import org.hyperledger.identus.iam.authentication.oidc.JwtSecurityLogic.jwtAuthHeader
import org.hyperledger.identus.pollux.credentialdefinition.http.{
  CredentialDefinitionDidUrlResponse,
  CredentialDefinitionDidUrlResponsePage,
  CredentialDefinitionInnerDefinitionDidUrlResponse,
  CredentialDefinitionInput,
  CredentialDefinitionResponse,
  CredentialDefinitionResponsePage,
  FilterInput
}
import org.hyperledger.identus.pollux.PrismEnvelopeResponse
import sttp.apispec.{ExternalDocumentation, Tag}
import sttp.model.StatusCode
import sttp.tapir.{
  endpoint,
  extractFromRequest,
  path,
  query,
  statusCode,
  stringToPath,
  Endpoint,
  EndpointInput,
  PublicEndpoint
}
import sttp.tapir.json.zio.{jsonBody, schemaForZioJsonValue}

import java.util.UUID

object CredentialDefinitionRegistryEndpoints {

  private val tagName = "Credential Definition Registry"
  private val tagDescription =
    s"""
      |The __${tagName}__ is a REST API that allows to publish and lookup [Anoncreds Credential Definition](https://hyperledger.github.io/anoncreds-spec/#term:credential-definition) entities.
      |
      |A credential definition is generated by the issuer before credential any issuances and published for anyone (primarily holders and verifiers) to use.
      |In generating the published credential definition, related private data is also generated and held as a secret by the issuer.
      |The secret data includes the private keys necessary to generate signed verifiable credentials that can be presented and verified using the published credential definition.
      |
      |Endpoints are secured by __apiKeyAuth__ or __jwtAuth__ authentication.
      |""".stripMargin

  private val tagExternalDocumentation = ExternalDocumentation(
    url = "https://docs.atalaprism.io/tutorials/category/credential-definition",
    description = Some("Credential Definition documentation")
  )

  val tag = Tag(name = tagName, description = Option(tagDescription), externalDocs = Option(tagExternalDocumentation))

  val createCredentialDefinitionHttpUrlEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, CredentialDefinitionInput),
    ErrorResponse,
    CredentialDefinitionResponse,
    Any
  ] =
    endpoint.post
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in("credential-definition-registry" / "definitions")
      .in(
        jsonBody[CredentialDefinitionInput]
          .description(
            "JSON object required for the credential definition creation"
          )
      )
      .out(
        statusCode(StatusCode.Created)
          .description(
            "The new credential definition record is successfully created"
          )
      )
      .out(jsonBody[http.CredentialDefinitionResponse])
      .description("Credential definition record")
      .errorOut(basicFailureAndNotFoundAndForbidden)
      .name("createCredentialDefinitionHttpUrl")
      .summary("Publish new definition to the definition registry, resolvable by HTTP url")
      .description(
        "Create the new credential definition record with metadata and internal JSON Schema on behalf of Cloud Agent. " +
          "The credential definition will be signed by the keys of Cloud Agent and issued by the DID that corresponds to it."
      )
      .tag(tagName)

  val createCredentialDefinitionDidUrlEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (RequestContext, CredentialDefinitionInput),
    ErrorResponse,
    CredentialDefinitionResponse,
    Any
  ] =
    endpoint.post
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in("credential-definition-registry" / "definitions" / "did-url")
      .in(
        jsonBody[CredentialDefinitionInput]
          .description(
            "JSON object required for the credential definition creation"
          )
      )
      .out(
        statusCode(StatusCode.Created)
          .description(
            "The new credential definition record is successfully created"
          )
      )
      .out(
        jsonBody[http.CredentialDefinitionResponse]
      ) // We use same response as for HTTP url on DID url for definitions
      .description("Credential definition record")
      .errorOut(basicFailureAndNotFoundAndForbidden)
      .name("createCredentialDefinitionDidUrl")
      .summary("Publish new definition to the definition registry, resolvable by DID url")
      .description(
        "Create the new credential definition record with metadata and internal JSON Schema on behalf of Cloud Agent. " +
          "The credential definition will be signed by the keys of Cloud Agent and issued by the DID that corresponds to it."
      )
      .tag(tagName)

  val getCredentialDefinitionByIdHttpUrlEndpoint: PublicEndpoint[
    (RequestContext, UUID),
    ErrorResponse,
    CredentialDefinitionResponse,
    Any
  ] =
    endpoint.get
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "credential-definition-registry" / "definitions" / path[UUID]("guid").description(
          "Globally unique identifier of the credential definition record"
        )
      )
      .out(jsonBody[CredentialDefinitionResponse].description("CredentialDefinition found by `guid`"))
      .errorOut(basicFailuresAndNotFound)
      .name("getCredentialDefinitionByIdHttpUrl")
      .summary("Fetch the credential definition from the registry by `guid`")
      .description(
        "Fetch the credential definition by the unique identifier"
      )
      .tag(tagName)

  val getCredentialDefinitionByIdDidUrlEndpoint: PublicEndpoint[
    (RequestContext, UUID),
    ErrorResponse,
    PrismEnvelopeResponse,
    Any
  ] =
    endpoint.get
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "credential-definition-registry" / "definitions" / "did-url" / path[UUID]("guid").description(
          "Globally unique identifier of the credential definition record"
        )
      )
      .out(
        jsonBody[PrismEnvelopeResponse].description(
          "CredentialDefinition found by `guid`, wrapped in an envelope"
        )
      )
      .errorOut(basicFailuresAndNotFound)
      .name("getCredentialDefinitionByIdDidUrl")
      .summary("Fetch the credential definition from the registry by `guid`, wrapped in an envelope")
      .description(
        "Fetch the credential definition by the unique identifier, it should have been crated via DID url, otherwise not found error is returned."
      )
      .tag(tagName)

  val getCredentialDefinitionInnerDefinitionByIdHttpUrlEndpoint: PublicEndpoint[
    (RequestContext, UUID),
    ErrorResponse,
    zio.json.ast.Json,
    Any
  ] =
    endpoint.get
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "credential-definition-registry" / "definitions" / path[UUID]("guid") / "definition".description(
          "Globally unique identifier of the credential definition record"
        )
      )
      .out(jsonBody[zio.json.ast.Json].description("CredentialDefinition found by `guid`"))
      .errorOut(basicFailuresAndNotFound)
      .name("getCredentialDefinitionInnerDefinitionByIdHttpUrl")
      .summary("Fetch the inner definition field of the credential definition from the registry by `guid`")
      .description(
        "Fetch the inner definition fields of the credential definition by the unique identifier"
      )
      .tag(tagName)

  val getCredentialDefinitionInnerDefinitionByIdDidUrlEndpoint: PublicEndpoint[
    (RequestContext, UUID),
    ErrorResponse,
    PrismEnvelopeResponse,
    Any
  ] =
    endpoint.get
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "credential-definition-registry" / "definitions" / path[UUID]("guid") / "definition".description(
          "Globally unique identifier of the credential definition record"
        )
      )
      .out(
        jsonBody[PrismEnvelopeResponse].description("CredentialDefinition found by `guid`")
      )
      .errorOut(basicFailuresAndNotFound)
      .name("getCredentialDefinitionInnerDefinitionByIdDidUrl")
      .summary(
        "Fetch the inner definition field of the credential definition from the registry by `guid`, wrapped in an envelope"
      )
      .description(
        "Fetch the inner definition fields of the credential definition by the unique identifier, it should have been crated via DID url, otherwise not found error is returned."
      )
      .tag(tagName)

  private val credentialDefinitionFilterInput: EndpointInput[http.FilterInput] = EndpointInput.derived[http.FilterInput]
  private val paginationInput: EndpointInput[PaginationInput] = EndpointInput.derived[PaginationInput]
  val lookupCredentialDefinitionsByQueryHttpUrlEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (
        RequestContext,
        FilterInput,
        PaginationInput,
        Option[Order]
    ),
    ErrorResponse,
    CredentialDefinitionResponsePage,
    Any
  ] =
    endpoint.get
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "credential-definition-registry" / "definitions".description(
          "Lookup credential definitions by query"
        )
      )
      .in(credentialDefinitionFilterInput)
      .in(paginationInput)
      .in(query[Option[Order]]("order"))
      .out(jsonBody[CredentialDefinitionResponsePage].description("Collection of CredentialDefinitions records."))
      .errorOut(basicFailures)
      .name("lookupCredentialDefinitionsByQueryHttpUrl")
      .summary("Lookup credential definitions by indexed fields")
      .description(
        "Lookup credential definitions by `author`, `name`, `tag` parameters and control the pagination by `offset` and `limit` parameters "
      )
      .tag(tagName)

  val lookupCredentialDefinitionsByQueryDidUrlEndpoint: Endpoint[
    (ApiKeyCredentials, JwtCredentials),
    (
        RequestContext,
        FilterInput,
        PaginationInput,
        Option[Order]
    ),
    ErrorResponse,
    CredentialDefinitionDidUrlResponsePage,
    Any
  ] =
    endpoint.get
      .securityIn(apiKeyHeader)
      .securityIn(jwtAuthHeader)
      .in(extractFromRequest[RequestContext](RequestContext.apply))
      .in(
        "credential-definition-registry" / "definitions" / "did-url".description(
          "Lookup credential definitions by query"
        )
      )
      .in(credentialDefinitionFilterInput)
      .in(paginationInput)
      .in(query[Option[Order]]("order"))
      .out(jsonBody[CredentialDefinitionDidUrlResponsePage].description("Collection of CredentialDefinitions records."))
      .errorOut(basicFailures)
      .name("lookupCredentialDefinitionsByQueryDidUrl")
      .summary("Lookup credential definitions by indexed fields")
      .description(
        "Lookup DID url resolvable credential definitions by `author`, `name`, `tag` parameters and control the pagination by `offset` and `limit` parameters "
      )
      .tag(tagName)
}
