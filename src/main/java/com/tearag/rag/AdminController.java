package com.tearag.rag;

import com.tearag.rag.EmbeddingIngestionService.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Yönetim uç noktaları. Uygulamada kimlik doğrulama yok; yalnızca yerelde çalıştırılmak içindir. */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final EmbeddingIngestionService ingestion;

    public AdminController(EmbeddingIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    /** Boş embedding'leri doldurur; ?force=true tümünü yeniden üretir. */
    @PostMapping("/embed")
    public Result embed(@RequestParam(defaultValue = "false") boolean force) {
        return ingestion.embed(force);
    }
}
