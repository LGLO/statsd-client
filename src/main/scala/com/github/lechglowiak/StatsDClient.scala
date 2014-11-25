package com.github.lechglowiak

import java.net.InetSocketAddress

import akka.actor._
import akka.io.{IO, Udp}
import akka.util.ByteString

import scala.concurrent.duration._


/**
 *  Actual logic of encoding requests and passing them to StatsDSenderActor reference.
 *  Requires ActorRef to sender actor that understands StatsDMessage.
 */
trait StatsDClient{

  def statsDSenderActor : ActorRef

  /**
   * Sends 'counter' metric for 'key' with optional 'sample' attribute.
   */
  def counter(key: String, value: Long, sample: Double = 1.0) = process(key, value.toString, "c", sample)
  /**
   * Shorthand for incrementing counter 'key' by 1.
   */
  def inc(key: String) = counter(key, 1)

  /**
   * Shorthand for decrementing counter 'key' by 1.
   */
  def dec(key: String) = counter(key, -1)

  /**
   * Sets value of gauge 'key'.
   */
  def gauge(key: String, value: Double) = process(key, value.toString, "g")

  /**
   * Changes value of gauge 'key' by 'value'.
   */
  def gaugeChange(key: String, value: Double) = process(key, sign(value) + value.toString, "g")

  /**
   * Sends timing metric for 'key'.
   */
  def timing(key: String, duration: Long) = process(key, duration.toString, "ms")

  def set(key: String, count: Long) = process(key, count.toString, "s")

  def meter(key: String, count: Long) = process(key, count.toString, "m")

  def histogram(key: String, duration: Long) = process(key, duration.toString, "h")

  private def sign(gaugeChange: Double) = if (gaugeChange >= 0.0) "+" else "-"

  private def process(key: String, value: String, t: String, sample: Double = 1.0) =
    send(encode(key, value, t, sample))

  private def encode(key: String, value: String, t: String, sample: Double) =
    if (sample >= 1.0)
      key + ":" + value + "|" + t
    else
      key + ":" + value + "|" + t + "@" + sample

  private def send(encodedMetric: String) =
    statsDSenderActor ! StatsDMessage(encodedMetric)
}

case class StatsDMessage(msg:String)

object StatsDClientImpl{
  def apply(system:ActorSystem, statsDSenderActor:InetSocketAddress, backoff:FiniteDuration, name: String)=
    system.actorOf(StatsDSenderActor.props(statsDSenderActor, backoff), name)
  def apply(system:ActorSystem, statsDSenderActor:InetSocketAddress, backoff:FiniteDuration)=
    system.actorOf(StatsDSenderActor.props(statsDSenderActor, backoff))
}

class StatsDClientImpl(val statsDSenderActor:ActorRef) extends StatsDClient

object StatsDSenderActor {
  def props(statsDAddress: InetSocketAddress, backoff: FiniteDuration) =
    Props(classOf[StatsDSenderActor], statsDAddress, backoff)
}

class StatsDSenderActor(statsDAddress: InetSocketAddress, backoff: FiniteDuration) extends Actor with ActorLogging {

  implicit val actorSystem = context.system
  implicit val ec = actorSystem.dispatcher
  log.debug(s"StatsDSender for: ${statsDAddress.toString}")

  override def preStart(): Unit = {
    IO(Udp) ! Udp.SimpleSender
  }

  def receive = uninitialized

  def uninitialized: Receive = {
    case Udp.SimpleSenderReady =>
      log.debug(s"StatsDSender becomes ready.")
      context.become(ready(sender()))
    case Udp.CommandFailed(cmd) =>
      log.debug(s"StatsDSender received CommandFailed: $cmd when uninitialized.")
      actorSystem.scheduler.scheduleOnce(backoff, IO(Udp), Udp.SimpleSender)
  }

  def ready(sender: ActorRef): Receive = {
    case Udp.CommandFailed(cmd) =>
      log.debug(s"StatsDSender received CommandFailed: $cmd when ready.")
      actorSystem.scheduler.scheduleOnce(backoff, IO(Udp), Udp.SimpleSender)
      context.become(uninitialized)
    case StatsDMessage(msg) =>
      sender ! Udp.Send(ByteString(msg), statsDAddress)
  }
}
