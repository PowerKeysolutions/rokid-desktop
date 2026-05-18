package com.rokid.desktop;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String DEFAULT_NUC_IP = "192.168.1.146";
    private static final String PREFS_NAME = "rokid_desktop";
    private static final String KEY_IP = "last_ip";
    private static final int AUTO_CONNECT_SECS = 3;

    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedIp = prefs.getString(KEY_IP, DEFAULT_NUC_IP);

        TextView statusText = findViewById(R.id.status_text);
        EditText ipInput = findViewById(R.id.ip_input);
        Button connectBtn = findViewById(R.id.connect_btn);
        Button cancelBtn = findViewById(R.id.cancel_btn);

        ipInput.setText(savedIp);
        ipInput.setSelection(ipInput.getText().length());
        ipInput.setVisibility(View.GONE);
        connectBtn.setVisibility(View.GONE);
        cancelBtn.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.VISIBLE);

        countDownTimer = new CountDownTimer(AUTO_CONNECT_SECS * 1000L, 1000) {
            @Override
            public void onTick(long ms) {
                statusText.setText("Conectando a " + savedIp + "...\n(" + (ms / 1000 + 1) + "s — toca para cancelar)");
            }
            @Override
            public void onFinish() {
                cancelBtn.setVisibility(View.GONE);
                statusText.setVisibility(View.GONE);
                launch(savedIp, prefs);
            }
        }.start();

        cancelBtn.setOnClickListener(v -> {
            countDownTimer.cancel();
            cancelBtn.setVisibility(View.GONE);
            statusText.setVisibility(View.GONE);
            ipInput.setVisibility(View.VISIBLE);
            connectBtn.setVisibility(View.VISIBLE);
        });

        connectBtn.setOnClickListener(v -> {
            String ip = ipInput.getText().toString().trim();
            if (ip.isEmpty()) return;
            launch(ip, prefs);
        });
    }

    private void launch(String ip, SharedPreferences prefs) {
        prefs.edit().putString(KEY_IP, ip).apply();
        String streamUrl = "http://" + ip + ":8889/desktop/";
        Intent intent = new Intent(this, StreamActivity.class);
        intent.putExtra("stream_url", streamUrl);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
    }
}
