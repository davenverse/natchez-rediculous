package example

import cats.effect._
import org.http4s.ember.server.EmberServerBuilder
import io.chrisdavenport.natchez.rediculous.RedisConnectionMiddleware
import org.http4s.server.Server
import org.http4s.implicits._
import io.chrisdavenport.natchezhttp4sotel._
import io.chrisdavenport.fiberlocal.GenFiberLocal
import com.comcast.ip4s._
import io.chrisdavenport.rediculous.RedisConnection
import fs2.io.net.Network

/**
 * Start up Jaeger thus:
 *
 *  docker run -d --name jaeger \
 *    -e COLLECTOR_ZIPKIN_HTTP_PORT=9411 \
 *    -p 5775:5775/udp \
 *    -p 6831:6831/udp \
 *    -p 6832:6832/udp \
 *    -p 5778:5778 \
 *    -p 16686:16686 \
 *    -p 14268:14268 \
 *    -p 9411:9411 \
 *    jaegertracing/all-in-one:1.8
 *
 * Run this example and do some requests. Go to http://localhost:16686 and select `Http4sExample`
 * and search for traces.
*/
object Http4sRediculousExample extends IOApp with Common {

  // Our main app resource
  def server[F[_]: Async: GenFiberLocal: Network]: Resource[F, Server] =
    for {
      ep <- entryPoint[F]
      connectionF <- RedisConnectionMiddleware.pooled(RedisConnection.pool, RedisConnectionMiddleware.logFullStatement)
      app = ServerMiddleware.default(ep).buildHttpApp{implicit T: natchez.Trace[F] => 
        val connection = connectionF(T)
        routes(connection).orNotFound
      }
      sv <- EmberServerBuilder.default[F].withPort(port"8080").withHttpApp(app).build
    } yield sv

  // Done!
  def run(args: List[String]): IO[ExitCode] =
    server[IO].use(_ => IO.never)

}