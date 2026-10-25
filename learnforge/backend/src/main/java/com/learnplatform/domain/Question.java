package com.learnplatform.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "question")
@Getter
@Setter
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id") private UUID orgId;
    @Column(name = "course_id") private UUID courseId;
    @Column(columnDefinition = "text") private String text;
    @Column(name = "options_json", columnDefinition = "text") private String optionsJson;
    @Column(name = "correct_index") private int correctIndex;
    @Column(columnDefinition = "text") private String explanation;
    private String difficulty;
    private String status = "DRAFT";
    @Column(name = "created_at") private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
