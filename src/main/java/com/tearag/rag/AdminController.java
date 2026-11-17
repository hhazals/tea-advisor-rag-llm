package com.tearag.rag;

import com.tearag.common.ApiException;
import com.tearag.ingestion.DocumentIngestionService;
import com.tearag.ingestion.DocumentIngestionService.SourceReport;
import com.tearag.rag.EmbeddingIngestionService.Result;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Yönetim uç noktaları. Uygulamada kimlik doğrulama yok; yalnızca yerelde çalıştırılmak içindir. */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final EmbeddingIngestionService embedding;
    private final DocumentIngestionService documents;

    public AdminController(EmbeddingIngestionService embedding, DocumentIngestionService documents) {
        this.embedding = embedding;
        this.documents = documents;
    }

    /** Boş embedding'leri doldurur; ?force=true tümünü yeniden üretir. */
    @PostMapping("/embed")
    public Result embed(@RequestParam(defaultValue = "false") boolean force) {
        return embedding.embed(force);
    }

    public record IngestResult(List<SourceReport> sources, int totalChunks, Result embedding, String embeddingError) {
    }

    /**
     * Kaynak belgeleri indirir, temizler, parçalar ve embed eder (birkaç dakika sürebilir).
     * Embedding başarısız olursa (Ollama kapalı) parçalar yine kaydedilir; sonra POST /admin/embed çalıştırılır.
     */
    @PostMapping("/ingest")
    public IngestResult ingest() {
        List<SourceReport> reports = documents.ingestAll();
        int total = reports.stream().mapToInt(SourceReport::chunks).sum();
        try {
            return new IngestResult(reports, total, embedding.embed(false), null);
        } catch (ApiException e) {
            return new IngestResult(reports, total, null, e.getMessage());
        }
    }
}
