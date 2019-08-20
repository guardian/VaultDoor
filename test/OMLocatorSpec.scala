import java.net.URI
import java.util.UUID

import helpers.OMLocator
import org.specs2.mutable.Specification

class OMLocatorSpec extends Specification {
  "OMLocator.fromUri" should {
    "break down into user/host/path sections" in {
      val uri = URI.create("omms://D8F2E17E-19C4-4E11-9A0B-D2CDADBB48BD:_VSENC__aoeujtkPKvhg4TT0GQv1PhOeQUhcOC8CmN5lRyYSY==@1.2.3.4/38b7c064-a862-0fe2-44cd-7191ca6201c3/90688ac0-52c6-11e4-ad57-f3b682b37806/Multimedia_Culture_Sport/Ingest_from_Prelude_footage/richard_sprenger_Ingest_from_Prelude_footage/Untitled/BPAV/CLPR/001_1088_01/001_1088_01I01.PPN")

      val result = OMLocator.fromUri(uri)

      result must beASuccessfulTry

      val loc = result.get

      loc.host mustEqual "1.2.3.4"
      loc.clusterId mustEqual UUID.fromString("38b7c064-a862-0fe2-44cd-7191ca6201c3")
      loc.vaultId mustEqual UUID.fromString("90688ac0-52c6-11e4-ad57-f3b682b37806")
      loc.filePath mustEqual "Multimedia_Culture_Sport/Ingest_from_Prelude_footage/richard_sprenger_Ingest_from_Prelude_footage/Untitled/BPAV/CLPR/001_1088_01/001_1088_01I01.PPN"
    }

    "return a Left if the provided URI is not omms" in {
      val uri = URI.create("https://www.google.com/")
      val result = OMLocator.fromUri(uri)

      result must beFailedTry
      result.failed.get.getMessage mustEqual "URI is not in omms scheme"
    }

    "return a Left if there are not enough path segments " in {
      val uri = URI.create("omms://D8F2E17E-19C4-4E11-9A0B-D2CDADBB48BD:_VSENC__aoeujtkPKvhg4TT0GQv1PhOeQUhcOC8CmN5lRyYSY==@1.2.3.4/38b7c064-a862-0fe2-44cd-7191ca6201c3/90688ac0-52c6-11e4-ad57-f3b682b37806")

      val result = OMLocator.fromUri(uri)

      result must beFailedTry
    }

    "return a Left if the path segments don't correspond to UUIDs" in {
      val uri = URI.create("omms://D8F2E17E-19C4-4E11-9A0B-D2CDADBB48BD:_VSENC__aoeujtkPKvhg4TT0GQv1PhOeQUhcOC8CmN5lRyYSY==@1.2.3.4/38b7c064-62-0fe2-44cd-7191ca6201c3/90688ac0-52c6-11e4-ad57-f3b682b37806/Multimedia_Culture_Sport/Ingest_from_Prelude_footage/richard_sprenger_Ingest_from_Prelude_footage/Untitled/BPAV/CLPR/001_1088_01/001_1088_01I01.PPN")
      val result = OMLocator.fromUri(uri)

      result must beFailedTry

    }
  }
}
