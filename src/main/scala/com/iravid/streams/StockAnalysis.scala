package com.iravid.streams

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._

object StockAnalysis {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("streams")
    implicit val materializer = ActorMaterializer()
  }
}

object Streams {
  // Write your streams here

}
