import akka.actor._
import akka.cluster._
import akka.cluster.routing._
import akka.cluster.sharding._
import akka.pattern.ask
import akka.routing._
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn

object ClusterRouterGroupWorkerRestart extends App {

  case class BigMsg(array: Array[Double])

  class Pinger extends Actor with ActorLogging {
    def receive = {
      case msg =>
        log.info("Pinging back")
        sender ! msg
    }
  }

  val remoteConfig = ConfigFactory.parseString("""
    akka {
      log-dead-letters = on

      actor {
        provider = "akka.remote.RemoteActorRefProvider"
      }
      remote {
        log-frame-size-exceeding = 1000b
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
          "akka.tcp://ClusterRouterGroupWorkerRestart@127.0.0.1:2552"
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

  val system = ActorSystem("ClusterRouterGroupWorkerRestart", moreConfig.withFallback(clusterConfig))

  args.headOption match {
    case Some("seed") =>

      val worker = system.actorOf(
        ClusterRouterGroup(
          RoundRobinGroup(Nil),
          ClusterRouterGroupSettings(totalInstances = 10, routeesPaths = List("/user/worker"), allowLocalRoutees = false, useRole = None)).props(),
        name = "worker")


      println("Waiting for workers to start. Press ENTER when started.")
      StdIn.readLine()

      import system.dispatcher
      implicit val timeout = Timeout(1.second)
      system.scheduler.schedule(0.seconds, 1.second) {
        val fut = worker ? BigMsg(Array.fill(2000)(scala.math.random))
        fut.foreach(println)
      }

      StdIn.readLine()
      Await.ready(system.terminate(), 10.seconds)
    case _ =>
      system.actorOf(Props[Pinger], "worker")

      StdIn.readLine()
      Await.ready(system.terminate(), 10.seconds)
  }

}
