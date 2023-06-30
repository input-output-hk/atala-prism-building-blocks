package io.iohk.atala.agent.server.http

import io.iohk.atala.api.http.ErrorResponse
import org.http4s.*
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.http4s.ztapir.ZHttp4sServerInterpreter
import sttp.tapir.ztapir.ZServerEndpoint
import zio.*
import zio.interop.catz.*

object ZHttp4sBlazeServer {

  val serverOptions: Http4sServerOptions[Task] = Http4sServerOptions
    .customiseInterceptors[Task]
    .defaultHandlers(ErrorResponse.failureResponseHandler)
    .options

  def start(
      endpoints: List[ZServerEndpoint[Any, Any]],
      port: Int
  ): Task[ExitCode] = {
    val http4sEndpoints: HttpRoutes[Task] =
      ZHttp4sServerInterpreter(serverOptions)
        .from(endpoints)
        .toRoutes

    val serve: Task[Unit] =
      ZIO.executor.flatMap(executor =>
        BlazeServerBuilder[Task]
          .withExecutionContext(executor.asExecutionContext)
          .bindHttp(port, "0.0.0.0")
          .withHttpApp(Router("/" -> http4sEndpoints).orNotFound)
          .serve
          .compile
          .drain
      )

    serve.exitCode
  }
}
