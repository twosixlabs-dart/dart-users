import com.typesafe.config.{Config, ConfigFactory}
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener
import org.slf4j.{Logger, LoggerFactory}

object Main {

    private val LOG : Logger = LoggerFactory.getLogger( getClass )

    def main( args : Array[ String ] ) : Unit = {

        // need to set system properties since we can't pass props directly to scalatra init
        val config : Config = ConfigFactory.defaultApplication().resolve()

        val port = config.getInt( "users.http.port" )
        val server = new Server( port )
        val context = new WebAppContext()

        context.setContextPath( "/" )
        context.setResourceBase( "src/main/webapp" )
        context.setInitParameter( ScalatraListener.LifeCycleKey, "com.twosixlabs.dart.users.ScalatraInit" ) // scalatra uses some magic defaults I don't like
        context.addEventListener( new ScalatraListener )

        server.setHandler( context )
        server.start()
        server.join()

    }
}