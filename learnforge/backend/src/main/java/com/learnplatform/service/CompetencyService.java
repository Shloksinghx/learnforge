package com.learnplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.domain.Simulation;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Averages the seven simulation competency scores. */
@Service
public class CompetencyService {
    public static final Map<String, String> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put("productKnowledge", "Product knowledge");
        LABELS.put("communication", "Communication");
        LABELS.put("questioning", "Questioning");
        LABELS.put("objectionHandling", "Objection handling");
        LABELS.put("accuracy", "Accuracy");
        LABELS.put("compliance", "Compliance");
        LABELS.put("confidence", "Confidence");
    }

    private final ObjectMapper mapper;

    public CompetencyService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public JsonNode scores(Simulation s) {
        try {
            return mapper.readTree(s.getEvaluationJson()).path("scores");
        } catch (Exception e) {
            return mapper.missingNode();
        }
    }

    /** Average per competency; a key is missing when there is no data for it. */
    public Map<String, Integer> averages(Collection<Simulation> sims) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String key : LABELS.keySet()) {
            int sum = 0;
            int n = 0;
            for (Simulation s : sims) {
                JsonNode v = scores(s).path(key);
                if (v.isNumber()) {
                    sum += v.asInt();
                    n++;
                }
            }
            if (n > 0) out.put(key, Math.round((float) sum / n));
        }
        return out;
    }
}
