package com.learnplatform.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "voice_assessment")
@Getter
@Setter
public class VoiceAssessment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id") private UUID orgId;
    @Column(name = "course_id") private UUID courseId;
    @Column(name = "user_id") private UUID userId;
    @Column(columnDefinition = "text") private String question;
    @Column(columnDefinition = "text") private String answer;
    private int score;
    @Column(name = "feedback_json", columnDefinition = "text") private String feedbackJson;
    @Column(name = "created_at") private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
