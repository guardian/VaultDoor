package streamcomponents

import akka.stream.{Attributes, Inlet, SinkShape}
import akka.stream.stage.{AbstractInHandler, GraphStage, GraphStageLogic, GraphStageWithMaterializedValue}
import models.{CustomMXSMetadata, ObjectMatrixEntry, ProjectSummary}
import org.slf4j.LoggerFactory

import scala.concurrent.{Future, Promise}
import scala.util.Success

class ProjectSummarySink extends GraphStageWithMaterializedValue[SinkShape[ObjectMatrixEntry], Future[ProjectSummary]] {
  private final val in:Inlet[ObjectMatrixEntry] = Inlet.create("ProjectSummarySink.in")

  override def shape: SinkShape[ObjectMatrixEntry] = SinkShape.of(in)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[ProjectSummary]) = {
    val summaryPromise = Promise[ProjectSummary]()

    val logic = new GraphStageLogic(shape) {
      private val logger = LoggerFactory.getLogger(getClass)
      private var ongoingSummary = ProjectSummary()

      setHandler(in, new AbstractInHandler {
        override def onPush(): Unit = {
          val elem = grab(in)
          val itemSize = elem.fileAttribues.get.size
          val maybeXtn = elem.attributes
              .flatMap(_.stringValues.get("MXFS_FILEEXT"))
              .map(xtn=>if(xtn=="") "unknown" else xtn)

          elem.attributes.flatMap(CustomMXSMetadata.fromMxsMetadata) match {
            case Some(customMeta)=>
              ongoingSummary = customMeta.itemType.map(t=>ongoingSummary.addGnmType(t,itemSize)).getOrElse(ongoingSummary)
              ongoingSummary = customMeta.hidden.map(h=>ongoingSummary.addHiddenFile(h, itemSize)).getOrElse(ongoingSummary)
              ongoingSummary = customMeta.projectId.map(p=>ongoingSummary.addGnmProject(p, itemSize)).getOrElse(ongoingSummary)
              ongoingSummary = maybeXtn.map(x=>ongoingSummary.addFileType(x, itemSize)).getOrElse(ongoingSummary)
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
