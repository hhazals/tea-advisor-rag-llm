package com.tearag.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * tearag.rag.* ayarları.
 *
 * @param similarityThreshold en iyi parçanın kosinüs benzerliği bunun altındaysa kural tabanlı öneriye dönülür
 * @param topK                LLM'e verilecek en fazla parça sayısı
 */
@ConfigurationProperties("tearag.rag")
public record RagProperties(double similarityThreshold, int topK) {
}
