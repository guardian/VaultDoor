package streamcomponents

import akka.stream.{Attributes, FlowShape, Inlet, Outlet}
import akka.stream.stage.{AbstractInHandler, AbstractOutHandler, GraphStage, GraphStageLogic}
import models.{ArchiveEntryDownloadSynopsis, ObjectMatrixEntry}
import org.slf4j.LoggerFactory

class MakeDownloadSynopsis (maybeStripPrefixes:Option[Seq[String]]) extends GraphStage[FlowShape[ObjectMatrixEntry, ArchiveEntryDownloadSynopsis]] {
  private final val in:Inlet[ObjectMatrixEntry] = Inlet.create("MakeDownloadSynopsis.in")
  private final val out:Outlet[ArchiveEntryDownloadSynopsis] = Outlet.create("ArchiveEntryDownloadSynopsis")

  override def shape: FlowShape[ObjectMatrixEntry, ArchiveEntryDownloadSynopsis] = FlowShape.of(in,out)

  /**
    * recursively iterate the provided strip prefixes and remove the first one that matches
    * @param filePath filepath determined from the appliance
    * @return
    */
  def strippedPrefix(filePath:String) = maybeStripPrefixes match {
    case None=>filePath
    case Some(prefixesToRemove)=>
      def stripNextPrefix(remainingList:Seq[String], onBaseString:String):String = {
        if(remainingList.isEmpty){
          onBaseString
        } else {
          val updatedString = if(onBaseString.startsWith(remainingList.head)){
            return onBaseString.substring(remainingList.head.length)
          } else {
            onBaseString
          }
          stripNextPrefix(remainingList.tail, onBaseString)
        }
      }

      stripNextPrefix(prefixesToRemove, filePath)
  }

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    private val logger:org.slf4j.Logger =  LoggerFactory.getLogger(getClass)

    def determineSize(entry:ObjectMatrixEntry) = {
      val maybeFileAttrSize = entry.fileAttribues.map(_.size)
      val maybeMetaSize = entry.attributes.flatMap(_.longValues.get("DPSP_SIZE"))
      val maybeMetaStringSize = entry.attributes.flatMap(_.stringValues.get("DPSP_SIZE").map(_.toLong))

      maybeFileAttrSize.getOrElse(maybeMetaSize.getOrElse(maybeMetaStringSize.getOrElse(0L)))
    }

    setHandler(out, new AbstractOutHandler {
      override def onPull(): Unit = pull(in)
    })

    setHandler(in, new AbstractInHandler {
      override def onPush(): Unit = {
        val elem = grab(in)

        val maybeFilePath = elem.attributes.flatMap(_.stringValues.get("MXFS_PATH").map(strippedPrefix))
        val maybeFileSize = determineSize(elem)
        val result = ArchiveEntryDownloadSynopsis(elem.oid, maybeFilePath.getOrElse(""), maybeFileSize)
        push(out, result)
      }
    })
  }
}
