package com.learnplatform.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "course_document")
@Getter
@Setter
public class CourseDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "course_id") private UUID courseId;
    private String filename;
    private String kind;
    @Column(name = "size_bytes") private long sizeBytes;
    @Column(name = "lessons_created") private int lessonsCreated;
    @Column(name = "uploaded_by") private UUID uploadedBy;
    @Column(name = "created_at") private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
