package com.aistudyassistant.service;

import com.aistudyassistant.model.Document;
import com.aistudyassistant.model.DocumentChunk;
import com.aistudyassistant.model.User;
import com.aistudyassistant.repository.DocumentChunkRepository;
import com.aistudyassistant.repository.DocumentRepository;
import com.aistudyassistant.repository.UserRepository;
import com.aistudyassistant.util.PdfExtractor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.tika.exception.TikaException;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final PdfExtractor pdfExtractor;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingModel embeddingModel;

    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * Now returns Document entity (with ID) instead of plain String
     */
    public Document uploadFile(MultipartFile file, String email)
            throws IOException, TikaException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String fileName = file.getOriginalFilename();
        Path targetLocation = uploadPath.resolve(fileName);

        Files.copy(file.getInputStream(),
                targetLocation,
                StandardCopyOption.REPLACE_EXISTING);

        String extractedText =
                pdfExtractor.extractText(targetLocation.toString());

        Document document = Document.builder()
                .fileName(fileName)
                .filePath(targetLocation.toString())
                .extractedText(extractedText)
                .user(user)
                .build();

        document = documentRepository.save(document);

        // -------- CHUNKING --------
        List<String> chunks = splitIntoChunks(extractedText, 1000);

        ObjectMapper mapper = new ObjectMapper();

        for (int i = 0; i < chunks.size(); i++) {

            String chunkText = chunks.get(i);

            float[] vector = embeddingModel.embed(chunkText);

            String embeddingJson =
                    mapper.writeValueAsString(vector);

            DocumentChunk chunk = DocumentChunk.builder()
                    .content(chunkText)
                    .chunkIndex(i)
                    .embedding(embeddingJson)
                    .document(document)
                    .build();

            documentChunkRepository.save(chunk);
        }

        return document;  // ← Returns Document with ID
    }

    private List<String> splitIntoChunks(String text, int chunkSize) {

        List<String> chunks = new ArrayList<>();
        int index = 0;

        while (index < text.length()) {
            int end = Math.min(index + chunkSize, text.length());
            chunks.add(text.substring(index, end));
            index = end;
        }

        return chunks;
    }
}