import org.mashupbots.socko.events.HttpResponseStatus
import org.mashupbots.socko.events.WebSocketHandshakeEvent
import org.mashupbots.socko.handlers.WebSocketBroadcastText
import org.mashupbots.socko.handlers.WebSocketBroadcaster
import org.mashupbots.socko.handlers.WebSocketBroadcasterRegistration
import org.mashupbots.socko.infrastructure.Logger
import org.mashupbots.socko.routes._
import org.mashupbots.socko.webserver.WebServer
import org.mashupbots.socko.webserver.WebServerConfig

import akka.actor._
import akka.actor.SupervisorStrategy
import akka.actor.SupervisorStrategy.Resume
import akka.actor.SupervisorStrategy.Decider
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala

// Fault Tolerance strategy. Because TwitterApp is not an Actor but is creating the
// top level actors, we need to set the strategy on the guardian. However by default Akka
// only has two guardian supervisor strategies - default and stop - whereas for Twitter we
// want to Resume in all cases in order to preserve state when the websocket session is still
// open. For example if the ClientHandler throws an exception trying to add or remove a follower,
// we still want it to be available with its current list of connected clients even though that
// one action failed.
// This guardiant strategy is created here and is set up in the application.conf file.
class ResumeSupervisorStrategy extends SupervisorStrategyConfigurator {
  override def create(): SupervisorStrategy = new SupervisorStrategy() {
    def handleChildTerminated(context: ActorContext, child: ActorRef, children: Iterable[ActorRef]): Unit = {

    }
    def processFailure(context: ActorContext, restart: Boolean, child: ActorRef, cause: Throwable, stats: ChildRestartStats, children: Iterable[ChildRestartStats]): Unit = {

    }

    def decider:Decider = {
      case _:Exception => Resume
    }
    OneForOneStrategy()(decider)
  }
}

object TwitterApp extends Logger {

  //
  // STEP #1 - Define Actors and Start Akka
  // `ChatHandler` is created in the route and is self-terminating
  //
  val actorSystem = ActorSystem("TwitterActorSystem")
  val SN = actorSystem.actorOf(Props[SocialNetworkHandler], "SN")
  val T = actorSystem.actorOf(Props[TweetHandler], "T")
  val CC = actorSystem.actorOf(Props(new ClientHandler(SN, T)), "CC")
  var CNMap = Map[String, ActorRef]()
  //
  // STEP #2 - Define Routes
  // Each route dispatches the request to a newly instanced `WebSocketHandler` actor for processing.
  // `WebSocketHandler` will `stop()` itself after processing the request. 
  //
  val routes = Routes({
	
    case HttpRequest(httpRequest) => httpRequest match {
        case GET(Path("/twitter")) => {
          // Create an Authentication actor to facilitate login/registration
          actorSystem.actorOf(Props[AuthenticationHandler]) ! httpRequest
  		  }

    		case Path("/favicon.ico") => {
    		  // If favicon.ico, just return a 404 because we don't have that file
          httpRequest.response.write(HttpResponseStatus.NOT_FOUND)
        }

    	  case POST(Path("/Login")) => {
      		// User has logged in, so create a client connection actor which will display the main
      		// interface page to establish the web socket and handle incoming/outgoing tweets for this user
      		val formDataMap = httpRequest.request.content.toFormDataMap
      		val username = formDataMap("username")(0)
          if (CNMap.contains(username)) {
            actorSystem.actorOf(Props[AuthenticationHandler]) ! AuthFailure(httpRequest)
          }
          else {
        		val CN = actorSystem.actorOf(Props(new ConnectionHandler(CC)), username)
            // Add CN to map so follow requests can later be properly routed
            CNMap += (username -> CN)
            // Send to CC
            CC ! Login(username, CN)
            // Pass on to newly created CN so it can display the html
        		CN ! httpRequest
          }
    	  }

        case POST(Path("/Logoff")) => {
          val formDataMap = httpRequest.request.content.toFormDataMap
          val username = formDataMap("username")(0)
          // Stop CN for this user
          val CN = CNMap.getOrElse(username, null)
          if (CN != null) {
            CN ! End
          }
          // Remove from map
          CNMap -= username
          // Send to CC
          CC ! Logoff(username)
          // Now we want to redirect the user back to the /twitter homepage
          actorSystem.actorOf(Props[AuthenticationHandler]) ! httpRequest
        }

    	  case POST(Path("/Follow")) => {
          val formDataMap = httpRequest.request.content.toFormDataMap
          val user = formDataMap("user")(0) // user to be followed
          println("user to follow: " + user)
          val self = formDataMap("self")(0) // follower
          println("follower: " + self)
          val tweets = formDataMap("tweets")(0)
          println("old tweets present?: " + tweets)
          SN ! NewFollower(user, self)
          CNMap(self) ! Refresh(self, tweets, httpRequest)
    	  }
    }
	
    case WebSocketHandshake(wsHandshake) => wsHandshake match {
  		case PathSegments("websocket" :: username :: Nil) => {
        // To start Web Socket processing, we first have to authorize the handshake.
        // This is a security measure to make sure that web sockets can only be established at your specified end points.
        wsHandshake.authorize(onComplete = Some((event: WebSocketHandshakeEvent) => { 			
  			   CC ! Registered(username, event.channel)
        }))
      }
    }
	
    case WebSocketFrame(wsFrame) => {
      // Send to CC so it can route the frame to the correct ConnectionHandler for the user
      // sending the tweet
      CC ! Tweet(wsFrame)
    }
  })
  
  //
  // STEP #3 - Start and Stop Socko Web Server
  //
  def main(args: Array[String]) {
    val webServer = new WebServer(WebServerConfig(), routes, actorSystem)
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run { webServer.stop() }
    })
    webServer.start()
    System.out.println("Open a few browsers and navigate to http://localhost:8888/html. Start tweeting!")
  }
}