package io.chrisdavenport.natchez.rediculous

import cats.syntax.all._
import natchez._
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import cats.data.NonEmptyList
import scodec.bits.ByteVector
import scala.collection.mutable.ListBuffer
import cats.effect.kernel.Outcome
import cats.effect.kernel.Outcome.Canceled
import cats.effect.kernel.Outcome.Errored
import cats.effect.kernel.Outcome.Succeeded

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

  // https://github.com/open-telemetry/opentelemetry-specification/blob/a50def370ef444029a12ea637769229768daeaf8/specification/trace/semantic_conventions/exceptions.md
  object Errors {
    def error(e: Throwable): List[(String, TraceValue)] = {
      val error = ("error", TraceValue.boolToTraceValue(true)).some
      val message: Option[(String, TraceValue)] = Option(e.getMessage()).map(m => "exception.message" -> m)
      val className: Option[(String, TraceValue)] = Option(e.getClass()).flatMap(c => Option(c.getName())).map(c => "exception.type" -> c)
      val stacktrace = ("exception.stacktrace" -> TraceValue.stringToTraceValue(ErrorHelpers.printStackTrace(e))).some
      List(error, message, className, stacktrace).flatten // List[Option[A]] => List[A] using internal speedery
    }

    def outcome[F[_], A](outcome: Outcome[F, Throwable, A]): List[(String, TraceValue)] = outcome match {
      case Canceled() => 
        List("exit.case" -> "canceled")
      case Errored(e) => "exit.case" -> TraceValue.stringToTraceValue("errored") :: error(e)
      case Succeeded(_) => List("exit.case" -> "succeeded")
    }
  }

  private object ErrorHelpers{
    import java.io.{OutputStream, FilterOutputStream, ByteArrayOutputStream, PrintStream}

    def printStackTrace(e: Throwable): String = {
      val baos = new ByteArrayOutputStream
      val fs   = new AnsiFilterStream(baos)
      val ps   = new PrintStream(fs, true, "UTF-8")
      e.printStackTrace(ps)
      ps.close
      fs.close
      baos.close
      new String(baos.toByteArray, "UTF-8")
    }

    /** Filter ANSI codes out of an OutputStream. */
    private class AnsiFilterStream(os: OutputStream) extends FilterOutputStream(os) {
      case class State(apply: Int => State)

      val S: State = State {
        case 27 => I0
        case _  => F
      }

      val F: State = State(_ => F)

      val T: State = State(_ => T)

      val I0: State = State {
        case '[' => I1
        case _   => F
      }

      val I1: State = State {
        case '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9'
                => I2
        case _   => F
      }

      val I2: State = State {
        case '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9'
                => I2
        case ';' => I1
        case '@' | 'A' | 'B' | 'C' | 'D' | 'E' | 'F' | 'G' | 'H' | 'I' | 'J' | 'K' | 'L' | 'M' | 'N' |
            'O' | 'P' | 'Q' | 'R' | 'S' | 'T' | 'U' | 'V' | 'W' | 'X' | 'Y' | 'Z' | '[' | '\\'| ']' |
            '^' | '_' | '`' | 'a' | 'b' | 'c' | 'd' | 'e' | 'f' | 'g' | 'h' | 'i' | 'j' | 'k' | 'l' |
            'm' | 'n' | 'o' | 'p' | 'q' | 'r' | 's' | 't' | 'u' | 'v' | 'w' | 'x' | 'y' | 'z' | '{' |
            '|' | '}' | '~'
                => T // end of ANSI escape
        case _   => F
      }

      // Strategy is, accumulate values as long as we're in a non-terminal state, then either discard
      // them if we reach T (which means we accumulated an ANSI escape sequence) or print them out if
      // we reach F.

      private var stack: List[Int] = Nil
      private var state: State     = S // Start

      override def write(n: Int): Unit =
        state.apply(n) match {

          case F =>
            stack.foldRight(())((c, _) => super.write(c))
            super.write(n)
            stack = Nil
            state = S

          case T =>
            stack = Nil
            state = S

          case s =>
            stack = n :: stack
            state = s

        }

    }
  }
}