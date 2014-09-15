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
    val akkaV = "2.3.6"
    val sprayV = "1.3.1"
    Seq(
      "io.spray"                  %%  "spray-client"          % sprayV,
      "com.typesafe.akka"         %%  "akka-slf4j"            % akkaV,
      "com.google.guava"          %   "guava"                 % "18.0",
      "com.google.code.findbugs"  %   "jsr305"                % "3.0.0",
      "com.blinkbox.books"        %%  "common-config"         % "1.2.1",
      "com.blinkbox.books"        %%  "common-json"           % "0.2.1",
      "com.blinkbox.books"        %%  "common-scala-test"     % "0.3.0"   % Test,
      "io.spray"                  %%  "spray-testkit"         % sprayV    % Test
    )
  }
)

val root = (project in file(".")).
  settings(buildSettings: _*).
  settings(dependencySettings: _*).
  settings(testSettings: _*)
