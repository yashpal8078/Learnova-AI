package com.aistudyassistant.service;

import com.aistudyassistant.model.ChatMessage;
import com.aistudyassistant.model.Conversation;
import com.aistudyassistant.model.DocumentChunk;
import com.aistudyassistant.model.User;
import com.aistudyassistant.repository.ChatMessageRepository;
import com.aistudyassistant.repository.ConversationRepository;
import com.aistudyassistant.repository.DocumentChunkRepository;
import com.aistudyassistant.repository.DocumentRepository;
import com.aistudyassistant.repository.UserRepository;
import com.aistudyassistant.util.VectorUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AiService {

    private final ChatClient chatClient;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingModel embeddingModel;
    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    // ═══════════════════════════════════════
    //  CONVERSATION METHODS
    // ═══════════════════════════════════════

    public List<Conversation> getConversations(String email) {
        User user = findUser(email);
        return conversationRepository
                .findByUserIdOrderByUpdatedAtDesc(user.getId());
    }

    public Conversation createConversation(String email) {
        User user = findUser(email);
        Conversation convo = Conversation.builder()
                .title("New Chat")
                .user(user)
                .build();
        return conversationRepository.save(convo);
    }

    public List<ChatMessage> getConversationMessages(Long conversationId) {
        return chatMessageRepository
                .findByConversationIdOrderByIdAsc(conversationId);
    }

    public String chatInConversation(String email, Long conversationId,
                                     String question) {
        User user = findUser(email);
        Conversation convo = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        // Save user message
        chatMessageRepository.save(
                ChatMessage.builder()
                        .role("USER")
                        .content(question)
                        .user(user)
                        .conversation(convo)
                        .build()
        );

        // Auto-generate title from first message
        if ("New Chat".equals(convo.getTitle())) {
            String title = question.length() > 40
                    ? question.substring(0, 40) + "..."
                    : question;
            convo.setTitle(title);
            conversationRepository.save(convo);
        }

        // Build prompt with conversation history
        String prompt = buildConversationPrompt(conversationId);

        String response = chatClient.prompt(prompt).call().content();

        // Save AI response
        chatMessageRepository.save(
                ChatMessage.builder()
                        .role("ASSISTANT")
                        .content(response)
                        .user(user)
                        .conversation(convo)
                        .build()
        );

        // Update conversation timestamp
        conversationRepository.save(convo);

        return response;
    }

    public Flux<String> streamChatInConversation(String email,
                                                 Long conversationId,
                                                 String question) {
        User user = findUser(email);
        Conversation convo = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        // Save user message
        chatMessageRepository.save(
                ChatMessage.builder()
                        .role("USER")
                        .content(question)
                        .user(user)
                        .conversation(convo)
                        .build()
        );

        // Auto-generate title
        if ("New Chat".equals(convo.getTitle())) {
            String title = question.length() > 40
                    ? question.substring(0, 40) + "..."
                    : question;
            convo.setTitle(title);
            conversationRepository.save(convo);
        }

        // Build prompt
        String prompt = buildConversationPrompt(conversationId);

        StringBuilder fullResponse = new StringBuilder();

        return chatClient.prompt(prompt)
                .stream()
                .content()
                .doOnNext(chunk -> {
                    // Each chunk is a token from the LLM
                    // Accumulate for saving to DB later
                    fullResponse.append(chunk);
                })
                .doOnComplete(() -> {
                    // Save the COMPLETE response to database
                    if (fullResponse.length() > 0) {
                        chatMessageRepository.save(
                                ChatMessage.builder()
                                        .role("ASSISTANT")
                                        .content(fullResponse.toString())
                                        .user(user)
                                        .conversation(convo)
                                        .build()
                        );
                        conversationRepository.save(convo);
                    }
                })
                .doOnError(err -> {
                    // Save partial response on error
                    if (fullResponse.length() > 0) {
                        chatMessageRepository.save(
                                ChatMessage.builder()
                                        .role("ASSISTANT")
                                        .content(fullResponse.toString())
                                        .user(user)
                                        .conversation(convo)
                                        .build()
                        );
                    }
                });
    }

    public void deleteConversation(String email, Long conversationId) {
        User user = findUser(email);
        Conversation convo = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (!convo.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized");
        }

        chatMessageRepository.deleteByConversationId(conversationId);
        conversationRepository.delete(convo);
    }

    public void renameConversation(String email, Long conversationId,
                                   String title) {
        User user = findUser(email);
        Conversation convo = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if (!convo.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized");
        }

        convo.setTitle(title);
        conversationRepository.save(convo);
    }

    private String buildConversationPrompt(Long conversationId) {
        List<ChatMessage> history = chatMessageRepository
                .findTop10ByConversationIdOrderByIdDesc(conversationId);

        Collections.reverse(history);

        StringBuilder conversation = new StringBuilder();
        for (ChatMessage msg : history) {
            conversation.append(msg.getRole())
                    .append(": ")
                    .append(msg.getContent())
                    .append("\n");
        }

        return """
            You are an AI study assistant.

            Continue the conversation naturally.
            Give helpful, detailed and well-formatted answers.
            Use markdown formatting when helpful.

            Conversation history:
            %s
            """.formatted(conversation.toString());
    }

    // ═══════════════════════════════════════
    //  LEGACY METHODS (kept for compatibility)
    // ═══════════════════════════════════════

    public String ask(String prompt) {
        return chatClient.prompt(prompt).call().content();
    }

    public String chatWithMemory(String email, String question) {
        User user = findUser(email);

        chatMessageRepository.save(
                ChatMessage.builder()
                        .role("USER")
                        .content(question)
                        .user(user)
                        .build()
        );

        List<ChatMessage> history =
                chatMessageRepository.findTop10ByUserIdOrderByIdDesc(user.getId());
        Collections.reverse(history);

        StringBuilder conversation = new StringBuilder();
        for (ChatMessage msg : history) {
            conversation.append(msg.getRole())
                    .append(": ").append(msg.getContent()).append("\n");
        }

        String prompt = """
            You are an AI study assistant.
            Continue the conversation naturally.
            Use markdown formatting.
            Conversation history:
            %s
            """.formatted(conversation.toString());

        String response = chatClient.prompt(prompt).call().content();

        chatMessageRepository.save(
                ChatMessage.builder()
                        .role("ASSISTANT")
                        .content(response)
                        .user(user)
                        .build()
        );

        return response;
    }

    public List<ChatMessage> getChatHistory(String email) {
        User user = findUser(email);
        return chatMessageRepository.findByUserIdOrderByIdAsc(user.getId());
    }

    public void clearChatHistory(String email) {
        User user = findUser(email);
        chatMessageRepository.deleteByUserId(user.getId());
    }

    public String askFromDocument(Long documentId, String question) throws Exception {
        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentId(documentId);
        float[] questionVector = embeddingModel.embed(question);
        ObjectMapper mapper = new ObjectMapper();
        List<Pair<DocumentChunk, Double>> scoredChunks = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            List<Double> chunkVector = mapper.readValue(chunk.getEmbedding(), List.class);
            double similarity = VectorUtils.cosineSimilarity(questionVector, chunkVector);
            scoredChunks.add(Pair.of(chunk, similarity));
        }
        scoredChunks.sort((a, b) -> Double.compare(b.getRight(), a.getRight()));
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < Math.min(3, scoredChunks.size()); i++) {
            contextBuilder.append(scoredChunks.get(i).getLeft().getContent()).append("\n\n");
        }
        String prompt = """
            You are an intelligent AI study assistant.
            Use the following document context to answer.
            DOCUMENT CONTEXT:
            %s
            QUESTION:
            %s
            """.formatted(contextBuilder.toString(), question);
        return chatClient.prompt(prompt).call().content();
    }

    public String generateQuiz(Long documentId) {

        List<DocumentChunk> chunks =
                documentChunkRepository.findByDocumentId(documentId);

        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < Math.min(8, chunks.size()); i++) {
            contextBuilder.append(chunks.get(i).getContent())
                    .append("\n\n");
        }

        String context = contextBuilder.toString();

        String prompt = """
        You are an AI study assistant and quiz generator.

        Based on the following study material,
        generate 15 multiple-choice questions.

        Rules:
        - Generate exactly 15 questions
        - Each question MUST have exactly 4 options
        - Questions should cover different topics from the material
        - Mix difficulty levels: 5 easy, 5 medium, 5 hard
        - Each question must include a clear explanation

        Return ONLY valid JSON in this exact format:
        {
          "questions": [
            {
              "question": "What is...?",
              "options": ["Option A", "Option B", "Option C", "Option D"],
              "answer": "Option A",
              "explanation": "Because..."
            }
          ]
        }

        STUDY MATERIAL:
        %s
        """.formatted(context);

        return chatClient.prompt(prompt).call().content();
    }

    public String generateConceptGraph(Long documentId, String question) {
        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentId(documentId);
        StringBuilder contextBuilder = new StringBuilder();
        for (int i = 0; i < Math.min(3, chunks.size()); i++) {
            contextBuilder.append(chunks.get(i).getContent()).append("\n\n");
        }
        String prompt = """
            You are an AI study assistant.
            Extract key concept relationships.
            Return JSON: {"nodes":[{"id":"x","label":"X"}],"edges":[{"from":"a","to":"b"}]}
            STUDY MATERIAL:
            %s
            QUESTION:
            %s
            """.formatted(contextBuilder.toString(), question);
        return chatClient.prompt(prompt).call().content();
    }

    public String summarizeDocument(Long documentId) {
        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentId(documentId);
        StringBuilder contextBuilder = new StringBuilder();
        for (DocumentChunk chunk : chunks) {
            contextBuilder.append(chunk.getContent()).append("\n\n");
        }
        String prompt = """
            You are an AI study assistant.
            Summarize with key concepts, definitions, bullet points.
            STUDY MATERIAL:
            %s
            """.formatted(contextBuilder.toString());
        return chatClient.prompt(prompt).call().content();
    }

    public Flux<String> streamAnswer(String question) {
        return chatClient.prompt(question).stream().content();
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}