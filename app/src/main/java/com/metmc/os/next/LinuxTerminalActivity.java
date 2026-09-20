package com.metmc.os.next;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.*;
import java.io.*;
import java.util.concurrent.Executors;

public class LinuxTerminalActivity extends Activity {
    private static final String ROOTFS = "/data/local/linux/rootfs";
    private TextView output;
    private EditText input;
    private BufferedWriter stdin;
    private Process shell;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8,11,15));

        TextView title = new TextView(this);
        title.setText("METMC Terminal  •  Debian");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(18,0,18,0);
        title.setBackgroundColor(Color.rgb(20,27,36));
        root.addView(title,new LinearLayout.LayoutParams(-1,58));

        output = new TextView(this);
        output.setTextColor(Color.rgb(220,230,240));
        output.setTextSize(13);
        output.setGravity(Gravity.TOP|Gravity.START);
        output.setPadding(14,14,14,14);
        output.setTypeface(android.graphics.Typeface.MONOSPACE);
        output.setText("Starting Debian chroot...\n");
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(output);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(8,8,8,8);
        bar.setBackgroundColor(Color.rgb(18,24,32));
        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(120,135,150));
        input.setHint("Enter Debian command...");
        input.setTextSize(14);
        input.setTypeface(android.graphics.Typeface.MONOSPACE);
        Button send = new Button(this);
        send.setText("Run");
        bar.addView(input,new LinearLayout.LayoutParams(0,58,1));
        bar.addView(send,new LinearLayout.LayoutParams(90,58));
        root.addView(bar);
        setContentView(root);

        Runnable run = () -> sendCommand(input.getText().toString());
        send.setOnClickListener(v -> run.run());
        input.setOnEditorActionListener((v,id,event)->{ run.run(); return true; });

        startShell(scroll);
    }

    private void startShell(ScrollView scroll) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String script =
                    "R=" + ROOTFS + "; " +
                    "mount --bind /dev "$R/dev" 2>/dev/null || true; " +
                    "mount --bind /dev/pts "$R/dev/pts" 2>/dev/null || true; " +
                    "mount -t proc proc "$R/proc" 2>/dev/null || true; " +
                    "mount -t sysfs sys "$R/sys" 2>/dev/null || true; " +
                    "mkdir -p "$R/tmp" "$R/run"; " +
                    "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; " +
                    "export HOME=/root; export TERM=xterm-256color; " +
                    "export LANG=C.UTF-8; export LC_ALL=C.UTF-8; " +
                    "cd "$R"; " +
                    "exec chroot "$R" /bin/bash -l";
                shell = new ProcessBuilder("su","-c",script)
                        .redirectErrorStream(true).start();
                stdin = new BufferedWriter(new OutputStreamWriter(shell.getOutputStream()));
                readOutput(shell.getInputStream(),scroll);
            } catch(Exception e) {
                append("\n[METMC] Debian chroot could not start: "+e+"\n");
            }
        });
    }

    private void readOutput(InputStream stream, ScrollView scroll) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(stream));
            char[] buf = new char[2048];
            int n;
            while((n=r.read(buf))!=-1) {
                final String s=new String(buf,0,n);
                runOnUiThread(() -> { append(s); scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN)); });
            }
        } catch(Exception e) { append("\n[terminal closed]\n"); }
    }

    private void sendCommand(String command) {
        if(command==null || command.trim().isEmpty() || stdin==null) return;
        final String cmd=command;
        input.setText("");
        try {
            stdin.write(cmd);
            stdin.newLine();
            stdin.flush();
        } catch(Exception e) { append("\n[write failed] "+e+"\n"); }
    }

    private void append(String s) {
        output.append(s);
    }

    @Override protected void onDestroy() {
        try { if(stdin!=null) { stdin.write("exit"); stdin.newLine(); stdin.flush(); } } catch(Exception ignored){}
        try { if(shell!=null) shell.destroy(); } catch(Exception ignored){}
        super.onDestroy();
    }
}