package com.aistudyassistant.util;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;

import java.util.*;

/**
 * Simple local embedding model using word-frequency hashing.
 * No external API needed — runs entirely in-memory.
 * Good enough for RAG similarity search on study documents.
 */
public class SimpleEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 384;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        List<String> texts = request.getInstructions();

        for (int i = 0; i < texts.size(); i++) {
            float[] vector = embed(texts.get(i));
            embeddings.add(new Embedding(vector, i));
        }

        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];

        if (text == null || text.isBlank()) {
            return vector;
        }

        // Normalize text
        String normalized = text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        String[] words = normalized.split(" ");

        // Build word frequency map
        Map<String, Integer> freq = new HashMap<>();
        for (String word : words) {
            if (word.length() > 1) { // skip single chars
                freq.merge(word, 1, Integer::sum);
            }
        }

        // Hash each word into multiple vector dimensions
        for (Map.Entry<String, Integer> entry : freq.entrySet()) {
            String word = entry.getKey();
            int count = entry.getValue();

            // Use multiple hash functions for better distribution
            int h1 = Math.abs(word.hashCode()) % DIMENSIONS;
            int h2 = Math.abs((word.hashCode() * 31 + 7) ) % DIMENSIONS;
            int h3 = Math.abs((word.hashCode() * 17 + 13)) % DIMENSIONS;

            // Weight by log-frequency (TF-like)
            float weight = (float) (1.0 + Math.log(count));

            vector[h1] += weight;
            vector[h2] += weight * 0.5f;
            vector[h3] += weight * 0.3f;

            // Character n-gram features (helps with similar words)
            for (int i = 0; i <= word.length() - 3 && i < 10; i++) {
                String ngram = word.substring(i, i + 3);
                int nh = Math.abs(ngram.hashCode()) % DIMENSIONS;
                vector[nh] += weight * 0.2f;
            }
        }

        // L2 normalize
        float norm = 0;
        for (float v : vector) norm += v * v;
        norm = (float) Math.sqrt(norm);

        if (norm > 0) {
            for (int i = 0; i < DIMENSIONS; i++) {
                vector[i] /= norm;
            }
        }

        return vector;
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getContent());
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> result = new ArrayList<>();
        for (String text : texts) {
            result.add(embed(text));
        }
        return result;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }
}