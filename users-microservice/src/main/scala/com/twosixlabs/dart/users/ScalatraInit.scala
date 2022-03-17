package com.twosixlabs.dart.users

import com.twosixlabs.dart.auth.user.stores.KeycloakUserStore
import com.twosixlabs.dart.rest.scalatra.DartRootServlet
import com.twosixlabs.dart.users.controllers.DartUsersController
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatra.LifeCycle
import org.slf4j.{Logger, LoggerFactory}

import javax.servlet.ServletContext

class ScalatraInit extends LifeCycle {

    private val LOG : Logger = LoggerFactory.getLogger( getClass )

    private val config : Config = ConfigFactory.defaultApplication().resolve()

    val keycloakUserStore : KeycloakUserStore = KeycloakUserStore( config )

    val usersController : DartUsersController = DartUsersController( keycloakUserStore, config )

    val basePath : String = "/dart/api/v1/users"

    val rootController = new DartRootServlet( Some( basePath ),
                                              Some( getClass.getPackage.getImplementationVersion ) )

    // Initialize scalatra: mounts servlets
    override def init( context : ServletContext ) : Unit = {
        context.mount( rootController, "/*" )
        context.mount( usersController, basePath + "/*" )
    }

    // Scalatra callback to close out resources
    override def destroy( context : ServletContext ) : Unit = {
        super.destroy( context )
    }

}
