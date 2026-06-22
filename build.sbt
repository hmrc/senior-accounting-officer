import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.3.4"

lazy val microservice = Project("senior-accounting-officer", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin, SwaggerPlugin)
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions += "-Wconf:src=routes/.*:s",
    PlayKeys.playDefaultPort := 10060,
    Compile / unmanagedResourceDirectories += baseDirectory.value / "src/main/resources"
  )
  .settings(CodeCoverageSettings.settings*)
  .settings(scalafixSettings*)
  .settings(playSwaggerSettings*)

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.it)

val scalafixSettings: Seq[Setting[?]] = Seq(
  semanticdbEnabled := true
)

val playSwaggerSettings: Seq[Setting[?]] = Seq(
  swaggerDomainNameSpaces := Seq(
    "uk.gov.hmrc.senioraccountingofficer.models"
  ),
  swaggerRoutesFile := "app.routes",
  swaggerV3         := true,
  swaggerPrettyJson := true
)

addCommandAlias("checkLint", "scalafmtSbtCheck;scalafmtCheckAll")
addCommandAlias("lint", "scalafixAll;scalafmtSbt;scalafmtAll")
