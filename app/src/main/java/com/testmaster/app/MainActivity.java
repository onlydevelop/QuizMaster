package com.testmaster.app;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REDACT_VISIBLE_CHARS = 15;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFilePicked);

    private final QuizAgent quizAgent = new QuizAgent();
    private final ExecutorService fileReadExecutor = Executors.newSingleThreadExecutor();

    private TextView statusText;
    private Button generateQuizButton;
    private TextView quizResultText;
    private Uri selectedFileUri;

    private android.widget.LinearLayout quizContainer;
    private android.widget.LinearLayout quizCompleteContainer;
    private ScoreStarView scoreStarView;
    private TextView quizProgressText;
    private TextView quizQuestionText;
    private RadioGroup quizOptionsGroup;
    private RadioButton[] quizOptionButtons;
    private Button submitAnswerButton;
    private Button nextQuestionButton;

    private List<QuizQuestion> currentQuestions;
    private int currentQuestionIndex;
    private boolean currentQuestionSubmitted;
    private int score;

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        generateQuizButton = findViewById(R.id.btnGenerateQuiz);
        quizResultText = findViewById(R.id.quizResultText);
        generateQuizButton.setOnClickListener(v -> generateQuiz());

        quizContainer = findViewById(R.id.quizContainer);
        quizCompleteContainer = findViewById(R.id.quizCompleteContainer);
        scoreStarView = findViewById(R.id.scoreStarView);
        quizProgressText = findViewById(R.id.quizProgressText);
        quizQuestionText = findViewById(R.id.quizQuestionText);
        quizOptionsGroup = findViewById(R.id.quizOptionsGroup);
        quizOptionButtons = new RadioButton[]{
                findViewById(R.id.quizOption0),
                findViewById(R.id.quizOption1),
                findViewById(R.id.quizOption2),
                findViewById(R.id.quizOption3)
        };
        submitAnswerButton = findViewById(R.id.btnSubmitAnswer);
        nextQuestionButton = findViewById(R.id.btnNextQuestion);

        quizOptionsGroup.setOnCheckedChangeListener((group, checkedId) ->
                submitAnswerButton.setEnabled(!currentQuestionSubmitted && checkedId != -1));
        submitAnswerButton.setOnClickListener(v -> submitAnswer());
        nextQuestionButton.setOnClickListener(v -> goToNextQuestion());

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.app_name, R.string.app_name);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        NavigationView navigationView = findViewById(R.id.navigationView);
        navigationView.setNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.menu_upload_file) {
                filePickerLauncher.launch(new String[]{"text/plain", "application/pdf"});
            } else if (item.getItemId() == R.id.menu_api_key) {
                showApiKeyDialog();
            }
            drawerLayout.closeDrawers();
            return true;
        });
    }

    private static String redact(String key) {
        if (key.length() <= REDACT_VISIBLE_CHARS) {
            return key;
        }
        return key.substring(0, REDACT_VISIBLE_CHARS) + "...";
    }

    private void showApiKeyDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.api_key_dialog_hint);
        String existingKey = ApiKeyStore.get(this);
        String redactedExistingKey = existingKey != null ? redact(existingKey) : null;
        if (redactedExistingKey != null) {
            input.setText(redactedExistingKey);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.api_key_dialog_title)
                .setView(input)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String entered = input.getText().toString().trim();
                    if (!entered.equals(redactedExistingKey)) {
                        ApiKeyStore.save(this, entered);
                    }
                    Toast.makeText(this, R.string.api_key_saved, Toast.LENGTH_SHORT).show();
                    updateStatus();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void onFilePicked(Uri uri) {
        if (uri == null) {
            return;
        }
        selectedFileUri = uri;
        quizResultText.setText(null);
        quizContainer.setVisibility(android.view.View.GONE);
        quizCompleteContainer.setVisibility(android.view.View.GONE);
        currentQuestions = null;
        updateStatus();
    }

    private void updateStatus() {
        boolean ready = selectedFileUri != null && ApiKeyStore.get(this) != null;
        generateQuizButton.setEnabled(ready);

        if (selectedFileUri == null) {
            statusText.setText(R.string.error_no_file);
        } else if (ApiKeyStore.get(this) == null) {
            statusText.setText(R.string.error_no_api_key);
        } else {
            statusText.setText(null);
        }
    }

    private void generateQuiz() {
        String apiKey = ApiKeyStore.get(this);
        String mimeType = getContentResolver().getType(selectedFileUri);
        if ("application/pdf".equals(mimeType)) {
            Toast.makeText(this, R.string.error_pdf_not_supported, Toast.LENGTH_LONG).show();
            return;
        }

        generateQuizButton.setEnabled(false);
        quizContainer.setVisibility(android.view.View.GONE);
        quizCompleteContainer.setVisibility(android.view.View.GONE);
        quizResultText.setText(R.string.generating_quiz);

        fileReadExecutor.execute(() -> {
            try {
                String documentText = readFileText(selectedFileUri);
                quizAgent.generateQuiz(apiKey, documentText, new QuizAgent.Callback() {
                    @Override
                    public void onSuccess(List<QuizQuestion> questions) {
                        currentQuestions = questions;
                        currentQuestionIndex = 0;
                        score = 0;
                        quizResultText.setText(null);
                        quizContainer.setVisibility(android.view.View.VISIBLE);
                        generateQuizButton.setEnabled(true);
                        showQuestion(currentQuestionIndex);
                    }

                    @Override
                    public void onError(Exception e) {
                        quizResultText.setText(getString(R.string.error_quiz_generation_failed, e.getMessage()));
                        generateQuizButton.setEnabled(true);
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    quizResultText.setText(getString(R.string.error_quiz_generation_failed, e.getMessage()));
                    generateQuizButton.setEnabled(true);
                });
            }
        });
    }

    private void showQuestion(int index) {
        QuizQuestion question = currentQuestions.get(index);
        currentQuestionSubmitted = false;

        quizProgressText.setText(getString(R.string.quiz_progress, index + 1, currentQuestions.size()));
        quizQuestionText.setText(question.question);

        quizOptionsGroup.clearCheck();
        for (int i = 0; i < quizOptionButtons.length; i++) {
            RadioButton button = quizOptionButtons[i];
            button.setText(question.choices.get(i));
            button.setEnabled(true);
        }

        submitAnswerButton.setEnabled(false);
        nextQuestionButton.setEnabled(false);
        nextQuestionButton.setText(index == currentQuestions.size() - 1 ? R.string.finish : R.string.next);
    }

    private void submitAnswer() {
        QuizQuestion question = currentQuestions.get(currentQuestionIndex);
        int checkedId = quizOptionsGroup.getCheckedRadioButtonId();
        int selectedIndex = -1;
        for (int i = 0; i < quizOptionButtons.length; i++) {
            if (quizOptionButtons[i].getId() == checkedId) {
                selectedIndex = i;
                break;
            }
        }

        if (selectedIndex == question.correctIndex) {
            score++;
        }

        for (int i = 0; i < quizOptionButtons.length; i++) {
            RadioButton button = quizOptionButtons[i];
            button.setEnabled(false);
            if (i == question.correctIndex) {
                button.setText(withSuffix(question.choices.get(i), " ✓", Color.parseColor("#2E7D32")));
            } else if (i == selectedIndex) {
                button.setText(withSuffix(question.choices.get(i), " ✗", Color.parseColor("#C62828")));
            }
        }

        currentQuestionSubmitted = true;
        submitAnswerButton.setEnabled(false);
        nextQuestionButton.setEnabled(true);
    }

    private void goToNextQuestion() {
        if (currentQuestionIndex == currentQuestions.size() - 1) {
            finishQuiz();
        } else {
            currentQuestionIndex++;
            showQuestion(currentQuestionIndex);
        }
    }

    private void finishQuiz() {
        int totalQuestions = currentQuestions.size();
        quizContainer.setVisibility(android.view.View.GONE);
        scoreStarView.setScore(score, totalQuestions);
        quizCompleteContainer.setVisibility(android.view.View.VISIBLE);
        currentQuestions = null;
    }

    private SpannableString withSuffix(String text, String suffix, int color) {
        SpannableString spannable = new SpannableString(text + suffix);
        spannable.setSpan(new ForegroundColorSpan(color), text.length(), spannable.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }

    private String readFileText(Uri uri) throws IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        }
        return text.toString();
    }
}
