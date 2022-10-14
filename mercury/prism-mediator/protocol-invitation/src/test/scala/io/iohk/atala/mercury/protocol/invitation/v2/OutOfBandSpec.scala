package io.iohk.atala.mercury.protocol.invitation.v2

import munit.*
import io.iohk.atala.mercury.protocol.invitation.v2._
import io.iohk.atala.mercury.protocol.invitation.OutOfBand
class OutOfBandSpec extends FunSuite {

  test("out-of-band (_oob URL) messagem parsing into Invitation") {
    val link =
      "http://127.0.0.1:8000?_oob=eyJ0eXBlIjoiaHR0cHM6Ly9kaWRjb21tLm9yZy9vdXQtb2YtYmFuZC8yLjAvaW52aXRhdGlvbiIsImlkIjoiNDIxZGJiYzgtNTdjYS00MzQxLWFhM2EtZjViNDIxNWM1NjhmIiwiZnJvbSI6ImRpZDpwZWVyOjIuRXo2TFNtTG1XbVR2d2pnTFN1VWFFUUhkSFNGV1B3eWliZ3pvbVdqRm1uQzZGaExuVS5WejZNa3ROZ0xoNE4xdTlLTmhEaXFlOEtaOGJzTHpMY3FzaWZvTmlVdEJvU3M5anhmLlNleUpwWkNJNkltNWxkeTFwWkNJc0luUWlPaUprYlNJc0luTWlPaUpvZEhSd09pOHZNVEkzTGpBdU1DNHhPamd3TURBaUxDSmhJanBiSW1ScFpHTnZiVzB2ZGpJaVhYMCIsImJvZHkiOnsiZ29hbF9jb2RlIjoicmVxdWVzdC1tZWRpYXRlIiwiZ29hbCI6IlJlcXVlc3RNZWRpYXRlIiwiYWNjZXB0IjpbImRpZGNvbW0vdjIiLCJkaWRjb21tL2FpcDI7ZW52PXJmYzU4NyJdfX0"

    val ret = OutOfBand.parseInvitation(link)

    assert(ret.isInstanceOf[Some[Invitation]])

    val expected = Invitation(
      "https://didcomm.org/out-of-band/2.0/invitation",
      "421dbbc8-57ca-4341-aa3a-f5b4215c568f",
      "did:peer:2.Ez6LSmLmWmTvwjgLSuUaEQHdHSFWPwyibgzomWjFmnC6FhLnU.Vz6MktNgLh4N1u9KNhDiqe8KZ8bsLzLcqsifoNiUtBoSs9jxf.SeyJpZCI6Im5ldy1pZCIsInQiOiJkbSIsInMiOiJodHRwOi8vMTI3LjAuMC4xOjgwMDAiLCJhIjpbImRpZGNvbW0vdjIiXX0",
      Body("request-mediate", "RequestMediate", Seq("didcomm/v2", "didcomm/aip2;env=rfc587")),
      None
    )
    assertEquals(ret, Some(expected))
  }

}
