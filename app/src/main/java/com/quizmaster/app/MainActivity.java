package com.quizmaster.app;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
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
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
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
    private int lastTotalQuestions;
    private String lastTopic;

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        } catch (SecurityException ignored) {
            // Some providers don't support persistable permissions; re-picking will still work.
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
        } catch (Exception ignored) {
            // Fall back to the last path segment computed above.
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
                    QuizCacheStore.delete(this, entry.uri);
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
        String mimeType = getContentResolver().getType(selectedFileUri);
        if ("application/pdf".equals(mimeType)) {
            Toast.makeText(this, R.string.error_pdf_not_supported, Toast.LENGTH_LONG).show();
            return;
        }

        generateQuizButton.setEnabled(false);
        quizContainer.setVisibility(View.GONE);
        quizCompleteContainer.setVisibility(View.GONE);
        quizResultText.setText(R.string.generating_quiz);

        String fileUriString = selectedFileUri.toString();
        String fileDisplayName = selectedFileDisplayName;

        fileReadExecutor.execute(() -> {
            try {
                String documentText = readFileText(selectedFileUri);
                quizAgent.generateQuiz(apiKey, documentText, new QuizAgent.Callback() {
                    @Override
                    public void onSuccess(QuizResult result) {
                        QuizCacheStore.save(MainActivity.this, fileUriString, fileDisplayName,
                                result.topic, result.questions);
                        generateQuizButton.setEnabled(true);
                        startQuizSession(result.topic, result.questions);
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

    private void startQuizFromCache(QuizCacheStore.Entry entry) {
        selectedFileUri = Uri.parse(entry.uri);
        selectedFileDisplayName = entry.displayName;
        startQuizSession(entry.topic, entry.questions);
    }

    private void startQuizSession(String topic, List<QuizQuestion> questions) {
        currentTopic = topic;
        currentQuestions = QuizRandomizer.randomize(questions);
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
        lastTotalQuestions = currentQuestions.size();
        lastTopic = currentTopic;
        quizContainer.setVisibility(View.GONE);
        quizTopicText.setText(getString(R.string.quiz_topic, currentTopic));
        scoreStarView.setScore(score, lastTotalQuestions);
        quizCompleteContainer.setVisibility(View.VISIBLE);
        currentQuestions = null;
    }

    private void backToHome() {
        quizCompleteContainer.setVisibility(View.GONE);
        updateStatus();
    }

    private void shareScore() {
        Uri screenshotUri = captureViewToUri(quizCompleteCaptureContainer);
        if (screenshotUri == null) {
            Toast.makeText(this, R.string.error_share_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/png");
        shareIntent.putExtra(Intent.EXTRA_STREAM, screenshotUri);
        shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_score_text, score, lastTotalQuestions, lastTopic));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_score_chooser_title)));
    }

    private Uri captureViewToUri(View view) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);

            File imagesDir = new File(getCacheDir(), "images");
            imagesDir.mkdirs();
            File imageFile = new File(imagesDir, "quiz_score.png");
            try (FileOutputStream out = new FileOutputStream(imageFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }

            return FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", imageFile);
        } catch (IOException e) {
            return null;
        }
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
