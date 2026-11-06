package com.learnplatform.web;

import com.learnplatform.ai.AiClient;
import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.Course;
import com.learnplatform.domain.CourseDocument;
import com.learnplatform.domain.Lesson;
import com.learnplatform.repo.CourseDocumentRepository;
import com.learnplatform.repo.CourseRepository;
import com.learnplatform.service.ContentStructuringService;
import com.learnplatform.service.DocumentTextExtractor;
import com.learnplatform.service.ScormImportService;
import com.learnplatform.service.WebPageFetcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Content engine: documents, audio, video, images, web pages and SCORM packages become structured lessons. Admin only. */
@RestController
public class ContentController {
    public record UrlReq(@NotBlank @Size(max = 500) String url) {}
    public record DocView(UUID id, String filename, String kind, long sizeBytes, int lessonsCreated, Instant uploadedAt) {}

    private static final int MAX_MEDIA_BYTES = 14 * 1024 * 1024; // inline AI requests are limited to about 20 MB once encoded
    private static final Map<String, String> MEDIA = Map.ofEntries(
            Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"), Map.entry("m4a", "audio/mp4"),
            Map.entry("aac", "audio/aac"), Map.entry("ogg", "audio/ogg"), Map.entry("flac", "audio/flac"),
            Map.entry("mp4", "video/mp4"), Map.entry("mov", "video/quicktime"), Map.entry("webm", "video/webm"),
            Map.entry("mpeg", "video/mpeg"), Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"), Map.entry("webp", "image/webp"));
    private static final String MEDIA_INSTRUCTION = "Extract everything a learner could be taught from this file. "
            + "For audio or video, transcribe all speech accurately and include important on-screen text or slides. "
            + "For images and scanned pages, read all text and describe diagrams or charts in words. "
            + "Output plain text only, with no commentary.";

    private final CourseRepository courses;
    private final CourseDocumentRepository documents;
    private final DocumentTextExtractor extractor;
    private final ContentStructuringService structuring;
    private final ScormImportService scorm;
    private final WebPageFetcher fetcher;
    private final AiClient ai;

    public ContentController(CourseRepository courses, CourseDocumentRepository documents, DocumentTextExtractor extractor,
                             ContentStructuringService structuring, ScormImportService scorm, WebPageFetcher fetcher,
                             AiClient ai) {
        this.courses = courses;
        this.documents = documents;
        this.extractor = extractor;
        this.structuring = structuring;
        this.scorm = scorm;
        this.fetcher = fetcher;
        this.ai = ai;
    }

    @PostMapping(value = "/api/courses/{id}/content/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@PathVariable UUID id, @RequestParam("file") MultipartFile file) throws IOException {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Course c = Access.course(courses, me, id);
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty");
        String name = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        byte[] data = file.getBytes();

        String kind;
        String text;
        if (MEDIA.containsKey(ext)) {
            if (data.length > MAX_MEDIA_BYTES) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio, video and image files can be up to 14 MB");
            }
            kind = "MEDIA";
            text = ai.describeMedia(data, MEDIA.get(ext), MEDIA_INSTRUCTION);
        } else if (ext.equals("zip")) {
            kind = "SCORM";
            text = scorm.extractText(data);
        } else {
            kind = "DOCUMENT";
            try {
                text = extractor.extract(name, data);
            } catch (ResponseStatusException e) {
                // scanned PDFs have no text layer: let the AI read the pages instead
                if (ext.equals("pdf") && DocumentTextExtractor.NO_TEXT.equals(e.getReason()) && data.length <= MAX_MEDIA_BYTES) {
                    text = ai.describeMedia(data, "application/pdf", MEDIA_INSTRUCTION);
                } else {
                    throw e;
                }
            }
        }
        return finish(c, me, name, kind, data.length, text);
    }

    @PostMapping("/api/courses/{id}/content/url")
    public Map<String, Object> fromUrl(@PathVariable UUID id, @Valid @RequestBody UrlReq req) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Course c = Access.course(courses, me, id);
        String text = fetcher.fetchText(req.url());
        if (text.trim().length() < 40) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No readable text found on that page");
        return finish(c, me, req.url(), "WEB", text.length(), text);
    }

    @GetMapping("/api/courses/{id}/documents")
    public List<DocView> list(@PathVariable UUID id) {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Course c = Access.course(courses, me, id);
        return documents.findByCourseIdOrderByCreatedAtDesc(c.getId()).stream()
                .map(d -> new DocView(d.getId(), d.getFilename(), d.getKind(), d.getSizeBytes(), d.getLessonsCreated(), d.getCreatedAt()))
                .toList();
    }

    private Map<String, Object> finish(Course c, AuthUser me, String name, String kind, long size, String text) {
        List<Lesson> created = structuring.structure(c, text, name);
        CourseDocument d = new CourseDocument();
        d.setCourseId(c.getId());
        d.setFilename(name.length() > 300 ? name.substring(0, 300) : name);
        d.setKind(kind);
        d.setSizeBytes(size);
        d.setLessonsCreated(created.size());
        d.setUploadedBy(me.userId());
        documents.save(d);
        return Map.of("created", created.size(), "titles", created.stream().map(Lesson::getTitle).toList());
    }
}
