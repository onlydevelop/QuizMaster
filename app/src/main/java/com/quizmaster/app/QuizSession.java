package com.quizmaster.app;

import java.util.List;

/**
 * Pure state and scoring logic for an in-progress quiz attempt: which
 * question is current, whether it's been submitted, and the running score.
 * Rendering (which views show what) stays in MainActivity.
 */
public class QuizSession {

    private final String topic;
    private final List<QuizQuestion> questions;
    private int index;
    private boolean submitted;
    private int score;

    public QuizSession(String topic, List<QuizQuestion> questions) {
        this.topic = topic;
        this.questions = questions;
    }

    public int size() {
        return questions.size();
    }

    public int getIndex() {
        return index;
    }

    public QuizQuestion getCurrentQuestion() {
        return questions.get(index);
    }

    public boolean isSubmitted() {
        return submitted;
    }

    public boolean isLastQuestion() {
        return index == questions.size() - 1;
    }

    /** Records the given choice as the answer to the current question; returns whether it was correct. */
    public boolean submitAnswer(int selectedIndex) {
        submitted = true;
        boolean correct = selectedIndex == getCurrentQuestion().correctIndex;
        if (correct) {
            score++;
        }
        return correct;
    }

    /** Moves to the next question. Must not be called when isLastQuestion() is true. */
    public void advance() {
        index++;
        submitted = false;
    }

    public QuizSummary toSummary() {
        return new QuizSummary(topic, score, questions.size());
    }
}
