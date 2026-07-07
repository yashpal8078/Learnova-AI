package com.aistudyassistant.service;

import org.springframework.stereotype.Service;

@Service
public class VoiceService {

    private final AiService aiService;

    public VoiceService(AiService aiService) {
        this.aiService = aiService;
    }

    public String processVoiceQuestion(String question) {

        // send the text question to AI
        String response = aiService.ask(question);

        return response;
    }
}