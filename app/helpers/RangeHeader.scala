package helpers

import scala.util.{Failure, Success, Try}

object RangeHeader extends ((Long,Long)=>RangeHeader) {
  private val xtractor = "^(\\d+)-(\\d+)".r

  def fromStringHeader(str:String):Try[RangeHeader] = {
    str match {
      case xtractor(startStr:String,endStr:String)=>
        val hdr = new RangeHeader(startStr.toLong, endStr.toLong)
        if(hdr.start>hdr.end){
          Failure(new BadDataError("range start must be earlier than range end"))
        } else {
          Success(hdr)
        }
      case _=>Failure(new RuntimeException(s"Could not get start and end parameters from $str"))
    }
  }
}

case class RangeHeader (start:Long, end:Long)
