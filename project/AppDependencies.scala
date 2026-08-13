import play.core.PlayVersion
import play.sbt.PlayImport.*
import sbt.Keys.libraryDependencies
import sbt.*

object AppDependencies {

  private val bootstrapVersion = "10.7.0"
  private val pekkoVersion     = "1.1.5"
  private val jacksonVersion   = "2.21.1"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"                     %% "bootstrap-backend-play-30" % bootstrapVersion,
    "uk.gov.hmrc"                     %% "domain-test-play-30"       % "13.0.0",
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml"   % jacksonVersion,
    ("com.networknt"                   % "json-schema-validator"     % "2.0.4")
      .exclude("com.fasterxml.jackson.core", "jackson-databind")
      .exclude("com.fasterxml.jackson.core", "jackson-core")
      .exclude("com.fasterxml.jackson.core", "jackson-annotations")
      .exclude("com.fasterxml.jackson.dataformat", "jackson-dataformat-yaml"),
    "org.typelevel"           %% "cats-core"                   % "2.13.0",
    "io.github.openhtmltopdf"  % "openhtmltopdf-pdfbox"        % "1.1.70",
    "org.apache.pekko"        %% "pekko-connectors-file"       % "1.3.0",
    "uk.gov.hmrc.objectstore" %% "object-store-client-play-30" % "2.6.0"
  )

  def overrides: Seq[ModuleID] = Seq(
    "org.apache.pekko"             %% "pekko-actor-typed"           % pekkoVersion,
    "org.apache.pekko"             %% "pekko-serialization-jackson" % pekkoVersion,
    "org.apache.pekko"             %% "pekko-slf4j"                 % pekkoVersion,
    "com.fasterxml.jackson.core"    % "jackson-core"                % jacksonVersion,
    "com.fasterxml.jackson.core"    % "jackson-databind"            % jacksonVersion,
    "com.fasterxml.jackson.core"    % "jackson-annotations"         % removePatch(jacksonVersion),
    "com.fasterxml.jackson.module" %% "jackson-module-scala"        % jacksonVersion
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapVersion % Test
  )

  val it: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "bootstrap-test-play-30" % bootstrapVersion % Test
  )

  def removePatch(versionString: String): String =
    versionString.replaceAll("\\.\\d*$", "")
}
