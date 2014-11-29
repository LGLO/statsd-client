import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.UntypedActor;
import com.github.lechglowiak.StatsDClient;
import com.github.lechglowiak.StatsDClientImpl;
import com.github.lechglowiak.StatsDSenderActor;
import scala.concurrent.duration.Duration;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class JavaExplicitActorAndClient {
    public static void main(String[] args) throws Exception{
        ActorSystem system = ActorSystem.create();
        ActorRef sender = system.actorOf(
                StatsDSenderActor.props(
                        new InetSocketAddress("localhost", 8125),
                        Duration.create(10, TimeUnit.MILLISECONDS)),
                "statsD-standalone-sender");
        StatsDClient client = new StatsDClientImpl(sender);
        Thread.sleep(1000);//TODO: get rid of that
        client.gauge("my-gauge", 13.0);
    }
}
