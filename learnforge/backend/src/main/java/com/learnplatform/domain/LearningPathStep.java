package com.learnplatform.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "learning_path_step")
@Getter
@Setter
public class LearningPathStep {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "path_id") private UUID pathId;
    @Column(name = "sort_order") private int sortOrder;
    private String type;
    @Column(name = "ref_id") private UUID refId;
    private String label;
    @Transient private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
