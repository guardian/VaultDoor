package auth

import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.{JWK, KeyUse, RSAKey}
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.Configuration

import scala.jdk.CollectionConverters._
import scala.util.{Success, Try}

class BearerTokenAuthSpec extends Specification with Mockito {
  val fakeConfig = Configuration.from(
    Map(
      "auth.validAudiences"-> List("my-app")
    )
  )

  "BearerTokenAuth.checkAudience" should {
    "return a Right if the audience claim matches" in {
      val mockedClaims = new JWTClaimsSet.Builder()
        .subject("testuser@testdomain.com")
        .audience(Seq("my-app","test").asJava)
        .build()

      val toTest = new BearerTokenAuth(fakeConfig)
      toTest.checkAudience(mockedClaims) must beRight(LoginResultOK(mockedClaims))
    }

    "return a Left if the audience claim does not match" in {
      val mockedClaims = new JWTClaimsSet.Builder()
        .subject("testuser@testdomain.com")
        .audience(Seq("their-app").asJava)
        .build()

      val toTest = new BearerTokenAuth(fakeConfig)
      toTest.checkAudience(mockedClaims) must beLeft(LoginResultInvalid("The token was not from a supported app"))
    }
  }

  "BearerTokenAuth.checkUserGroup" should {
    "return left if the user is neither MM creator nor admin" in {
      val mockedClaims = new JWTClaimsSet.Builder()
        .subject("testuser@testdomain.com")
        .audience("my-app")
        .build()

      val toTest = new BearerTokenAuth(fakeConfig)
      toTest.checkUserGroup(mockedClaims) must beLeft(LoginResultInvalid("You don't have access to this system.  Contact Multimediatech if you think this is an error."))
    }

    "return right if the user is an MM creator only" in {
      val mockedClaims = new JWTClaimsSet.Builder()
        .subject("testuser@testdomain.com")
        .audience("my-app")
        .claim("multimedia_creator", "true")
        .build()

      val toTest = new BearerTokenAuth(fakeConfig)
      toTest.checkUserGroup(mockedClaims) must beRight(LoginResultOK(mockedClaims))
    }

    "return right if the user is an MM admin only" in {
      val mockedClaims = new JWTClaimsSet.Builder()
        .subject("testuser@testdomain.com")
        .audience("my-app")
        .claim("multimedia_admin", "true")
        .build()

      val toTest = new BearerTokenAuth(fakeConfig)
      toTest.checkUserGroup(mockedClaims) must beRight(LoginResultOK(mockedClaims))
    }

    "return right if the user is an both an admin and creator" in {
      val mockedClaims = new JWTClaimsSet.Builder()
        .subject("testuser@testdomain.com")
        .audience("my-app")
        .claim("multimedia_admin", "true")
        .claim("multimedia_creator", "true")
        .build()

      val toTest = new BearerTokenAuth(fakeConfig)
      toTest.checkUserGroup(mockedClaims) must beRight(LoginResultOK(mockedClaims))
    }
  }

  "BearerTokenAuth.validateToken" should {
    "return login ok if the token is valid" in {
      val mockedJwt = mock[SignedJWT]
      mockedJwt.verify(any) returns true

      val mockedClaims = new JWTClaimsSet.Builder()
        .subject("testuser@testdomain.com")
        .audience("my-app")
        .claim("multimedia_admin", "true")
        .claim("multimedia_creator", "true")
        .build()
      mockedJwt.getJWTClaimsSet returns mockedClaims

      val mockedJWK = mock[JWK]
      val mockedVerifier = mock[RSASSAVerifier]

      val toTest = new BearerTokenAuth(fakeConfig) {
        override def loadInKey(): Try[JWK] = Success(mockedJWK)
        override protected def parseTokenContent(content: String): Try[SignedJWT] = Success(mockedJwt)
        override protected def getVerifier(jwk: JWK): RSASSAVerifier = mockedVerifier
      }

      toTest.validateToken(LoginResultOK("fake-token")) must beRight(LoginResultOK(mockedClaims))
      there was one(mockedJwt).verify(mockedVerifier)
    }

    "return left if the token is not valid" in {
      val mockedJwt = mock[SignedJWT]
      mockedJwt.verify(any) returns false

      val mockedClaims = new JWTClaimsSet.Builder()
        .subject("testuser@testdomain.com")
        .audience("my-app")
        .claim("multimedia_admin", "true")
        .claim("multimedia_creator", "true")
        .build()
      mockedJwt.getJWTClaimsSet returns mockedClaims

      val mockedJWK = mock[JWK]
      val mockedVerifier = mock[RSASSAVerifier]

      val toTest = new BearerTokenAuth(fakeConfig) {
        override def loadInKey(): Try[JWK] = Success(mockedJWK)
        override protected def parseTokenContent(content: String): Try[SignedJWT] = Success(mockedJwt)
        override protected def getVerifier(jwk: JWK): RSASSAVerifier = mockedVerifier
      }

      toTest.validateToken(LoginResultOK("fake-token")) must beLeft(LoginResultInvalid("fake-token"))
      there was one(mockedJwt).verify(mockedVerifier)
    }

    "block login if the claims are not correct" in {
      val mockedJwt = mock[SignedJWT]
      mockedJwt.verify(any) returns true

      val mockedClaims = new JWTClaimsSet.Builder()
        .subject("testuser@testdomain.com")
        .audience("my-app")
        .build()
      mockedJwt.getJWTClaimsSet returns mockedClaims

      val mockedJWK = mock[JWK]
      val mockedVerifier = mock[RSASSAVerifier]

      val toTest = new BearerTokenAuth(fakeConfig) {
        override def loadInKey(): Try[JWK] = Success(mockedJWK)
        override protected def parseTokenContent(content: String): Try[SignedJWT] = Success(mockedJwt)
        override protected def getVerifier(jwk: JWK): RSASSAVerifier = mockedVerifier
      }

      toTest.validateToken(LoginResultOK("fake-token")) must beLeft(LoginResultInvalid("You don't have access to this system.  Contact Multimediatech if you think this is an error."))
      there was one(mockedJwt).verify(mockedVerifier)
    }
  }
}
