import sbt._

object Dependencies {

    val slf4jVersion = "1.7.20"
    val logbackVersion = "1.2.3"
    val scalaTestVersion = "3.2.5"
    val scalatraVersion = "2.7.1"
    val jettyWebappVersion = "9.4.18.v20190429"
    val servletApiVersion = "3.1.0"
    val betterFilesVersion = "3.8.0"
    val jacksonVersion = "2.9.9"
    val okhttpVersion = "4.1.0"
    val scalaMockVersion = "4.1.0"
    val cdr4sVersion = "3.0.9"
    val dartCommonsVersion = "3.0.30"
    val dartRestVersion = "3.0.4"
    val dartAuthVersion = "3.1.11"
    val keycloak4sVersion = "2.4.0"
    val tapirVersion = "0.17.19"
    val json4sVersion = "3.6.11"
    val typesafeConfigVersion = "1.4.1"

    val logging = Seq( "org.slf4j" % "slf4j-api" % slf4jVersion,
                       "ch.qos.logback" % "logback-classic" % logbackVersion )

    val betterFiles = Seq( "com.github.pathikrit" %% "better-files" % betterFilesVersion )

    val scalatra = Seq( "org.scalatra" %% "scalatra" % scalatraVersion,
                        "org.scalatra" %% "scalatra-scalate" % scalatraVersion,
                        "org.scalatra" %% "scalatra-scalatest" % scalatraVersion % "test",
                        "org.eclipse.jetty" % "jetty-webapp" % jettyWebappVersion,
                        "javax.servlet" % "javax.servlet-api" % servletApiVersion )

    val scalaTest = Seq( "org.scalatest" %% "scalatest" % scalaTestVersion % "test" )

    val jackson = Seq( "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
                       "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion )

    val jsonValidator = Seq( "org.everit.json" % "org.everit.json.schema" % "1.5.1" )

    val okhttp = Seq( "com.squareup.okhttp3" % "okhttp" % okhttpVersion,
                      "com.squareup.okhttp3" % "mockwebserver" % okhttpVersion )

    val scalaMock = Seq( "org.scalamock" %% "scalamock" % scalaMockVersion )

    val cdr4s = Seq( "com.twosixlabs.cdr4s" %% "cdr4s-core" % cdr4sVersion,
                     "com.twosixlabs.cdr4s" %% "cdr4s-dart-json" % cdr4sVersion )

    val dartCommons = Seq( "com.twosixlabs.dart" %% "dart-test-base" % dartCommonsVersion )

    val dartRest = Seq( "com.twosixlabs.dart.rest" %% "dart-scalatra-commons" % dartRestVersion )

    val dartAuth = Seq( "com.twosixlabs.dart.auth" %% "controllers" % dartAuthVersion )

    val keycloakAdminClient = Seq("org.keycloak" % "keycloak-admin-client" % "12.0.4")

    val tapir = Seq( "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirVersion,
                     "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % tapirVersion,
                     "com.softwaremill.sttp.tapir" %% "tapir-openapi-circe-yaml" % tapirVersion,
                     "com.softwaremill.sttp.tapir" %% "tapir-json-json4s" % tapirVersion )

    val json4s = Seq( "org.json4s" %% "json4s-jackson" % json4sVersion )

    val typesafeConfig = Seq( "com.typesafe" % "config" % typesafeConfigVersion )

}
