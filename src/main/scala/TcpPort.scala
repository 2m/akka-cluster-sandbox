import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object TcpPort extends App {

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
    }
    """
  
  val conf = ConfigFactory.defaultOverrides.withFallback(ConfigFactory.parseString(config))  
  val system = ActorSystem("TcpPort", conf)
  
}