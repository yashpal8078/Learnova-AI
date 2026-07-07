package com.aistudyassistant.controller;

import com.aistudyassistant.dto.AiRequest;
import com.aistudyassistant.dto.AiResponse;
import com.aistudyassistant.model.ChatMessage;
import com.aistudyassistant.model.Conversation;
import com.aistudyassistant.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final ObjectMapper objectMapper;

    @PostMapping("/ask")
    public ResponseEntity<AiResponse> ask(@Valid @RequestBody AiRequest request) {
        return ResponseEntity.ok(new AiResponse(aiService.ask(request.prompt())));
    }

    @PostMapping("/ask/document/{documentId}")
    public ResponseEntity<AiResponse> askFromDocument(
            @PathVariable Long documentId,
            @RequestBody AiRequest request) throws Exception {
        return ResponseEntity.ok(
                new AiResponse(aiService.askFromDocument(documentId, request.prompt())));
    }

    @PostMapping("/quiz/{documentId}")
    public ResponseEntity<String> generateQuiz(@PathVariable Long documentId) {
        return ResponseEntity.ok(aiService.generateQuiz(documentId));
    }

    // ═══════════════════════════════════════
    //  CONVERSATIONS
    // ═══════════════════════════════════════

    @GetMapping("/conversations")
    public ResponseEntity<List<Map<String, Object>>> getConversations(
            Authentication authentication) {
        String email = authentication.getName();
        List<Conversation> convos = aiService.getConversations(email);
        List<Map<String, Object>> result = convos.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId());
            map.put("title", c.getTitle());
            map.put("createdAt", c.getCreatedAt().toString());
            map.put("updatedAt", c.getUpdatedAt().toString());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/conversations")
    public ResponseEntity<Map<String, Object>> createConversation(
            Authentication authentication) {
        String email = authentication.getName();
        Conversation convo = aiService.createConversation(email);
        Map<String, Object> result = new HashMap<>();
        result.put("id", convo.getId());
        result.put("title", convo.getTitle());
        result.put("createdAt", convo.getCreatedAt().toString());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<Map<String, String>>> getConversationMessages(
            @PathVariable Long conversationId) {
        List<ChatMessage> messages = aiService.getConversationMessages(conversationId);
        List<Map<String, String>> result = messages.stream().map(msg -> {
            Map<String, String> map = new HashMap<>();
            map.put("role", msg.getRole().toLowerCase());
            map.put("content", msg.getContent());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/conversations/{conversationId}/chat")
    public ResponseEntity<String> chatInConversation(
            @PathVariable Long conversationId,
            @RequestBody AiRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(
                aiService.chatInConversation(email, conversationId, request.prompt()));
    }

    /**
     * ★ STREAMING ENDPOINT ★
     *
     * Each SSE event contains a JSON string in data field:
     *   data:{"t":"Hello "}
     *   data:{"t":"world\n"}
     *   data:{"t":"## Heading"}
     *   data:{"done":true}
     *
     * Using JSON ensures spaces, newlines, and special chars
     * are properly escaped and survive SSE transport.
     */
    @GetMapping(value = "/conversations/{conversationId}/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChatInConversation(
            @PathVariable Long conversationId,
            @RequestParam String prompt,
            Authentication authentication) {

        String email = authentication.getName();

        // Get raw token stream from service
        Flux<String> tokenStream =
                aiService.streamChatInConversation(email, conversationId, prompt);

        // Convert each token to a JSON-encoded SSE event
        Flux<ServerSentEvent<String>> jsonTokens = tokenStream
                .map(token -> {
                    // JSON-encode: {"t":"token value with spaces\nand newlines"}
                    String json;
                    try {
                        Map<String, String> payload = new HashMap<>();
                        payload.put("t", token);
                        json = objectMapper.writeValueAsString(payload);
                    } catch (Exception e) {
                        json = "{\"t\":\"\"}";
                    }
                    return ServerSentEvent.<String>builder()
                            .data(json)
                            .build();
                });

        // Append a "done" signal at the end
        Flux<ServerSentEvent<String>> doneSignal = Flux.just(
                ServerSentEvent.<String>builder()
                        .data("{\"done\":true}")
                        .build()
        );

        return Flux.concat(jsonTokens, doneSignal);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<String> deleteConversation(
            @PathVariable Long conversationId,
            Authentication authentication) {
        aiService.deleteConversation(authentication.getName(), conversationId);
        return ResponseEntity.ok("Deleted");
    }

    @PutMapping("/conversations/{conversationId}")
    public ResponseEntity<String> renameConversation(
            @PathVariable Long conversationId,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        aiService.renameConversation(
                authentication.getName(), conversationId, body.get("title"));
        return ResponseEntity.ok("Renamed");
    }

    // ═══ Legacy ═══

    @PostMapping("/chat")
    public ResponseEntity<String> chat(
            @RequestBody AiRequest request, Authentication authentication) {
        return ResponseEntity.ok(
                aiService.chatWithMemory(authentication.getName(), request.prompt()));
    }

    @GetMapping("/chat/history")
    public ResponseEntity<List<Map<String, String>>> getChatHistory(
            Authentication authentication) {
        List<ChatMessage> messages = aiService.getChatHistory(authentication.getName());
        List<Map<String, String>> result = messages.stream().map(msg -> {
            Map<String, String> map = new HashMap<>();
            map.put("role", msg.getRole().toLowerCase());
            map.put("content", msg.getContent());
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/chat/history")
    public ResponseEntity<String> clearChatHistory(Authentication authentication) {
        aiService.clearChatHistory(authentication.getName());
        return ResponseEntity.ok("Cleared");
    }

    @PostMapping("/concept/{documentId}")
    public ResponseEntity<String> generateConceptGraph(
            @PathVariable Long documentId, @RequestBody AiRequest request) {
        return ResponseEntity.ok(
                aiService.generateConceptGraph(documentId, request.prompt()));
    }

    @PostMapping("/summary/{documentId}")
    public ResponseEntity<String> summarize(@PathVariable Long documentId) {
        return ResponseEntity.ok(aiService.summarizeDocument(documentId));
    }
}