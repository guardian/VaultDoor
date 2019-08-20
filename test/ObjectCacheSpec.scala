import java.util.UUID

import actors.ObjectCache
import actors.ObjectCache.CacheEntry
import akka.actor.{Cancellable, Props}
import org.specs2.mutable.Specification
import testhelpers.AkkaTestkitSpecs2Support
import akka.pattern.ask
import akka.testkit.TestProbe
import com.om.mxs.client.japi.UserInfo
import helpers.{OMLocator, UserInfoBuilder, UserInfoCache}
import org.specs2.mock.Mockito
import play.api.Configuration

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class ObjectCacheSpec extends Specification with Mockito {
  val fakeClusterId = UUID.fromString("dd112074-e6c4-455e-bf35-3a5f58470036")
  val fakeVaultId = UUID.fromString("5c761fd7-debc-4c68-8b08-48fae490ddfb")
  implicit val timeout:akka.util.Timeout = 10 seconds

  "ObjectCache!Lookup" should {
    "return the OID of an existing item in the cache if present" in new AkkaTestkitSpecs2Support {
      import ObjectCache._

      val mockSelf = TestProbe()

      val mockedUserCache = mock[UserInfoCache]
      val objectCache = system.actorOf(Props(new ObjectCache(mockedUserCache,Configuration.empty, system) {
        override protected val ownRef = mockSelf.ref

        override protected def setupTimer(): Cancellable = Cancellable.alreadyCancelled

        content = Map(
          (fakeVaultId,"path/to/some/file")->CacheEntry("dda069ae-8e2f-4e12-a494-81970c7555ae", 12345678L)
        )
      }))

      val locator = OMLocator("somehost",fakeClusterId, fakeVaultId, "path/to/some/file")

      val resultFuture = (objectCache ? Lookup(locator)).mapTo[OCMsg]

      val result = Await.result(resultFuture, timeout.duration)
      mockSelf.expectNoMessage(1.second)
      result mustEqual ObjectFound(locator, "dda069ae-8e2f-4e12-a494-81970c7555ae")
    }

    "return ObjectNotFound if there is no item present in the cache and it can't be looked up" in new AkkaTestkitSpecs2Support {
      import ObjectCache._

      val mockSelf = TestProbe()

      val mockedUserCache = mock[UserInfoCache]
      mockedUserCache.infoForAddress(any, any) returns Some(mock[UserInfo])
      val objectCache = system.actorOf(Props(new ObjectCache(mockedUserCache,Configuration.empty, system) {
        override protected val ownRef = mockSelf.ref

        override protected def setupTimer(): Cancellable = Cancellable.alreadyCancelled

        override def findByFilename(userInfo: UserInfo, fileName: String): Future[Seq[String]] = Future(Seq())
        content = Map(
          (fakeVaultId,"path/to/some/file")->CacheEntry("dda069ae-8e2f-4e12-a494-81970c7555ae", 12345678L)
        )
      }))

      val locator = OMLocator("somehost",fakeClusterId, fakeVaultId, "path/to/some/otherfile")
      val resultFuture = (objectCache ? Lookup(locator)).mapTo[OCMsg]
      mockSelf.expectNoMessage(1.second)
      val result = Await.result(resultFuture, timeout.duration)
      result mustEqual ObjectNotFound(locator)
    }

    "return ObjectFound and dispatch a message to self to cache a newly found item" in new AkkaTestkitSpecs2Support {
      import ObjectCache._

      val mockSelf = TestProbe()

      val mockedUserCache = mock[UserInfoCache]
      mockedUserCache.infoForAddress(any, any) returns Some(mock[UserInfo])
      val objectCache = system.actorOf(Props(new ObjectCache(mockedUserCache,Configuration.empty, system) {
        override protected val ownRef = mockSelf.ref

        override protected def setupTimer(): Cancellable = Cancellable.alreadyCancelled

        override def findByFilename(userInfo: UserInfo, fileName: String): Future[Seq[String]] = Future(Seq("f0103f1f-b8ec-4ecb-bda3-cf0236fdc922"))
        content = Map(
          (fakeVaultId,"path/to/some/file")->CacheEntry("dda069ae-8e2f-4e12-a494-81970c7555ae", 12345678L)
        )
      }))

      val locator = OMLocator("somehost",fakeClusterId, fakeVaultId, "path/to/some/otherfile")
      val resultFuture = (objectCache ? Lookup(locator)).mapTo[OCMsg]
      val result = Await.result(resultFuture, timeout.duration)
      mockSelf.expectMsg(UpdateCache(locator,"f0103f1f-b8ec-4ecb-bda3-cf0236fdc922"))
      result mustEqual ObjectFound(locator,"f0103f1f-b8ec-4ecb-bda3-cf0236fdc922")
    }
  }

  "ObjectCache!UpdateCache" should {
    "update the internal cache object" in new AkkaTestkitSpecs2Support {
      import ObjectCache._

      val mockSelf = TestProbe()
      val mockedUserCache = mock[UserInfoCache]
      mockedUserCache.infoForAddress(any, any) returns Some(mock[UserInfo])
      val objectCache = system.actorOf(Props(new ObjectCache(mockedUserCache,Configuration.empty, system) {
        override protected val ownRef = mockSelf.ref

        override protected def setupTimer(): Cancellable = Cancellable.alreadyCancelled

        override def findByFilename(userInfo: UserInfo, fileName: String): Future[Seq[String]] = Future(Seq())
        content = Map()
      }))

      val locator = OMLocator("somehost",fakeClusterId, fakeVaultId, "path/to/some/otherfile")
      val preUpdateRequest = Await.result((objectCache ? Lookup(locator)).mapTo[OCMsg], 10.seconds)
      preUpdateRequest must beAnInstanceOf[ObjectNotFound]

      objectCache ! UpdateCache(locator,"f0103f1f-b8ec-4ecb-bda3-cf0236fdc922")
      Thread.sleep(1000)  //allow the actor to process

      val postUpdateRequest = Await.result((objectCache ? Lookup(locator)).mapTo[OCMsg], 10.seconds)
      postUpdateRequest mustEqual ObjectFound(locator, "f0103f1f-b8ec-4ecb-bda3-cf0236fdc922")

    }
  }

  "ObjectCache!ExpiryTick" should {
    "expire cache entries that are old" in new AkkaTestkitSpecs2Support {
      import ObjectCache._

      val mockSelf = TestProbe()
      val mockedUserCache = mock[UserInfoCache]
      mockedUserCache.infoForAddress(any, any) returns Some(mock[UserInfo])
      val objectCache = system.actorOf(Props(new ObjectCache(mockedUserCache,Configuration.empty, system) {
        override protected val ownRef = mockSelf.ref

        override protected def setupTimer(): Cancellable = Cancellable.alreadyCancelled

        override def findByFilename(userInfo: UserInfo, fileName: String): Future[Seq[String]] = Future(Seq())
        content = Map(
          (fakeVaultId,"path/to/some/file")->CacheEntry("dda069ae-8e2f-4e12-a494-81970c7555ae", 3600L)
        )
      }))

      val locator = OMLocator("somehost",fakeClusterId, fakeVaultId, "path/to/some/file")
      val preUpdateRequest = Await.result((objectCache ? Lookup(locator)).mapTo[OCMsg], 10.seconds)
      preUpdateRequest mustEqual ObjectFound(locator, "dda069ae-8e2f-4e12-a494-81970c7555ae")

      objectCache ! ExpiryTick
      Thread.sleep(1000)  //allow the actor to process

      val postUpdateRequest = Await.result((objectCache ? Lookup(locator)).mapTo[OCMsg], 10.seconds)
      postUpdateRequest mustEqual ObjectNotFound(locator)
    }
  }
}
