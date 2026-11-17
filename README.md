# Evening Drink

A small app that suggests an evening herbal tea, kefir or kombucha (such hazal things) based on what you ate during the day or typed in text area.

The goal is to try few products/tools together and having a free-text + lmm:

- **Java 21 + Spring Boot** – REST API and a web UI
- **Docker** (very simplified) – PostgreSQL + pgvector
- **LLM (Gemini)** – parses free-text user entries and writes the explanation for the suggestions
- **Simple RAG** – embeddings with Ollama, similarity search with pgvector; the LLM writes only from the retrieved source passages 
- **External API** – calorie data from USDA FoodData Central

Ollama, Gemini and USDA FoodData Central were chosen because they are free to use (Ollama runs locally, Gemini and USDA have free API tiers), not because they are the best fit for the task.

## Running
Requirements: Java 21, Docker, [Ollama](https://ollama.com/download)

1. Pull the embedding model:
   ```
   ollama pull paraphrase-multilingual
   ```
2. Copy `.env.example` to `.env` and add your keys:
   - `GEMINI_API_KEY` – https://aistudio.google.com/apikey
   - `USDA_API_KEY` – https://fdc.nal.usda.gov/api-key-signup (optional)
3. Start the database and the app:
   ```
   docker compose up -d
   ./mvnw spring-boot:run        (Windows: .\mvnw.cmd spring-boot:run)
   ```
4. On first run, generate the embeddings once:
   ```
   curl -X POST http://localhost:8080/admin/embed        (Windows PowerShell: curl.exe ...)
   ```
5. Open http://localhost:8080

If the default Gemini model isn't available on your account, set `GEMINI_MODEL=...` in `.env`.