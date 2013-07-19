package test;

import org.openqa.selenium.htmlunit.HtmlUnitDriver;

import com.gargoylesoftware.htmlunit.BrowserVersion;

public class MyHtmlUnitDriver extends HtmlUnitDriver {

  public MyHtmlUnitDriver() {
    super(BrowserVersion.FIREFOX_3_6);
    this.setJavascriptEnabled(true);
  }

}
