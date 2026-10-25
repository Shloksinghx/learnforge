package com.learnplatform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class GeminiAiClient implements AiClient {
    private final RestClient http;
    private final String model;

    public GeminiAiClient(@Value("${ai.gemini.api-key}") String apiKey,
                          @Value("${ai.gemini.base-url}") String baseUrl,
                          @Value("${ai.gemini.model}") String model) {
        this.http = RestClient.builder().baseUrl(baseUrl).defaultHeader("x-goog-api-key", apiKey).build();
        this.model = model;
    }

    @Override
    public String chat(String systemPrompt, List<ChatMessage> history, boolean jsonOutput) {
        Map<String, Object> body = new HashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        body.put("contents", history.stream()
                .map(m -> Map.of("role", m.role(), "parts", List.of(Map.of("text", m.text()))))
                .toList());
        if (jsonOutput) {
            body.put("generationConfig", Map.of("responseMimeType", "application/json"));
        }
        try {
            JsonNode res = http.post()
                    .uri("/models/{m}:generateContent", model)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String text = res.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            if (text.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI returned an empty response");
            }
            return text;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI provider error");
        }
    }
}
