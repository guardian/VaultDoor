package helpers

import org.specs2.mutable.Specification

class SearchTermHelperSpec extends Specification {
  "SearchTermHelper.projectIdQuery" should {
    "return a SearchTerm for a purely numeric project id" in {
      SearchTermHelper.projectIdQuery("1234567", false) must beSome
    }

    "return a SearchTerm for a Vidispine format id" in {
      SearchTermHelper.projectIdQuery("AB-889900", false) must beSome
    }

    "return None for an invalid search term" in {
      SearchTermHelper.projectIdQuery("fdjkhsfdjkhfsd", false) must beNone
      SearchTermHelper.projectIdQuery("; select * from user", false) must beNone
    }
  }
}
