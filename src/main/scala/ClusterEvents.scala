import akka.actor._
import akka.cluster._
import akka.cluster.ClusterEvent._
import com.typesafe.config.ConfigFactory

object ClusterEvents extends App {

  class EventsListener extends Actor {

    val cluster = Cluster(context.system)

    override def preStart(): Unit = {
      cluster.subscribe(self, classOf[MemberEvent], classOf[UnreachableMember])
    }

    def receive = {
      case evt => println(s"Received event $evt")
    }
  }

  val config = """
    akka {
      actor {
        provider = "akka.cluster.ClusterActorRefProvider"
      }
      remote {
        log-remote-lifecycle-events = off
        netty.tcp {
          hostname = "127.0.0.1"
          port = 0
        }
      }
      cluster {
        seed-nodes = [
          "akka.tcp://ClusterEvents@127.0.0.1:2551",
          "akka.tcp://ClusterEvents@127.0.0.1:2552"]

        auto-down-unreachable-after = 10s
      }
    }
    """

  val commonConfig = ConfigFactory.defaultOverrides.withFallback(ConfigFactory.parseString(config))
  val conf = args.headOption match {
    case Some("seed1") => ConfigFactory.parseString("akka.remote.netty.tcp.port=2551").withFallback(commonConfig)
    case Some("seed2") => ConfigFactory.parseString("akka.remote.netty.tcp.port=2552").withFallback(commonConfig)
    case _ => commonConfig
  }

  val system = ActorSystem("ClusterEvents", conf)
  val ref = system.actorOf(Props[EventsListener])
}
