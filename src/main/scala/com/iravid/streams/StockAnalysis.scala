package com.iravid.streams

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, IOResult }
import akka.stream.scaladsl._
import akka.util.ByteString

import java.nio.file.{ Path, Paths }
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.{ LocalDateTime, OffsetDateTime }
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }

object StockAnalysis {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("streams")
    implicit val materializer = ActorMaterializer()
    import system.dispatcher

    val res = Await.result(Streams.graph.run(), 30.seconds)
    println(res)
    // Result should be:
    // GraphResult(Some((CMG,75.80999799999995)),Some((5-2010,9732376.684434967)))

    system.terminate()
  }
}

object Streams {
  case class Symbol(date: LocalDate, symbol: String, open: Double, close: Double, volume: Double)
  case class Average(count: Long, sum: Double) {
    def avg = sum / count
  }
  case class GraphResult(highestDiff: Option[(String, Double)], highestAvgVolume: Option[(String, Double)])

  def fileLines(path: Path): Source[ByteString, Future[IOResult]] =
    FileIO.fromPath(path)
      .via(Framing.delimiter(ByteString("\n"), 8192, true))

  def parseLine(line: ByteString): Try[Symbol] = Try {
    val fields = new String(line.toArray, "UTF-8").split(",")
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    Symbol(
      LocalDate.from(formatter.parse(fields(0).split(" ")(0))),
      fields(1),
      fields(2).toDouble,
      fields(3).toDouble,
      fields(6).toDouble
    )
  } match {
    case Success(v) => Success(v)
    case Failure(e) => e.printStackTrace(); Failure(e)
  }

  val symbols = fileLines(Paths.get("/Users/iravid/Development/personal/nyse-stocks-kaggle/prices.csv"))
    .drop(1)
    .map(parseLine)
    .collect {
      case Success(s) => s
    }

  val maxPriceDiffSink = Sink.fold(None: Option[(String, Double)]) {
    (currMax, s: Symbol) =>

    val diff = math.abs(s.close - s.open)

    currMax match {
      case None =>
        Some(s.symbol -> diff)
      case Some((currName, currDiff)) if currDiff <= diff =>
        Some(s.symbol -> diff)
      case Some((currName, currDiff)) if currDiff > diff =>
        Some((currName -> currDiff))
    }
  }

  def highestAvgVolumeSink(implicit ec: ExecutionContext) = Sink.fold(Map.empty[String, Average]) {
    (averages, symbol: Symbol) =>
    val key = s"${symbol.date.getMonthValue()}-${symbol.date.getYear()}"

    averages get key match {
      case Some(avg) => averages + (key -> Average(avg.count + 1, avg.sum + symbol.volume))
      case None => averages + (key -> Average(1, symbol.volume))
    }
  }.mapMaterializedValue(_.map { averages =>
    if (averages.nonEmpty) Some(averages.mapValues(_.avg).maxBy(_._2))
    else None
  })

  def graph(implicit ec: ExecutionContext) =
    symbols
      .alsoToMat(maxPriceDiffSink)(Keep.right)
      .toMat(highestAvgVolumeSink)(Keep.both)
      .mapMaterializedValue {
        case (maxPriceDiffF, highestAvgVolumeF) =>
          for {
            maxPriceDiff <- maxPriceDiffF
            highestAvg <- highestAvgVolumeF
          } yield GraphResult(maxPriceDiff, highestAvg)
      }
}
