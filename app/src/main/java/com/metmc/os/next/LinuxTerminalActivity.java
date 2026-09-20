package com.metmc.os.next;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.*;
import java.io.*;
import java.util.concurrent.Executors;

public class LinuxTerminalActivity extends Activity {
    private static final String ROOTFS = "/data/local/linux/rootfs";
    private TextView output;
    private EditText input;
    private BufferedWriter stdin;
    private Process shell;
    private ScrollView scroll;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(10,12,16));
        getWindow().setNavigationBarColor(Color.rgb(10,12,16));
        hideSystemBars();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8,10,13));

        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(14,0,8,0);
        titleBar.setBackgroundColor(Color.rgb(24,28,34));

        TextView title = new TextView(this);
        title.setText("Terminal");
        title.setTextColor(Color.WHITE);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);

        Button close = windowButton("×");
        close.setOnClickListener(v -> finish());
        titleBar.addView(title, new LinearLayout.LayoutParams(0,54,1));
        titleBar.addView(close, new LinearLayout.LayoutParams(54,54));
        root.addView(titleBar);

        output = new TextView(this);
        output.setTextColor(Color.rgb(225,232,240));
        output.setTextSize(13);
        output.setGravity(Gravity.TOP|Gravity.START);
        output.setPadding(12,10,12,10);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextIsSelectable(true);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(8,10,13));
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout commandBar = new LinearLayout(this);
        commandBar.setGravity(Gravity.CENTER_VERTICAL);
        commandBar.setPadding(8,6,8,6);
        commandBar.setBackgroundColor(Color.rgb(20,24,30));

        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(125,135,148));
        input.setHint("Command");
        input.setTextSize(13);
        input.setTypeface(Typeface.MONOSPACE);
        input.setPadding(12,0,12,0);

        Button send = windowButton("Run");
        send.setOnClickListener(v -> sendCommand(input.getText().toString()));
        input.setOnEditorActionListener((v,id,event) -> {
            sendCommand(input.getText().toString());
            return true;
        });

        commandBar.addView(input, new LinearLayout.LayoutParams(0,52,1));
        commandBar.addView(send, new LinearLayout.LayoutParams(70,52));
        root.addView(commandBar);

        setContentView(root);
        startShell();
    }

    private Button windowButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(0,0,0,0);
        return b;
    }

    private void hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void startShell() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String script =
                    "R='" + ROOTFS + "'; " +
                    "mount --bind /dev \"$R/dev\" 2>/dev/null || true; " +
                    "mount --bind /dev/pts \"$R/dev/pts\" 2>/dev/null || true; " +
                    "mount -t proc proc \"$R/proc\" 2>/dev/null || true; " +
                    "mount -t sysfs sys \"$R/sys\" 2>/dev/null || true; " +
                    "mkdir -p \"$R/tmp\" \"$R/run\"; " +
                    "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; " +
                    "export HOME=/root; export TERM=xterm-256color; " +
                    "export LANG=C.UTF-8; export LC_ALL=C.UTF-8; " +
                    "exec chroot \"$R\" /bin/bash -l";
                shell = new ProcessBuilder("su","-c",script).redirectErrorStream(true).start();
                stdin = new BufferedWriter(new OutputStreamWriter(shell.getOutputStream()));
                readOutput(shell.getInputStream());
            } catch (Exception e) {
                append("\n[METMC] Unable to start Debian terminal: " + e + "\n");
            }
        });
    }

    private void readOutput(InputStream stream) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                final String text = new String(buffer,0,count);
                runOnUiThread(() -> {
                    output.append(text);
                    scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                });
            }
        } catch (Exception e) {
            append("\n[Terminal closed]\n");
        }
    }

    private void sendCommand(String command) {
        if (command == null || command.trim().isEmpty() || stdin == null) return;
        try {
            stdin.write(command);
            stdin.newLine();
            stdin.flush();
            input.setText("");
        } catch (Exception e) {
            append("\n[Command failed] " + e + "\n");
        }
    }

    private void append(String text) {
        runOnUiThread(() -> output.append(text));
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override protected void onDestroy() {
        try {
            if (stdin != null) { stdin.write("exit"); stdin.newLine(); stdin.flush(); }
        } catch (Exception ignored) {}
        try { if (shell != null) shell.destroy(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}