package com.learnplatform.ai;

import java.util.List;

/** Provider-agnostic AI port. Swap the implementation without touching business code. */
public interface AiClient {
    String chat(String systemPrompt, List<ChatMessage> history, boolean jsonOutput);

    /** Reads speech, text or visuals out of an audio, video, image or PDF file. */
    String describeMedia(byte[] data, String mimeType, String instruction);

    /** Text to speech. Returns a WAV file. */
    byte[] speak(String text);
}
