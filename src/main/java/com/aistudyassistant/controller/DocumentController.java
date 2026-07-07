package com.aistudyassistant.controller;

import com.aistudyassistant.model.Document;
import com.aistudyassistant.repository.DocumentRepository;
import com.aistudyassistant.repository.UserRepository;
import com.aistudyassistant.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    /**
     * Upload document — now returns document ID + filename
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file) throws Exception {

        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        Document document = documentService.uploadFile(file, email);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "File uploaded successfully");
        response.put("documentId", document.getId());
        response.put("fileName", document.getFileName());

        return ResponseEntity.ok(response);
    }

    /**
     * GET all documents for the logged-in user
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getMyDocuments() {

        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Document> docs = documentRepository.findByUserId(user.getId());

        List<Map<String, Object>> result = docs.stream().map(doc -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", doc.getId());
            map.put("fileName", doc.getFileName());
            map.put("uploadedAt", doc.getUploadedAt().toString());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * DELETE a document
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteDocument(@PathVariable Long id) {

        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        // Check ownership
        if (!doc.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized");
        }

        documentRepository.delete(doc);

        return ResponseEntity.ok("Document deleted");
    }
}