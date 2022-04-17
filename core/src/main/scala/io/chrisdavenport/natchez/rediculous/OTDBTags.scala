package io.chrisdavenport.natchez.rediculous

import natchez._
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import cats.data.NonEmptyList
import scodec.bits.ByteVector
import scala.collection.mutable.ListBuffer

object OTDBTags {
  def system: (String, TraceValue) = ("db.system", "redis")
  def name(host: Host): (String, TraceValue) = "net.peer.name" -> host.toString()
  def port(port: Port): (String, TraceValue) = "net.peer.port" -> port.value
  def transport: (String, TraceValue) = "net.transport" -> "ip_tcp"

  def operation(string: String): (String, TraceValue) = "db.operation" -> string
  def statement(string: String): (String, TraceValue) = "db.statement" -> string


  private[rediculous] def tagOperation(
    host: Option[Host],
    port: Option[Port],
    renderStatement: NonEmptyList[ByteVector] => Option[String]
  )(operation: NonEmptyList[ByteVector]): List[(String, TraceValue)] = {
    val builder = new ListBuffer[(String, TraceValue)]()
    builder += system
    host.foreach(host => 
      builder += name(host)
    )
    port.foreach(port => 
      builder += OTDBTags.port(port)
    )
    builder += transport
    operation.head.decodeUtf8.foreach(op => 
      builder += OTDBTags.operation(op)
    )
    renderStatement(operation).foreach(statement => 
      builder += OTDBTags.statement(statement)
    )

    builder.toList
  }

  private[rediculous] def tagPipeline(
    host: Option[Host],
    port: Option[Port],
  ): List[(String, TraceValue)] = {
    val builder = new ListBuffer[(String, TraceValue)]()
    builder += system
    host.foreach(host => 
      builder += name(host)
    )
    port.foreach(port => 
      builder += OTDBTags.port(port)
    )
    builder += transport
    builder += OTDBTags.operation("pipeline")

    builder.toList
  }
}