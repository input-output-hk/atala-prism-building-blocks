import sbt._

object Dependencies {
  object Versions {
    val zio = "2.0.2"
    val akka = "2.6.19"
    val akkaHttp = "10.2.9"
    val doobie = "1.0.0-RC2"
    val zioCatsInterop = "3.3.0"
    val prismSdk = "v1.3.3-snapshot-1657194253-992dd96"
    val shared = "0.1.0"
    val enumeratum = "1.7.0"
  }

  private lazy val zio = "dev.zio" %% "zio" % Versions.zio
  private lazy val zioStream = "dev.zio" %% "zio-streams" % Versions.zio
  private lazy val zioCatsInterop = "dev.zio" %% "zio-interop-cats" % Versions.zioCatsInterop

  private lazy val akkaTyped = "com.typesafe.akka" %% "akka-actor-typed" % Versions.akka
  private lazy val akkaStream = "com.typesafe.akka" %% "akka-stream" % Versions.akka
  private lazy val akkaHttp = "com.typesafe.akka" %% "akka-http" % Versions.akkaHttp
  private lazy val akkaSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % Versions.akkaHttp

  private lazy val grpcNetty = "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion
  private lazy val grpcServices = "io.grpc" % "grpc-services" % scalapb.compiler.Version.grpcJavaVersion
  private lazy val scalaPbProto = "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
  private lazy val scalaPbGrpc = "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion

  private lazy val doobiePostgres = "org.tpolecat" %% "doobie-postgres" % Versions.doobie
  private lazy val doobieHikari = "org.tpolecat" %% "doobie-hikari" % Versions.doobie
  // We have to exclude bouncycastle since for some reason bitcoinj depends on bouncycastle jdk15to18
  // (i.e. JDK 1.5 to 1.8), but we are using JDK 11
  private lazy val prismCrypto = "io.iohk.atala" % "prism-crypto-jvm" % Versions.prismSdk excludeAll
    ExclusionRule(
      organization = "org.bouncycastle"
    )

  private lazy val shared = "io.iohk.atala" % "shared" % Versions.shared

  private lazy val enumeratum = ("com.beachape" %% "enumeratum" % Versions.enumeratum).cross(CrossVersion.for3Use2_13)

  // Dependency Modules
  private lazy val baseDependencies: Seq[ModuleID] = Seq(zio, prismCrypto, shared, enumeratum)
  private lazy val akkaHttpDependencies: Seq[ModuleID] = Seq(akkaTyped, akkaStream, akkaHttp, akkaSprayJson).map(_.cross(CrossVersion.for3Use2_13))
  private lazy val grpcDependencies: Seq[ModuleID] = Seq(grpcNetty, grpcServices, scalaPbProto, scalaPbGrpc)
  private lazy val doobieDependencies: Seq[ModuleID] = Seq(doobiePostgres, doobieHikari)

  // Project Dependencies
  lazy val coreDependencies: Seq[ModuleID] = baseDependencies ++ grpcDependencies
  lazy val sqlDependencies: Seq[ModuleID] = baseDependencies ++ doobieDependencies ++ Seq(zioCatsInterop)
  lazy val apiServerDependencies: Seq[ModuleID] = baseDependencies ++ akkaHttpDependencies
}
