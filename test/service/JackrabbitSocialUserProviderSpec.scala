package test.service

import test.AorraTestUtils.fakeApp
import com.wingnest.play2.jackrabbit.Jcr
import java.io.File
import javax.jcr.Repository
import org.apache.commons.io.FileUtils
import org.specs2.mutable._
import org.specs2.specification.Scope
import play.api.libs.oauth.OAuth
import play.api.test.Helpers._
import play.api.test._
import securesocial.core.AuthenticationMethod
import securesocial.core.SocialUser
import securesocial.core.{Identity,PasswordInfo,UserId}
import service.JackrabbitSocialUserProvider
import javax.jcr.Session
import models.User
import java.util.UUID

/**
 * Check that Jackrabbit is hooked up properly for testing.
 */
class JackrabbitUserServiceSpec extends Specification {

  "Jackrabbit Social User Provider" should {

    val testSocialUser = SocialUser(
      UserId("testuser", "testprovider"),
      "Test", "User", "Test User",
      Some("test@example.com"),
      Some("http://example.test/"),
      AuthenticationMethod.UserPassword,
      None,
      None,
      Some(PasswordInfo(
        "bcrypt",
        "$2a$10$IK84/N39CQ.zdYgmc8I3o.XOMYm1SoJobtI35XsVa5MgI1/MaMzhS",
        None)))

    def checkEqual(expected: Identity, actual: Identity) {
      // OK, let's run through Identity and check all the methods match
      actual.id should_== expected.id
      actual.firstName should_== expected.firstName
      actual.lastName should_== expected.lastName
      actual.fullName should_== expected.fullName
      actual.email should_== expected.email
      actual.avatarUrl should_== expected.avatarUrl
      actual.authMethod should_== expected.authMethod
      actual.oAuth1Info should_== expected.oAuth1Info
      actual.oAuth2Info should_== expected.oAuth2Info
      actual.passwordInfo should_== expected.passwordInfo
    }

    "allow social users to be saved" in {
      running(fakeApp) {
        val testImpl = new TestImpl(Jcr.getRepository)
        val testUser = testSocialUser
        testImpl.save(testUser)

        val loadedUser = testImpl.find(testUser.id)
        loadedUser must beSome
        checkEqual(testUser, loadedUser.get)
      }
    }

    "can find with email/provider" in {
      running(fakeApp) {
        val testImpl = new TestImpl(Jcr.getRepository)
        val testUser = testSocialUser
        testImpl.save(testUser)

        val loadedUser = testImpl.findByEmailAndProvider(
            testUser.email.get,
            testUser.id.providerId)
        loadedUser must beSome
        checkEqual(testUser, loadedUser.get)
      }
    }

    "can list users" in {
      running(fakeApp) {
        val testImpl = new TestImpl(Jcr.getRepository)
        val testUser = testSocialUser
        testImpl.save(testUser)

        val allUsers = testImpl.list
        allUsers must have size(1)
        allUsers match {
          case Seq(u: User) => checkEqual(testUser, u)
        }
      }
    }

    "use UUIDs for usernames" in {
      running(fakeApp) {
        val testImpl = new TestImpl(Jcr.getRepository)
        val loadedUser = testImpl.save(testSocialUser)

        // It should be a User
        val u = loadedUser.asInstanceOf[User]

        val uuidRegex =
          """[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}""".r
        uuidRegex.findFirstIn(u.jackrabbitUser.getID) must beSome
      }
    }

    "create users that can be impersonated" in {
      running(fakeApp) {
        val testImpl = new TestImpl(Jcr.getRepository)
        val loadedUser = testImpl.save(testSocialUser)

        // It should be a User
        val u = loadedUser.asInstanceOf[User]
        testImpl.inSession { session =>
          val newSession = u.impersonate(session)
          newSession.getUserID() should_== u.jackrabbitUser.getID
          newSession.logout()
          session.isLive must beTrue
        }
      }
    }
  }

  class TestImpl(repository: Repository) extends JackrabbitSocialUserProvider {
    override def inSession[A](op: (Session) => A): A = {
      val session = Jcr.login("admin", "admin")
      op(session)
      // Note: We don't close the session, because it constantly gets reused.
    }
  }

}