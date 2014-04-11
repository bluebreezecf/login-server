/*******************************************************************************
 *     Cloud Foundry 
 *     Copyright (c) [2009-2014] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
package org.cloudfoundry.identity.uaa.login.feature;

import org.cloudfoundry.identity.uaa.login.test.DefaultIntegrationTestConfig;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = DefaultIntegrationTestConfig.class)
public class HomeIT {

    @Autowired
    WebDriver webDriver;

    @Value("${integration.test.base_url}")
    String baseUrl;

    @Test
    public void theHeaderDropdown() throws Exception {
        webDriver.get(baseUrl + "/logout.do");

        webDriver.get(baseUrl + "/login");
        webDriver.findElement(By.name("username")).sendKeys("marissa");
        webDriver.findElement(By.name("password")).sendKeys("koala");
        webDriver.findElement(By.xpath("//input[@value='Sign in']")).click();

        HomePagePerspective asOnHomePage = new HomePagePerspective(webDriver, "marissa");
        Assert.assertNotNull(asOnHomePage.getUsernameElement());
        Assert.assertFalse(asOnHomePage.getAccountSettingsElement().isDisplayed());
        Assert.assertFalse(asOnHomePage.getSignOutElement().isDisplayed());

        asOnHomePage.getUsernameElement().click();

        Assert.assertTrue(asOnHomePage.getAccountSettingsElement().isDisplayed());
        Assert.assertTrue(asOnHomePage.getSignOutElement().isDisplayed());

        asOnHomePage.getAccountSettingsElement().click();

        Assert.assertThat(webDriver.findElement(By.cssSelector("h1")).getText(), Matchers.containsString("Account Settings"));
    }

    static class HomePagePerspective {
        private final WebDriver webDriver;
        private final String username;

        public HomePagePerspective(WebDriver webDriver, String username) {
            this.webDriver = webDriver;
            this.username = username;
        }

        public WebElement getUsernameElement() {
            return getWebElementWithText(username);
        }

        public WebElement getAccountSettingsElement() {
            return getWebElementWithText("Account Settings");
        }

        public WebElement getSignOutElement() {
            return getWebElementWithText("Sign Out");
        }

        private WebElement getWebElementWithText(String text) {
            return webDriver.findElement(By.xpath("//*[text()='" + text + "']"));
        }
    }
}
