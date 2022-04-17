package io.chrisdavenport.natchez.rediculous

import cats.syntax.all._
import cats.effect.kernel._
import cats.effect.syntax.all._
import io.chrisdavenport.rediculous._
import io.chrisdavenport.rediculous.RedisConnection._
import natchez._
import com.comcast.ip4s._
import cats.data.NonEmptyList
import fs2.Chunk
import scodec.bits.ByteVector

object RedisConnectionMiddleware {

  def direct[F[_]: MonadCancelThrow](
    d: DirectConnectionBuilder[F],
    renderStatement: NonEmptyList[ByteVector] => Option[String] = {(_: NonEmptyList[ByteVector]) => None},
  ): Resource[F, Trace[F] => RedisConnection[F]] = {
    d.build.map(init => 
      {implicit T: Trace[F] => traced(d.host.some, d.port.some, renderStatement, init)}
    )
  }

  def queued[F[_]: MonadCancelThrow](
    d: QueuedConnectionBuilder[F],
    renderStatement: NonEmptyList[ByteVector] => Option[String] = {(_: NonEmptyList[ByteVector]) => None},
  ): Resource[F, Trace[F] => RedisConnection[F]] = {
    d.build.map(init => 
      {implicit T: Trace[F] => traced(d.host.some, d.port.some, renderStatement, init)}
    )
  }

  def pooled[F[_]: MonadCancelThrow](
    d: PooledConnectionBuilder[F],
    renderStatement: NonEmptyList[ByteVector] => Option[String] = {(_: NonEmptyList[ByteVector]) => None},
  ): Resource[F, Trace[F] => RedisConnection[F]] = {
    d.build.map(init => 
      {implicit T: Trace[F] => traced(d.host.some, d.port.some, renderStatement, init)}
    )
  }
  
  def cluster[F[_]: MonadCancelThrow](
    d: ClusterConnectionBuilder[F],
    renderStatement: NonEmptyList[ByteVector] => Option[String] = {(_: NonEmptyList[ByteVector]) => None},
  ): Resource[F, Trace[F] => RedisConnection[F]] = {
    d.build.map(init => 
      {implicit T: Trace[F] => traced(None, None, renderStatement, init)}
    )
  }


  def traced[F[_]: MonadCancelThrow: Trace](
    host: Option[Host],
    port: Option[Port],
    renderStatement: NonEmptyList[ByteVector] => Option[String],
    connection: RedisConnection[F]
  ): RedisConnection[F] = new TracedConnection[F](host, port, renderStatement, connection)

  private class TracedConnection[F[_]: Trace: MonadCancelThrow](
    host: Option[Host],
    port: Option[Port], 
    renderStatement: NonEmptyList[ByteVector] => Option[String],
    connection: RedisConnection[F]
  ) extends RedisConnection[F]{
    def runRequest(inputs: Chunk[NonEmptyList[ByteVector]], key: Option[ByteVector]): F[Chunk[Resp]] = MonadCancelThrow[F].uncancelable{ poll =>
      if (inputs.size === 1){
        val command = inputs.head.get // The check before ensures this is safe
        val first = command.head.decodeUtf8.toOption
        val second = command.tail.headOption.flatMap(_.decodeUtf8.toOption)
        val dualCommand = (first, second) match {
          case (None, _) => "Redis Command"
          case (Some(command), None) => command
          case (Some(command), Some(second)) => command ++ " " ++ second
        }
        Trace[F].span(dualCommand)(
          poll(
            Trace[F].put(OTDBTags.tagOperation(host, port, renderStatement)(command):_*) >> 
            connection.runRequest(inputs, key)
          ).guaranteeCase(outcome => Trace[F].put(OTDBTags.Errors.outcome(outcome):_*))
        )
      } else {
        Trace[F].span("Redis Compound Operation")(
          poll(
            Trace[F].put(OTDBTags.tagPipeline(host, port):_*) >>
            connection.runRequest(inputs, key)
          ).guaranteeCase(outcome => Trace[F].put(OTDBTags.Errors.outcome(outcome):_*))
        )
      }
    }
  }

  /* Logs Full Statement if all segments are UTF-8 compliant
   *
   */
  def logFullStatement(nel: NonEmptyList[ByteVector]): Option[String] = {
    nel.traverse(bv => bv.decodeUtf8.toOption).map(nel => 
      nel.toList.mkString(" ")
    )
  }

}