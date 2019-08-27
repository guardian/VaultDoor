package streamcomponents

import java.awt.event.KeyEvent

import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink, Source}
import akka.util.ByteString
import helpers.RangeHeader
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import testhelpers.AkkaTestkitSpecs2Support
import scala.concurrent.duration._
import scala.concurrent.Await

class MultipartSourceSpec extends Specification with Mockito {
  def isPrintableChar(c: Char): Boolean = {
    val block = Character.UnicodeBlock.of(c)
    (!Character.isISOControl(c)) && c != KeyEvent.CHAR_UNDEFINED && block != null && (block ne Character.UnicodeBlock.SPECIALS)
  }

  "MultipartSource.genSeparatorText" should {
    "generate a 10-charactor randomised string" in {
      val str = MultipartSource.genSeparatorText

      println(str)
      str.length mustEqual 10

      val nonPrintables = str.toCharArray.filter(isPrintableChar)
      nonPrintables.length mustEqual str.length
    }
  }

  "MultipartSource.makeSectionHeader" should {
    "generate a MIME part header with the specified parameters" in {
      val result = MultipartSource.makeSectionHeader(RangeHeader(Some(123L),Some(456L)),123456L, "application/xml", "aabbccddee")

      result.utf8String mustEqual
        """
          |--aabbccddee
          |Content-Type: application/xml
          |Content-Range: bytes 123-456/123456
          |
          |""".stripMargin.replace("\n","\r\n")
    }
  }

  "MultipartSource.getSource" should {
    "return a single source that yields the contents of the provided sources in multipart format" in new AkkaTestkitSpecs2Support {
      implicit val mat = ActorMaterializer.create(system)
      val rangeAndSource = Seq(
        (RangeHeader(None,Some(123)),Source.single(ByteString("First part"))),
        (RangeHeader(Some(140),Some(180)),Source.single(ByteString("Second part")))
      )

      val sinkFactory = Sink.fold[ByteString,ByteString](ByteString())((acc, elem)=>acc ++ elem)
      val graph = GraphDSL.create(sinkFactory) { implicit builder=> sink=>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(MultipartSource.getSource(rangeAndSource,12345L,"text/plain", "aabbccddee"))
        src ~> sink
        ClosedShape
      }

      val result = Await.result(RunnableGraph.fromGraph(graph).run(), 30 seconds)

      result.utf8String.replace("\r\n","\n") mustEqual """
                                    |--aabbccddee
                                    |Content-Type: text/plain
                                    |Content-Range: bytes 0-123/12345
                                    |
                                    |First part
                                    |--aabbccddee
                                    |Content-Type: text/plain
                                    |Content-Range: bytes 140-180/12345
                                    |
                                    |Second part
                                    |--aabbccddee--""".stripMargin
    }
  }
}
