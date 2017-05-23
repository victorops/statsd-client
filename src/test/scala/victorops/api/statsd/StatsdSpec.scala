package victorops.api.statsd

import java.net.{SocketTimeoutException, DatagramPacket, DatagramSocket}
import org.specs2.mutable.{Specification, BeforeAfter}
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
case class StatsdSpec() extends Specification {
  sequential
  "Statsd" should {
    "send gauge value" in new Setup {
      Statsd.gauge("test", 42)
      receive() mustEqual "statsd.test:42|g"
    }
    "send gauge increment value" in new Setup {
      Statsd.gaugeRelative("test", 42)
      receive() mustEqual "statsd.test:+42|g"
    }
    "send gauge decrement value" in new Setup {
      Statsd.gaugeRelative("test", -42)
      receive() mustEqual "statsd.test:-42|g"
    }
    "send increment by one message" in new Setup {
      Statsd.increment("test")
      receive() mustEqual "statsd.test:1|c"
    }
    "send increment by more message" in new Setup {
      Statsd.increment("test", 10)
      receive() mustEqual "statsd.test:10|c"
    }
    // There is a 0.0001% chance that the following two tests might fail
    "hopefully send a message when sampling rate is only just below 1" in new Setup {
      Statsd.increment("test", 10, 0.999999)
      receive() mustEqual "statsd.test:10|c|@0.999999"
    }
    "hopefully not send a message when sampling rate is only just above 0" in new Setup {
      Statsd.increment("test", 10, 0.000001)
      verifyNothingReceived()
    }
    "send timing message" in new Setup {
      Statsd.timing("test", 1234)
      receive() mustEqual "statsd.test:1234|ms"
    }
    "execute timed function and report" in new Setup {
      Statsd.time("test", 1.0) { Thread.sleep(10) }
      val msg = receive()
      msg must ((_:String).startsWith("statsd.test:"), "incorrect prefix")
      msg must ((_:String).endsWith("|ms"), "incorrect postfix")
      val time = msg.stripPrefix("statsd.test:").stripSuffix("|ms").toInt
      time must be_>=(10)
    }
    "return return value of timed function" in new Setup {
      Statsd.time("test", 1.0) { "blah" } mustEqual "blah"
    }
  }

  trait Setup extends BeforeAfter {
    val PORT = 57475;

    System.setProperty("statsd.enabled", "true")
    System.setProperty("statsd.host", "localhost")
    System.setProperty("statsd.port", PORT.toString)

   lazy val mockStatsd = {
      val socket = new DatagramSocket(PORT)
      socket.setSoTimeout(200)
      socket
    }

    def receive() = {
      val buf: Array[Byte] = new Array[Byte](1024)
      val packet = new DatagramPacket(buf, buf.length)
      try {
        mockStatsd.receive(packet)
      }
      catch {
        case s: SocketTimeoutException => failure("Didn't receive message within 200ms")
      }
      new String(packet.getData, 0, packet.getLength)
    }

    def verifyNothingReceived() {
      val buf: Array[Byte] = new Array[Byte](1024)
      val packet = new DatagramPacket(buf, buf.length)
      try {
        mockStatsd.receive(packet)
        failure("Unexpected packet received: " + new String(packet.getData, 0, packet.getLength))
      }
      catch {
        case s: SocketTimeoutException => Unit
      }
    }

    def before {
      mockStatsd
    }

    def after {
      mockStatsd.close()
    }
  }
}
