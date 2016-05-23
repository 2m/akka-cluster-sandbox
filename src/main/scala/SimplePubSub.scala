import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.cluster.pubsub.DistributedPubSub
import akka.actor._
import akka.cluster.pubsub.DistributedPubSubMediator._
import scala.concurrent.duration._

object SimplePubSub extends App {

  class Echo extends Actor with ActorLogging {
    def receive = {
      case msg => log.info("Received {}", msg)
    }
  }

  val config = ConfigFactory.parseString("""
    akka {
      extensions = ["akka.cluster.pubsub.DistributedPubSub"]
      actor {
        provider = "akka.cluster.ClusterActorRefProvider"
      }
      remote {
        log-remote-lifecycle-events = on
        netty.tcp {
          hostname = "127.0.0.1"
          port = 2551
        }
      }
      cluster {
        seed-nodes = ["akka.tcp://ClusterSystem@127.0.0.1:2551"]
        #auto-down-unreachable-after = 10s
      }
    }""")


  args.toList match {
    case "seed" :: rest =>
      val sys = ActorSystem("ClusterSystem", config)
      val mediator = DistributedPubSub(sys).mediator

      import sys.dispatcher
      sys.scheduler.schedule(0.seconds, 1.second) {
        mediator ! Send(path = "/user/echo", msg = math.random, localAffinity = false)
      }

    case _ =>
      val sys = ActorSystem("ClusterSystem", ConfigFactory.parseString("akka.remote.netty.tcp.port=0").withFallback(config))
      val mediator = DistributedPubSub(sys).mediator
      mediator ! Put(sys.actorOf(Props[Echo], "echo"))
  }


}
