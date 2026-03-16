import play.core.PlayVersion
import play.sbt.PlayImport.*
import sbt.Keys.libraryDependencies
import sbt.*

object AppDependencies {

  private val bootstrapVersion = "10.7.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"   %% "bootstrap-backend-play-30" % bootstrapVersion,
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml"   % "2.14.3",
    ("com.networknt" % "json-schema-validator"     % "2.0.0")
      .exclude("com.fasterxml.jackson.core", "jackson-databind")
      .exclude("com.fasterxml.jackson.core", "jackson-core")
      .exclude("com.fasterxml.jackson.core", "jackson-annotations")
      .exclude("com.fasterxml.jackson.dataformat", "jackson-dataformat-yaml")
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapVersion % Test
  )

  val it: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapVersion % Test
  )
}
