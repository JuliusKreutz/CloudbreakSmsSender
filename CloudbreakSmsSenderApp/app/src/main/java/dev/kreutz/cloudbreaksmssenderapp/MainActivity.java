package dev.kreutz.cloudbreaksmssenderapp;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.github.javiersantos.appupdater.AppUpdater;
import com.github.javiersantos.appupdater.enums.UpdateFrom;

/**
 * This class provides basic permission management and the entrypoint to the SmsSenderService
 *
 * @see dev.kreutz.cloudbreaksmssenderapp.SmsSenderService
 */
public class MainActivity extends AppCompatActivity {

    /**
     * The request code used for the permission request
     */
    private final int REQUEST_CODE = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new AppUpdater(this)
                .setUpdateFrom(UpdateFrom.GITHUB)
                .setGitHubUserAndRepo("JuliusKreutz", "CloudbreakSmsSender")
                .setTitleOnUpdateAvailable("Update available")
                .setContentOnUpdateAvailable("Do you want to update?")
                .setButtonDoNotShowAgain(null)
                .start();

        Button button = findViewById(R.id.button);
        TextView textView = findViewById(R.id.textView);

        SharedPreferences sharedPreferences = getSharedPreferences("name", MODE_PRIVATE);
        String name = sharedPreferences.getString("name", "Name");
        textView.setText(name);

        button.setOnClickListener(v -> {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("name", textView.getText().toString());
            editor.apply();

            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();

            if (readyToStart())
                SmsSenderService.start(this);
        });

        if (readyToStart()) {
            SmsSenderService.start(this);
        } else {
            requestPermissions(new String[]{Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS}, REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE && readyToStart()) {
            SmsSenderService.start(this);
        }
    }

    /**
     * @return true if the permissions where granted
     */
    private boolean readyToStart() {
        boolean permissionsGranted = checkSelfPermission(Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;

        SharedPreferences sharedPreferences = getSharedPreferences("name", MODE_PRIVATE);
        String name = sharedPreferences.getString("name", null);

        return name != null && permissionsGranted;
    }
}