import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

val catsV = "2.7.0"
val catsEffectV = "3.3.4"
val fs2V = "3.2.3"
val http4sV = "0.23.7"
val circeV = "0.14.1"
val log4catsV = "2.1.1"

ThisBuild / crossScalaVersions := Seq("2.12.15", "2.13.7", "3.1.0")


// Projects
lazy val `whale-tail` = project.in(file("."))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .aggregate(core.jvm, core.js, manager.jvm, manager.js, examples)

lazy val core = crossProject(JSPlatform, JVMPlatform)
.crossType(CrossType.Pure)
.in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "whale-tail"
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.github.jnr" % "jnr-unixsocket" % "0.38.15" % Test,
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
    libraryDependencies += "com.github.jnr" % "jnr-unixsocket" % "0.38.15" % Test,
  )
  .jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule)},
  )

lazy val examples = project.in(file("examples"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .settings(commonSettings)
  .dependsOn(core.jvm, manager.jvm)
  .settings(
    name := "whale-tail-examples",
    libraryDependencies ++= Seq(
      "org.typelevel"           %% "log4cats-slf4j"             % log4catsV,
      "ch.qos.logback" % "logback-classic"      % "1.2.10",
      "org.http4s"                  %% "http4s-ember-server"        % http4sV,
      "com.github.jnr" % "jnr-unixsocket" % "0.38.15",
    )
  )

lazy val site = project.in(file("site"))
  .disablePlugins(MimaPlugin)
  .enablePlugins(DavenverseMicrositePlugin)
  .settings(commonSettings)
  .dependsOn(core.jvm)
  .settings{
    import microsites._
    Seq(
      micrositeDescription := "Pure Docker Client",
    )
  }

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

    "org.typelevel" %%% "cats-effect-testing-specs2" % "1.4.0" % Test,
  )
)