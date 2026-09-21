package com.metmc.os.next;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.concurrent.Executor;

public class SecurityActivity extends Activity {
    LinearLayout root;
    TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setStatusBarColor(Color.rgb(5,8,12));
        getWindow().setNavigationBarColor(Color.rgb(5,8,12));
        build();
    }

    TextView label(String text, int size) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(size);
        v.setPadding(0,8,0,8);
        return v;
    }

    Button action(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        return b;
    }

    void build() {
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32,28,32,28);
        root.setBackgroundColor(Color.rgb(8,13,19));

        TextView title = label("METMC Security", 26);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        root.addView(title);
        status = label(statusText(), 13);
        root.addView(status);

        root.addView(label("Lock method",17));
        String[] methods = {"None","PIN","Password","Pattern"};
        String[] values = {SecurityStore.NONE,SecurityStore.PIN,SecurityStore.PASSWORD,SecurityStore.PATTERN};
        for (int i=0;i<methods.length;i++) {
            Button b = action(methods[i] + (SecurityStore.mode(this).equals(values[i]) ? "  ✓" : ""));
            final String mode = values[i];
            b.setOnClickListener(v -> {
                if (SecurityStore.NONE.equals(mode)) {
                    SecurityStore.clearCredential(this);
                    refresh();
                } else if (SecurityStore.PATTERN.equals(mode)) {
                    showPatternSetup();
                } else {
                    showTextCredentialSetup(mode);
                }
            });
            root.addView(b,new LinearLayout.LayoutParams(-1,58));
        }

        Button lock = action("Lock METMC now");
        lock.setOnClickListener(v -> {
            if (!SecurityStore.enabled(this)) {
                Toast.makeText(this,"Set a PIN, password, or pattern first.",Toast.LENGTH_SHORT).show();
                return;
            }
            SecurityStore.lock(this);
            startActivity(new Intent(this,LockScreenActivity.class));
            finish();
        });
        root.addView(lock,new LinearLayout.LayoutParams(-1,58));

        CheckBox auto = new CheckBox(this);
        auto.setText("Lock when leaving METMC");
        auto.setTextColor(Color.WHITE);
        auto.setChecked(SecurityStore.autoLock(this));
        auto.setOnCheckedChangeListener((buttonView,isChecked)->SecurityStore.setAutoLock(this,isChecked));
        root.addView(auto);

        CheckBox device = new CheckBox(this);
        device.setText("Allow device PIN/biometric unlock");
        device.setTextColor(Color.WHITE);
        device.setChecked(SecurityStore.deviceAuth(this));
        device.setOnCheckedChangeListener((buttonView,isChecked)->SecurityStore.setDeviceAuth(this,isChecked));
        root.addView(device);

        Button test = action("Test device authentication");
        test.setOnClickListener(v -> authenticateDevice());
        root.addView(test,new LinearLayout.LayoutParams(-1,58));

        Button close = action("Back to METMC");
        close.setOnClickListener(v -> finish());
        root.addView(close,new LinearLayout.LayoutParams(-1,58));

        scroll.addView(root);
        setContentView(scroll);
    }

    String statusText() {
        String mode = SecurityStore.mode(this);
        return "Current protection: " + ("none".equals(mode) ? "None" : mode.toUpperCase()) +
                "\nCredentials are stored as a salted SHA-256 verifier in private app storage.";
    }

    void refresh() {
        build();
    }

    void showTextCredentialSetup(String mode) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(8,4,8,0);

        EditText first = new EditText(this);
        first.setSingleLine(true);
        first.setTextColor(Color.WHITE);
        first.setHint(mode.equals(SecurityStore.PIN) ? "New PIN (4–12 digits)" : "New password");
        first.setInputType(mode.equals(SecurityStore.PIN) ? 2 : 129);
        box.addView(first);

        EditText second = new EditText(this);
        second.setSingleLine(true);
        second.setTextColor(Color.WHITE);
        second.setHint("Confirm");
        second.setInputType(mode.equals(SecurityStore.PIN) ? 2 : 129);
        box.addView(second);

        new AlertDialog.Builder(this).setTitle("Set " + mode)
                .setView(box)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Save",(d,w)->{
                    String a=first.getText().toString();
                    String b=second.getText().toString();
                    boolean valid = mode.equals(SecurityStore.PIN) ? a.matches("\\d{4,12}") : a.length()>=4;
                    if(!valid || !a.equals(b)) {
                        Toast.makeText(this,"Credential is invalid or does not match.",Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SecurityStore.setCredential(this,mode,a);
                    refresh();
                }).show();
    }

    void showPatternSetup() {
        final ArrayList<Integer> sequence = new ArrayList<>();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(18,8,18,8);
        TextView hint = label("Tap at least 4 points in order.",13);
        box.addView(hint);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(3);
        for(int i=0;i<9;i++){
            final int point=i;
            Button b=action(String.valueOf(i+1));
            b.setOnClickListener(v->{
                if(!sequence.contains(point)){sequence.add(point);b.setText("●");hint.setText("Pattern: "+sequence.size()+" points");}
            });
            GridLayout.LayoutParams gp=new GridLayout.LayoutParams();
            gp.width=0;gp.height=72;gp.columnSpec=GridLayout.spec(i%3,1f);gp.rowSpec=GridLayout.spec(i/3,1f);
            gp.setMargins(6,6,6,6);grid.addView(b,gp);
        }
        box.addView(grid);
        new AlertDialog.Builder(this).setTitle("Set Pattern").setView(box)
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Save",(d,w)->{
                    if(sequence.size()<4){Toast.makeText(this,"Pattern must contain at least 4 points.",Toast.LENGTH_SHORT).show();return;}
                    StringBuilder s=new StringBuilder();
                    for(Integer n:sequence)s.append(n).append('-');
                    SecurityStore.setCredential(this,SecurityStore.PATTERN,s.toString());
                    refresh();
                }).show();
    }

    void authenticateDevice() {
        if (android.os.Build.VERSION.SDK_INT < 30) {
            Toast.makeText(this,"Device authentication requires Android 11 or newer.",Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            BiometricManager bm = getSystemService(BiometricManager.class);
            int can = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            if(can != BiometricManager.BIOMETRIC_SUCCESS) {
                Toast.makeText(this,"No supported device credential/biometric is available.",Toast.LENGTH_SHORT).show();
                return;
            }
            Executor executor=getMainExecutor();
            BiometricPrompt prompt=new BiometricPrompt.Builder(this)
                    .setTitle("METMC Security Test")
                    .setSubtitle("Use your device biometric or PIN/pattern/password")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();
            prompt.authenticate(null,executor,new BiometricPrompt.AuthenticationCallback(){
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){Toast.makeText(SecurityActivity.this,"Authentication succeeded.",Toast.LENGTH_SHORT).show();}
                @Override public void onAuthenticationError(int code,CharSequence msg){Toast.makeText(SecurityActivity.this,String.valueOf(msg),Toast.LENGTH_SHORT).show();}
            });
        } catch(Exception e) {
            Toast.makeText(this,"Authentication unavailable: "+e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }
}
