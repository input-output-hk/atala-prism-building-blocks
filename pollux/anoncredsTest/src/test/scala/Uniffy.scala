import uniffi.anoncreds_wrapper._
object Uniffy extends App {
  val prover = new Prover()
  val seceret = prover.createLinkSecret();

  println("Prover secret")
  println(seceret.getBigNumber)
}
