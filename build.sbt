import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.3.4"

lazy val microservice = Project("senior-accounting-officer", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions += "-Wconf:src=routes/.*:s",
    PlayKeys.playDefaultPort := 10060
  )
  .settings(CodeCoverageSettings.settings: _*)
  .settings(scalafixSettings *)

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.it)

val scalafixSettings: Seq[Setting[?]] = Seq(
  semanticdbEnabled := true
)

addCommandAlias("checkLint", "scalafmtSbtCheck;scalafmtCheckAll")
addCommandAlias("lint", "scalafixAll;scalafmtSbt;scalafmtAll")
