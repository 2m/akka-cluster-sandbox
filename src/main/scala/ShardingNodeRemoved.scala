import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.contrib.pattern.ClusterSharding
import akka.actor.Props
import akka.actor.ActorLogging
import akka.actor.Actor
import akka.cluster.Cluster
import akka.contrib.pattern.ShardRegion

object ShardingNodeRemoved extends App {
  
  class Printer extends Actor with ActorLogging {
    def receive = {
      case msg => log.info(msg.toString)
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
          "akka.tcp://ShardingNodeRemoved@127.0.0.1:2552"
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
  
  val system = ActorSystem("ShardingNodeRemoved", moreConfig.withFallback(clusterConfig))
  
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
  
  import system.dispatcher
  import scala.concurrent.duration._
  system.scheduler.schedule(0.seconds, 1.second) {
    region ! (math.random * 24).toInt + " message"
  }
  
}