import akka.actor.Actor
import scala.collection.JavaConversions._

case class NewFollower(username: String, follower: String)

class SocialNetworkHandler extends Actor {

	var FollowersMap = Map[String, List[String]]()

	def receive = {

		case Followers(username: String) => {
			sender ! FollowersList(username, FollowersMap.getOrElse(username, Nil))
		}

		case NewFollower(username: String, follower: String) => {
			FollowersMap += (username -> (follower :: FollowersMap.getOrElse(username, Nil)))
		}

		case WhoIsFollowedBy(username: String, adding: Boolean) => {
			var followees = List[String]()
			FollowersMap.foreach { 
				case (key, value) => {
					if (value.contains(username)) {
						followees = key :: followees
					}
				}
			}
			sender ! FollowedByList(username, followees, adding)
		}

		case _ => {
			println("Social Network Handler received unknown message")
		}
	}
}