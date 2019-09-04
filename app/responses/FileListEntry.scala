package responses

import java.time.{Instant, ZoneId, ZonedDateTime}

import models.ObjectMatrixEntry

case class FileListEntry (oid:String, fileName:String, path:String, mimeType:Option[String], description:Option[String], inTrash:Boolean, mtime:ZonedDateTime,ctime:ZonedDateTime, atime:ZonedDateTime)

object FileListEntry {
  def apply(oid: String, fileName: String, path: String, mimeType: Option[String], description: Option[String], inTrash: Boolean, mtime: ZonedDateTime, ctime: ZonedDateTime, atime: ZonedDateTime): FileListEntry = new FileListEntry(oid, fileName, path, mimeType, description, inTrash, mtime, ctime, atime)
  def fromObjectMatrixEntry(e:ObjectMatrixEntry):Option[FileListEntry] = e.attributes.map(attribs=>
    new FileListEntry(e.oid,
      attribs.stringValues("MXFS_FILENAME"),
      attribs.stringValues("MXFS_PATH"),
      attribs.stringValues.get("MXFS_MIMETYPE"),
      attribs.stringValues.get("MXFS_DESCRIPTION"),
      attribs.boolValues.getOrElse("MXFS_INTRASH", false),
      ZonedDateTime.ofInstant(Instant.ofEpochMilli(attribs.longValues("MXFS_MODIFICATION_TIME")), ZoneId.systemDefault()),
      ZonedDateTime.ofInstant(Instant.ofEpochMilli(attribs.longValues("MXFS_CREATION_TIME")), ZoneId.systemDefault()),
      ZonedDateTime.ofInstant(Instant.ofEpochMilli(attribs.longValues("MXFS_ACCESS_TIME")), ZoneId.systemDefault()),
    )
  )
}