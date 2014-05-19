import akka.actor.Actor
import akka.actor.actorRef2Scala
import akka.actor._
import org.jboss.netty.channel.Channel
import org.mashupbots.socko.events.WebSocketFrameEvent
import scala.collection.JavaConversions._

case class Login(username: String, cN: ActorRef)
case class Logoff(username: String)
case class ConnectedFollowers(username: String)
case class Followers(username: String)
case class FollowersList(username: String, followerList: List[String])
case class ConnectedList(connectedFollowers: List[Channel])
case class Registered(username: String, channel: Channel)
case class WhoIsFollowedBy(username: String, adding: Boolean)
case class FollowedByList(follower: String, followees: List[String], adding: Boolean)
case class Tweet(event: WebSocketFrameEvent)
case class NewTweet(username: String, tweet: String)
case class Retrieve(username: String)
case class TweetsFrom(username:String, tweets: List[String])

class ClientHandler(SN: ActorRef, T: ActorRef) extends Actor {

	var CNMap = Map[String, ActorRef]()
	var ConnectedUsersMap = Map[String, Channel]()

	def receive = {

		case Login(username: String, cN: ActorRef) => {
			CNMap  += (username -> cN)
		}
		case Registered(username: String, channel: Channel) => {
			ConnectedUsersMap += (username -> channel)
			// Get a list of who this user follows (with add/remove user flag set to add)
			SN ! WhoIsFollowedBy(username, true)
		}
		case ConnectedFollowers(username: String) => {
			SN ! Followers(username)
		}
		case FollowersList(username: String, followerList: List[String]) => {
			var connectedFollowersList = List[Channel]()
			followerList.foreach { follower =>
				// Get the channel for the follower from ConnectedUsersMap and if the
				// channel is found (meaning follower is online) then add to list
				val followerChannel = ConnectedUsersMap.getOrElse(follower, null)
				if (followerChannel != null) {
					connectedFollowersList = followerChannel :: connectedFollowersList
				}
			}
			// Send the list of connected followers to the user's CN
			val CN = CNMap.getOrElse(username, null)
			if (CN != null) {
				CN ! ConnectedList(connectedFollowersList)
			}
			else {
				println("ERROR: Error sending follower list from ClientHandler")
			}
		}
		case FollowedByList(follower: String, followees: List[String], adding: Boolean) => {
			// Get the channel for the follower
			val followerChannel = ConnectedUsersMap.getOrElse(follower, null)
			// Then for each followee, get their CN and notify them
			// of their newly connected or newly disconnected follower
			// (connected/disconnected determined by the 'adding' flag which tells
			// the CN if the follower is to be added or removed)
			// *If followerChannel is null then we don't have a channel to notify the followees
			// about, so just leave it. This might happen if a user logs off immediately after
			// logging on, but at that point they should have no followees anyway since we aren't
			// persisting data.
			if (followerChannel != null) {
				followees.foreach { followee => 
					val CN = CNMap.getOrElse(followee, null)
					if (CN != null) {
						CN ! FollowerUpdate(followerChannel, adding)
					}
					else {
						println("ERROR: Error sending follower update from ClientHandler")
					}
				}
			}
		}
		case Tweet(event) => {
			// Send the tweet on to the CN for this user
			val username = event.readText.substring(0, event.readText.indexOf(':'))
        	val CN = CNMap.getOrElse(username, null)
        	if (CN != null) {
        		CN ! Tweet(event)
        	}
        	else {
        		println("ERROR: Error sending a tweet from CC to CN. No CN for this user")
        	}
        	// Send the tweet to the TweetHandler to save
        	T ! NewTweet(username, event.readText)
		}
		case Logoff(username: String) => {
			CNMap -= username
			ConnectedUsersMap -= username
			// Get a list of who this user follows with the add/remove user flag set to false
			SN ! WhoIsFollowedBy(username, false)
		}
		case _ => {
			println("ClientHandler received unknown message")
		}
	}
}