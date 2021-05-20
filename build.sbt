lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    name := """play-mongo""",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.13.4",
    libraryDependencies ++= Seq(
      ws,
      ehcache,
      "org.mongodb.scala" %% "mongo-scala-driver" % "4.2.2",
      "io.minio" % "minio" % "8.2.1",
      "com.amazonaws" % "aws-java-sdk-s3" % "1.11.860",
      "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
    ),
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-Xfatal-warnings"
    )
  )
