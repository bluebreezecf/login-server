package org.cloudfoundry.identity.uaa.login;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.identity.uaa.error.UaaException;
import org.cloudfoundry.identity.uaa.login.AccountCreationService.ExistingUserResponse;
import org.cloudfoundry.identity.uaa.message.PasswordChangeRequest;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring4.SpringTemplateEngine;

import javax.mail.MessagingException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.cloudfoundry.identity.uaa.login.ExpiringCodeService.*;

public class EmailInvitationsService implements InvitationsService {
    private final Log logger = LogFactory.getLog(getClass());

    private final SpringTemplateEngine templateEngine;
    private final EmailService emailService;
    private final String uaaBaseUrl;

    private String brand;

    public EmailInvitationsService(SpringTemplateEngine templateEngine, EmailService emailService, String brand, String uaaBaseUrl) {
        this.templateEngine = templateEngine;
        this.emailService = emailService;
        this.brand = brand;
        this.uaaBaseUrl = uaaBaseUrl;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }
    
    @Autowired
    private AccountCreationService accountCreationService;

    @Autowired
    private ExpiringCodeService expiringCodeService;

    @Autowired
    private RestTemplate authorizationTemplate;

    private void sendInvitationEmail(String email, String currentUser, String code) {
        String subject = getSubjectText();
        try {
            String htmlContent = getEmailHtml(email, currentUser, code);
            try {
                emailService.sendMimeMessage(email, subject, htmlContent);
            } catch (MessagingException e) {
                logger.error("Exception raised while sending message to " + email, e);
            } catch (UnsupportedEncodingException e) {
                logger.error("Exception raised while sending message to " + email, e);
            }
        } catch (RestClientException e) {
            logger.info("Exception raised while creating invitation email from " + email, e);
        }
    }

    private String getSubjectText() {
        return brand.equals("pivotal") ? "Invitation to join Pivotal" : "Invitation to join Cloud Foundry";
    }

    private String getEmailHtml(String email, String currentUser, String code) {
        String accountsUrl = ServletUriComponentsBuilder.fromCurrentContextPath().path("/invitations/accept").build().toUriString();
        final Context ctx = new Context();
        ctx.setVariable("serviceName", brand.equals("pivotal") ? "Pivotal" : "Cloud Foundry");
        ctx.setVariable("email", email);
        ctx.setVariable("code", code);
        ctx.setVariable("currentUser", currentUser);
        ctx.setVariable("accountsUrl", accountsUrl);
        return templateEngine.process("invite", ctx);
    }

    @Override
    public void inviteUser(String email, String currentUser) {
        try {
            ScimUser user = accountCreationService.createUser(email, null);
            Map<String,String> data = new HashMap<>();
            data.put("user_id", user.getId());
            String code = expiringCodeService.generateCode(data, 30, TimeUnit.DAYS);
            sendInvitationEmail(email, currentUser, code);
        } catch (HttpClientErrorException e) {
            String uaaResponse = e.getResponseBodyAsString();
            try {
                ExistingUserResponse existingUserResponse = new ObjectMapper().readValue(uaaResponse, ExistingUserResponse.class);
                if (existingUserResponse.getVerified()) {
                    throw new UaaException(e.getStatusText(), e.getStatusCode().value());
                }
                Map<String,String> data = new HashMap<>();
                data.put("user_id", existingUserResponse.getUserId());
                String code = expiringCodeService.generateCode(data, 30, TimeUnit.DAYS);
                sendInvitationEmail(email, currentUser, code);
            } catch (IOException ioe) {
            	logger.warn("couldn't invite user",ioe);
            }
        } catch (IOException e) {
            logger.warn("couldn't invite user",e);
        }
    }

    @Override
    public InvitationAcceptanceResponse acceptInvitation(String email, String password, String code) throws CodeNotFoundException, IOException {
        Map<String,String> codeData = expiringCodeService.verifyCode(code);
        ScimUser user = authorizationTemplate.getForObject(uaaBaseUrl + "/Users/" + codeData.get("user_id") + "/verify", ScimUser.class);

        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setPassword(password);
        authorizationTemplate.put(uaaBaseUrl + "/Users/" + codeData.get("user_id") + "/password", request);
        InvitationAcceptanceResponse response = new InvitationAcceptanceResponse(codeData.get("user_id"), user.getUserName(), user.getPrimaryEmail());
        return response;
    }
}
