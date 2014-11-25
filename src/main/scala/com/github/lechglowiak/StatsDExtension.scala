package com.github.lechglowiak

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.actor._

import scala.concurrent.duration.Duration

class StatsDExtensionImpl(system: ActorSystem) extends StatsDClient with Extension {

  private val config = system.settings.config.getConfig("akka.contrib.statsd-client")
  private val hostname = config.getString("hostname")
  private val port = config.getInt("port")
  private val backoffTimeMillis = config.getLong("backoff-time-millis")

  val statsDSenderActor: ActorRef = system.actorOf(
    StatsDSenderActor.props(
      new InetSocketAddress(hostname, port),
      Duration(backoffTimeMillis, TimeUnit.MILLISECONDS)),
    "statsD-extension-sender")
}

object StatsDExtension
  extends ExtensionId[StatsDExtensionImpl]
  with ExtensionIdProvider {

  override def lookup() = StatsDExtension

  override def createExtension(system: ExtendedActorSystem): StatsDExtensionImpl =
    new StatsDExtensionImpl(system)

  /**
   * Java API
   */
  override def get(system: ActorSystem): StatsDExtensionImpl = super.get(system)
}

trait StatsDSending extends StatsDClient {
  this: Actor =>
  val statsDSenderActor = StatsDExtension(context.system).statsDSenderActor
}
