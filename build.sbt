ThisBuild / tlBaseVersion := "0.2" // your current series x.y

ThisBuild / organization := "io.chrisdavenport"
ThisBuild / organizationName := "Christopher Davenport"
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  tlGitHubDev("christopherdavenport", "Christopher Davenport")
)
ThisBuild / tlCiReleaseBranches := Seq("main")
ThisBuild / tlSonatypeUseLegacyHost := true


val Scala213 = "2.13.14"

ThisBuild / crossScalaVersions := Seq("2.12.19", Scala213, "3.4.2")
ThisBuild / scalaVersion := Scala213

ThisBuild / testFrameworks += new TestFramework("munit.Framework")

val catsV = "2.11.0"
val catsEffectV = "3.5.4"
val fs2V = "3.10.2"
val http4sV = "0.23.27"
val circeV = "0.14.9"
val natchezV = "0.3.5"
val rediculousV = "0.6.0-M1"
val munitCatsEffectV = "2.0.0-M4"

val slf4jV    = "1.7.36"


// Projects
lazy val `natchez-rediculous` = tlCrossRootProject
  .aggregate(core, examples)

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "natchez-rediculous",
    mimaPreviousArtifacts := Set(),

    libraryDependencies ++= Seq(
      "org.typelevel"               %%% "cats-core"                  % catsV,
      "org.typelevel"               %%% "cats-effect"                % catsEffectV,

      "co.fs2"                      %%% "fs2-core"                   % fs2V,
      "co.fs2"                      %%% "fs2-io"                     % fs2V,

      "io.chrisdavenport"           %%% "rediculous"                 % rediculousV,
      "org.tpolecat"                %%% "natchez-core"               % natchezV,
      "org.typelevel"               %%% "munit-cats-effect"          % munitCatsEffectV         % Test,

    )
  ).jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule)},
  )

lazy val examples = project.in(file("examples"))
  .enablePlugins(NoPublishPlugin)
  .dependsOn(core.jvm)
  .settings(
    scalacOptions        -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-jaeger"      % natchezV,
      "io.chrisdavenport" %% "natchez-http4s-otel" % "0.3.0-RC1",
      "org.http4s"   %% "http4s-dsl"          % http4sV,
      "org.http4s"   %% "http4s-ember-server" % http4sV,
      "org.slf4j"     % "slf4j-simple"        % slf4jV,
    )
  )

lazy val site = project.in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .dependsOn(core.jvm)
