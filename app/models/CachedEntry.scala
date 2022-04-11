package models

case class CachedEntry(oid:String, mxfsPath:String, mxfsFilename:String, maybeAssetFolder:Option[String], maybeType:Option[String], maybeProject:Option[String], checkSum:String, byteSize:Long)