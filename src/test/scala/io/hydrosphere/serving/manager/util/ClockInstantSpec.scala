package io.hydrosphere.serving.manager.util


import java.time.Instant
import java.util.concurrent.TimeUnit

import cats.Id
import cats.effect.{Clock, IO}
import io.hydrosphere.serving.manager.GenericUnitTest

import scala.concurrent.duration.TimeUnit

class ClockInstantSpec extends GenericUnitTest {
  describe("InstantClock") {
    it("should return correct Instant for now") {
      import InstantClockSyntax._
      val orig = Instant.now()
      val nowLong = System.currentTimeMillis()
      val mockedClock  = new Clock[Id] {
        override def realTime(unit: TimeUnit): Id[Long] = ???

        override def monotonic(unit: TimeUnit): Id[Long] = {
          if (unit == TimeUnit.MILLISECONDS) {
            nowLong
          } else {fail(s"Unexpected TimeUnit ${unit}")}
        }
      }
      val inst = mockedClock.instant()
      println(inst)
      assert(inst.toEpochMilli == nowLong)
      val clock = Clock.create[IO]
      val millis = clock.realTime(TimeUnit.MILLISECONDS).unsafeRunSync()
      val clocked = Instant.ofEpochMilli(millis)
      println(orig)
      println(clocked)
      println(millis)
      assert(orig.isBefore(clocked))
    }
  }
}
