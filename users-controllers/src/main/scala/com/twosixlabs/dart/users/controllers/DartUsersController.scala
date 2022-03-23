package com.twosixlabs.dart.users.controllers

import com.twosixlabs.dart.auth.controllers.SecureDartController
import com.twosixlabs.dart.auth.controllers.SecureDartController.AuthDependencies
import com.twosixlabs.dart.auth.groups.DartGroup
import com.twosixlabs.dart.auth.permissions.DartOperations.{AddUser, DeleteUser, RetrieveUser, UpdateUserRole}
import com.twosixlabs.dart.auth.user.DartUserStore.{InvalidUserNameException, UserAlreadyExistsException, UserNotFoundException}
import com.twosixlabs.dart.auth.user.{DartUser, DartUserStore, UserMod}
import com.twosixlabs.dart.exceptions.ExceptionImplicits.FutureExceptionLogging
import com.twosixlabs.dart.exceptions.{AuthenticationException, AuthorizationException, BadQueryParameterException, BadRequestBodyException, DartRestException, ResourceNotFoundException, ServiceUnreachableException}
import com.twosixlabs.dart.json.JsonFormat._
import com.twosixlabs.dart.rest.scalatra.AsyncDartScalatraServlet
import com.twosixlabs.dart.users.api.models.DartUserForAddDto
import com.typesafe.config.Config
import org.hungerford.rbac.exceptions.{UnpermittedOperationException, UnpermittedOperationsException}
import org.hungerford.rbac.http.exceptions.{AuthenticationException => RbacAuthenticationException}
import org.scalatra.{Created, MethodOverride, Ok}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object DartUsersController {
    trait Dependencies extends SecureDartController.Dependencies {
        val userStore : DartUserStore

        def buildUsersController : DartUsersController = new DartUsersController( this )
    }

    def apply(
        userStore : DartUserStore,
        serviceName : String,
        secretKey : Option[ String ] = None,
        useDartAuth : Boolean = true,
        basicAuthCreds : Seq[ (String, String) ] = Nil,
    ) : DartUsersController = {
        val us = userStore; val sn = serviceName; val sk = secretKey; val ua = useDartAuth; val bac = basicAuthCreds
        new Dependencies {
            override val userStore : DartUserStore = us
            override val serviceName : String = sn
            override val secretKey : Option[ String ] = sk
            override val useDartAuth : Boolean = ua
            override val basicAuthCredentials : Seq[ (String, String) ] = bac
        } buildUsersController
    }

    def apply(
        userStore : DartUserStore,
        serviceName : String,
        authDependencies : AuthDependencies
    ) : DartUsersController = apply(
        userStore,
        serviceName,
        authDependencies.secretKey,
        authDependencies.bypassAuth,
    )

    def apply( userStore : DartUserStore, config : Config ) : DartUsersController = apply(
        userStore,
        Try( config.getString( "users.service.name" ) ).getOrElse( "users" ),
        SecureDartController.authDeps( config ),
    )
}

class DartUsersController( dependencies : DartUsersController.Dependencies )
  extends AsyncDartScalatraServlet with SecureDartController with MethodOverride {

    override val serviceName : String = dependencies.serviceName
    override val useDartAuth : Boolean = dependencies.useDartAuth
    override val secretKey : Option[ String ] = dependencies.secretKey
    override val basicAuthCredentials : Seq[ (String, String) ] = dependencies.basicAuthCredentials

    import dependencies.userStore

    setStandardConfig()

    override protected implicit def executor : ExecutionContext = scala.concurrent.ExecutionContext.global

    import com.twosixlabs.dart.users.api.models.DartUserDto.DartUserWithDto

    get( "/" )( handleOutput( AuthenticateRoute.withUser { implicit user : DartUser =>
        userStore.allUsers.map( _.filter( u => user.can( RetrieveUser.atLevel( u.groups ) ) ).map( _.toDto ) ) transform {
            case res@Success( _ ) => res
            case Failure( e ) => Failure( new ServiceUnreachableException( "user store", Some( e.getMessage ) ) )
        }
    } ) )

    get( "/:userId" )( handleOutput( AuthenticateRoute.withUser { implicit user : DartUser =>
        val userId = params.get( "userId" ).getOrElse( throw new BadQueryParameterException( List( "userId" ), Some( "userId is required" ) ) )
        userStore.user( userId ).map( retrievedUser => RetrieveUser.atLevel( retrievedUser.groups ).secure {
            retrievedUser.toDto
        } ).logged transform {
            case res@Success( _ ) => res
            case Failure( e : UserNotFoundException ) => Failure( new ResourceNotFoundException( "user", Some( userId ) ) )
            case Failure( e : RbacAuthenticationException ) => Failure( new AuthenticationException( e.getMessage ) )
            case Failure( e : UnpermittedOperationException ) => Failure( new AuthorizationException( s"unable to retrieve user ${userId}", e ) )
            case Failure( e : UnpermittedOperationsException ) => Failure( new AuthorizationException( s"unable to retrieve user ${userId}", e ) )
            case Failure( e ) => Failure( new ServiceUnreachableException( "user store", Some( e.getMessage ) ) )
        }
    } ) )

    post( "/:userId" )( handleOutput( AuthenticateRoute.withUser { implicit user : DartUser =>
        val userId = params.get( "userId" ).getOrElse( throw new BadQueryParameterException( List( "userId" ), Some( "userId is required" ) ) )
        val userDto = Try( unmarshalTo( request.body, classOf[ DartUserForAddDto ] ) ).flatten match {
            case Success( dto ) => dto
            case Failure( e ) => throw new BadRequestBodyException( s"Unable to deserialize user data: ${e.getMessage}" )
        }
        val userForAdd = Try( userDto.toUserMod( userId ) ) match {
            case Success( res ) => res
            case Failure( e ) => throw new BadRequestBodyException( e.getMessage )
        }

        Try( AddUser.atLevel( userForAdd.user.groups ).secure {
            ( for {
                _ <- userStore.user( userId ) transform {
                    case Success( _ ) => Failure( new BadQueryParameterException( List( userId ), Some( s"User $userId already exists" ) ) )
                    case Failure( _ : UserNotFoundException ) => Success()
                    case Failure( e ) => Failure( new ServiceUnreachableException( "user store", Some( e.getMessage ) ) )
                }
                _ <- userStore.addUser( userForAdd )
            } yield Created() ) transform {
                case res@Success( _ ) => res
                case dartFailure@Failure( e : DartRestException ) => dartFailure
                case Failure( e : UserAlreadyExistsException ) => Failure( new BadQueryParameterException( List( "userId" ), Some( e.getMessage ) ) )
                case Failure( e : InvalidUserNameException ) => Failure( new BadQueryParameterException( List( "userId" ), Some( e.getMessage ) ) )
                case Failure( e ) => Failure( new ServiceUnreachableException( "user store", Some( e.getMessage ) ) )
            }
        } ) match {
            case Success( res ) => res
            case Failure( e : RbacAuthenticationException ) => throw new AuthenticationException( e.getMessage )
            case Failure( e : UnpermittedOperationException ) => throw new AuthorizationException( s"unable to retrieve user ${userId}", e )
            case Failure( e : UnpermittedOperationsException ) => throw new AuthorizationException( s"unable to retrieve user ${userId}", e )
            case Failure( e ) => throw e

        }
    } ) )

    put( "/:userId" )( handleOutput( AuthenticateRoute.withUser { implicit user : DartUser =>
        val userId = params.get( "userId" )
          .getOrElse( throw new BadQueryParameterException( List( "userId" ), Some( "userId is required" ) ) )
        val userDto = unmarshalTo( request.body, classOf[ DartUserForAddDto ] ) match {
            case Success( dto ) => dto
            case Failure( e ) => throw new BadRequestBodyException( s"Unable to deserialize user data: ${e.getMessage}" )
        }
        val userForUpdate : UserMod = Try( userDto.toUserMod( userId ) ) match {
            case Success( res ) => res
            case Failure( e ) => throw new BadRequestBodyException( e.getMessage )
        }

        userStore.user( userId ) transformWith {
            case Success( u ) =>
                val userForUpdateFixed = userDto.groups match {
                    case Some( _ ) => userForUpdate
                    case None => userForUpdate.copy( user = userForUpdate.user.copy( groups = u.groups ) )
                }
                Future.fromTry( Try( AddUser.atLevel( u.groups ++ userForUpdateFixed.user.groups ).secure() ) )
                  .flatMap( _ => userStore.updateUser( userForUpdateFixed ) )
                  .map( _ => Ok() )
            case Failure( _ : UserNotFoundException ) =>
                Future.fromTry( Try( AddUser.atLevel( userForUpdate.user.groups ).secure() ) )
                  .flatMap( _ => userStore.addUser( userForUpdate ) )
                  .map( _ => Created() )
            case Failure( e ) => Future.failed( new ServiceUnreachableException( "user store", Some( e.getMessage ) ) )
        } transform {
            case res@Success( _ ) => res
            case Failure( e : InvalidUserNameException ) => Failure( new BadQueryParameterException( List( "userId" ), Some( e.getMessage ) ) )
            case Failure( e : RbacAuthenticationException ) => Failure( new AuthenticationException( e.getMessage )  )
            case Failure( e : UnpermittedOperationException ) => Failure( new AuthorizationException( s"unable to retrieve user ${userId}", e )  )
            case Failure( e : UnpermittedOperationsException ) => Failure( new AuthorizationException( s"unable to retrieve user ${userId}", e )  )
            case Failure( e ) => Failure( new ServiceUnreachableException( "user store", Some( e.getMessage ) ) )
        }
    } ) )

    post( "/:userId/groups" ) ( handleOutput( AuthenticateRoute.withUser { implicit user : DartUser =>
        val userId = params.get( "userId" ).getOrElse( throw new BadQueryParameterException( List( "userId" ), Some( "userId is required" ) ) )
        val groups : Set[ DartGroup ] = unmarshalTo[ List[ String ] ]( request.body, classOf[ List[ String ] ] ) match {
            case Success( grps ) => Try( grps.map( DartGroup.fromString ).toSet ) match {
                case Success( res ) => res
                case Failure( e : IllegalArgumentException ) => throw new BadRequestBodyException( e.getMessage )
                case Failure( e ) => throw e
            }
            case Failure( e ) => throw new BadRequestBodyException( s"Unable to deserialize groups: ${e.getMessage}" )
        }

        ( for {
            currentGroups <- userStore.user( userId ).map( _.groups )
            _ <- Future.fromTry( Try( UpdateUserRole.atLevel( currentGroups ++ groups ).secure() ) )
            _ <- userStore.addUserToGroups( userId, groups.toSeq )
        } yield Ok() ) transform {
            case Success( res ) => Success( res )
            case Failure( _ : UserNotFoundException ) => Failure( new ResourceNotFoundException( "user", Some( userId ) ) )
            case Failure( e : RbacAuthenticationException ) => Failure( new AuthenticationException( e.getMessage )  )
            case Failure( e : UnpermittedOperationException ) => Failure( new AuthorizationException( s"unable to retrieve user ${userId}", e )  )
            case Failure( e : UnpermittedOperationsException ) => Failure( new AuthorizationException( s"unable to retrieve user ${userId}", e )  )
            case Failure( e ) => Failure( new ServiceUnreachableException( "user store", Some( e.getMessage ) ) )
        }
    } ) )

    delete( "/:userId" )( handleOutput( AuthenticateRoute.withUser { implicit user : DartUser =>
        val userId = params.get( "userId" ).getOrElse( throw new BadQueryParameterException( List( "userId" ), Some( "userId is required" ) ) )
        ( for {
            retrievedUser <- userStore.user( userId )
            _ <- Future.fromTry( Try( DeleteUser.atLevel( retrievedUser.groups ).secure() ) ).logged
            _ <- userStore.removeUser( retrievedUser )
        } yield Ok() ) transform {
            case Success( res ) => Success( res )
            case Failure( _ : UserNotFoundException ) => Failure( new ResourceNotFoundException( "user", Some( userId ) ) )
            case Failure( e : RbacAuthenticationException ) => Failure( new AuthenticationException( e.getMessage )  )
            case Failure( e : UnpermittedOperationException ) => Failure( new AuthorizationException( s"unable to retrieve user ${userId}", e )  )
            case Failure( e : UnpermittedOperationsException ) => Failure( new AuthorizationException( s"unable to retrieve user ${userId}", e )  )
            case Failure( e ) => Failure( new ServiceUnreachableException( "user store", Some( e.getMessage ) ) )
        }
    } ) )

}
