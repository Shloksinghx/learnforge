package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.Course;
import com.learnplatform.domain.Lesson;
import com.learnplatform.repo.CourseRepository;
import com.learnplatform.service.ContentStructuringService;
import com.learnplatform.service.DocumentTextExtractor;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Content engine: upload a document and let the AI turn it into structured lessons. Admin only. */
@RestController
public class ContentController {
    private final CourseRepository courses;
    private final DocumentTextExtractor extractor;
    private final ContentStructuringService structuring;

    public ContentController(CourseRepository courses, DocumentTextExtractor extractor,
                             ContentStructuringService structuring) {
        this.courses = courses;
        this.extractor = extractor;
        this.structuring = structuring;
    }

    @PostMapping(value = "/api/courses/{id}/content/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@PathVariable UUID id, @RequestParam("file") MultipartFile file) throws IOException {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Course c = Access.course(courses, me, id);
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty");
        String text = extractor.extract(file.getOriginalFilename(), file.getBytes());
        List<Lesson> created = structuring.structure(c, text, String.valueOf(file.getOriginalFilename()));
        return Map.of("created", created.size(), "titles", created.stream().map(Lesson::getTitle).toList());
    }
}
