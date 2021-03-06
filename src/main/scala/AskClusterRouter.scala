import akka.actor._
import akka.cluster._
import akka.cluster.routing._
import akka.cluster.sharding._
import akka.pattern.ask
import akka.routing._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

object AskClusterRouter extends App {

  class Pinger extends Actor with ActorLogging {
    def receive = {
      case msg =>
        log.info("Pinging back")
        sender ! msg
    }
  }

  val remoteConfig = ConfigFactory.parseString("""
    akka {
      actor {
        provider = "akka.remote.RemoteActorRefProvider"
      }
      remote {
        enabled-transports = ["akka.remote.netty.tcp"]
        netty.tcp {
          hostname = "127.0.0.1"
          port = 0
        }
      }
    }
  """)

  val clusterConfig = ConfigFactory.parseString("""
    akka {
      actor {
        provider = "akka.cluster.ClusterActorRefProvider"
      }

      cluster {
        seed-nodes = [
          "akka.tcp://AskClusterRouter@127.0.0.1:2552"
        ]
        auto-down-unreachable-after = 10s
      }
    }

    akka.persistence.journal.leveldb.native = off
  """).withFallback(remoteConfig)

  val moreConfig = args.headOption match {
    case Some("seed") => ConfigFactory.parseString("akka.remote.netty.tcp.port = 2552")
    case _ => ConfigFactory.empty()
  }

  val system = ActorSystem("AskClusterRouter", moreConfig.withFallback(clusterConfig))

  args.headOption match {
    case Some("seed") =>

      val worker = system.actorOf(
        ClusterRouterPool(RoundRobinPool(0), ClusterRouterPoolSettings(
          totalInstances = 100, maxInstancesPerNode = 3,
          allowLocalRoutees = true, useRole = None)).props(Props[Pinger]),
        name = "worker")

      import system.dispatcher
      import scala.concurrent.duration._
      implicit val timeout = Timeout(1.second)
      system.scheduler.schedule(0.seconds, 1.second) {
        val fut = worker ? "in anybody out there"
        fut.foreach(println)
      }
    case _ =>
  }

}
