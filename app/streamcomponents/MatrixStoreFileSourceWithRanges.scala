package streamcomponents

import java.io.InputStream
import java.nio.ByteBuffer

import akka.stream.stage.{AbstractOutHandler, GraphStage, GraphStageLogic}
import akka.stream.{Attributes, Outlet, SourceShape}
import akka.util.ByteString
import com.om.mxs.client.japi.{AccessOption, MatrixStore, MxsObject, SeekableByteChannel, UserInfo, Vault}
import helpers.RangeHeader
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._


/**
  * a Source that streams out the specific ranges of the given file
  * @param userInfo UserInfo object giving the location and login information for the vault
  * @param sourceId source object ID
  * @param sourceFileSize file size of the source
  * @param ranges ranges to stream as a Sequence of RangeHeader
  * @param bufferSize size to load through in a single ByteString
  */
class MatrixStoreFileSourceWithRanges(userInfo:UserInfo, sourceId:String, sourceFileSize:Long, ranges:Seq[RangeHeader], bufferSize:Int=2*1024*1024) extends GraphStage[SourceShape[ByteString]]{
  private final val out:Outlet[ByteString] = Outlet.create("MatrixStoreFileSourceWithRanges.out")
  private val outerLogger = LoggerFactory.getLogger(getClass)
  override def shape: SourceShape[ByteString] = SourceShape.of(out)

  if(sourceFileSize==0){
    throw new RuntimeException("source file size can't be zero!")
  }
  /**
    * gets the byte range to pull this time, or None if we have completed everything
    * @return an Option containing a tuple of (start,end)
    */
  def getNextDownloadRange(prevBytesPtr:Long): Option[(Long,Long)] = {
    //find the chunk that contains the prevBytesPtr location
    def checkChunk(toCheck:RangeHeader, remainder:Seq[RangeHeader]):Option[(Long,Long)] = {
      outerLogger.debug(s"checkChunk: $toCheck")
      val rangeToCheck = toCheck.getAbsolute(sourceFileSize)
      outerLogger.debug(s"rangeToCheck: $rangeToCheck, prevBytesPtr: $prevBytesPtr")
      if(prevBytesPtr+1 < rangeToCheck._2){
        Some((rangeToCheck._1, rangeToCheck._2))
      } else {
        if(remainder.isEmpty){
          None
        } else {
          checkChunk(remainder.head, remainder.tail)
        }
      }
    }

    if(ranges.isEmpty) {
      if(prevBytesPtr>=sourceFileSize){
        None
      } else if(prevBytesPtr+bufferSize<sourceFileSize){
        Some((prevBytesPtr, prevBytesPtr+bufferSize))
      } else {
        Some((prevBytesPtr, sourceFileSize))
      }
    } else {
      checkChunk(ranges.head, ranges.tail)
    }
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger = LoggerFactory.getLogger(getClass)
    private val sortedRanges = ranges.sortBy(_.start)

    private var channel:SeekableByteChannel = _
    private var mxsFile:MxsObject = _
    private var vault:Vault = _
    private var bytesPtr:Long = _


    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = {
        logger.debug("source is pulled")
        getNextDownloadRange(bytesPtr) match {
          case Some((start,end))=>
            logger.debug(s"Next chunk is from byte $start to $end")
            val bufferSize:Int = (end-start).toInt
            val buffer = ByteBuffer.allocate(bufferSize)  //should check if allocateDirect helps here

            bytesPtr=start
            channel.position(start)

            logger.debug(s"channel position is ${channel.position()}")
            val bytesRead = channel.read(buffer)
            logger.debug(s"Read $bytesRead bytes")
            if(bytesRead!=bufferSize){
              logger.error(s"Expected $bufferSize bytes but got $bytesRead")
            }
            bytesPtr += bytesRead
            buffer.flip()
            logger.debug(s"pushing to stream ${buffer.capacity()}, ${buffer}")
            push(out, ByteString(buffer))
          case None=>
            logger.info("Last range is uploaded")
            complete(out)
        }
      }
    })

    override def preStart(): Unit = {
      vault = MatrixStore.openVault(userInfo)
      mxsFile = vault.getObject(sourceId)

      channel = mxsFile.newSeekableObjectChannel(Set(AccessOption.READ).asJava)

      bytesPtr = 0
      logger.debug(s"Channel is $channel")
    }

    override def postStop(): Unit = {
      logger.debug("postStop")
      //if(stream!=null) stream.close()
      if(channel!=null) channel.close()
      vault.dispose()
    }
  }
}
