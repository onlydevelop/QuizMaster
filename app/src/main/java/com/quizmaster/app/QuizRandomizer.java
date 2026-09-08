package com.quizmaster.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuizRandomizer {

    public static List<QuizQuestion> randomize(List<QuizQuestion> questions) {
        List<QuizQuestion> shuffled = new ArrayList<>();
        for (QuizQuestion question : questions) {
            shuffled.add(shuffleChoices(question));
        }
        Collections.shuffle(shuffled);
        return shuffled;
    }

    public static List<QuizQuestion> pickAndRandomize(List<QuizQuestion> pool, int count) {
        List<QuizQuestion> shuffledPool = new ArrayList<>(pool);
        Collections.shuffle(shuffledPool);

        int limit = Math.min(count, shuffledPool.size());
        List<QuizQuestion> selected = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            selected.add(shuffleChoices(shuffledPool.get(i)));
        }
        return selected;
    }

    private static QuizQuestion shuffleChoices(QuizQuestion question) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < question.choices.size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order);

        List<String> shuffledChoices = new ArrayList<>();
        int newCorrectIndex = 0;
        for (int i = 0; i < order.size(); i++) {
            int originalIndex = order.get(i);
            shuffledChoices.add(question.choices.get(originalIndex));
            if (originalIndex == question.correctIndex) {
                newCorrectIndex = i;
            }
        }

        return new QuizQuestion(question.question, shuffledChoices, newCorrectIndex);
    }
}
