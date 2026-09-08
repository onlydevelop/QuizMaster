package com.quizmaster.app;

import java.util.List;

public class QuizResult {

    public final String topic;
    public final List<QuizQuestion> questions;

    public QuizResult(String topic, List<QuizQuestion> questions) {
        this.topic = topic;
        this.questions = questions;
    }
}
