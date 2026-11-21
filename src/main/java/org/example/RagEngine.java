package org.example;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

    public class RagEngine {
        private static final Logger log = LoggerFactory.getLogger(RagEngine.class);

        interface Assistant {
            String chat(String question);
        }


        public static void main(String[] args) {
            Path rpgFolder = Paths.get("C:/Users/gaina/Documents/javaProjects/ibmi-rag-modernizer/target/classes/RPG");
            URL url = RagEngine.class.getClassLoader().getResource("RPG");
            List<Document> documents = FileSystemDocumentLoader.loadDocuments(rpgFolder);
            log.info("Loaded RPG files: " + documents.size());

            // STEP 2: Create Embedding Model - Converts text to numbers
            EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
            GuardRailService guardRailService = new GuardRailService();

            // STEP 3: Create Vector Store - In-Memory DB to store numbers
            EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

            // STEP 4: Ingest - Split + Embed + Store (RAG magic happens here)
            EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .build();
            ingestor.ingest(documents);
            log.info("RAG Ingestion Done! RPG code is now searchable.");

            // STEP 5: Create Retriever - Finds relevant RPG chunk for question
            EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(3) // Get top 3 relevant chunks
                    .minScore(0.6)
                    .build();

            // STEP 6: Connect to Gemini - Your LLM
            String apiKey = System.getenv("GOOGLE_API_KEY");
            if (apiKey == null) {
                System.out.println("ERROR: Set GOOGLE_API_KEY env variable first!");
                return;
            }

            ChatLanguageModel chatModel = GoogleAiGeminiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName("gemini-3.5-flash-lite")
                    .temperature(0.1) // Low temp = more accurate for code
                    .build();

            // STEP 7: Build AI Assistant with RAG
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatLanguageModel(chatModel)
                    .contentRetriever(retriever)
                    .chatMemory(MessageWindowChatMemory.builder().maxMessages(10).build())
                    .build();

            // STEP 8: Ask Questions - Test RAG
            log.info("\n--- TEST 1: Without RAG it would guess ---");
            String q1 = "What is the data type of CustBal in CUSTMST? How to convert it to Java?";
            log.info("Q: " + q1);
            try {
                // GATE 1: Input Guardrail
                guardRailService.checkInput(q1);

                // Call AI via Assistant interface
                String rawAnswer = assistant.chat(q1);

                // GATE 2: Output Guardrail
                String safeAnswer = guardRailService.checkOutput(rawAnswer);

                log.info("FINAL ANSWER: " + safeAnswer);

            } catch (IllegalArgumentException e) {
                log.error(e.getMessage());
                log.error("Request blocked by Guardrail, not sent to Gemini.");
            }

            log.info("\n--- TEST 2: COMMIT check ---");
            String q2 = "In UpdateBalance procedure, what should we do after UPDATE in Java conversion?";
            log.info("Q: " + q2);
            try {
                // GATE 1: Input Guardrail
                guardRailService.checkInput(q2);

                // Call AI via Assistant interface
                String rawAnswer = assistant.chat(q2);

                // GATE 2: Output Guardrail
                String safeAnswer = guardRailService.checkOutput(rawAnswer);

                log.info("FINAL ANSWER: " + safeAnswer);

            } catch (IllegalArgumentException e) {
                log.error(e.getMessage());
                log.error("Request blocked by Guardrail, not sent to Gemini.");
            }
        }
    }
