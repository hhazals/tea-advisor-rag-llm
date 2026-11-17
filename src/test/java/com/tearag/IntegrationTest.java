package com.tearag;

import com.tearag.ingestion.SourceFetcher;
import com.tearag.nutrition.CalorieLookup;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Gerçek USDA API'si testlerde çağrılmaz; CalorieLookup varsayılan olarak boş döner.
 */
@SpringBootTest(properties = "spring.ai.google.genai.api-key=test-key") // gerçek anahtar değil
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @MockitoBean
    protected CalorieLookup calorieLookup;

    @MockitoBean
    protected EmbeddingModel embeddingModel;

    @MockitoBean
    protected ChatModel chatModel;

    @MockitoBean
    protected SourceFetcher sourceFetcher;
}
