package helpers

import org.specs2.mutable.Specification

class SearchTermHelperSpec extends Specification {
  "SearchTermHelper.getSearchTerm" should {
    "return a SearchTerm for a purely numeric project id" in {
      SearchTermHelper.getSearchTerm("1234567") must beSome
    }

    "return a SearchTerm for a Vidispine format id" in {
      SearchTermHelper.getSearchTerm("AB-889900") must beSome
    }

    "return None for an invalid search term" in {
      SearchTermHelper.getSearchTerm("fdjkhsfdjkhfsd") must beNone
      SearchTermHelper.getSearchTerm("; select * from user") must beNone
    }
  }
}
