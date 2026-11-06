package com.learnplatform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnplatform.service.UsageService;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GeminiAiClient implements AiClient {
    private final RestClient http;
    private final String model;
    private final String ttsModel;
    private final String voice;
    private final UsageService usage;

    public GeminiAiClient(@Value("${ai.gemini.api-key}") String apiKey,
                          @Value("${ai.gemini.base-url}") String baseUrl,
                          @Value("${ai.gemini.model}") String model,
                          @Value("${ai.gemini.tts-model:gemini-2.5-flash-preview-tts}") String ttsModel,
                          @Value("${ai.gemini.voice:Kore}") String voice,
                          UsageService usage) {
        this.http = RestClient.builder().baseUrl(baseUrl).defaultHeader("x-goog-api-key", apiKey).build();
        this.model = model;
        this.ttsModel = ttsModel;
        this.voice = voice;
        this.usage = usage;
    }

    @Override
    public String chat(String systemPrompt, List<ChatMessage> history, boolean jsonOutput) {
        usage.consumeAiCall();
        Map<String, Object> body = new HashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        body.put("contents", history.stream()
                .map(m -> Map.of("role", m.role(), "parts", List.of(Map.of("text", m.text()))))
                .toList());
        if (jsonOutput) {
            body.put("generationConfig", Map.of("responseMimeType", "application/json"));
        }
        return text(call(model, body));
    }

    @Override
    public String describeMedia(byte[] data, String mimeType, String instruction) {
        usage.consumeAiCall();
        Map<String, Object> inline = Map.of("mimeType", mimeType, "data", Base64.getEncoder().encodeToString(data));
        Map<String, Object> body = Map.of("contents", List.of(Map.of("role", "user",
                "parts", List.of(Map.of("inlineData", inline), Map.of("text", instruction)))));
        return text(call(model, body));
    }

    @Override
    public byte[] speak(String text) {
        usage.consumeAiCall();
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", text)))),
                "generationConfig", Map.of(
                        "responseModalities", List.of("AUDIO"),
                        "speechConfig", Map.of("voiceConfig", Map.of("prebuiltVoiceConfig", Map.of("voiceName", voice)))));
        JsonNode res = call(ttsModel, body);
        String b64 = res.path("candidates").path(0).path("content").path("parts").path(0).path("inlineData").path("data").asText("");
        if (b64.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI voice returned no audio");
        return wav(Base64.getDecoder().decode(b64), 24000);
    }

    private JsonNode call(String modelName, Map<String, Object> body) {
        try {
            return http.post()
                    .uri("/models/{m}:generateContent", modelName)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI provider error");
        }
    }

    private static String text(JsonNode res) {
        String text = res.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        if (text.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI returned an empty response");
        return text;
    }

    /** Wraps raw 16-bit mono PCM in a WAV header so browsers can play it. */
    private static byte[] wav(byte[] pcm, int sampleRate) {
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length)
                .put("WAVE".getBytes(StandardCharsets.US_ASCII)).put("fmt ".getBytes(StandardCharsets.US_ASCII))
                .putInt(16).putShort((short) 1).putShort((short) 1).putInt(sampleRate)
                .putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + pcm.length);
        out.writeBytes(h.array());
        out.writeBytes(pcm);
        return out.toByteArray();
    }
}
