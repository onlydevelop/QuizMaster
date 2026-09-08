package com.quizmaster.app;

/**
 * Result of a completed quiz attempt, kept around after currentQuestions is
 * cleared so the completion screen and share action have something to read.
 */
public class QuizSummary {

    public final String topic;
    public final int score;
    public final int totalQuestions;

    public QuizSummary(String topic, int score, int totalQuestions) {
        this.topic = topic;
        this.score = score;
        this.totalQuestions = totalQuestions;
    }
}
