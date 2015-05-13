import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.contrib.pattern.ClusterSharding
import akka.actor.Props
import akka.actor.ActorLogging
import akka.actor.Actor
import akka.cluster.Cluster
import akka.contrib.pattern.ShardRegion
import akka.persistence.AtLeastOnceDelivery

object ShardedAtLeastOnceDelivery extends App {

  case class Msg(id: Long)
  case class Ack(id: Long)

  class Printer extends Actor with ActorLogging {
    def receive = {
      case Msg(id) => {
        log.info(s"Received $id")
        if (math.random < 0.25) {
          log.info(s"Ack $id")
          sender() ! Ack(id)
        }
      }
    }
  }

  class ShardForwarder extends Actor with ActorLogging with AtLeastOnceDelivery {
    val path = ClusterSharding(context.system).shardRegion("printer").path
    def receive = {
      case "tick" => deliver(path, Msg)
      case Ack(id) => confirmDelivery(id)
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
          "akka.tcp://ShardedAtLeastOnceDelivery@127.0.0.1:2552"
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

  val system = ActorSystem("ShardedAtLeastOnceDelivery", moreConfig.withFallback(clusterConfig))

  val idExtractor: ShardRegion.IdExtractor = {
    case msg ⇒ (msg.hashCode.toString, msg)
  }

  val shardResolver: ShardRegion.ShardResolver = msg ⇒ msg match {
    case msg ⇒ (msg.hashCode % 12).toString
  }

  val region = ClusterSharding(system).start(
    typeName = "printer",
    entryProps = Some(Props[Printer]),
    idExtractor = idExtractor,
    shardResolver = shardResolver
  )

  val f = system.actorOf(Props[ShardForwarder])

  import system.dispatcher
  import scala.concurrent.duration._
  system.scheduler.schedule(0.seconds, 5.second) {
    f ! "tick"
  }

}
