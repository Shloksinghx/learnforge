package com.learnplatform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.domain.Question;
import java.util.HashSet;
import java.util.List;

/** Scoring rules shared by every test type. Multiple-response questions are all-or-nothing. */
public final class QuestionScoring {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private QuestionScoring() {}

    public static List<Integer> correctSet(Question q) {
        if (q.getCorrectJson() != null && !q.getCorrectJson().isBlank()) {
            try {
                return MAPPER.readValue(q.getCorrectJson(), new TypeReference<List<Integer>>() {});
            } catch (Exception ignored) {
                // fall through to the single answer
            }
        }
        return List.of(q.getCorrectIndex());
    }

    public static boolean isCorrect(Question q, List<Integer> selected) {
        return selected != null && !selected.isEmpty() && new HashSet<>(selected).equals(new HashSet<>(correctSet(q)));
    }

    public static String toJson(List<Integer> values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (Exception e) {
            return null;
        }
    }

    public static List<Integer> parse(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<Integer>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
