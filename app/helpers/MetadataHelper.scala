package helpers

import java.nio.ByteBuffer

import akka.stream.Materializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.om.mxs.client.internal.TaggedIOException
import com.om.mxs.client.japi.MxsObject
import models.MxsMetadata
import org.apache.commons.codec.binary.Hex
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object MetadataHelper {
  private val logger = LoggerFactory.getLogger(getClass)
  /**
    * iterates the available metadata and presents it as a dictionary
    * @param obj [[MxsObject]] entity to retrieve information from
    * @param mat implicitly provided materializer for streams
    * @param ec implicitly provided execution context
    * @return a Future, with the relevant map
    */
  def getAttributeMetadata(obj:MxsObject)(implicit mat:Materializer, ec:ExecutionContext) = {
    val view = obj.getAttributeView

    val sink = Sink.fold[MxsMetadata,(String,Any)](MxsMetadata.empty)((acc,elem)=>{
      elem._2 match {
        case boolValue: Boolean => acc.copy(boolValues = acc.boolValues ++ Map(elem._1->boolValue))
        case intValue:Int => acc.copy(intValues = acc.intValues ++ Map(elem._1 -> intValue))
        case longValue:Long => acc.copy(longValues = acc.longValues ++ Map(elem._1 -> longValue))
        case floatValue:java.lang.Float =>  acc.copy(floatValues = acc.floatValues ++ Map(elem._1->Float2float(floatValue)))//acc.copy(floatValues = acc.floatValues ++ Map(elem._1 -> floatValue))
        case byteBuffer:ByteBuffer => acc.copy(stringValues = acc.stringValues ++ Map(elem._1 -> Hex.encodeHexString(byteBuffer.array())))
        case stringValue:String => acc.copy(stringValues = acc.stringValues ++ Map(elem._1 -> stringValue))
        case _=>
          logger.warn(s"Could not get metadata value for ${elem._1} on ${obj.getId}, type ${elem._2.getClass.toString} not recognised")
          acc
      }
    })
    Source.fromIterator(()=>view.iterator.asScala)
      .map(elem=>(elem.getKey, elem.getValue))
      .toMat(sink)(Keep.right)
      .run()
  }

  def getAttributeMetadataSync(obj:MxsObject) = {
    val view = obj.getAttributeView

    view.iterator.asScala.foldLeft(MxsMetadata.empty){ (acc, elem)=>{
      val v = elem.getValue.asInstanceOf[Any]
      v match {
        case boolValue: Boolean => acc.copy(boolValues = acc.boolValues ++ Map(elem.getKey->boolValue))
        case intValue:Int => acc.copy(intValues = acc.intValues ++ Map(elem.getKey -> intValue))
        case longValue:Long => acc.copy(longValues = acc.longValues ++ Map(elem.getKey -> longValue))
        case floatValue:java.lang.Float =>  acc.copy(floatValues = acc.floatValues ++ Map(elem.getKey->Float2float(floatValue)))
        case byteBuffer:ByteBuffer => acc.copy(stringValues = acc.stringValues ++ Map(elem.getKey -> Hex.encodeHexString(byteBuffer.array())))
        case stringValue:String => acc.copy(stringValues = acc.stringValues ++ Map(elem.getKey -> stringValue))
        case _=>
          logger.warn(s"Could not get metadata value for ${elem.getKey} on ${obj.getId}, type ${elem.getValue.getClass.toString} not recognised")
          acc
      }
    }}
  }

  /**
    * get the MXFS file metadata
    * @param obj [[MxsObject]] entity to retrieve information from
    * @return
    */
  def getMxfsMetadata(obj:MxsObject) = {
    val view = obj.getMXFSFileAttributeView
    view.readAttributes()
  }

  def setAttributeMetadata(obj:MxsObject, newMetadata:MxsMetadata) = {
    val view = obj.getAttributeView

    //meh, this is probably not very efficient
    newMetadata.stringValues.foreach(entry=>view.writeString(entry._1,entry._2))
    newMetadata.longValues.foreach(entry=>view.writeLong(entry._1, entry._2))
    newMetadata.intValues.foreach(entry=>view.writeInt(entry._1,entry._2))
    newMetadata.boolValues.foreach(entry=>view.writeBoolean(entry._1, entry._2))
  }

  def isNonNull(arr:Array[Byte], charAt:Int=0, maybeLength:Option[Int]=None):Boolean = {
    val length = maybeLength.getOrElse(arr.length)
    if(charAt>=length) return false

    if(arr(charAt) != 0) {
      true
    } else {
      isNonNull(arr,charAt+1,Some(length))
    }
  }

  /**
    * request MD5 checksum of the given object, as calculated by the appliance.
    * as per the MatrixStore documentation, a blank string implies that the digest is still being calculated; in this
    * case we sleep 1 second and try again.
    * for this reason we do the operation in a sub-thread
    * @param f MxsObject representing the object to checksum
    * @return a Future, which resolves to a Try containing a String of the checksum.
    */
  def getOMFileMd5(f:MxsObject, maxAttempts:Int=2):Try[String] = {

    def lookup(attempt:Int=1):Try[String] = {
      if(attempt>maxAttempts) return Failure(new RuntimeException(s"Could not get valid checksum after $attempt tries"))
      val view = f.getAttributeView
      val result = Try {
        logger.debug(s"getting result for ${f.getId}...")
        val buf = ByteBuffer.allocate(16)
        view.read("__mxs__calc_md5", buf)
        buf
      }

      result match {
        case Failure(err:TaggedIOException)=>
          if(err.getError==302){
            logger.warn(s"Got 302 (server busy) from appliance, retrying after delay")
            Thread.sleep(500)
            lookup(attempt+1)
          } else {
            Failure(err)
          }
        case Failure(err:java.io.IOException)=>
          if(err.getMessage.contains("error 302")){
            logger.warn(err.getMessage)
            logger.warn(s"Got an error containing 302 string, assuming server busy, retrying after delay")
            Thread.sleep(500)
            lookup(attempt+1)
          } else {
            Failure(err)
          }
        case Failure(otherError)=>Failure(otherError)
        case Success(buffer)=>
          val arr = buffer.array()
          if(! isNonNull(arr)) {
            logger.info(s"Empty string returned for file MD5 on attempt $attempt, assuming still calculating. Will retry...")
            Thread.sleep(1000) //this feels nasty but without resorting to actors i can't think of an elegant way
            //to delay and re-call in a non-blocking way
            lookup(attempt + 1)
          } else {
            val converted = Hex.encodeHexString(arr)
            if (converted.length == 32)
              Success(converted)
            else {
              logger.warn(s"Returned checksum $converted is wrong length (${converted.length}; should be 32).")
              Thread.sleep(1500)
              lookup(attempt + 1)
            }
          }
      }
    }

    lookup()
  }
}
