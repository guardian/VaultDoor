package helpers

import org.specs2.mutable.Specification

class ContentSearchBuilderSpec extends Specification {
  "ContentSearchBuilder.withTerm" should {
    "deliver a basic key-value search string" in {
      val result = ContentSearchBuilder("").withTerm("field","value",SearchCombiner.AND).build
      result mustEqual "field:value"
    }

    "combine multiple queries with AND, OR and NOT" in {
      val result = ContentSearchBuilder("")
        .withTerm("field1","value1",SearchCombiner.AND, invert=true)
        .withTerm("field2","value2", SearchCombiner.AND)
        .withTerm("field3","value3", SearchCombiner.OR, invert=true)
        .build

      result mustEqual "NOT field1:value1 AND field2:value2 OR NOT field3:value3"
    }

    "automatically quote values with spaces" in {
      val result = ContentSearchBuilder("")
        .withTerm("field1", "value 1", SearchCombiner.AND)
        .build
      result mustEqual "field1:\"value 1\""
    }
  }

  "ContentSearchBuilder.withKeywords" should {
    "add the keywords into the query on a second line" in {
      val result = ContentSearchBuilder("")
        .withTerm("field","value",SearchCombiner.AND)
        .withKeywords(Seq("field1","field2"))
        .withKeywords(Seq("field3"))
        .build

      result mustEqual "field:value\nkeywords: ,field1,field2,field3"
    }
  }
}
