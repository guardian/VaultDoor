package models

import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}

case class PresentableFile(oid:String,
                           filepath: Option[String],
                           size:Option[Long],
                           mimeType: Option[String],
                           attributes: Option[FileAttributes],
                           gnmMetadata: Option[GnmMetadata],
                           customMeta:Option[String]
                          )

object PresentableFile extends ((String, Option[String], Option[Long], Option[String], Option[FileAttributes],Option[GnmMetadata], Option[String])=>PresentableFile) {
  def fromObjectMatrixEntry(src:ObjectMatrixEntry):PresentableFile =
    PresentableFile(src.oid,
      src.stringAttribute("MXFS_PATH"),
      maybeSize(src),
      src.stringAttribute("MXFS_MIMETYPE"),
      src.fileAttribues,
      GnmMetadata.fromObjectMatrixEntry(src),
      src.attributes.map(_.dumpString(None))
    )

  /**
    * internal helper method to check if the DPSP_SIZE data is present as a string field instead of a Long
    * @param src ObjectMatrixEntry to query
    * @return an Option with a Long of the DPSP_SIZE value, if it's present
    */
  private def maybeSize(src:ObjectMatrixEntry):Option[Long] = {
    src.longAttribute("DPSP_SIZE") match {
      case longValue@Some(_)=>longValue
      case None=>
        src.stringAttribute("DPSP_SIZE").flatMap(stringValue=>Try {
          stringValue.toLong
        }.toOption)
    }
  }

  val MXFSFields = Array(
    "MXFS_FILENAME",
    "MXFS_PATH",
    "DPSP_SIZE",
    "MXFS_FILEEXT",
    "MXFS_MIMETYPE",
  )
}

case class GnmMetadata(`type`: String,projectId:Option[String],commissionId:Option[String],
                       projectName:Option[String], commissionName:Option[String],workingGroupName:Option[String],
                       masterId:Option[String], masterName:Option[String], masterUser:Option[String],
                       deliverableBundleId:Option[Int], deliverableVersion:Option[Int],deliverableType:Option[String]
                      )

object GnmMetadata {
  private val logger = LoggerFactory.getLogger(getClass)

  val Fields = Array(
    "GNM_TYPE",
    "GNM_TYPE",
    "GNM_PROJECT_ID",
    "GNM_COMMISSION_ID",
    "GNM_PROJECT_NAME",
    "GNM_COMMISSION_NAME",
    "GNM_WORKING_GROUP_NAME",
    "GNM_MASTER_ID",
    "GNM_MASTER_NAME",
    "GNM_MASTER_USER",
    "GNM_DELIVERABLE_BUNDLE_ID",
    "GNM_DELIVERABLE_VERSION",
    "GNM_DELIVERABLE_TYPE"
  )
  def fromObjectMatrixEntry(src:ObjectMatrixEntry):Option[GnmMetadata] = Try {
    src.attributes.map(attribs => GnmMetadata(
      attribs.stringValues("GNM_TYPE"),
      attribs.stringValues.get("GNM_PROJECT_ID"),
      attribs.stringValues.get("GNM_COMMISSION_ID"),
      attribs.stringValues.get("GNM_PROJECT_NAME"),
      attribs.stringValues.get("GNM_COMMISSION_NAME"),
      attribs.stringValues.get("GNM_WORKING_GROUP_NAME"),
      attribs.stringValues.get("GNM_MASTER_ID"),
      attribs.stringValues.get("GNM_MASTER_NAME"),
      attribs.stringValues.get("GNM_MASTER_USER"),
      attribs.intValues.get("GNM_DELIVERABLE_BUNDLE_ID"),
      attribs.intValues.get("GNM_DELIVERABLE_VERSION"),
      attribs.stringValues.get("GNM_DELIVERABLE_TYPE")
    ))
  } match {
    case Success(meta)=>meta
    case Failure(err)=>
      logger.warn(s"Could not get GNM custom metadata values for ${src.oid}: ${err.getMessage}")
      None
  }
}