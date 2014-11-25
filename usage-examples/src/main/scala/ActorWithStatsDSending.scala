import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.io.{Udp, IO}
import com.github.lechglowiak.StatsDSending

import scala.concurrent.duration.Duration

object Main extends App {
  val as = ActorSystem("main")
  as.actorOf(Props(classOf[Receiver]))
  as.actorOf(Props(classOf[ActorWithStatsDSending]))
}


class ActorWithStatsDSending extends Actor with StatsDSending {
  var sent = 0l
  var timer: Cancellable = _

  override def preStart(): Unit = {
    implicit val ex = context.system.dispatcher
    timer = context.system.scheduler.schedule(Duration(100, TimeUnit.MILLISECONDS), Duration(100, TimeUnit.MILLISECONDS), self, "send-next")
  }

  def receive: Receive = {
    case "send-next" =>
      if (sent < 100) {
        counter("test.metric", sent, 1.0 / 8)
        sent += 1
        println(s"sent: $sent")
      } else {
        timer.cancel()
        context.system.shutdown()
      }
  }
}

class Receiver extends Actor {
  implicit val actorSystem = context.system
  var counter = 0

  override def preStart(): Unit = {
    IO(Udp) ! Udp.Bind(self, new InetSocketAddress("127.0.0.1", 8125))
  }

  def receive: Receive = {
    case Udp.Bound(addr) =>
      println(s"Bound to: $addr")
    case Udp.Received(bs, sender) =>
      counter += 1
      println(s"Received metric ${bs.decodeString("UTF-8")} from $sender, total received: $counter")
  }
}