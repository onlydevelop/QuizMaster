package com.quizmaster.app;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
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
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REDACT_VISIBLE_CHARS = 15;
    private static final int QUESTIONS_PER_QUIZ = 10;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFilePicked);

    private final ExecutorService fileReadExecutor = Executors.newSingleThreadExecutor();
    private QuizGenerator quizGenerator;
    private ScoreSharer scoreSharer;

    private TextView statusText;
    private Button generateQuizButton;
    private TextView quizResultText;
    private Uri selectedFileUri;
    private String selectedFileDisplayName;

    private TextView filesSectionTitle;
    private GridLayout fileTilesGrid;

    private LinearLayout quizContainer;
    private LinearLayout quizCompleteContainer;
    private LinearLayout quizCompleteCaptureContainer;
    private TextView quizTopicText;
    private ScoreStarView scoreStarView;
    private TextView quizProgressText;
    private TextView quizQuestionText;
    private RadioGroup quizOptionsGroup;
    private RadioButton[] quizOptionButtons;
    private Button submitAnswerButton;
    private Button nextQuestionButton;
    private Button shareScoreButton;

    private List<QuizQuestion> currentQuestions;
    private String currentTopic;
    private int currentQuestionIndex;
    private boolean currentQuestionSubmitted;
    private int score;
    private QuizSummary lastQuizSummary;

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        quizGenerator = new QuizGenerator(this, new QuizAgent(), fileReadExecutor);
        scoreSharer = new ScoreSharer(this);

        View contentRoot = findViewById(R.id.contentRoot);
        ViewCompat.setOnApplyWindowInsetsListener(contentRoot, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        statusText = findViewById(R.id.statusText);
        generateQuizButton = findViewById(R.id.btnGenerateQuiz);
        quizResultText = findViewById(R.id.quizResultText);
        generateQuizButton.setOnClickListener(v -> generateQuiz());

        filesSectionTitle = findViewById(R.id.filesSectionTitle);
        fileTilesGrid = findViewById(R.id.fileTilesGrid);

        quizContainer = findViewById(R.id.quizContainer);
        quizCompleteContainer = findViewById(R.id.quizCompleteContainer);
        quizCompleteCaptureContainer = findViewById(R.id.quizCompleteCaptureContainer);
        quizTopicText = findViewById(R.id.quizTopicText);
        scoreStarView = findViewById(R.id.scoreStarView);
        quizProgressText = findViewById(R.id.quizProgressText);
        quizQuestionText = findViewById(R.id.quizQuestionText);
        quizOptionsGroup = findViewById(R.id.quizOptionsGroup);
        // One RadioButton per QuizQuestion.CHOICES_COUNT; kept in sync with activity_main.xml.
        quizOptionButtons = new RadioButton[]{
                findViewById(R.id.quizOption0),
                findViewById(R.id.quizOption1),
                findViewById(R.id.quizOption2),
                findViewById(R.id.quizOption3)
        };
        submitAnswerButton = findViewById(R.id.btnSubmitAnswer);
        nextQuestionButton = findViewById(R.id.btnNextQuestion);
        shareScoreButton = findViewById(R.id.btnShareScore);
        shareScoreButton.setOnClickListener(v -> shareScore());

        Button backToHomeButton = findViewById(R.id.btnBackToHome);
        backToHomeButton.setOnClickListener(v -> backToHome());

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
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            // Some providers don't support persistable permissions; re-picking will still work.
            Log.w(TAG, "Provider does not support persistable URI permissions for " + uri, e);
        }

        selectedFileUri = uri;
        selectedFileDisplayName = getDisplayName(uri);
        quizResultText.setText(null);
        quizContainer.setVisibility(View.GONE);
        quizCompleteContainer.setVisibility(View.GONE);
        currentQuestions = null;
        updateStatus();
    }

    private String getDisplayName(Uri uri) {
        String name = uri.getLastPathSegment();
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index != -1) {
                    String displayName = cursor.getString(index);
                    if (displayName != null) {
                        name = displayName;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to query display name for " + uri + "; falling back to path segment", e);
        }
        return name;
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

        refreshFileTiles();
    }

    private void refreshFileTiles() {
        List<QuizCacheStore.Entry> entries = QuizCacheStore.getAll(this);
        fileTilesGrid.removeAllViews();

        boolean hasEntries = !entries.isEmpty();
        filesSectionTitle.setVisibility(hasEntries ? View.VISIBLE : View.GONE);
        fileTilesGrid.setVisibility(hasEntries ? View.VISIBLE : View.GONE);

        for (QuizCacheStore.Entry entry : entries) {
            Button tile = new Button(this);
            tile.setText(entry.displayName);
            tile.setAllCaps(false);
            tile.setMaxLines(3);
            tile.setTextSize(12f);
            tile.setTextColor(Color.WHITE);

            GradientDrawable tileBackground = new GradientDrawable();
            tileBackground.setColor(ContextCompat.getColor(this, R.color.colorViolet));
            tileBackground.setCornerRadius(dpToPx(8));
            tile.setBackground(tileBackground);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dpToPx(100);
            params.height = dpToPx(80);
            params.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
            tile.setLayoutParams(params);

            tile.setOnClickListener(v -> startQuizFromCache(entry));
            tile.setOnLongClickListener(v -> {
                confirmDeleteCachedQuiz(entry);
                return true;
            });
            fileTilesGrid.addView(tile);
        }
    }

    private void confirmDeleteCachedQuiz(QuizCacheStore.Entry entry) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_quiz_title)
                .setMessage(getString(R.string.delete_quiz_message, entry.displayName))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    QuizCacheStore.delete(this, entry.contentKey);
                    DocumentTextCache.delete(this, entry.contentKey);
                    refreshFileTiles();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private void generateQuiz() {
        String apiKey = ApiKeyStore.get(this);

        generateQuizButton.setEnabled(false);
        quizContainer.setVisibility(View.GONE);
        quizCompleteContainer.setVisibility(View.GONE);
        quizResultText.setText(R.string.generating_quiz);

        quizGenerator.generate(apiKey, selectedFileUri, selectedFileDisplayName, new QuizGenerator.Listener() {
            @Override
            public void onStatusUpdate(int statusStringRes) {
                quizResultText.setText(statusStringRes);
            }

            @Override
            public void onSuccess(String topic, List<QuizQuestion> questions) {
                generateQuizButton.setEnabled(true);
                startQuizSession(topic, questions);
            }

            @Override
            public void onGenerationError(String message) {
                quizResultText.setText(getString(R.string.error_quiz_generation_failed, message));
                generateQuizButton.setEnabled(true);
            }

            @Override
            public void onExtractionError(String message) {
                quizResultText.setText(getString(R.string.error_pdf_extraction_failed, message));
                generateQuizButton.setEnabled(true);
            }
        });
    }

    private void startQuizFromCache(QuizCacheStore.Entry entry) {
        selectedFileUri = Uri.parse(entry.uri);
        selectedFileDisplayName = entry.displayName;
        startQuizSession(entry.topic, entry.questions);
    }

    private void startQuizSession(String topic, List<QuizQuestion> questions) {
        currentTopic = topic;
        currentQuestions = QuizRandomizer.pickAndRandomize(questions, QUESTIONS_PER_QUIZ);
        currentQuestionIndex = 0;
        score = 0;

        quizResultText.setText(null);
        quizCompleteContainer.setVisibility(View.GONE);
        filesSectionTitle.setVisibility(View.GONE);
        fileTilesGrid.setVisibility(View.GONE);
        quizContainer.setVisibility(View.VISIBLE);

        showQuestion(currentQuestionIndex);
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
                button.setText(withSuffix(question.choices.get(i), " ✓", ContextCompat.getColor(this, R.color.colorCorrect)));
            } else if (i == selectedIndex) {
                button.setText(withSuffix(question.choices.get(i), " ✗", ContextCompat.getColor(this, R.color.colorIncorrect)));
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
        lastQuizSummary = new QuizSummary(currentTopic, score, currentQuestions.size());
        quizContainer.setVisibility(View.GONE);
        quizTopicText.setText(getString(R.string.quiz_topic, currentTopic));
        scoreStarView.setScore(lastQuizSummary.score, lastQuizSummary.totalQuestions);
        quizCompleteContainer.setVisibility(View.VISIBLE);
        currentQuestions = null;
    }

    private void backToHome() {
        quizCompleteContainer.setVisibility(View.GONE);
        updateStatus();
    }

    private void shareScore() {
        boolean shared = scoreSharer.share(quizCompleteCaptureContainer, lastQuizSummary);
        if (!shared) {
            Toast.makeText(this, R.string.error_share_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private SpannableString withSuffix(String text, String suffix, int color) {
        SpannableString spannable = new SpannableString(text + suffix);
        spannable.setSpan(new ForegroundColorSpan(color), text.length(), spannable.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return spannable;
    }
}
