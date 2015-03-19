import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem

object SimplePubSub extends App {

  val config = ConfigFactory.parseString("""
    akka {
      extensions = ["akka.contrib.pattern.DistributedPubSubExtension"]
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
        auto-down-unreachable-after = 10s
      }
    }""")

  val seed = ActorSystem("ClusterSystem", config)
  val node = ActorSystem("ClusterSystem", ConfigFactory.parseString("akka.remote.netty.tcp.port=0").withFallback(config))
  
  
}