import helpers.{BadDataError, RangeHeader}
import org.specs2.mutable.Specification

class RangeHeaderSpec extends Specification {
  "RangeHeader.fromStringHeader" should {
    "return a BadDataError if the units aren't bytes" in {
      val testHeader = "records=1-3,4-7"

      val result = RangeHeader.fromStringHeader(testHeader)
      result must beFailedTry
      result.failed.get must beAnInstanceOf[BadDataError]
    }

    "return RangeHeader records for each portion" in {
      val testHeader = "bytes=0-1234, 2000-4000"

      val result = RangeHeader.fromStringHeader(testHeader)

      result must beSuccessfulTry
      result.get.length mustEqual 2
      result.get.head mustEqual RangeHeader(Some(0),Some(1234))
      result.get(1) mustEqual RangeHeader(Some(2000),Some(4000))
    }

    "return an open-ended range if there is no end part" in {
      val testHeader = "bytes=1234-"

      val result = RangeHeader.fromStringHeader(testHeader)

      result must beSuccessfulTry
      result.get.length mustEqual 1
      result.get.head mustEqual RangeHeader(Some(1234),None)
    }

    "return an open-starting range if there is no start part" in {
      val testHeader = "bytes=-1234"

      val result = RangeHeader.fromStringHeader(testHeader)

      result must beSuccessfulTry
      result.get.length mustEqual 1
      result.get.head mustEqual RangeHeader(None,Some(1234))
    }

    "return an error if the start is later than the end" in {
      val testHeader = "bytes=1234-200"

      val result = RangeHeader.fromStringHeader(testHeader)

      result must beFailedTry
      result.failed.get must beAnInstanceOf[BadDataError]
    }

    "return an error if the ranges overlap" in {
      val testHeader = "bytes=0-1234,1000-2000"

      val result = RangeHeader.fromStringHeader(testHeader)

      result must beFailedTry
      result.failed.get must beAnInstanceOf[BadDataError]
    }

    "return an error if an open-start is specified as a subsequent range" in {
      val testHeader = "bytes=0-1234,-2000"

      val result = RangeHeader.fromStringHeader(testHeader)

      result must beFailedTry
      result.failed.get must beAnInstanceOf[BadDataError]
    }

    "return an error if an open-end is specified as an earlier range" in {
      val testHeader = "bytes=1234-,1000-2000"

      val result = RangeHeader.fromStringHeader(testHeader)

      result must beFailedTry
      result.failed.get must beAnInstanceOf[BadDataError]
    }

  }
}
