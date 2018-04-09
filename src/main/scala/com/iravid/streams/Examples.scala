package com.iravid.streams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

object First extends App {
  implicit val system = ActorSystem("examples")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val stream = Source(1 to 100)
    .filter(_ % 2 == 0)
    .map(_ * 2)
    .fold(0)(_ + _)
    .to(Sink.foreach(println(_)))

  stream.run()
}

object Composition {
  // Sources
  val source = Source(1 to 100)

  def sourceFrom(from: Int) = Source(from to 1000)

  val evenNumbers = source.filter(_ % 2 == 0)

  // Flows
  val multiplier = Flow[Int].map(_ * 2)

  val multiplyAndDuplicate = multiplier
    .mapConcat(i => List(i, i))

  // Sinks
  val firstElement = Sink.headOption[Int]

  val multipliedFirstElem = multiplier.to(firstElement)
}

object MaterializedValues extends App {
  implicit val system = ActorSystem("examples")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  import akka.actor.Cancellable
  val ticker: Source[String, Cancellable] = Source.tick(1.second, 1.second, "Tick")

  val cancellable: Cancellable = ticker.to(Sink.foreach(println)).run()

  Thread.sleep(5000)

  cancellable.cancel()
  system.terminate()
}

object CombiningMaterializedValues extends App {
  implicit val system = ActorSystem("examples")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  import akka.actor.Cancellable
  val ticker: Source[String, Cancellable] = Source.tick(1.second, 1. second, "Tick")

  import akka.NotUsed
  val counter: Flow[String, Long, NotUsed] = Flow[String].zipWithIndex.map(_._2)

  val lastElement: Sink[Long, Future[Option[Long]]] = Sink.lastOption[Long]

  ////////////////

  val graph: RunnableGraph[(Cancellable, Future[Option[Long]])] =
    ticker.via(counter).toMat(lastElement)(Keep.both)

  ////////////////

  case class GraphControl(cancel: Cancellable, result: Future[Option[Long]])

  val mappedGraph: RunnableGraph[GraphControl] = graph.mapMaterializedValue {
    case (cancel, result) => GraphControl(cancel, result)
  }
}
