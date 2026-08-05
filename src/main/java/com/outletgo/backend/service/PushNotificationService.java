package com.outletgo.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

@Service
public class PushNotificationService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Async
    public void sendPushNotification(String pushToken, String title, String body, Map<String, Object> data) {
        if (pushToken == null || pushToken.trim().isEmpty()) {
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("to", pushToken);
            payload.put("sound", "default");
            payload.put("title", title);
            payload.put("body", body);
            payload.put("channelId", "default");
            payload.put("priority", "high");
            payload.put("badge", 1);
            if (data != null) {
                payload.put("data", data);
            }

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EXPO_PUSH_URL))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Accept-Encoding", "gzip, deflate")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            System.err.println("Expo Push Notification failed: " + response.body());
                        } else {
                            System.out.println("Expo Push Notification sent successfully: " + response.body());
                        }
                    });

        } catch (Exception e) {
            System.err.println("Error triggering push notification: " + e.getMessage());
        }
    }
}
