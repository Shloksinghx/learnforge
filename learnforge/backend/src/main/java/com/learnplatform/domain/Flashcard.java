package com.learnplatform.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "flashcard")
@Getter
@Setter
public class Flashcard {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "course_id") private UUID courseId;
    @Column(columnDefinition = "text") private String front;
    @Column(columnDefinition = "text") private String back;
    @Column(name = "lesson_title") private String lessonTitle;
    @Column(name = "sort_order") private int sortOrder;
    @Column(name = "created_at") private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
