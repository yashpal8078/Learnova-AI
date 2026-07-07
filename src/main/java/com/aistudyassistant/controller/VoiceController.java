package com.aistudyassistant.controller;

import com.aistudyassistant.service.VoiceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final VoiceService voiceService;

    public VoiceController(VoiceService voiceService) {
        this.voiceService = voiceService;
    }

    @PostMapping("/ask")
    public ResponseEntity<String> askVoiceQuestion(
            @RequestBody String question) {

        String response = voiceService.processVoiceQuestion(question);

        return ResponseEntity.ok(response);
    }
}