// This is needed due to a bug in the scala reflection that makes tests intermittently fail.
// See: https://issues.scala-lang.org/browse/SI-6240
val testSettings = Seq(
  parallelExecution in Test := false
)

val buildSettings = Seq(
  name := "midas-client",
  organization := "com.blinkbox.books.agora",
  version := scala.util.Try(scala.io.Source.fromFile("VERSION").mkString.trim).getOrElse("0.0.0"),
  scalaVersion  := "2.10.4",
  scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8", "-target:jvm-1.7",
    "-Xfatal-warnings", "-Xlint", "-Yno-adapted-args", "-Xfuture")
)

val dependencySettings = Seq(
  libraryDependencies ++= {
    val akkaV = "2.3.4"
    val sprayV = "1.3.1"
    val json4sV = "3.2.10"
    Seq(
      "io.spray"                  %   "spray-client"          % sprayV,
      "com.typesafe.akka"         %%  "akka-slf4j"            % akkaV,
      "com.typesafe.slick"        %%  "slick"                 % "2.1.0",
      "com.blinkbox.books"        %%  "common-config"         % "1.0.0",
      "org.json4s"                %%  "json4s-native"         % json4sV,
      //"com.blinkbox.books"        %%  "common-json"           % "0.2.0",
      "com.blinkbox.books"        %%  "common-scala-test"     % "0.3.0"   % "test",
      "io.spray"                  %   "spray-testkit"         % sprayV    % "test"
    )
  }
)

val root = (project in file(".")).
  settings(buildSettings: _*).
  settings(dependencySettings: _*).
  settings(testSettings: _*)
