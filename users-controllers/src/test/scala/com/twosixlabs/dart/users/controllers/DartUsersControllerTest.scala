package com.twosixlabs.dart.users.controllers

import com.fasterxml.jackson.core.{JsonParseException, JsonProcessingException}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twosixlabs.dart.auth.controllers.SecureDartController
import com.twosixlabs.dart.auth.groups.{DartGroup, ProgramManager, TenantGroup}
import com.twosixlabs.dart.auth.tenant.{CorpusTenant, GlobalCorpus, Leader, Member, ReadOnly}
import com.twosixlabs.dart.auth.user.stores.InMemoryUserStore
import com.twosixlabs.dart.auth.user.{DartUser, DartUserInfo, DartUserStore}
import com.twosixlabs.dart.rest.scalatra.models.FailureResponse
import com.twosixlabs.dart.test.tags.annotations.WipTest
import com.twosixlabs.dart.users.api.models.DartUserDto
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatra.test.scalatest.ScalatraSuite

import javax.servlet.http.HttpServletRequest
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.reflect.{ClassTag, classTag}
import scala.util.{Failure, Success, Try}

@WipTest
class DartUsersControllerTest extends AnyFlatSpecLike with ScalatraSuite with BeforeAndAfterEach with Matchers {
    protected val MAPPER : ObjectMapper = {
        val m = new ObjectMapper()
        m.registerModule( DefaultScalaModule )
        m.registerModule( new JavaTimeModule )
    }

    def marshalFrom( obj : Any ) : Try[ String ] = {
        try Success( MAPPER.writeValueAsString( obj ) )
        catch {
            case e : JsonProcessingException => Failure( e )
        }
    }

    def unmarshalTo[ T: ClassTag ]( json : String ) : Try[ T ] = {
        try Success( MAPPER.readValue( json, classTag[ T ].runtimeClass.asInstanceOf[ Class[ T ] ] ) )
        catch {
            case e : JsonParseException => Failure( e )
        }
    }

    val programManager : DartGroup = ProgramManager
    val globalLeader : DartGroup = TenantGroup( GlobalCorpus, Leader )
    val globalMember : DartGroup = TenantGroup( GlobalCorpus, Member )
    val globalReadOnly : DartGroup = TenantGroup( GlobalCorpus, ReadOnly )
    val balticsLeader : DartGroup = TenantGroup( CorpusTenant( "baltics-1" ), Leader )
    val balticsMember : DartGroup = TenantGroup( CorpusTenant( "baltics-1" ), Member )
    val balticsReadOnly : DartGroup = TenantGroup( CorpusTenant( "baltics-1" ), ReadOnly )

    val user1 = DartUser( "user-1", Set( programManager ) )
    val user2 = DartUser( "user-2", Set( globalReadOnly, balticsLeader ) )
    val user3 = DartUser( "user-3", Set( globalReadOnly, balticsMember ) )
    val user4 = DartUser( "user-4", Set( balticsMember ) )
    val user5 = DartUser( "user-5", Set( balticsReadOnly ) )

    val controllerDependencies = new DartUsersController.Dependencies {
        override val userStore : DartUserStore = new InMemoryUserStore()
        override val serviceName : String = "users"
        override val secretKey : Option[ String ] = None
        override val bypassAuth : Boolean = false
    }

    val pmController = new DartUsersController( controllerDependencies ) {
        override def authenticateUser( req : HttpServletRequest ) : DartUser = user1
    }
    val blController = new DartUsersController( controllerDependencies ) {
        override def authenticateUser( req : HttpServletRequest ) : DartUser = user2
    }
    val bmController = new DartUsersController( controllerDependencies ) {
        override def authenticateUser( req : HttpServletRequest ) : DartUser = user3
    }

    val userStore = controllerDependencies.userStore

    addServlet( pmController, "/pm/*" )
    addServlet( blController, "/bl/*" )
    addServlet( bmController, "/bm/*" )

    implicit class AwaitableFuture[ T ]( fut : Future[ T ] ) {
        def await : T = Await.result( fut, 5.seconds )
    }

    import com.twosixlabs.dart.users.api.models.DartUserDto.DartUserWithDto
    import com.twosixlabs.dart.users.api.models.DartUserForAddDto.DartUserWithAddDto

    override def beforeEach( ) : Unit = {
        super.beforeEach()
        ( for {
            users <- userStore.allUsers
            _ <- userStore.removeUsers( users.map( _.userName ).toSeq )
        } yield () ).await
        require( userStore.allUsers.await.isEmpty )
    }

    behavior of "GET /"

    it should "Return an empty list if nothing is in the user store" in {
        get( "/pm/" ) {
            status shouldBe 200
            body shouldBe "[]"
        }
        get( "/bl/" ) {
            status shouldBe 200
            body shouldBe "[]"
        }
        get( "/bm/" ) {
            status shouldBe 200
            body shouldBe "[]"
        }
    }

    it should "Return a list of all users if requesting user is a program-manager" in {
        userStore.addUser( user1, user2, user3 )
        get( "/pm/" ) {
            status shouldBe 200
            println( body )
            unmarshalTo[ Array[ DartUserDto ] ]( body ).get.toSet shouldBe Set( user1.toDto, user2.toDto, user3.toDto )
        }
    }

    it should "return a list only of baltics users if requesting user is program-leader" in {
        userStore.addUser( user1, user2, user3 )
        get( "/bl/" ) {
            status shouldBe 200
            println( body )
            unmarshalTo[ Array[ DartUserDto ] ]( body ).get.toSet shouldBe Set( user2.toDto, user3.toDto )
        }
    }

    behavior of "GET /userId"

    it should "return a user if the user is in the store and the requesting user is program-leader" in {
        userStore.addUser( user3 )
        get( s"/pm/${user3.userName}" ) {
            status shouldBe 200
            unmarshalTo[ DartUserDto ]( body ).get shouldBe user3.toDto
        }
    }

    it should "return 404 and an appropriate message if user is not in store" in {
        get( s"/pm/${user3.userName}" ) {
            status shouldBe 404
            unmarshalTo[ FailureResponse ]( body ).get.message should include( user3.userName )
        }
    }

    it should "return 403 if the requesting user does not have permission to retrieve requested user" in {
        userStore.addUser( user1 )
        get( s"/bm/${user1.userName}" ) {
            status shouldBe 403
            unmarshalTo[ FailureResponse ]( body ).get.message should include( user1.userName )
        }
    }

    behavior of "POST /userId"

    it should "successfully add a user if the user name is not already in the store, the name is valid, and the requesting user has permissions" in {
        val jsonBody = marshalFrom( user2.toAddDto ).get.getBytes( "utf8" )
        post( s"/pm/${user2.userName}", jsonBody ) {
            status shouldBe 201
            body.isEmpty shouldBe true
        }
        userStore.allUsers.await shouldBe Set( user2 )
    }

    it should "return 400 with an appropriate error message if user already exists" in {
        userStore.addUser( user2 ).await
        val jsonBody = marshalFrom( user2.toAddDto ).get.getBytes( "utf8" )
        post( s"/pm/${user2.userName}", jsonBody ) {
            status shouldBe 400
            unmarshalTo[ FailureResponse ]( body ).get.message should include( user2.userName )
        }
        userStore.allUsers.await shouldBe Set( user2 )
    }

    it should "return 400 with an appropriate error message if user name is invalid" in {
        val jsonBody = marshalFrom( user2.toAddDto ).get.getBytes( "utf8" )
        post( s"/pm/inValid_Username", jsonBody ) {
            status shouldBe 400
            unmarshalTo[ FailureResponse ]( body ).get.message should include( DartUserStore.ValidUser.toString )
        }
        userStore.allUsers.await shouldBe Set.empty[ DartUser ]
    }

    it should "return 400 with an appropriate error message if the body is missing" in {
        post( s"/pm/valid-username" ) {
            status shouldBe 400
            unmarshalTo[ FailureResponse ]( body ).get.message should include( "Unable to deserialize user data" )
        }
        userStore.allUsers.await shouldBe Set.empty[ DartUser ]
    }

    it should "return 400 and an appropriate error message if unable to read json body" in {
        val jsonBody = """{"groups ":[],}""".getBytes( "utf8" )
        post( s"/pm/${user2.userName}", jsonBody ) {
            status shouldBe 400
            unmarshalTo[ FailureResponse ]( body ).get.message should include( "Unable to deserialize user data" )
        }
        userStore.allUsers.await shouldBe Set()
    }

    it should "return 400 and an appropriate error message if group names are invalid" in {
        val jsonBody = marshalFrom( user2.toAddDto.copy( groups = Some( Set( "baltics@-member" ) ) ) ).get.getBytes( "utf8" )
        post( s"/pm/${user2.userName}", jsonBody ) {
            status shouldBe 400
            unmarshalTo[ FailureResponse ]( body ).get.message should include( "baltics@-member is not a valid group name" )
        }
        userStore.allUsers.await shouldBe Set()
    }

    it should "return 403 with an appropriate error message if the requesting user does not have permissions to add at the permissions level of the added user" in {
        val jsonBody = marshalFrom( user1.toAddDto ).get.getBytes( "utf8" )
        post( s"/bm/${user1.userName}", jsonBody ) {
            status shouldBe 403
            unmarshalTo[ FailureResponse ]( body ).get.message should include( user1.userName )
        }
        userStore.allUsers.await shouldBe Set.empty[ DartUser ]
    }

    it should "return 201 and add a user with empty groups if groups field is missing" in {
        val jsonBody = marshalFrom( user2.toAddDto.copy( groups = None ) ).get.getBytes( "utf8" )
        post( s"/pm/${user2.userName}", jsonBody ) {
            status shouldBe 201
            body.isEmpty shouldBe true
        }
        userStore.allUsers.await shouldBe Set( user2.copy( groups = Set.empty ) )
    }

    behavior of "PUT /userId"

    it should "successfully add user and return 201 with no body if the user name is not already in the store, the name is valid, valid user data is included, and the requesting" +
              " user has permissions" in {
        val jsonBody = marshalFrom( user2.toAddDto ).get.getBytes( "utf8" )
        put( s"/pm/${user2.userName}", jsonBody ) {
            status shouldBe 201
            body.isEmpty shouldBe true
        }
        userStore.allUsers.await shouldBe Set( user2 )
    }

    it should "successfully update user and return 200 with no body if the user is in the store, valid user data is included, and the requesting user has permissions to add a " +
              "user at the permissions levels of both the existing user and user" in {
        userStore.addUser( user2 )
        val updatedUser = user2.copy( userInfo = DartUserInfo( firstName = Some( "John" ), lastName = Some( "Doe" ) ), groups = Set( balticsReadOnly ) )
        val jsonBody = marshalFrom( updatedUser.toAddDto ).get.getBytes( "utf8" )
        put( s"/pm/${updatedUser.userName}", jsonBody ) {
            status shouldBe 200
            body.isEmpty shouldBe true
        }
        userStore.allUsers.await shouldBe Set( updatedUser )
    }

    it should "return 400 with an appropriate error message if the body is missing" in {
        post( s"/pm/valid-username" ) {
            status shouldBe 400
            unmarshalTo[ FailureResponse ]( body ).get.message should include( "Unable to deserialize user data" )
        }
        userStore.allUsers.await shouldBe Set.empty[ DartUser ]
    }

    it should "return 400 and an appropriate error message if unable to read json body" in {
        userStore.addUser( user2 )
        val jsonBody = """{"groups ":[],}""".getBytes( "utf8" )
        put( s"/pm/${user2.userName}", jsonBody ) {
            status shouldBe 400
            unmarshalTo[ FailureResponse ]( body ).get.message should include( "Unable to deserialize user data" )
        }
        userStore.allUsers.await shouldBe Set( user2 )
    }

    it should "return 400 and an appropriate error message if group names are invalid" in {
        userStore.addUser( user2 )
        val jsonBody = marshalFrom( user2.toAddDto.copy( groups = Some( Set( "baltics@-member" ) ) ) ).get.getBytes( "utf8" )
        put( s"/pm/${user2.userName}", jsonBody ) {
            status shouldBe 400
            unmarshalTo[ FailureResponse ]( body ).get.message should include( "baltics@-member is not a valid group name" )
        }
        userStore.allUsers.await shouldBe Set( user2 )
    }

    it should "return 403 with an appropriate error message if the requesting user does not have permissions to add at the permissions level of the original user or of the " +
              "updated version" in {
        userStore.addUser( user1 )
        val updatedUser = user1.copy( userInfo = DartUserInfo( firstName = Some( "John" ), lastName = Some( "Doe" ) ), groups = Set( balticsLeader, globalMember ) )
        val jsonBody = marshalFrom( updatedUser.toAddDto ).get.getBytes( "utf8" )
        put( s"/bm/${updatedUser.userName}", jsonBody ) {
            status shouldBe 403
            unmarshalTo[ FailureResponse ]( body ).get.message should include( updatedUser.userName )
        }
        userStore.allUsers.await shouldBe Set( user1 )
    }

    it should "return 403 with an appropriate error message if the requesting user does not have permissions to add at the permissions level of the original user but does have " +
              "permissions to add at the level submitted" in {
        userStore.addUser( user1 ).await
        val updatedUser = user1.copy( userInfo = DartUserInfo( firstName = Some( "John" ), lastName = Some( "Doe" ) ), groups = Set( balticsReadOnly ) )
        val jsonBody = marshalFrom( updatedUser.toAddDto ).get.getBytes( "utf8" )
        put( s"/bm/${updatedUser.userName}", jsonBody ) {
            status shouldBe 403
            unmarshalTo[ FailureResponse ]( body ).get.message should include( updatedUser.userName )
        }
        userStore.allUsers.await shouldBe Set( user1 )
    }

    it should "return 403 with an appropriate error message if the requesting user has permissions to add at the permissions level of the original user but does not have " +
              "permissions to add at the level submitted" in {
        userStore.addUser( user3 ).await
        val updatedUser = user3.copy( userInfo = DartUserInfo( firstName = Some( "John" ), lastName = Some( "Doe" ) ), groups = Set( balticsLeader, globalMember ) )
        val jsonBody = marshalFrom( updatedUser.toAddDto ).get.getBytes( "utf8" )
        put( s"/bm/${updatedUser.userName}", jsonBody ) {
            status shouldBe 403
            unmarshalTo[ FailureResponse ]( body ).get.message should include( updatedUser.userName )
        }
        userStore.allUsers.await shouldBe Set( user3 )
    }

    it should "return 201 and add a user with empty groups if groups field is missing" in {
        val jsonBody = marshalFrom( user2.toAddDto.copy( groups = None ) ).get.getBytes( "utf8" )
        put( s"/pm/${user2.userName}", jsonBody ) {
            status shouldBe 201
            body.isEmpty shouldBe true
        }
        userStore.allUsers.await shouldBe Set( user2.copy( groups = Set.empty ) )
    }

    it should "return 200 and update a user but not change groups if request's groups field is missing" in {
        userStore.addUser( user2 ).await

        val jsonBody = marshalFrom( user2.toAddDto.copy( firstName = Some( "DERP" ), groups = None ) ).get.getBytes( "utf8" )
        put( s"/pm/${user2.userName}", jsonBody ) {
            status shouldBe 200
            body.isEmpty shouldBe true
        }

        val updatedUsers = userStore.allUsers.await
        updatedUsers.size shouldBe 1
        val updatedUser = updatedUsers.head
        updatedUser.userInfo.firstName shouldBe Some( "DERP" )
        updatedUser.groups shouldBe user2.groups
        updatedUser.groups.size should not be( 0 )
    }

    it should "return 200 and remove all of a user's groups if request's groups field is defined but empty" in {
        userStore.addUser( user2 )
        val jsonBody = marshalFrom( user2.toAddDto.copy( firstName = Some( "DERP" ), groups = Some( Set.empty ) ) ).get.getBytes( "utf8" )
        put( s"/pm/${user2.userName}", jsonBody ) {
            status shouldBe 200
            body.isEmpty shouldBe true
        }
        val storedUser = userStore.allUsers.await.head
        storedUser.userInfo.firstName shouldBe Some( "DERP" )
        storedUser.groups shouldBe Set.empty
    }

    behavior of "POST /userId/groups"

    it should "add a user to groups and return 200 with no body if requesting user has permissions level to update at the level of those groups and at the level of the existing " +
              "user and the groups are all valid" in {
        userStore.addUser( user3 )
        val newGroups = List( globalMember, balticsLeader )
        val jsonBody = marshalFrom( newGroups.map( _.toString ) ).get.getBytes
        post( s"/pm/${user3.userName}/groups", jsonBody ) {
            status shouldBe 200
            body.isEmpty shouldBe true
        }
        userStore.user( user3.userName ).await shouldBe user3.copy( groups = user3.groups ++ newGroups.toSet )
    }

    it should "return 404 if user does not exist but requesting user has permissions to add selected groups" in {
        val newGroups = List( globalMember, balticsLeader )
        val jsonBody = marshalFrom( newGroups.map( _.toString ) ).get.getBytes
        post( s"/pm/${user3.userName}/groups", jsonBody ) {
            status shouldBe 404
            unmarshalTo[ FailureResponse ]( body ).get.message should include( user3.userName )
        }
        userStore.allUsers.await.isEmpty shouldBe true
    }

    it should "return 404 if user does not exist and requesting user does not have permissions to add selected groups" in {
        val newGroups = List( globalMember, programManager )
        val jsonBody = marshalFrom( newGroups.map( _.toString ) ).get.getBytes
        post( s"/bm/${user3.userName}/groups", jsonBody ) {
            status shouldBe 404
            unmarshalTo[ FailureResponse ]( body ).get.message should include( user3.userName )
        }
        userStore.allUsers.await.isEmpty shouldBe true
    }

    it should "return 400 and an appropriate message if any group names are invalid" in {
        userStore.addUser( user3 )
        val jsonBody = """["global/read-only","baltics@-member"]""".getBytes
        post( s"/pm/${user3.userName}/groups", jsonBody ) {
            status shouldBe 400
            unmarshalTo[ FailureResponse ]( body ).get.message should include( "baltics@-member is not a valid group name" )
        }
        userStore.allUsers.await shouldBe Set( user3 )
    }

    it should "return 403 and an appropriate error message if requesting user has permissions to update neither the original user nor the groups it is adding to it" in {
        userStore.addUser( user1 )
        val newGroups = List( globalMember )
        val jsonBody = marshalFrom( newGroups.map( _.toString ) ).get.getBytes
        post( s"/bm/${user1.userName}/groups", jsonBody ) {
            status shouldBe 403
            unmarshalTo[ FailureResponse ]( body ).get.message should include( user1.userName )
        }
        userStore.allUsers.await shouldBe Set( user1 )
    }

    it should "return 403 and an appropriate error message if requesting user has permissions to update the original user but not the groups it is adding to it" in {
        userStore.addUser( user3 )
        val newGroups = List( globalMember )
        val jsonBody = marshalFrom( newGroups.map( _.toString ) ).get.getBytes
        post( s"/bm/${user3.userName}/groups", jsonBody ) {
            status shouldBe 403
            unmarshalTo[ FailureResponse ]( body ).get.message should include( user3.userName )
        }
        userStore.allUsers.await shouldBe Set( user3 )
    }

    it should "return 403 and an appropriate error message if requesting user does not have permissions to update the original user but does have permissions to update the " +
              "groups it is adding to it" in {
        userStore.addUser( user3 )
        val newGroups = List( globalMember )
        val jsonBody = marshalFrom( newGroups.map( _.toString ) ).get.getBytes
        post( s"/bm/${user3.userName}/groups", jsonBody ) {
            status shouldBe 403
            unmarshalTo[ FailureResponse ]( body ).get.message should include( user3.userName )
        }
        userStore.allUsers.await shouldBe Set( user3 )
    }

    behavior of "DELETE /userId"

    it should "return 200 and an empty body and remove user from store if userId is in the store and the requesting user has permissions to remove it" in {
        val userForDelete = user3.copy( groups = Set( balticsMember ) )
        userStore.addUser( userForDelete )
        delete( s"/bl/${userForDelete.userName}" ) {
            println( body )
            status shouldBe 200
            body.isEmpty shouldBe true
        }
        userStore.allUsers.await shouldBe Set()
    }

    it should "return 404 and an appropriate error message if userId is not in the store" in {
        delete( s"/pm/${user3.userName}" ) {
            status shouldBe 404
            unmarshalTo[ FailureResponse ]( body ).get.message should include( user3.userName )
        }
        userStore.allUsers.await shouldBe Set()
    }

    it should "return 403 and an appropriate error message if userId is in the store but requesting user does not have permissions to delete it" in {
        userStore.addUser( user2 )
        delete( s"/bm/${user2.userName}" ) {
            status shouldBe 403
            unmarshalTo[ FailureResponse ]( body ).get.message should include( user2.userName )
        }
        userStore.allUsers.await shouldBe Set( user2 )
    }

    override def header = ???
}
