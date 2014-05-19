import java.text.SimpleDateFormat
import java.util.GregorianCalendar

import org.mashupbots.socko.events.HttpRequestEvent
import org.mashupbots.socko.events.WebSocketFrameEvent
import org.mashupbots.socko.events.WebSocketHandshakeEvent

import akka.actor.actorRef2Scala
import akka.actor.Actor
import akka.actor._

import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.group.DefaultChannelGroup
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame

import scala.collection.JavaConversions._

case class FollowerUpdate(follower: Channel, adding: Boolean)
case class Refresh(user: String, tweets: String, event: HttpRequestEvent)
case object End

/**
 * Web Socket processor for chatting
 */
class ConnectionHandler(CC: ActorRef) extends Actor {

  val followers = new DefaultChannelGroup()
  var username = "";
  var oldTweets = "";

  /**
   * Process incoming events
   */
  def receive = {
	
    case event: HttpRequestEvent => {
		// Request list of connected followers
		val formDataMap = event.request.content.toFormDataMap
		username = formDataMap("username")(0)
		CC ! ConnectedFollowers(username)

      	// Return the HTML page to setup web sockets in the browser
      	writeHTML(event)
	}
	case Refresh(user, tweets, event: HttpRequestEvent) => {
		username = user
		oldTweets = tweets
		CC ! ConnectedFollowers(username)
		writeHTML(event)
	}
	case ConnectedList(connectedFollowers: List[Channel]) => {
		connectedFollowers.foreach(follower => {
			followers.add(follower)
		})
	}
	case FollowerUpdate(follower: Channel, adding: Boolean) => {
		if (adding) {
			followers.add(follower)
		}
		else {
			followers.remove(follower)
		}
	}
	case Tweet(event) => {
		// Send to followers
		println(username + " - Sending tweet to followers")
		val text = event.readText
		val user = text.substring(0, text.indexOf(':'))
		val tweet = text.substring(text.indexOf(':')+1, text.length)
		val dateFormatter = new SimpleDateFormat("HH:mm:ss")
		val time = new GregorianCalendar()
		val ts = dateFormatter.format(time.getTime())
		
		followers.write(new TextWebSocketFrame("From: " + user + "</br>" + ts + "</br>" + tweet))
	}
	case End => {
		context.stop(self)
	}
    case _ => {
      println("CN for " + username + " received unknown message")
      context.stop(self)
    }
  }

  /**
   * Write HTML page to setup a web socket on the browser
   */
  private def writeHTML(ctx: HttpRequestEvent) {
    // Send 100 continue if required
    if (ctx.request.is100ContinueExpected) {
      ctx.response.write100Continue()
    }

    val buf = new StringBuilder()

	buf.append("<html><head><title>Twitter App</title></head>\n")
	buf.append("<body>\n")
	buf.append("<script type=\"text/javascript\">\n")
	buf.append("  var socket;\n")
	buf.append("  if (!window.WebSocket) {\n")
	buf.append("    window.WebSocket = window.MozWebSocket;\n")
	buf.append("  }\n")
	buf.append("  if (window.WebSocket) {\n")
	buf.append("    socket = new WebSocket(\"ws://localhost:8888/websocket/" + username + "\");\n") // Note the address must match the route
	// When a new message comes over the socket, add it to the displayed tweets only if it wasn't sent out by this user
	buf.append("    socket.onmessage = function(event) {\n")
	buf.append("		 if (event.data.substring(event.data.indexOf(':')+2, event.data.indexOf('<')) != '" + username + "') { \n") 
	buf.append("			var oldhtml = '<p>' + document.getElementById('responseText').innerHTML + '</p>';\n")
	buf.append("			document.getElementById('responseText').innerHTML=oldhtml+event.data;\n")
	buf.append("		 }\n")
	buf.append("	};\n")
	buf.append("    socket.onopen = function(event) { };\n")
	buf.append("    socket.onclose = function(event) { };\n")
	buf.append("  } else { \n")
	buf.append("    alert(\"Your browser does not support Web Sockets.\");\n")
	buf.append("  }\n")
	buf.append("  \n")
	buf.append("  function send(message) {\n")
	buf.append("    if (!window.WebSocket) { return; }\n")
	buf.append("    if (socket.readyState == WebSocket.OPEN) {\n")
	buf.append("      socket.send('" + username + ":' + message);\n")
	buf.append("    } else {\n")
	buf.append("      alert(\"The socket is not open.\");\n")
	buf.append("    }\n")
	buf.append("  }\n")
	buf.append("  function setTweetsValue() {\n")
	buf.append("	  document.getElementById('tweets').value='<p>' + document.getElementById('responseText').innerHTML + '</p>';\n")
	buf.append("  }\n")
	buf.append("</script>\n")
	buf.append("<h1>Twitter Clone</h1>\n")
	buf.append("<h4>Welcome, " + username + "</h4>\n")
	buf.append("<h2>Follow Someone</h2>\n")
	buf.append("<form action=\"/Follow\" enctype=\"application/x-www-form-urlencoded\" method=\"Post\">\n")
	buf.append("	<input type=\"text\" onclick=\"setTweetsValue()\" name=\"user\" value=\"User\"/>\n")
	buf.append("	<input type=\"hidden\" name=\"self\" value=\"" + username + "\"/>\n")
	buf.append("	<input type=\"hidden\" id=\"tweets\" name=\"tweets\" value=\"\"/>\n")
	buf.append("	<input type=\"submit\" value=\"Follow\"/>\n")
	buf.append("</form>\n")
	buf.append("<h2>Send Out A Tweet</h2>\n")
	buf.append("<form onsubmit=\"return false;\">\n")
	buf.append("  <input type=\"text\" name=\"message\" value=\"Tweet tweet\"/>\n")
	buf.append("  <input type=\"button\" value=\"Tweet\" onclick=\"send(this.form.message.value)\" />\n")
	buf.append("</form>\n")
	buf.append("<h3>Log Out</h3>\n")
	buf.append("<form action=\"/Logoff\" enctype=\"application/x-www-form=urlencoded\" method=\"Post\">\n")
	buf.append("	<input type=\"hidden\" name=\"username\" value=\"" + username + "\"/>\n")
	buf.append("	<input type=\"submit\" value=\"Logoff\"/>\n")
	buf.append("</form>\n")
	buf.append("<h2>Latest Tweets</h2>\n")
	buf.append("<div id=\"responseText\"></div>\n")
	buf.append("<script type=\"text/javascript\">\n")
	buf.append("  document.getElementById('responseText').innerHTML='" + oldTweets + "';\n")
	buf.append("</script>\n")
	buf.append("</body>\n")
	buf.append("</html>\n")

	oldTweets = "";

    ctx.response.write(buf.toString, "text/html; charset=UTF-8")
  }
}