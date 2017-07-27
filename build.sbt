import de.heikoseeberger.sbtheader.license.Apache2_0
import de.heikoseeberger.sbtheader.CommentStyleMapping._
import ReleaseTransformations._

// Commands to run on Travis CI
val validateCommands = List(
  "clean",
  "compile",
  "test:compile",
  "scalafmtTest",
  "test",
  "doc"
)
// Do-it-all build alias for Travis CI
addCommandAlias("validate", validateCommands.mkString(";", ";", ""))

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    name := "gitlab-template",
    startYear := Some(2017),
    description := "Rendering your authorized_keys files based on Gitlab since 2017!",
    homepage := Some(url(s"https://github.com/FRosner/gitlab-template")),
    licenses += "Apache 2" -> url("https://www.apache.org/licenses/LICENSE-2.0"),
    // License headers
    headers := createFrom(Apache2_0, "2017", "Frank Rosner"),
    // Release settings: Publish maven style, sign our releases, and define the release steps
    publishMavenStyle := true,
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    mainClass in Compile := Some("de.frosner.gitlabtemplate.Main"),
    Defaults.itSettings,
    // Build settings of this project
    // Macro tooling (for simulacrum and others)
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    // A set of useful dependencies
    libraryDependencies ++= List(
      // Basic data structures for funtional programming
      "org.typelevel" %% "cats" % "0.9.0",
      // Enum types,
      "com.beachape" %% "enumeratum" % "1.5.11",
      "com.beachape" %% "enumeratum-play-json" % "1.5.12",
      // HTTP client
      "com.typesafe.play" %% "play-ahc-ws-standalone" % "1.0.2",
      // HTTP client JSON support
      "com.typesafe.play" %% "play-ws-standalone-json" % "1.0.2",
      // Nicer syntax for type classes
      "com.github.mpilquist" %% "simulacrum" % "0.10.0",
      // Generic programming over data structures
      "com.chuusai" %% "shapeless" % "2.3.2",
      // Test framework
      "org.scalatest" %% "scalatest" % "3.0.1" % "it,test",
      // Property testing
      "org.scalacheck" %% "scalacheck" % "1.13.4" % "it,test"
    ),
    // Build settings for all projects in this build
    inThisBuild(
      List(
        organization := "de.frosner",
        scmInfo := Some(ScmInfo(
          url("https://github.com/FRosner/gitlab-template"),
          "scm:git:https://github.com/FRosner/gitlab-template.git",
          Some(s"scm:git:git@github.com:FRosner/gitlab-template.git")
        )),
        // Credentials for Travis CI, see
        // http://www.cakesolutions.net/teamblogs/publishing-artefacts-to-oss-sonatype-nexus-using-sbt-and-travis-ci
        credentials ++= (for {
          username <- Option(System.getenv().get("SONATYPE_USERNAME"))
          password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
        } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)).toSeq,
        // Release settings for all projects
        releaseTagComment := s"gitlab-template ${version.value}",
        releaseCommitMessage := s"Bump version to ${version.value}",
        releaseCrossBuild := true,
        releaseProcess := List[ReleaseStep](
          checkSnapshotDependencies,
          inquireVersions,
          runTest,
          setReleaseVersion,
          commitReleaseVersion,
          tagRelease,
          publishArtifacts,
          setNextVersion,
          commitNextVersion,
          pushChanges,
          releaseStepCommand("sonatypeRelease")
        ),
        // Scala versions we build for
        scalaVersion := "2.12.1",
        crossScalaVersions := List("2.12.1", "2.11.8"),
        // Build settings
        scalacOptions ++= List(
          // Code encoding
          "-encoding",
          "UTF-8",
          // Deprecation warnings
          "-deprecation",
          // Warnings about features that should be imported explicitly
          "-feature",
          // Enable additional warnings about assumptions in the generated code
          "-unchecked",
          // Recommended additional warnings
          "-Xlint",
          // Warn when argument list is modified to match receiver
          "-Ywarn-adapted-args",
          // Warn about dead code
          "-Ywarn-dead-code",
          // Warn about inaccessible types in signatures
          "-Ywarn-inaccessible",
          // Warn when non-nullary overrides a nullary (def foo() over def foo)
          "-Ywarn-nullary-override",
          // Warn when numerics are unintentionally widened
          "-Ywarn-numeric-widen",
          // Fail compilation on warnings
          "-Xfatal-warnings"
        )
      )
    )
  )
  .enablePlugins(AutomateHeaderPlugin)
