package org.cloudfoundry.identity.uaa.login;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class NotificationsServiceTest {

    private RestTemplate notificationsTemplate;
    private MockEnvironment environment;
    private MockRestServiceServer mockNotificationsServer;
    private RestTemplate uaaTemplate;
    private Map<MessageType, HashMap<String, Object>> notifications;
    private NotificationsService notificationsService;
    private Map<String, Object> response;

    @Before
    public void setUp(){
        notificationsTemplate = new RestTemplate();
        mockNotificationsServer = MockRestServiceServer.createServer(notificationsTemplate);

        uaaTemplate = Mockito.mock(RestTemplate.class);
        notifications = new HashMap<>();
        HashMap<String, Object> passwordResetNotification = new HashMap<>();

        passwordResetNotification.put("id", "kind-id-01");
        passwordResetNotification.put("description", "password reset");
        notifications.put(MessageType.PASSWORD_RESET, passwordResetNotification);

        environment = new MockEnvironment();
        environment.setProperty("notifications.url", "http://notifications.example.com/notifications");

        notificationsService = new NotificationsService(notificationsTemplate, "http://notifications.example.com", notifications, uaaTemplate, "http://uaa.com");

        response = new HashMap<>();
        List<Map<String, String>> resources = new ArrayList<>();
        Map<String,String> userDetails = new HashMap<>();
        userDetails.put("id", "user-id-01");
        resources.add(userDetails);
        response.put("resources", resources);
    }

    @Test
    public void testSendMessage() throws Exception {
        when(uaaTemplate.getForObject("http://uaa.com/ids/Users?attributes=id&filter=userName eq \"user@example.com\"", Map.class)).thenReturn(response);

        mockNotificationsServer.expect(requestTo("http://notifications.example.com/registration"))
            .andExpect(method(PUT))
            .andExpect(jsonPath("$.source_description").value("CF_Identity"))
            .andExpect(jsonPath("$.kinds[0].id").value("kind-id-01"))
            .andExpect(jsonPath("$.kinds[0].description").value("password reset"))
            .andRespond(withSuccess());

        mockNotificationsServer.expect(requestTo("http://notifications.example.com/users/user-id-01"))
            .andExpect(method(POST))
            .andExpect(jsonPath("$.kind_id").value("kind-id-01"))
            .andExpect(jsonPath("$.subject").value("First message"))
            .andExpect(jsonPath("$.text").value("<p>Text</p>"))
            .andRespond(withSuccess());

        mockNotificationsServer.expect(requestTo("http://notifications.example.com/users/user-id-01"))
            .andExpect(method(POST))
            .andExpect(jsonPath("$.subject").value("Second message"))
            .andRespond(withSuccess());

        notificationsService.sendMessage("user@example.com", MessageType.PASSWORD_RESET, "First message", "<p>Text</p>");

        assertTrue(notificationsService.getIsNotificationsRegistered());

        notificationsService.sendMessage("user@example.com", MessageType.PASSWORD_RESET, "Second message", "<p>Text</p>");

        mockNotificationsServer.verify();
    }
}
