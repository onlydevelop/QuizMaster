package com.testmaster.app;

import android.net.Uri;
import android.os.Bundle;
import android.widget.EditText;
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

public class MainActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::onFilePicked);

    private TextView statusText;
    private Uri selectedFileUri;

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

    private static final int REDACT_VISIBLE_CHARS = 15;

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
        updateStatus();
    }

    private void updateStatus() {
        if (selectedFileUri == null) {
            statusText.setText(R.string.error_no_file);
        } else if (ApiKeyStore.get(this) == null) {
            statusText.setText(R.string.error_no_api_key);
        } else {
            statusText.setText(null);
        }
    }
}
