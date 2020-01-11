package streamcomponents

import akka.stream.scaladsl.{GraphDSL, Source}
import akka.stream.{Attributes, Inlet, SinkShape, SourceShape}
import akka.stream.stage.{AbstractInHandler, GraphStage, GraphStageLogic, GraphStageWithMaterializedValue}
import com.om.mxs.client.japi.{SearchTerm, UserInfo}
import javax.activation.MimeType
import models.{CustomMXSMetadata, ObjectMatrixEntry, ProjectSummary}
import org.slf4j.LoggerFactory

import scala.concurrent.{Future, Promise}
import scala.util.{Success, Try}

class ProjectSummarySink extends GraphStageWithMaterializedValue[SinkShape[ObjectMatrixEntry], Future[ProjectSummary]] {
  private final val in:Inlet[ObjectMatrixEntry] = Inlet.create("ProjectSummarySink.in")

  override def shape: SinkShape[ObjectMatrixEntry] = SinkShape.of(in)

  def makeMimeType(from:String) = Try { new MimeType(from)}.toOption

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[ProjectSummary]) = {
    val summaryPromise = Promise[ProjectSummary]()

    val logic = new GraphStageLogic(shape) {
      private val logger = LoggerFactory.getLogger(getClass)
      private var ongoingSummary = ProjectSummary()

      setHandler(in, new AbstractInHandler {
        override def onPush(): Unit = {
          val elem = grab(in)
          val maybeSizeFromAttribs = elem.fileAttribues.map(_.size)
          val maybeSizeFromMeta = elem.attributes.flatMap(_.longValues.get("DPSP_SIZE"))
          val maybeSizeFromStringMeta = elem.attributes.flatMap(_.stringValues.get("DPSP_SIZE").map(_.toLong))

          val itemSize = maybeSizeFromAttribs.getOrElse(maybeSizeFromMeta.getOrElse(maybeSizeFromStringMeta.getOrElse(0L)))

          val maybeXtn = elem.attributes
              .flatMap(_.stringValues.get("MXFS_FILEEXT"))
              .map(xtn=>if(xtn=="") "unknown" else xtn)

          val maybeMimeType = for {
            attrs <- elem.attributes
            typeString <- attrs.stringValues.get("MXFS_MIMETYPE")
            mimeType <- makeMimeType(typeString)
          } yield mimeType

          elem.attributes.flatMap(CustomMXSMetadata.fromMxsMetadata) match {
            case Some(customMeta)=>
              ongoingSummary = customMeta.itemType.map(t=>ongoingSummary.addGnmType(t,itemSize)).getOrElse(ongoingSummary)
              ongoingSummary = customMeta.hidden.map(h=>ongoingSummary.addHiddenFile(h, itemSize)).getOrElse(ongoingSummary)
              ongoingSummary = customMeta.projectId.map(p=>ongoingSummary.addGnmProject(p, itemSize)).getOrElse(ongoingSummary)
              ongoingSummary = maybeXtn.map(x=>ongoingSummary.addFileType(x, itemSize)).getOrElse(ongoingSummary)
              ongoingSummary = maybeMimeType.map(x=>ongoingSummary.addMediaType(x, itemSize)).getOrElse(ongoingSummary)
              ongoingSummary = ongoingSummary.addToTotal(itemSize)
            case None=>
              logger.warn(s"Item ${elem.oid} has no custom metadata attributes")
          }
          pull(in)
        }
      })

      override def preStart(): Unit = pull(in)

      override def postStop(): Unit = summaryPromise.complete(Success(ongoingSummary))
    }

    (logic, summaryPromise.future)
  }
}

object ProjectSummarySink {
  val usefulFields = Array(
    "GNM_TYPE",
    "GNM_HIDDEN_FILE",
    "GNM_PROJECT_ID",
    "MXFS_FILEEXT",
    "DPSP_SIZE",
    "MXFS_MIMETYPE"
  )

  /**
    * return an initialised OMFastSearchSource initialised to return the correct fields for building a summary
    * @param userInfo UserInfo object pointing to the appliance and vault to use
    * @param searchTerms an Array of MXS SearchTerm. These are ANDed together and used as the final search term
    * @return an Akka Source that yields partialilly initialised ObjectMatrixEntry instances. It is guaranteed to give good results with ProjectSummarySink
    */
  def suitableFastSource(userInfo:UserInfo, searchTerms: Array[SearchTerm]) = Source.fromGraph(GraphDSL.create() { implicit builder =>
    val src = builder.add(new OMFastSearchSource(userInfo, searchTerms, includeFields = usefulFields))
    SourceShape(src.out)
  })
}