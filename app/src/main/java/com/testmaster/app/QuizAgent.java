package com.testmaster.app;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuizAgent {

    public interface Callback {
        void onSuccess(QuizResult result);
        void onError(Exception e);
    }

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL = "claude-haiku-4-5-20251001";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_DOCUMENT_CHARS = 20000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void generateQuiz(String apiKey, String documentText, Callback callback) {
        executor.execute(() -> {
            try {
                QuizResult result = requestQuiz(apiKey, documentText);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e));
            }
        });
    }

    private QuizResult requestQuiz(String apiKey, String documentText) throws IOException {
        String truncated = documentText.length() > MAX_DOCUMENT_CHARS
                ? documentText.substring(0, MAX_DOCUMENT_CHARS)
                : documentText;

        String prompt = "Based only on the following text, generate exactly 10 multiple choice quiz questions. "
                + "Each question must have exactly 4 answer choices with exactly one correct answer. "
                + "Do not use any information outside the given text. "
                + "Also come up with a short topic title (at most 6 words) summarizing what the text is about. "
                + "Respond with ONLY a valid JSON object (no markdown, no commentary) with this shape: "
                + "{\"topic\": string, \"questions\": [{\"question\": string, "
                + "\"choices\": [string, string, string, string], \"correctIndex\": integer 0-3}, ...]}."
                + "\n\nText:\n" + truncated;

        JSONObject body;
        try {
            JSONObject message = new JSONObject()
                    .put("role", "user")
                    .put("content", prompt);
            body = new JSONObject()
                    .put("model", MODEL)
                    .put("max_tokens", 3000)
                    .put("temperature", 0)
                    .put("messages", new JSONArray().put(message));
        } catch (Exception e) {
            throw new IOException("Failed to build request", e);
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(API_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("x-api-key", apiKey);
        connection.setRequestProperty("anthropic-version", ANTHROPIC_VERSION);
        connection.setRequestProperty("content-type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int status = connection.getResponseCode();
        String responseBody = readStream(status >= 400 ? connection.getErrorStream() : connection.getInputStream());

        if (status >= 400) {
            throw new IOException("Anthropic API error (" + status + "): " + responseBody);
        }

        return parseQuiz(responseBody);
    }

    private String readStream(InputStream inputStream) {
        if (inputStream == null) {
            return "";
        }
        try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name())) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private QuizResult parseQuiz(String responseBody) throws IOException {
        try {
            JSONObject response = new JSONObject(responseBody);
            JSONArray content = response.getJSONArray("content");
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                JSONObject block = content.getJSONObject(i);
                if ("text".equals(block.optString("type"))) {
                    text.append(block.getString("text"));
                }
            }

            JSONObject quizJson = new JSONObject(extractJsonObject(text.toString()));
            String topic = quizJson.getString("topic");
            JSONArray questionsJson = quizJson.getJSONArray("questions");

            List<QuizQuestion> questions = new ArrayList<>();
            for (int i = 0; i < questionsJson.length(); i++) {
                JSONObject q = questionsJson.getJSONObject(i);
                JSONArray choicesJson = q.getJSONArray("choices");
                List<String> choices = new ArrayList<>();
                for (int j = 0; j < choicesJson.length(); j++) {
                    choices.add(choicesJson.getString(j));
                }
                questions.add(new QuizQuestion(q.getString("question"), choices, q.getInt("correctIndex")));
            }
            return new QuizResult(topic, questions);
        } catch (Exception e) {
            throw new IOException("Failed to parse quiz response: " + e.getMessage(), e);
        }
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) {
            throw new IllegalArgumentException("No JSON object found in response");
        }
        return text.substring(start, end + 1);
    }
}
