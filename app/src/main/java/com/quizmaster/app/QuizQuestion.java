package com.quizmaster.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuizQuestion {

    /**
     * Number of answer choices per question. This is also baked into
     * activity_main.xml (one RadioButton per choice) and
     * MainActivity's quizOptionButtons array; changing it requires
     * updating both of those as well.
     */
    public static final int CHOICES_COUNT = 4;

    public final String question;
    public final List<String> choices;
    public final int correctIndex;

    public QuizQuestion(String question, List<String> choices, int correctIndex) {
        this.question = question;
        this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
        this.correctIndex = correctIndex;
    }
}
