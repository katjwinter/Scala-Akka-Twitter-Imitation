import java.text.SimpleDateFormat
import java.util.GregorianCalendar

import org.mashupbots.socko.events.HttpRequestEvent
import org.mashupbots.socko.events.WebSocketFrameEvent
import org.mashupbots.socko.handlers.WebSocketBroadcastText

import akka.actor.actorRef2Scala
import akka.actor.Actor

case class AuthFailure(event: HttpRequestEvent)

/**
 * Handler for client authentication
 */
class AuthenticationHandler extends Actor {
  val loggedIn = false;
  var failure = false;

  /**
   * Process incoming events
   */
  def receive = {
    case event: HttpRequestEvent => {
       // Present login or registration form
      writeHTML(event)
      context.stop(self)
    }
    case AuthFailure(event: HttpRequestEvent) => {
      failure = true;
      writeHTML(event)
      context.stop(self)
    }
    case _ => {
      println("Authentication Handler received unknown message")
    }
  }

  /**
   * Write HTML page to allow user to log in or register
   */
  private def writeHTML(ctx: HttpRequestEvent) {
    // Send 100 continue if required
    if (ctx.request.is100ContinueExpected) {
      ctx.response.write100Continue()
    }

    val buf = new StringBuilder()

  	buf.append("<html><head><title>Welcome</title></head>\n")
  	buf.append("<body>\n")
  	buf.append("<script type=\"text/javascript\">\n")
    buf.append("if (" + failure + ") { \n")
    buf.append("  alert(\"That username is already in use. Try logging in with a different username\");\n")
    buf.append("}\n")
  	buf.append("</script>\n")
  	buf.append("<h1>Log In</h1>\n")
  	buf.append("<form action=\"/Login\" enctype=\"application/x-www-form-urlencoded\" method=\"Post\">\n")
  	buf.append("	<input type=\"text\" name=\"username\" value=\"Username\"/>\n")
  	buf.append("	<input type=\"submit\" value=\"Login\"/>\n")
  	buf.append("</form>\n")
  	buf.append("</body>\n")
  	buf.append("</html>\n")

    ctx.response.write(buf.toString, "text/html; charset=UTF-8")
  }
}