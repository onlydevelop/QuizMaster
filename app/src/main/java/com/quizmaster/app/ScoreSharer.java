package com.quizmaster.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.util.Log;
import android.view.View;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Captures a screenshot of the quiz-complete view and launches a share
 * sheet for it, alongside a short text summary of the score.
 */
public class ScoreSharer {

    private static final String TAG = "ScoreSharer";

    private final Context context;

    public ScoreSharer(Context context) {
        this.context = context;
    }

    public boolean share(View captureView, QuizSummary summary) {
        Uri screenshotUri = captureViewToUri(captureView);
        if (screenshotUri == null) {
            return false;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/png");
        shareIntent.putExtra(Intent.EXTRA_STREAM, screenshotUri);
        shareIntent.putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_score_text,
                summary.score, summary.totalQuestions, summary.topic));
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_score_chooser_title)));
        return true;
    }

    private Uri captureViewToUri(View view) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);

            File imagesDir = new File(context.getCacheDir(), "images");
            imagesDir.mkdirs();
            File imageFile = new File(imagesDir, "quiz_score.png");
            try (FileOutputStream out = new FileOutputStream(imageFile)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }

            return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", imageFile);
        } catch (IOException e) {
            Log.e(TAG, "Failed to capture score screenshot for sharing", e);
            return null;
        }
    }
}
