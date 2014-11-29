import java.net.InetSocketAddress

import akka.actor.{ActorRef, ActorSystem}
import com.github.lechglowiak.{StatsDClientImpl, StatsDSenderActor}

import scala.concurrent.duration._

object ExplicitActorAndClient extends App {
  val system = ActorSystem("statsd-tests")
  val sender: ActorRef = system.actorOf(
    StatsDSenderActor.props(
      new InetSocketAddress("localhost", 8125),
      Duration(10, MILLISECONDS)),
    "statsD-standalone-sender")

  val client = new StatsDClientImpl(sender)
  Thread.sleep(1000)//TODO:get rid of that - need API to get state(blocking and non-blocking).
  client.gauge("my-gauge", 13.0)
}


