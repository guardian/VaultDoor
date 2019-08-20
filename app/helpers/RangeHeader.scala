package helpers

import scala.util.{Failure, Success, Try}
//see https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Range

object RangeHeader extends ((Option[Long],Option[Long])=>RangeHeader) {
  private val fullRangeXtractor = "^(\\d+)-(\\d+)".r
  private val partialStartRangeXtractor = "(\\d+)-$".r
  private val partialEndRangeXtractor = "^-(\\d+)$".r

  private val unitsXtractor = "^(\\w+)=([\\d,\\-]+)$".r
  private val groupSeparator = "\\s*,\\s*".r

  protected def extractRange(rangePart:String):Try[RangeHeader] = rangePart match {
    case fullRangeXtractor(startStr:String, endStr:String)=>
      val hdr = new RangeHeader(Some(startStr.toLong), Some(endStr.toLong))
      if(hdr.start.get>hdr.end.get){
        Failure(new BadDataError("range start must be earlier than range end"))
      } else {
        Success(hdr)
      }
    case partialStartRangeXtractor(startStr:String)=>
      val startNum = startStr.toLong
      val hdr = if(startNum>0) {
        new RangeHeader(Some(startNum), None)
      } else {
        new RangeHeader(None,Some(-startNum))
      }
      Success(hdr)
    case partialEndRangeXtractor(endStr:String)=>
      val endNum = endStr.toLong
      if(endNum>0){
        Success(new RangeHeader(None, Some(endNum)))
      } else {
        Failure(new BadDataError("range end must be a positive integer"))
      }
    case _=>
      Failure(new BadDataError(s"incorrect range format '$rangePart'"))
  }

  /**
    * parses a header string into a sequence of RangeHeader values
    * @param str
    * @return
    */
  def fromStringHeader(str:String):Try[Seq[RangeHeader]] = {
    str match {
      case unitsXtractor(units:String,remainder:String)=>
        if(units!="bytes"){
          Failure(new BadDataError("only ranges in bytes are supported"))
        } else {
          val remainingParts = groupSeparator.split(remainder)
          val ranges = remainingParts.map(extractRange)

          val failures = ranges.collect({case Failure(err)=>err})
          if(failures.nonEmpty){
            Failure(failures.head)
          } else {
            Success(ranges.collect({case Success(range)=>range}))
          }
        }
      case _=>Failure(new RuntimeException(s"Could not get start and end parameters from $str"))
    }
  }
}

case class RangeHeader (start:Option[Long], end:Option[Long]) {
  /**
    * convenience method to always return values - if there is no set start point then the start point is 0,
    * if there is no set end point then the end point is the provided file length
    * @param fileLength file length as a Long integer
    * @return a tuple of (start,end) both as Long integers
    */
  def getAbsolute(fileLength:Long):(Long,Long) = (start.getOrElse(0L), end.getOrElse(fileLength))
}
