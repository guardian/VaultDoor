package helpers

import com.om.mxs.client.japi.{Constants, SearchTerm}

import scala.util.matching.Regex

object SearchTermHelper {
  private val projectIdValidators = Array(
    "^\\d+$".r,
    "^\\w{2}-\\d+".r
  )

  def projectIdQuery(forProject:String, onlyRushes:Boolean):Option[ContentSearchBuilder] = {
    def testValidator(validator:Regex, remaining:Array[Regex]):Option[ContentSearchBuilder] = {
      forProject match {
        case validator()=>
          val baseSearch = ContentSearchBuilder("")
            .withTerm("GNM_PROJECT_ID", "\"" + forProject + "\"", SearchCombiner.AND)

          val typeSearch = if(onlyRushes) baseSearch.withTerm("GNM_TYPE", "rushes", SearchCombiner.AND) else baseSearch
          Some(typeSearch)

        case _=>
          if(remaining.nonEmpty) {
            testValidator(remaining.head, remaining.tail)
          } else {
            None
          }
      }
    }
    testValidator(projectIdValidators.head, projectIdValidators.tail)
  }
}
