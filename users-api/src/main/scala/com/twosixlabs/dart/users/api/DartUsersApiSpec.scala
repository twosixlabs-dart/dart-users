package com.twosixlabs.dart.users.api

import com.twosixlabs.dart.auth.groups.DartGroup.{GroupPattern, ProgramManagerPattern}
import com.twosixlabs.dart.auth.groups.{DartGroup, ProgramManager}
import com.twosixlabs.dart.rest.scalatra.models.FailureResponse
import com.twosixlabs.dart.users.api.models.{DartUserDto, DartUserForAddDto}
import org.json4s.{Formats, NoTypeHints, Serialization}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.json.json4s._

object DartUsersApiSpec extends DartServiceApiDefinition {

    implicit val serialization: Serialization = org.json4s.jackson.Serialization
    implicit val formats: Formats = org.json4s.jackson.Serialization.formats(NoTypeHints)
    implicit val userSchema : Schema[ DartUserDto ] = DartUserDto.schema
    implicit val userAddSchema : Schema[ DartUserForAddDto ] = DartUserForAddDto.schema

    override val serviceName : String = "Dart Users"

    override val servicePathName : Option[ String ] = Some( "users" )

    val getUsers = endpoint
      .description( "List all users" )
      .get
      .out( jsonBody[ List[ DartUserDto ] ] )
      .addToDart()

    val getUser = endpoint
      .description( "Retrieve single user data" )
      .get
      .in( path[ String ]( "userId" ) )
      .out( jsonBody[ DartUserDto ] )
      .addToDart( notFoundErr( "User does not exist" ) )

    val addUser = endpoint
      .description( "Add new user" )
      .post
      .in( path[ String ]( "userId" ) )
      .in( jsonBody[ DartUserForAddDto ] )
      .out( statusCode( StatusCode.Created ).description( "Succesfully created user" ) )
      .addToDart( badRequestErr( "User id is invalid or already exists, or user data is invalid" ) )

    val updateUser = endpoint
      .description( "Add or update a user (overwrites)" )
      .put
      .in( path[ String ]( "userId" ) )
      .in( jsonBody[ DartUserForAddDto ] )
      .addToDart( badRequestErr( "User data is invalid" ) )

    val addUserToGroup = endpoint
      .description( "Add user to one or more groups" )
      .post
      .in( path[ String ]( "userId" ) / "groups" )
      .in( jsonBody[ List[ String ] ].modifySchema( v => v.copy( schemaType = SchemaType.SArray( SchemaBuilder.string.regex( s"${ProgramManagerPattern}|${GroupPattern}" ).build() ) ) ) )
      .addToDart( badRequestErr( "Invalid group" ), notFoundErr( "User does not exist" ) )

    val removeUser = endpoint
      .description( "Remove existing user" )
      .delete
      .in( path[ String ]( "userId" ) )
      .out( statusCode( StatusCode.Ok ).description( "Succesfully deleted user" ) )
      .addToDart( notFoundErr( "User does not exist" ) )
}
