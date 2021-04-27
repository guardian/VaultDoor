package models

import io.circe.{Decoder, Encoder}
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success, Try}
object CustomMXSMetadata {
  private val logger = LoggerFactory.getLogger(getClass)

  object GnmType extends Enumeration {
    type GnmType = Value
    val master, rushes, deliverables, project, unsorted, poster, proxy, metadata = Value

    /**
      * does the same as `withName` - i.e. returns the enum value with the corresponding string value - but returns
      * a Scala Failure() if there is no match instead of throwing an exception
      * @param n name to look up
      * @return Success with the enum Value if matching otherwise false
      */
    def withNameSafe(n:String) = Try { withName(n.toLowerCase) }
  }

  object Encoders {
    implicit val GnmTypeEncoder:Encoder[GnmType.Value] = Encoder.encodeEnumeration(GnmType)
    implicit val GnmTypeDecoder:Decoder[GnmType.Value] = Decoder.decodeEnumeration(GnmType)
  }

  /**
    * initialise from an MxsMetadata object from the SDK.
    * this assumes that GNM_TYPE is set to a string value, if so any supplementary values are
    * added in.
    * if GNM_TYPE is not set then None is returned
    * if GNM_TYPE is not recognised then None is returned and a warning emitted
    * @param incoming an MxsMetadata object from the OM SDK
    * @return an initialised CustomMXSMetadata object or None
    */
  def fromMxsMetadata(incoming:MxsMetadata):Option[CustomMXSMetadata] = {
    import cats.implicits._ //for .sequence; turns Option[Try[String]] into Try[Option[String]]

    incoming.stringValues.get("GNM_TYPE").map(GnmType.withNameSafe).sequence match {
      case Success(Some(itemType))=>
        Some(new CustomMXSMetadata(Some(itemType),
          incoming.stringValues.get("GNM_PROJECT_ID"),
          incoming.stringValues.get("GNM_COMMISSION_ID"),
          incoming.stringValues.get("GNM_MASTER_ID"),
          incoming.stringValues.get("GNM_MASTER_NAME"),
          incoming.stringValues.get("GNM_MASTER_USER"),
          incoming.stringValues.get("GNM_PROJECT_NAME"),
          incoming.stringValues.get("GNM_COMMISSION_NAME"),
          incoming.stringValues.get("GNM_WORKING_GROUP_NAME"),
          incoming.intValues.get("GNM_DELIVERABLE_ASSET_ID"),
          incoming.intValues.get("GNM_DELIVERABLE_BUNDLE_ID"),
          incoming.intValues.get("GNM_DELIVERABLE_VERSION"),
          incoming.stringValues.get("GNM_DELIVERABLE_TYPE"),
          incoming.boolValues.get("GNM_HIDDEN_FILE")
        ))
      case Success(None)=>None
      case Failure(_)=>
        logger.error(s"Did not recognise GNM_TYPE value ${incoming.stringValues.get("GNM_TYPE")}")
        None
    }
  }
}

case class CustomMXSMetadata(itemType:Option[CustomMXSMetadata.GnmType.Value],
                             projectId:Option[String],
                             commissionId:Option[String],
                             masterId:Option[String],
                             masterName:Option[String],
                             masterUser:Option[String],
                             projectName:Option[String],
                             commissionName:Option[String],
                             workingGroupName:Option[String],
                             deliverableAssetId:Option[Int],
                             deliverableBundle:Option[Int],
                             deliverableVersion:Option[Int],
                             deliverableType:Option[String],
                             hidden:Option[Boolean]=Some(false)) {
  /**
    * adds the contents of the record to the given MxsMetadata object, ignoring empty fields
    * @param addTo existing [[MxsMetadata]] object to add to; this can be `MxsMetadata.empty`
    * @return a new, updated [[MxsMetadata]] object
    */
  def toAttributes(addTo:MxsMetadata):MxsMetadata = {
    val content = Seq(
      projectId.map(s=>"GNM_PROJECT_ID"->s),
      commissionId.map(s=>"GNM_COMMISSION_ID"->s),
      masterId.map(s=>"GNM_MASTER_ID"->s),
      masterName.map(s=>"GNM_MASTER_NAME"->s),
      masterUser.map(s=>"GNM_MASTER_USER"->s),
      projectName.map(s=>"GNM_PROJECT_NAME"->s),
      commissionName.map(s=>"GNM_COMMISSION_NAME"->s),
      workingGroupName.map(s=>"GNM_WORKING_GROUP_NAME"->s),
      deliverableType.map(s=>"GNM_DELIVERABLE_TYPE"->s)
    ).collect({case Some(kv)=>kv}).toMap

    val firstUpdate = content.foldLeft(addTo)((acc,kv)=>acc.withString(kv._1,kv._2))

    val intContent = Seq(
      deliverableAssetId.map(i=>"GNM_DELIVERABLE_ASSETID"->i),
      deliverableBundle.map(i=>"GNM_DELIVERABLE_BUNDLEID"->i),
      deliverableVersion.map(i=>"GNM_DELIVERABLE_VERSION"->i)
    ).collect({case Some(kv)=>kv}).toMap

    val secondUpdate = intContent.foldLeft(firstUpdate)((acc,kv)=>acc.withValue(kv._1,kv._2))

    secondUpdate.withValue("GNM_HIDDEN_FILE", hidden)
  }
}

