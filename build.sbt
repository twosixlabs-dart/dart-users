import sbt._
import Dependencies._
import sbtassembly.AssemblyPlugin.autoImport.assemblyMergeStrategy

/*
   ##############################################################################################
   ##                                                                                          ##
   ##                                  SETTINGS DEFINITIONS                                    ##
   ##                                                                                          ##
   ##############################################################################################
 */

// integrationConfig and wipConfig are used to define separate test configurations for integration testing
// and work-in-progress testing
lazy val IntegrationConfig = config( "integration" ) extend( Test )
lazy val WipConfig = config( "wip" ) extend( Test )

lazy val commonSettings = {
    inConfig( IntegrationConfig )( Defaults.testTasks ) ++
    inConfig( WipConfig )( Defaults.testTasks ) ++
    Seq(
        organization := "com.twosixlabs.dart.users",
        scalaVersion := "2.12.7",
        resolvers ++= Seq( "Maven Central" at "https://repo1.maven.org/maven2/",
                           "JCenter" at "https://jcenter.bintray.com",
                           "Local Ivy Repository" at s"file://${System.getProperty( "user.home" )}/.ivy2/local/default" ),
        javacOptions ++= Seq( "-source", "1.8", "-target", "1.8" ),
        scalacOptions += "-target:jvm-1.8",
        scalacOptions += "-Ypartial-unification",
        useCoursier := false,
        libraryDependencies ++= logging ++
                                scalaTest ++
                                betterFiles ++
                                scalaMock ++
                                dartCommons,
        // `sbt test` should skip tests tagged IntegrationTest
        Test / testOptions := Seq( Tests.Argument( "-l", "annotations.IntegrationTest" ) ),
        // `sbt integration:test` should run only tests tagged IntegrationTest
        IntegrationConfig / parallelExecution := false,
        IntegrationConfig / testOptions := Seq( Tests.Argument( "-n", "annotations.IntegrationTest" ) ),
        // `sbt wip:test` should run only tests tagged WipTest
        WipConfig / testOptions := Seq( Tests.Argument( "-n", "annotations.WipTest" ) ),
    )
}

lazy val publishSettings = Seq(
    publishTo := {
        // TODO
        None
    },
    publishMavenStyle := true,
)

lazy val disablePublish = Seq(
    publish := {}
)

lazy val assemblySettings = Seq(
    libraryDependencies ++= scalatra ++ jackson,
    assemblyMergeStrategy in assembly := {
        case PathList( "META-INF", "MANIFEST.MF" ) => MergeStrategy.discard
        case PathList( "reference.conf" ) => MergeStrategy.concat
        case x => MergeStrategy.last
    },
    Compile / unmanagedResourceDirectories += baseDirectory.value / "src/main/webapp",
    test in assembly := {},
    mainClass in( Compile, run ) := Some( "Main" ),
)


/*
   ##############################################################################################
   ##                                                                                          ##
   ##                                  PROJECT DEFINITIONS                                     ##
   ##                                                                                          ##
   ##############################################################################################
 */

lazy val root = ( project in file( "." ) )
  .disablePlugins( sbtassembly.AssemblyPlugin )
  .aggregate( usersApi, usersControllers, usersMicroservice, usersClient )
  .settings(
      name := "dart-users",
      disablePublish
   )

lazy val usersApi = ( project in file( "users-api" ) )
  .configs( IntegrationConfig, WipConfig )
  .disablePlugins( sbtassembly.AssemblyPlugin )
  .settings(
      commonSettings,
      libraryDependencies ++= jackson
                              ++ logging
                              ++ dartCommons
                              ++ dartRest
                              ++ scalaTest
                              ++ dartAuth
                              ++ tapir
                              ++ json4s,
      dependencyOverrides ++= Seq( "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.9" )
                              ++ jackson,
      publishSettings,
  )

lazy val usersControllers = ( project in file( "users-controllers" ) )
  .dependsOn( usersApi )
  .configs( IntegrationConfig, WipConfig )
  .disablePlugins( sbtassembly.AssemblyPlugin )
  .settings(
      commonSettings,
      libraryDependencies ++= dartRest ++ dartAuth ++ jackson ++ scalatra ++ jsonValidator,
      dependencyOverrides ++= Seq( "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.9" )
                              ++ jackson,
      publishSettings,
   )

lazy val usersMicroservice = ( project in file( "users-microservice" ) )
  .dependsOn( usersControllers )
  .configs( IntegrationConfig, WipConfig )
  .enablePlugins( JavaAppPackaging )
  .settings(
      commonSettings,
      libraryDependencies ++= dartCommons ++ scalatra ++ jackson ++ typesafeConfig,
      dependencyOverrides ++= Seq( "com.softwaremill.sttp.model" %% "core" % "1.1.4",
                                   "com.fasterxml.jackson.core" % "jackson-databind" % "2.9.9")
                              ++ jackson,
      assemblySettings,
      disablePublish,
   )

lazy val usersClient = ( project in file( "users-client" ) )
  .dependsOn( usersApi )
  .configs( IntegrationConfig, WipConfig )
  .disablePlugins( sbtassembly.AssemblyPlugin )
  .settings(
      commonSettings,
      libraryDependencies ++= betterFiles ++ okhttp ++ jackson,
      publishSettings,
   )

ThisBuild / useCoursier := false
