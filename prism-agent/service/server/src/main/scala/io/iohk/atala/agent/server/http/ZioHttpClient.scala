package io.iohk.atala.agent.server.http

import io.iohk.atala.mercury.*
import zio.*
import zio.http.{Header as _, *}
<<<<<<< HEAD
<<<<<<< HEAD
=======

import java.time.Instant
>>>>>>> 7aa9b4c2 (feat: upgrade ZIO http client to improve performance (#850))
=======

import java.time.Instant
>>>>>>> 7aa9b4c2 (feat: upgrade ZIO http client to improve performance (#850))

object ZioHttpClient {
  val layer: URLayer[Client, ZioHttpClient] = ZLayer.fromFunction(new ZioHttpClient(_))
}

class ZioHttpClient(client: zio.http.Client) extends HttpClient {

  override def get(url: String): Task[HttpResponse] =
    val program = for {
      url <- ZIO.fromEither(URL.decode(url)).orDie
      response <- client
        .request(Request(url = url))
        .flatMap { response =>
          response.headers.toSeq.map(e => e)
          response.body.asString
            .map(body =>
              HttpResponse(
                response.status.code,
                response.headers.map(h => Header(h.headerName, h.renderedValue)).toSeq,
                body
              )
            )
        }
    } yield response
    program.provideSomeLayer(zio.Scope.default)

  def postDIDComm(url: String, data: String): Task[HttpResponse] =
    val program = for {
      url <- ZIO.fromEither(URL.decode(url)).orDie
      response <- client
        .request(
          Request(
            url = url, // TODO make ERROR type
            method = Method.POST,
            headers = Headers("content-type" -> "application/didcomm-encrypted+json"),
            body = Body.fromChunk(Chunk.fromArray(data.getBytes))
          )
        )
        .flatMap { response =>
          response.headers.toSeq.map(e => e)
          response.body.asString
            .map(body =>
              HttpResponse(
                response.status.code,
                response.headers.map(h => Header(h.headerName, h.renderedValue)).toSeq,
                body
              )
            )
        }
    } yield response
    program.provideSomeLayer(zio.Scope.default)
}
