package models

import org.slf4j.LoggerFactory

class ExistingArchiveContentCache(rawData:Seq[CachedEntry]) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val byPathMap = rawData.foldLeft(Map[String, Seq[CachedEntry]]())((acc, elem)=>{
    acc.get(elem.mxfsPath) match {
      case Some(existingElem)=>
        acc + (elem.mxfsPath -> (existingElem :+ elem) )
      case None=>
        acc + (elem.mxfsPath -> Seq(elem))
    }
  })

  def getAllForPath(path:String) = byPathMap.getOrElse(path, Seq())

  def getFirstForPath(path:String) = byPathMap.get(path).flatMap(_.headOption)

  def countForPath(path:String) = {
    val count = byPathMap.get(path).map(_.length).getOrElse(0)
    logger.debug(s"Path $path has $count matches in the archive")
    count
  }

  def count = byPathMap.values.foldLeft(0)((acc, elem)=>acc + elem.length)

  /**
    * returns a count of the number of paths that have multiple items
    * @return
    */
  def dupesCount = byPathMap.values.count(_.length>1)

  /**
    * returns a list of the paths that have multiple items
    * @return a Map containing the path as a string and the number of assocaited items as an integer
    */
  def dupedPaths = byPathMap.filter(_._2.length>1).map(el=>(el._1, el._2.length))
}
