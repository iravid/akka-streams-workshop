lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.iravid",
      scalaVersion := "2.12.4",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "Akka Streams workshop",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-stream" % "2.5.11"
    )
  )
