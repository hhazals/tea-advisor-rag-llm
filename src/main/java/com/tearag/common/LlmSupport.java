package com.tearag.common;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Gemini erişimi: anahtar tanımlı mı ve ChatClient üretimi (RAG ve yemek metni ayrıştırma ortak kullanır). */
@Component
public class LlmSupport {

    /** application.yml'deki yer tutucu: GEMINI_API_KEY tanımlı değil (Google istemcisi anahtarsız başlamıyor). */
    public static final String NOT_CONFIGURED = "not-configured";

    private final ObjectProvider<ChatClient.Builder> builder;
    private final boolean configured;

    public LlmSupport(ObjectProvider<ChatClient.Builder> builder,
                      @Value("${spring.ai.google.genai.api-key:}") String apiKey) {
        this.builder = builder;
        this.configured = !apiKey.isBlank() && !NOT_CONFIGURED.equals(apiKey);
    }

    public boolean isConfigured() {
        return configured && builder.getIfAvailable() != null;
    }

    public ChatClient client() {
        ChatClient.Builder b = builder.getIfAvailable();
        if (b == null) {
            throw new IllegalStateException("LLM yapılandırılmamış");
        }
        return b.build();
    }
}
