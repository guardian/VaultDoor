package services

import org.slf4j.LoggerFactory
import akka.stream.{ClosedShape, Materializer}
import akka.stream.scaladsl.{GraphDSL, RunnableGraph, Sink}
import com.om.mxs.client.japi.SearchTerm
import helpers.{MetadataHelper, UserInfoCache}
import models.{CachedEntry, ExistingArchiveContentCache}
import streamcomponents.OMFastSearchSource

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global


@Singleton
class DuplicateFinderService @Inject()(userInfoCache:UserInfoCache)(implicit mat:Materializer){
  private val logger = LoggerFactory.getLogger(getClass)


  def loadExistingContent(vaultId: String) = {
    val interestingFields = Array(
      "MXFS_PATH",
      "MXFS_FILENAME",
      "GNM_ASSET_FOLDER",
      "GNM_TYPE",
      "GNM_PROJECT_ID",
      "__mxs__length"
    )

    val catchAllSearchTerm = SearchTerm.createNOTTerm(SearchTerm.createSimpleTerm("oid", ""))
    val finalSink = Sink.seq[CachedEntry]
    val content = userInfoCache.infoForVaultId(vaultId)
    val graph = GraphDSL.create(finalSink) { implicit builder =>
      sink =>
        import akka.stream.scaladsl.GraphDSL.Implicits._

        val src = builder.add(new OMFastSearchSource(content.head, Array(catchAllSearchTerm), interestingFields, atOnce = 100))
        src.out.map(elem => {
          val ent = CachedEntry(
            elem.oid,
            elem.attributes.flatMap(_.stringValues.get("MXFS_PATH")).getOrElse("(no path)"),
            elem.attributes.flatMap(_.stringValues.get("MXFS_FILENAME")).getOrElse("(no filename)"),
            elem.attributes.flatMap(_.stringValues.get("GNM_ASSET_FOLDER")),
            elem.attributes.flatMap(_.stringValues.get("GNM_TYPE")),
            elem.attributes.flatMap(_.stringValues.get("GNM_PROJECT_ID")),
            "",
            elem.stringAttribute("__mxs__length").map(_.toLong).getOrElse(0L),
          )

          logger.debug(s"Got entry $ent")
          ent
        }) ~> sink
        ClosedShape
    }

    RunnableGraph.fromGraph(graph).run()
  }

  case class FullDuplicateData(mxfsPath:String, duplicateNumber:Int, duplicatesData:Seq[CachedEntry])
  case class AllDuplicateData(dupes_count:Int, item_count:Int, duplicates:Seq[FullDuplicateData])

  def getDuplicateData(vaultId: String)  = {
    loadExistingContent(vaultId).map(results=>{
      val contentCache = new ExistingArchiveContentCache(results)
      val dupeCount = contentCache.dupesCount
      if (dupeCount > 0) {
        logger.warn(s"There are $dupeCount duplicated files in the archive")
      } else {
        logger.info(s"No duplicates found.")
      }
      val duplicatesArray = contentCache.dupedPaths.map(dupe=>{
        logger.debug(s"${dupe._1}: ${dupe._2} copies")
        val duplicatedItemData = contentCache.getAllForPath(dupe._1)
        FullDuplicateData(dupe._1, dupe._2, duplicatedItemData)
      }).toSeq
      logger.info(s"Got existing ${results.length} items in the vault")
      val duplicateDataToReturn = AllDuplicateData(dupes_count = contentCache.dupesCount, item_count = results.length, duplicates = duplicatesArray)
      duplicateDataToReturn
    })
  }
}