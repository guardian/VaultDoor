package helpers

import com.om.mxs.client.japi.{Constants, SearchTerm}

import scala.util.matching.Regex

object SearchTermHelper {
  private val projectIdValidators = Array(
    "^\\d+$".r,
    "^\\w{2}-\\d+".r
  )

  def projectIdQuery(forProject:String):Option[ContentSearchBuilder] = {
    def testValidator(validator:Regex, remaining:Array[Regex]):Option[ContentSearchBuilder] = {
      forProject match {
        case validator()=>
          Some(ContentSearchBuilder("").withTerm("GNM_PROJECT_ID", "\"" + forProject + "\"", SearchCombiner.AND))
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
