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
    def page(relUrl: String): FluentPage = {
      new FluentPage(browser.getDriver()) {
        override def getUrl = browser.baseUrl.get + relUrl
        override def isAt {
          url().must_==(getUrl)
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
      val loginPage = page("/login")

      // Create User
      var email = ""
      asAdminUser({ (session, user, newRequest) =>
        email = user.getEmail()
      })

      browser.goTo(loginPage).await.atMost(5, TimeUnit.SECONDS).untilPage.isLoaded
      browser.pageSource must endWith("</html>")
      browser.fill("#email").`with`(email)
      browser.fill("#password").`with`("password")
      browser.click("form button[type='submit']")
      browser.await().atMost(5, TimeUnit.SECONDS).untilPage(page("/")).isAt()
    }

  }

}