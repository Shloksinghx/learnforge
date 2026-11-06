package com.learnplatform.web;

import com.learnplatform.config.AuthUser;
import com.learnplatform.config.CurrentUser;
import com.learnplatform.domain.Course;
import com.learnplatform.repo.CourseRepository;
import com.learnplatform.service.QuestionImportService;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Bring an existing question bank in from CSV or Excel. Admin only; imports arrive as drafts. */
@RestController
public class QuestionImportController {
    private final CourseRepository courses;
    private final QuestionImportService importer;

    public QuestionImportController(CourseRepository courses, QuestionImportService importer) {
        this.courses = courses;
        this.importer = importer;
    }

    @PostMapping(value = "/api/courses/{id}/questions/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public QuestionImportService.Result importQuestions(@PathVariable UUID id, @RequestParam("file") MultipartFile file)
            throws IOException {
        AuthUser me = CurrentUser.get();
        Access.requireAuthor(me);
        Course c = Access.course(courses, me, id);
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The file is empty");
        return importer.importFile(c, file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping("/api/questions/import-template.csv")
    public ResponseEntity<String> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"question-template.csv\"")
                .body(QuestionImportService.TEMPLATE);
    }
}
