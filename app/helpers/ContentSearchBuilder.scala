package helpers

import scala.util.matching.Regex

object SearchCombiner extends Enumeration {
  val AND,OR = Value
}

/**
 * DSL-style helper for building MatrixStore content-search strings
 *
 * @param queryTerms
 * @param keywords
 */
case class ContentSearchBuilder(queryTerms:String, keywords:Seq[String]) {
  def build = {
    val base = queryTerms
    if(keywords.isEmpty) {
      base
    } else {
      base ++ "\n" ++ s"keywords: ,${keywords.mkString(",")}"
    }
  }

  /**
    * put an AND term into the query. Note that the `value` parameter is automatically quoted if it contains any spaces
    * @param field field to search for
    * @param value value to search for
    */
  def withTerm(field:String, value:String, combiner:SearchCombiner.Value, invert:Boolean=false) = {
    val quotedValue = if(ContentSearchBuilder.containsSpaces.findFirstIn(value).isDefined) {
      "\"" + value + "\""
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

    this.copy(queryTerms=queryTerms + newQueryTerm)
  }

  def withKeywords(moreKeywords:Seq[String]) =
    this.copy(keywords=this.keywords.union(moreKeywords))

  def withoutKeywords = this.copy(keywords=Seq())
}

object ContentSearchBuilder {
  private val containsSpaces = new Regex("\\s+")
  def apply(queryString:String) = new ContentSearchBuilder(queryString, Seq())
}
