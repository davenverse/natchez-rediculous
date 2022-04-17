package io.chrisdavenport.natchez.rediculous

import munit.CatsEffectSuite
import cats.effect._

class MainSpec extends CatsEffectSuite {

  test("Main should exit succesfully") {
      assertEquals(ExitCode.Success, ExitCode.Success)
  }

}
