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

import static org.hamcrest.Matchers.containsString;

import org.cloudfoundry.identity.uaa.login.test.DefaultIntegrationTestConfig;
import org.cloudfoundry.identity.uaa.login.test.LoginServerClassRunner;
import org.cloudfoundry.identity.uaa.login.test.TestClient;
import org.cloudfoundry.identity.uaa.login.test.UnlessProfileActive;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;

import com.dumbster.smtp.SimpleSmtpServer;
import java.security.SecureRandom;

@RunWith(LoginServerClassRunner.class)
@ContextConfiguration(classes = DefaultIntegrationTestConfig.class)
@UnlessProfileActive(values = {"saml", "ldap"})
public class ChangePasswordIT {

    @Autowired
    WebDriver webDriver;

    @Autowired
    SimpleSmtpServer simpleSmtpServer;

    @Autowired
    TestClient testClient;

    @Autowired
    RestTemplate restTemplate;

    @Value("${integration.test.base_url}")
    String baseUrl;
    
    private String userEmail;
    private String userName;

    @Before
    public void setUp() throws Exception {
        int randomInt = new SecureRandom().nextInt();

        String adminAccessToken = testClient.getOAuthAccessToken("admin", "adminsecret", "client_credentials", "clients.read clients.write clients.secret");

        String scimClientId = "scim" + randomInt;
        testClient.createScimClient(adminAccessToken, scimClientId);

        String scimAccessToken = testClient.getOAuthAccessToken(scimClientId, "scimsecret", "client_credentials", "scim.read scim.write password.write");

        userEmail = "user" + randomInt + "@example.com";
        userName = "JOE" + randomInt;
        testClient.createUser(scimAccessToken, userName, userEmail, "secret");
    }

    @Test
    public void testChangePassword() throws Exception {
        signIn(userName, "secret");

        changePassword("secret", "newsecret", "new");
        WebElement errorMessage = webDriver.findElement(By.className("error-message"));
        Assert.assertTrue(errorMessage.isDisplayed());
        Assert.assertEquals("Passwords must match and not be empty", errorMessage.getText());

        changePassword("secret", "newsecret", "newsecret");
        signOut();

        signIn(userName, "newsecret");
    }

    private void changePassword(String originalPassword, String newPassword, String confirmPassword) {
        webDriver.findElement(By.xpath("//*[text()='"+userName+"']")).click();
        webDriver.findElement(By.linkText("Account Settings")).click();
        webDriver.findElement(By.linkText("Change Password")).click();
        webDriver.findElement(By.name("current_password")).sendKeys(originalPassword);
        webDriver.findElement(By.name("new_password")).sendKeys(newPassword);
        webDriver.findElement(By.name("confirm_password")).sendKeys(confirmPassword);

        webDriver.findElement(By.xpath("//input[@value='Change password']")).click();
    }

    private void signOut() {
        webDriver.findElement(By.xpath("//*[text()='"+userName+"']")).click();
        webDriver.findElement(By.linkText("Sign Out")).click();
    }

    private void signIn(String userName, String password) {
        webDriver.get(baseUrl + "/login");
        webDriver.findElement(By.name("username")).sendKeys(userName);
        webDriver.findElement(By.name("password")).sendKeys(password);
        webDriver.findElement(By.xpath("//input[@value='Sign in']")).click();
        Assert.assertThat(webDriver.findElement(By.cssSelector("h1")).getText(), containsString("Where to?"));
    }
}
