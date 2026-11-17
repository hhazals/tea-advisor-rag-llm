package com.tearag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywayMigrationTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationsApplyAndSeedDataIsLoaded() {
        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        assertThat(applied).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM source_drinks", Integer.class)).isEqualTo(22);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_chunks WHERE origin = 'curated'",
                Integer.class)).isEqualTo(21);
        assertThat(jdbc.queryForObject("""
                SELECT format_type(atttypid, atttypmod) FROM pg_attribute
                WHERE attrelid = 'knowledge_chunks'::regclass AND attname = 'embedding'""", String.class))
                .isEqualTo("vector(768)");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM drinks", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sources", Integer.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM food_categories", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM knowledge_chunks", Integer.class)).isEqualTo(21);

        // Türkçe karakterler
        assertThat(jdbc.queryForObject("SELECT name_tr FROM drinks WHERE slug = 'ginger'", String.class))
                .isEqualTo("Zencefil");
        assertThat(jdbc.queryForObject("SELECT name_tr FROM food_categories WHERE slug = 'chicken'", String.class))
                .isEqualTo("Tavuk yemeği");
    }
}
