import akka.actor._
import akka.cluster._
import akka.cluster.sharding._
import akka.persistence.AtLeastOnceDelivery
import com.typesafe.config.ConfigFactory

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
    override def receive = {
      case "tick" => deliver(path)(Msg)
      case Ack(id) => confirmDelivery(id)
    }

    // Members declared in akka.persistence.Eventsourced
    def receiveCommand: PartialFunction[Any,Unit] = ???
    def receiveRecover: PartialFunction[Any,Unit] = ???

    // Members declared in akka.persistence.PersistenceIdentity
    def persistenceId: String = ???
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

  val idExtractor: ShardRegion.ExtractEntityId = {
    case msg ⇒ (msg.hashCode.toString, msg)
  }

  val shardResolver: ShardRegion.ExtractShardId = msg ⇒ msg match {
    case msg ⇒ (msg.hashCode % 12).toString
  }

  val region = ClusterSharding(system).start(
    typeName = "printer",
    entityProps = Props[Printer],
    settings = ClusterShardingSettings(system),
    extractEntityId = idExtractor,
    extractShardId = shardResolver
  )

  val f = system.actorOf(Props[ShardForwarder])

  import system.dispatcher
  import scala.concurrent.duration._
  system.scheduler.schedule(0.seconds, 5.second) {
    f ! "tick"
  }

}
