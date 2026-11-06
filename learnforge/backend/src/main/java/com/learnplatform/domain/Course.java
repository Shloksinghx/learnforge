package com.learnplatform.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "course")
@Getter
@Setter
public class Course {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "org_id") private UUID orgId;
    private String title;
    @Column(columnDefinition = "text") private String description;
    private String category;
    private String status = "DRAFT";
    @Column(name = "created_at") private Instant createdAt;

    private boolean shared;
    @Column(name = "certificate_title") private String certificateTitle;
    @Column(name = "pass_mark") private int passMark = 60;
    @Column(name = "exam_count") private int examCount = 20;
    @Column(name = "exam_minutes") private int examMinutes = 30;
    @Column(name = "requires_simulation") private boolean requiresSimulation;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
