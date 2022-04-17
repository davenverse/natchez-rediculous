package example

import cats._
import cats.effect.{ Trace => _, _ }
import cats.syntax.all._
import io.jaegertracing.Configuration.ReporterConfiguration
import io.jaegertracing.Configuration.SamplerConfiguration
import natchez._
import natchez.jaeger.Jaeger
import org.http4s.dsl.Http4sDsl
import org.http4s.HttpRoutes
import io.chrisdavenport.rediculous._

trait Common {

  // A dumb subroutine that does some tracing
  def greet[F[_]: Monad: Trace](input: String) =
    Trace[F].span("greet") {
      for {
        _ <- Trace[F].put("input" -> input)
      } yield s"Hello $input!\n"
    }

  // Our routes, in abstract F with a Trace constraint.
  def routes[F[_]: Trace](connection: RedisConnection[F])(
    implicit ev: GenConcurrent[F, Throwable]
  ): HttpRoutes[F] = {
    object dsl extends Http4sDsl[F]; import dsl._ // bleh
    HttpRoutes.of[F] {

      case GET -> Root / "hello" / name =>
        for {
          str <- greet[F](name)
          res <- Ok(str)
        } yield res
      case GET -> Root / "redis" / "hello" / name => 
        RedisCommands.get[Redis[F, *]](name).run(connection).flatMap{
          case None => greet(name).flatMap(Ok(_))
          case Some(value) => Ok(value)
        }
      case GET -> Root / "redis" / "set-hello" / name / value => 
        RedisCommands.set(name, value, RedisCommands.SetOpts.default.copy(setSeconds = Some(600))).run(connection)
          .flatMap{
            case None => InternalServerError()
            case Some(status) => Ok(status.toString())
          }
      case GET -> Root / "fail" =>
        ev.raiseError(new RuntimeException("💥 Boom!"))

    }
  }

  // A Jaeger entry point
  def entryPoint[F[_]: Sync]: Resource[F, EntryPoint[F]] =
    Jaeger.entryPoint[F](
      system    = "Http4sExample",
      uriPrefix = Some(new java.net.URI("http://localhost:16686")),
    ) { c =>
      Sync[F].delay {
        c.withSampler(SamplerConfiguration.fromEnv)
         .withReporter(ReporterConfiguration.fromEnv)
         .getTracer
      }
    }
}