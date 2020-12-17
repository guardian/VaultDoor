package helpers

import org.specs2.mutable.Specification

class SearchTermHelperSpec extends Specification {
  "SearchTermHelper.projectIdSearchTerm" should {
    "return a SearchTerm for a purely numeric project id" in {
      SearchTermHelper.projectIdSearchTerm("1234567") must beSome
    }

    "return a SearchTerm for a Vidispine format id" in {
      SearchTermHelper.projectIdSearchTerm("AB-889900") must beSome
    }

    "return None for an invalid search term" in {
      SearchTermHelper.projectIdSearchTerm("fdjkhsfdjkhfsd") must beNone
      SearchTermHelper.projectIdSearchTerm("; select * from user") must beNone
    }
  }
}
