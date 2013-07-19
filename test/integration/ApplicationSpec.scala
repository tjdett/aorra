package integration

import java.util.concurrent.TimeUnit
import org.openqa.selenium.Platform
import org.openqa.selenium.WebDriver
import org.openqa.selenium.htmlunit.HtmlUnitDriver
import org.specs2.mutable.Specification
import play.api.test.Helpers
import play.api.test.WithBrowser
import test.AorraScalaHelper._
import test.MyHtmlUnitDriver
import org.fluentlenium.core.FluentPage

class ApplicationSpec extends Specification {

  class HtmlUnit extends WithBrowser(classOf[MyHtmlUnitDriver], fakeAorraApp) {
    def page(theUrl: String): FluentPage = {
      new FluentPage(browser.getDriver()) {
        override def getUrl = theUrl
        override def isAt {
          url().must_==(theUrl)
        }
      }
    }
  }

  "AORRA" should {

    "redirect unauthenticated users to the login page" in new HtmlUnit {
      browser.goTo("/")
      browser.url must endWith("/login")
    }

    "login page allows login" in new HtmlUnit {
      // Create User
      var email = ""
      asAdminUser({ (session, user, newRequest) =>
        email = user.getEmail()
      })

      browser.goTo("/login")
      browser.fill("form input[name='email']").`with`(email)
      browser.fill("form input[name='password']").`with`("password")
      browser.click("form button[type='submit']")
      browser.await().atMost(5, TimeUnit.SECONDS).untilPage(page("/")).isAt()
    }

  }

}