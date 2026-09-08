package com.quizmaster.app;

import java.util.List;

public class QuizQuestion {

    public final String question;
    public final List<String> choices;
    public final int correctIndex;

    public QuizQuestion(String question, List<String> choices, int correctIndex) {
        this.question = question;
        this.choices = choices;
        this.correctIndex = correctIndex;
    }
}
