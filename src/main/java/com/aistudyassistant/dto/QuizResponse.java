package com.aistudyassistant.dto;

import java.util.List;

public record QuizResponse(
        List<Question> questions
) {

    public record Question(
            String question,
            List<String> options,
            String answer,
            String explanation
    ) {}
}