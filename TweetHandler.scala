import akka.actor.Actor

class TweetHandler extends Actor {

	var tweetMap = Map[String, List[String]]()

	def receive = {
		case NewTweet(username, tweet) => {
			println("Adding tweet in TweetHandler")
			tweetMap += (username -> (tweet :: tweetMap.getOrElse(username, Nil)))
		}
		case Retrieve(username) => {
			sender ! TweetsFrom(username, tweetMap.getOrElse(username, Nil))
		}
		case _ => {
			println("Tweet Handler received unknown message")
		}
	}
}