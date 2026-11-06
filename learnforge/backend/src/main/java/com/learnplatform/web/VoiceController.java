package com.learnplatform.web;

import com.learnplatform.ai.AiClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Server-side voice (beta): speech to text and text to speech through the AI provider. */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {
    public record SpeakReq(@NotBlank @Size(max = 1500) String text) {}

    private static final int MAX_AUDIO_BYTES = 8 * 1024 * 1024;
    private final AiClient ai;

    public VoiceController(AiClient ai) {
        this.ai = ai;
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> transcribe(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_AUDIO_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Record a shorter clip (under 8 MB)");
        }
        String mime = file.getContentType() == null || file.getContentType().isBlank() ? "audio/wav" : file.getContentType();
        String text = ai.describeMedia(file.getBytes(), mime,
                "Transcribe this speech exactly as spoken, in the original language. Output only the transcript, with no commentary. "
                        + "If there is no speech, output an empty line.");
        return Map.of("text", text.trim());
    }

    @PostMapping(value = "/speak", produces = "audio/wav")
    public ResponseEntity<byte[]> speak(@Valid @RequestBody SpeakReq req) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav")).body(ai.speak(req.text()));
    }
}
