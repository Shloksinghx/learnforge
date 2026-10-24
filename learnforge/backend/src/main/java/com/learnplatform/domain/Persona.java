package com.learnplatform.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "persona")
@Getter
@Setter
public class Persona {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "org_id") private UUID orgId;
    private String name;
    private String profession;
    private Integer age;
    private String personality;
    private String communication;
    @Column(name = "knowledge_level") private String knowledgeLevel;
    @Column(columnDefinition = "text") private String behaviour;
    @Column(columnDefinition = "text") private String objections;
    @Column(columnDefinition = "text") private String scenario;
    @Column(name = "mandatory_info", columnDefinition = "text") private String mandatoryInfo;
    @Column(name = "prohibited_statements", columnDefinition = "text") private String prohibitedStatements;
    @Column(name = "created_at") private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
