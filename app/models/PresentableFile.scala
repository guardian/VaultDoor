package models

case class PresentableFile(oid:String, attributes: FileAttributes, customMeta:Option[String])

object PresentableFile extends ((String, FileAttributes,Option[String])=>PresentableFile) {
  def fromObjectMatrixEntry(src:ObjectMatrixEntry):Option[PresentableFile] =
    src.fileAttribues.map(attribs=>PresentableFile(src.oid, attribs, src.attributes.map(_.dumpString(None))))

}