package config.services

import com.sksamuel.hoplite.ConfigAlias
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.specification.RequestSpecification
import org.apache.http.HttpStatus
import org.testcontainers.containers.ComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.io.File

data class Keycloak(
    @ConfigAlias("http_port") val httpPort: Int,
    val realm: String = "atala-demo",
    @ConfigAlias("client_id") val clientId: String = "prism-agent",
    @ConfigAlias("client_secret") val clientSecret: String = "prism-agent-demo-secret",
    @ConfigAlias("keep_running") override val keepRunning: Boolean = false
) : ServiceBase {

    private val keycloakComposeFile = "src/test/resources/containers/keycloak.yml"
    private val keycloakEnvConfig: Map<String, String> = mapOf(
        "KEYCLOAK_HTTP_PORT" to httpPort.toString()
    )
    override val env: ComposeContainer =
        ComposeContainer(File(keycloakComposeFile)).withEnv(keycloakEnvConfig)
            .waitingFor("keycloak", Wait.forLogMessage(".*Running the server.*", 1))
    private val keycloakBaseUrl = "http://localhost:$httpPort/"
    private var requestBuilder: RequestSpecification? = null

    fun start(users: List<String>) {
        super.start()
        initRequestBuilder()
        createRealm()
        createClient()
        createUsers(users)
    }

    fun getKeycloakAuthToken(username: String, password: String): String {
        val tokenResponse =
            RestAssured
                .given().body("grant_type=password&client_id=$clientId&client_secret=$clientSecret&username=$username&password=$password")
                .contentType("application/x-www-form-urlencoded")
                .header("Host", "localhost")
                .post("http://localhost:$httpPort/realms/$realm/protocol/openid-connect/token")
                .thenReturn()
        tokenResponse.then().statusCode(HttpStatus.SC_OK)
        return tokenResponse.body.jsonPath().getString("access_token")
    }

    private fun getAdminToken(): String {
        val getAdminTokenResponse =
            RestAssured.given().body("grant_type=password&client_id=admin-cli&username=admin&password=admin")
                .contentType("application/x-www-form-urlencoded")
                .baseUri(keycloakBaseUrl)
                .post("/realms/master/protocol/openid-connect/token")
                .thenReturn()
        getAdminTokenResponse.then().statusCode(HttpStatus.SC_OK)
        return getAdminTokenResponse.body.jsonPath().getString("access_token")
    }

    private fun initRequestBuilder() {
        requestBuilder = RequestSpecBuilder()
            .setBaseUri(keycloakBaseUrl)
            .setContentType("application/json")
            .addHeader("Authorization", "Bearer ${getAdminToken()}")
            .build()
    }

    private fun createRealm() {
        RestAssured.given().spec(requestBuilder)
            .body(
                mapOf(
                    "realm" to realm,
                    "enabled" to true,
                    "accessTokenLifespan" to 3600000
                )
            )
            .post("/admin/realms")
            .then().statusCode(HttpStatus.SC_CREATED)
    }

    private fun createClient() {
        RestAssured.given().spec(requestBuilder)
            .body(
                mapOf(
                    "id" to clientId,
                    "directAccessGrantsEnabled" to true,
                    "authorizationServicesEnabled" to true,
                    "serviceAccountsEnabled" to true,
                    "secret" to clientSecret
                )
            )
            .post("/admin/realms/$realm/clients")
            .then().statusCode(HttpStatus.SC_CREATED)
    }

    private fun createUsers(users: List<String>) {
        users.forEach { keycloakUser ->
            RestAssured.given().spec(requestBuilder)
                .body(
                    mapOf(
                        "id" to keycloakUser,
                        "username" to keycloakUser,
                        "firstName" to keycloakUser,
                        "enabled" to true,
                        "credentials" to listOf(
                            mapOf(
                                "value" to keycloakUser,
                                "temporary" to false
                            )
                        )
                    )
                )
                .post("/admin/realms/$realm/users")
                .then().statusCode(HttpStatus.SC_CREATED)
        }
    }
}
