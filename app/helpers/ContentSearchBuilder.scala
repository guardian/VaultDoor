package helpers

import scala.util.matching.Regex

object SearchCombiner extends Enumeration {
  val AND,OR = Value
}
case class ContentSearchBuilder(queryTerms:String, keywords:Seq[String]) {
  def build = {
    val base = queryTerms
    if(keywords.isEmpty) {
      base
    } else {
      base ++ "\n" ++ s"keywords: ${keywords.mkString(",")}"
    }
  }

  private val containsSpaces = new Regex("\\s+")

  /**
    * put an AND term into the query. Note that the `value` parameter is automatically quoted if it contains any spaces
    * @param field field to search for
    * @param value value to search for
    */
  def withTerm(field:String, value:String, combiner:SearchCombiner.Value, invert:Boolean=false) = {
    val quotedValue = if(containsSpaces.findFirstIn(value).isDefined) {
      s"\"$value\""
    } else {
      value
    }

    val invertedField = if(invert) {
      s"NOT $field"
    } else {
      field
    }

    val newQueryTerm = if(queryTerms=="") {
      s"$invertedField:$quotedValue"
    } else {
      s" ${combiner.toString} $invertedField:$quotedValue"
    }

    this.copy(queryTerms=newQueryTerm)
  }

  def withKeywords(moreKeywords:Seq[String]) =
    this.copy(keywords=this.keywords.union(moreKeywords))

  def withoutKeywords = this.copy(keywords=Seq())
}

object ContentSearchBuilder {
  def apply(queryString:String) = new ContentSearchBuilder(queryString, Seq())
}
