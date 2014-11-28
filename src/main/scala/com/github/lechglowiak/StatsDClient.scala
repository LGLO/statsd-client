package com.github.lechglowiak

import java.net.InetSocketAddress
import java.text.{DecimalFormatSymbols, DecimalFormat}
import java.util.Locale

import akka.actor._
import akka.io.{IO, Udp}
import akka.util.ByteString

import scala.concurrent.duration._


/**
 * Actual logic of encoding requests and passing them to StatsDSenderActor reference.
 * Requires ActorRef to sender actor that understands StatsDMessage.
 */
trait StatsDClient {

  def statsDSenderActor: ActorRef

  val df = new DecimalFormat("0.##########", DecimalFormatSymbols.getInstance(Locale.ENGLISH))

  /**
   * Sends 'counter' metric for 'key' with optional 'sample' attribute.
   * With no 'sample' specified it should be accepted by most statsD implementations.
   * Not all servers accept sample rates.
   */
  def counter(key: String, value: Long, sample: Double = 1.0) = process(key, value.toString, "c", sample)
  /**
   * Sends 'counter' metric for 'key' with optional 'sample' attribute.
   * Not all servers accept fractions.
   */
  def counterDouble(key: String, value: Double, sample: Double = 1.0) = process(key, df.format(value), "c", sample)

  /**
   * Shorthand for incrementing counter 'key' by 1.
   */
  def inc(key: String) = counter(key, 1l)

  /**
   * Shorthand for decrementing counter 'key' by 1.
   */
  def dec(key: String) = counter(key, -1l)

  /**
   * Sets value of gauge 'key'.
   * Most servers accept values in [0, 2**64]
   */
  def gauge(key: String, value: Long) = process(key, value.toString, "g")

  /**
   * Sets value of gauge 'key'.
   * Not all servers accept double values.
   */
  def gauge(key: String, value: Double) = process(key, df.format(value), "g")

  /**
   * Changes value of gauge 'key' by 'value'.
   * Sends sign of value in encoded message.
   */
  def gaugeChange(key: String, value: Long) = process(key, sign(value) + value.toString, "g")

  /**
   * Changes value of gauge 'key' by 'value'.
   * Sends sign of value in encoded message.
   */
  def gaugeChange(key: String, value: Double) = process(key, sign(value) + df.format(value), "g")

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

case class StatsDMessage(msg: String)

object StatsDClientImpl {
  def apply(system: ActorSystem, statsDSenderActor: InetSocketAddress, backoff: FiniteDuration, name: String) =
    system.actorOf(StatsDSenderActor.props(statsDSenderActor, backoff), name)

  def apply(system: ActorSystem, statsDSenderActor: InetSocketAddress, backoff: FiniteDuration) =
    system.actorOf(StatsDSenderActor.props(statsDSenderActor, backoff))
}

class StatsDClientImpl(val statsDSenderActor: ActorRef) extends StatsDClient

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
