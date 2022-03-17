package com.twosixlabs.dart.users.api

import com.twosixlabs.dart.rest.ApiStandards
import com.twosixlabs.dart.rest.scalatra.models.FailureResponse
import sttp.model.{Method, StatusCode}
import sttp.tapir.docs.openapi.{OpenAPIDocsInterpreter, OpenAPIDocsOptions}
import sttp.tapir.openapi.circe.yaml.RichOpenAPI
import sttp.tapir.{Endpoint, EndpointInput, EndpointOutput, auth, oneOf, statusMapping}
import sttp.tapir._

import java.io
import scala.collection.mutable.ListBuffer

trait DartServiceApiDefinition {

    val serviceName : String

    val servicePathName : Option[ String ]

    private val endpointList : ListBuffer[ Endpoint[ _, _, _, _ ] ] = ListBuffer[ Endpoint[ _, _, _, _ ] ]()

    def notFoundErr( desc : String ) : EndpointOutput.StatusMapping[ String ] = statusMapping( StatusCode.NotFound, stringBody )
    def badRequestErr( desc : String ) : EndpointOutput.StatusMapping[ String ] = statusMapping( StatusCode.BadRequest, stringBody )
    def serviceUnavailableErr( desc: String ) : EndpointOutput.StatusMapping[ String ] = statusMapping( StatusCode.ServiceUnavailable, stringBody )
    def authenticationFailure( desc: String ) : EndpointOutput.StatusMapping [String ] = statusMapping( StatusCode.Unauthorized, stringBody )
    def authorizationFailure( desc: String ) : EndpointOutput.StatusMapping[ String ] = statusMapping( StatusCode.Forbidden, stringBody )

    lazy val basePath : String = ApiStandards.DART_API_PREFIX_V1 + "/" + servicePathName.getOrElse( serviceName.toLowerCase )

    private lazy val basePathSections = basePath.stripPrefix( "/" ).split( '/' ).map( _.trim )

    private def AddToDart[ I, E, O, R ]( endpt : Endpoint[ I, E, O, R ], errorResponses : EndpointOutput.StatusMapping[ String ]* ) : Endpoint[(String, I), (String, E), O, R] = {
        val dartEndPt = endpt
          .tag( serviceName )
          .prependErrorOut(
              oneOf(
                  authenticationFailure( "Authentication token missing or invalid" ),
                  authorizationFailure( "User not authorized for this operation" ) +: errorResponses : _*,
              )
           )
          .prependIn( auth.bearer[ String ]() )
          .prependIn( basePathSections.tail.foldLeft( basePathSections.head : EndpointInput[ Unit ] )( _ / _ ) )

        endpointList += dartEndPt
        dartEndPt
    }

    implicit class RegisterableEndpoint[ I, E, O, -R ]( endpt : Endpoint[ I, E, O, R ] ) {
        def addToDart( errorResponses : EndpointOutput.StatusMapping[ String ]* ) : Endpoint[ (String, I), (String, E), O, R ] = {
            AddToDart( endpt, errorResponses : _* )
        }
    }

    def allEndpoints : List[ Endpoint[ _, _, _, _ ] ] = endpointList.toList

    implicit val openApiOps : OpenAPIDocsOptions = OpenAPIDocsOptions(
        ( ids : Vector[ String ], method : Method ) => method.method.toLowerCase + ids.drop( basePathSections.length ).map( s => {
            val charArray = s.toLowerCase.toCharArray
            charArray( 0 ) = Character.toUpperCase( charArray( 0 ) )
            new String( charArray )
        } ).mkString
    )

    def openApiSpec : String = {
        println( allEndpoints )
        OpenAPIDocsInterpreter
          .toOpenAPI( allEndpoints, serviceName, "1.0" )
          .toYaml
    }

}
