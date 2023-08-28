val catsV = "2.10.0"
val catsEffectV = "3.5.1"
val fs2V = "3.8.0"
val http4sV = "0.23.19"
val circeV = "0.14.5"
val log4catsV = "2.6.0"

ThisBuild / tlBaseVersion := "0.0"

ThisBuild / organization := "io.chrisdavenport"
ThisBuild / organizationName := "Christopher Davenport"
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("christopherdavenport", "Christopher Davenport")
)
ThisBuild / tlSonatypeUseLegacyHost := true

ThisBuild / crossScalaVersions := Seq("2.12.18", "2.13.11", "3.3.0")
ThisBuild / tlCiReleaseBranches := Seq("main")

// Projects
lazy val `whale-tail` = tlCrossRootProject
  .aggregate(core, manager, examples)

lazy val core = crossProject(JSPlatform, JVMPlatform)
.crossType(CrossType.Pure)
.in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "whale-tail"
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.github.jnr" % "jnr-unixsocket" % "0.38.20" % Test,
    )
  )
  .jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule)},
  )

lazy val manager = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .dependsOn(core)
  .in(file("manager"))
  .settings(name := "whale-tail-manager")
  .jvmSettings(
    libraryDependencies += "com.github.jnr" % "jnr-unixsocket" % "0.38.20" % Test,
  )
  .jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule)},
  )

lazy val examples = project.in(file("examples"))
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .dependsOn(core.jvm, manager.jvm)
  .settings(
    name := "whale-tail-examples",
    libraryDependencies ++= Seq(
      "org.typelevel"           %% "log4cats-slf4j"             % log4catsV,
      "ch.qos.logback" % "logback-classic"      % "1.2.11",
      "org.http4s"                  %% "http4s-ember-server"        % http4sV,
      "com.github.jnr" % "jnr-unixsocket" % "0.38.20",
    )
  )

lazy val site = project.in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .settings(tlSiteIsTypelevelProject := true)
  .settings(commonSettings)
  .dependsOn(core.jvm)

// General Settings
lazy val commonSettings = Seq(

  libraryDependencies ++= Seq(
    // "com.github.jnr"              %  "jnr-unixsocket"             % "0.33",
    "org.typelevel"               %%% "cats-core"                  % catsV,

    "org.typelevel"               %%% "cats-effect"                % catsEffectV,

    "co.fs2"                      %%% "fs2-core"                   % fs2V,
    "co.fs2"                      %%% "fs2-io"                     % fs2V,

    "org.http4s"                  %%% "http4s-dsl"                 % http4sV,
    "org.http4s"                  %%% "http4s-ember-core"          % http4sV,
    "org.http4s"                  %%% "http4s-client"              % http4sV,
    "org.http4s"                  %%% "http4s-circe"               % http4sV,
    "org.http4s"                  %%% "http4s-ember-client"        % http4sV,

    "io.circe"                    %%% "circe-core"                 % circeV,
    "io.circe"                    %%% "circe-generic"              % circeV,
    "io.circe"                    %%% "circe-parser"               % circeV,
    "io.chrisdavenport"           %%% "env"                         % "0.1.0",

    "org.typelevel"                %%% "log4cats-core"              % log4catsV,
    "org.typelevel"           %%% "log4cats-testing"           % log4catsV     % Test,

    "org.typelevel" %%% "cats-effect-testing-specs2" % "1.5.0" % Test,
  )
)
