package com.learnplatform.ai;

/** role is "user" or "model" (Gemini naming). */
public record ChatMessage(String role, String text) {}
