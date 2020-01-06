package models

import io.circe.{Encoder, Json}
import io.circe.generic.auto._
import io.circe.syntax._

case class SummaryEntry(count:Int, size:Long)

case class ProjectSummary (gnmType: Map[String,SummaryEntry], hiddenFile:Map[Boolean,SummaryEntry], gnmProject:Map[String,SummaryEntry], total:SummaryEntry) {
  def addGnmType(newType:String, size:Long) = {
    val toUpdate = gnmType.getOrElse(newType, SummaryEntry(0,0))
    val updated = toUpdate.copy(count=toUpdate.count+1, size=toUpdate.size+size)
    this.copy(gnmType=gnmType + (newType->updated))
  }

  def addHiddenFile(isHidden:Boolean,size:Long) = {
    val toUpdate = hiddenFile.getOrElse(isHidden, SummaryEntry(0,0))
    val updated = toUpdate.copy(count=toUpdate.count+1,size=toUpdate.size+size)
    this.copy(hiddenFile=hiddenFile + (isHidden->updated))
  }

  def addGnmProject(projectId:String, size:Long) = {
    val toUpdate = gnmProject.getOrElse(projectId, SummaryEntry(0,0))
    val updated = toUpdate.copy(count=toUpdate.count+1, size=toUpdate.size+size)
    this.copy(gnmProject=gnmProject + (projectId->updated))
  }

  def addToTotal(size:Long) = {
    val updated = total.copy(count = total.count+1, size=total.size+size)
    this.copy(total=updated)
  }
}

object ProjectSummary {
  def apply(gnmType: Map[String, SummaryEntry], hiddenFile: Map[Boolean, SummaryEntry], gnmProject: Map[String, SummaryEntry], total:SummaryEntry): ProjectSummary = new ProjectSummary(gnmType, hiddenFile, gnmProject, total)

  def apply():ProjectSummary = ProjectSummary(Map(),Map(),Map(),SummaryEntry(0,0))
}

class MapEntryEncoder[A:io.circe.Encoder] {
  implicit val encodeMap:Encoder[Map[A,SummaryEntry]] = new Encoder[Map[A,SummaryEntry]] {
    override final def apply(a: Map[A, SummaryEntry]): Json = {
      val tplList = a.map(entry=>(entry._1.toString, entry._2.asJson)).toIterable
      Json.fromFields(tplList)
    }
  }
}

trait ProjectSummaryEncoder {
  implicit val stringMapEncoder = new MapEntryEncoder[String].encodeMap
  implicit val boolMapEncoder = new MapEntryEncoder[Boolean].encodeMap
}