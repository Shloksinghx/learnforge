package com.learnplatform.ai;

import java.util.List;

/** Provider-agnostic LLM port. Swap the implementation without touching business code. */
public interface AiClient {
    String chat(String systemPrompt, List<ChatMessage> history, boolean jsonOutput);
}
