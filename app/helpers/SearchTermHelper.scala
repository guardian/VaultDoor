package helpers

import com.om.mxs.client.japi.{Constants, SearchTerm}

import scala.util.matching.Regex

object SearchTermHelper {
  private val projectIdValidators = Array(
    "^\\d+$".r,
    "^\\w{2}-\\d+".r
  )

  def projectIdSearchTerm(forProject:String) = {
    def testValidator(validator:Regex, remaining:Array[Regex]):Option[SearchTerm] = {
      forProject match {
        case validator()=>
          Some(SearchTerm.createSimpleTerm(Constants.CONTENT, s"GNM_PROJECT_ID:$forProject"))
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
