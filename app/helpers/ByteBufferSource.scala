package helpers

import java.nio.ByteBuffer

import akka.stream.scaladsl.GraphDSL
import akka.stream.{Attributes, ClosedShape, Outlet, SourceShape}
import akka.stream.stage.{AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.util.ByteString
import org.slf4j.LoggerFactory

object ByteBufferSource {
  import akka.stream.scaladsl.GraphDSL._

  def apply(buffer: ByteBuffer, readSize: Int) = GraphDSL.create() { implicit builder=>
    val src = builder.add(new ByteBufferSource(buffer, readSize))
    SourceShape(src.out)
  }
}

/**
  * implements a source that emits elements from a ByteBuffer as a ByteString.
  * the builtin functions appear not to do this in a simple way
  */
class ByteBufferSource (buffer:ByteBuffer, readSize:Int) extends GraphStage[SourceShape[ByteString]] {
  private final val out:Outlet[ByteString] = Outlet.create("ByteBufferSource.out")

  override def shape: SourceShape[ByteString] = SourceShape.of(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)
    private var ctr:Int = 0

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = {
        logger.debug(s"Buffer capacity is ${buffer.capacity()}, current position is $ctr")

        val bytes:Array[Byte] = if(ctr+readSize<buffer.capacity()) {
          logger.debug(s"Getting string of length $readSize")
          val xtracted = new Array[Byte](readSize)
          buffer.get(xtracted, ctr, readSize)
          ctr+=readSize
          xtracted
        } else {
          logger.debug(s"Last chunk - getting string of ${buffer.capacity() - ctr}")
          val xtracted = new Array[Byte](buffer.capacity()-ctr)
          buffer.get(xtracted,ctr,buffer.capacity()-ctr)
          ctr+=buffer.capacity()-ctr
          xtracted
        }

        push(out, ByteString(bytes))
        if(ctr>=buffer.capacity()){
          logger.debug(s"ctr=$ctr, capacity=${buffer.capacity()}, completing stream")
          complete(out)
        }
      }
    })
  }
}
