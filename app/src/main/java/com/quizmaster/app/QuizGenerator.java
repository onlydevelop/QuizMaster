package com.quizmaster.app;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Produces the question pool for a picked file: reuses a cached pool by
 * content identity when one exists, otherwise reads the source text (with
 * PDF extraction plus agent-based cleanup for PDFs, both cached) and asks
 * QuizAgent to generate questions. All I/O and QuizAgent callbacks run on
 * the supplied executor; results are delivered to the Listener on the main
 * thread.
 */
public class QuizGenerator {

    public interface Listener {
        void onStatusUpdate(int statusStringRes);
        void onSuccess(String topic, List<QuizQuestion> questions);
        void onGenerationError(String message);
        void onExtractionError(String message);
    }

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private final Context appContext;
    private final QuizAgent quizAgent;
    private final ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public QuizGenerator(Context context, QuizAgent quizAgent, ExecutorService executor) {
        this.appContext = context.getApplicationContext();
        this.quizAgent = quizAgent;
        this.executor = executor;
        PDFBoxResourceLoader.init(appContext);
    }

    public void generate(String apiKey, Uri fileUri, String fileDisplayName, Listener listener) {
        String fileUriString = fileUri.toString();
        ContentResolver contentResolver = appContext.getContentResolver();

        executor.execute(() -> {
            String contentKey;
            try {
                contentKey = computeContentHash(contentResolver, fileUri);
            } catch (IOException e) {
                postOnMain(() -> listener.onGenerationError(e.getMessage()));
                return;
            }

            QuizCacheStore.Entry existing = QuizCacheStore.find(appContext, contentKey);
            if (existing != null) {
                postOnMain(() -> listener.onSuccess(existing.topic, existing.questions));
                return;
            }

            PickedFile pickedFile = new PickedFile(contentKey, fileUriString, fileDisplayName);
            boolean isPdf = "application/pdf".equals(contentResolver.getType(fileUri));
            if (!isPdf) {
                try {
                    String documentText = readFileText(contentResolver, fileUri);
                    requestQuestions(apiKey, documentText, pickedFile, listener);
                } catch (IOException e) {
                    postOnMain(() -> listener.onGenerationError(e.getMessage()));
                }
                return;
            }

            String cachedText = DocumentTextCache.get(appContext, contentKey);
            if (cachedText != null) {
                requestQuestions(apiKey, cachedText, pickedFile, listener);
                return;
            }

            try {
                postOnMain(() -> listener.onStatusUpdate(R.string.extracting_pdf_text));
                String rawText = extractPdfText(contentResolver, fileUri);
                postOnMain(() -> listener.onStatusUpdate(R.string.cleaning_pdf_text));
                quizAgent.cleanDocumentText(apiKey, rawText, new QuizAgent.Callback<String>() {
                    @Override
                    public void onSuccess(String cleanedText) {
                        DocumentTextCache.save(appContext, pickedFile.contentKey, cleanedText);
                        requestQuestions(apiKey, cleanedText, pickedFile, listener);
                    }

                    @Override
                    public void onError(Exception e) {
                        listener.onGenerationError(e.getMessage());
                    }
                });
            } catch (IOException e) {
                postOnMain(() -> listener.onExtractionError(e.getMessage()));
            }
        });
    }

    private void requestQuestions(String apiKey, String documentText, PickedFile pickedFile, Listener listener) {
        quizAgent.generateQuiz(apiKey, documentText, new QuizAgent.Callback<QuizResult>() {
            @Override
            public void onSuccess(QuizResult result) {
                QuizCacheStore.save(appContext, pickedFile, result.topic, result.questions);
                listener.onSuccess(result.topic, result.questions);
            }

            @Override
            public void onError(Exception e) {
                listener.onGenerationError(e.getMessage());
            }
        });
    }

    private void postOnMain(Runnable action) {
        mainHandler.post(action);
    }

    private String extractPdfText(ContentResolver contentResolver, Uri uri) throws IOException {
        try (InputStream inputStream = contentResolver.openInputStream(uri)) {
            try (PDDocument document = PDDocument.load(inputStream)) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            }
        }
    }

    private String readFileText(ContentResolver contentResolver, Uri uri) throws IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(contentResolver.openInputStream(uri), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        }
        return text.toString();
    }

    private String computeContentHash(ContentResolver contentResolver, Uri uri) throws IOException {
        try (InputStream inputStream = contentResolver.openInputStream(uri)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_DIGITS[value >>> 4];
            hexChars[i * 2 + 1] = HEX_DIGITS[value & 0x0F];
        }
        return new String(hexChars);
    }
}
