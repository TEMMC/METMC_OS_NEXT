package com.metmc.os.next;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.concurrent.Executor;

public class LockScreenActivity extends Activity {
    LinearLayout root;
    EditText credential;
    TextView message;
    ArrayList<Integer> pattern = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.rgb(3,6,10));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!SecurityStore.locked(this)) finish();
    }

    TextView text(String s,float size) {
        TextView v=new TextView(this);
        v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setGravity(Gravity.CENTER);
        return v;
    }

    Button button(String s) {
        Button b=new Button(this);
        b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setAllCaps(false);
        b.setMinHeight(0);b.setMinWidth(0);return b;
    }

    void build() {
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(28,24,28,24);
        root.setBackgroundColor(Color.rgb(4,9,15));

        TextView brand=text("METMC OS NEXT",20);
        brand.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        root.addView(brand,new LinearLayout.LayoutParams(-1,48));

        String time=new java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault()).format(new java.util.Date());
        TextView clock=text(time,54);
        root.addView(clock,new LinearLayout.LayoutParams(-1,78));
        root.addView(text(new java.text.SimpleDateFormat("EEEE, dd MMMM yyyy",java.util.Locale.getDefault()).format(new java.util.Date()),14),new LinearLayout.LayoutParams(-1,38));
        root.addView(text("Desktop locked",14),new LinearLayout.LayoutParams(-1,34));

        String mode=SecurityStore.mode(this);
        if(SecurityStore.PATTERN.equals(mode)) buildPattern();
        else buildText(mode);

        if(SecurityStore.deviceAuth(this)) {
            Button device=button("Use device PIN / biometric");
            device.setOnClickListener(v->authenticateDevice());
            root.addView(device,new LinearLayout.LayoutParams(360,54));
        }
        message=text("",12);
        message.setTextColor(0xffff7180);
        root.addView(message,new LinearLayout.LayoutParams(-1,42));

        setContentView(root);
    }

    void buildText(String mode) {
        credential=new EditText(this);
        credential.setSingleLine(true);
        credential.setGravity(Gravity.CENTER);
        credential.setTextColor(Color.WHITE);
        credential.setHintTextColor(0xff8190a1);
        credential.setHint(SecurityStore.PIN.equals(mode) ? "Enter PIN" : "Enter password");
        credential.setTextSize(18);
        credential.setInputType(SecurityStore.PIN.equals(mode) ? InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(credential,new LinearLayout.LayoutParams(360,58));

        Button unlock=button("Unlock");
        unlock.setOnClickListener(v->verifyText());
        root.addView(unlock,new LinearLayout.LayoutParams(360,58));
        credential.setOnEditorActionListener((v,id,event)->{verifyText();return true;});
        credential.requestFocus();
    }

    void buildPattern() {
        TextView hint=text("Enter your pattern",14);
        root.addView(hint,new LinearLayout.LayoutParams(-1,34));
        GridLayout grid=new GridLayout(this);
        grid.setColumnCount(3);grid.setRowCount(3);
        for(int i=0;i<9;i++){
            final int point=i;
            Button b=button(String.valueOf(i+1));
            b.setOnClickListener(v->{if(!pattern.contains(point)){pattern.add(point);b.setText("●");}});
            GridLayout.LayoutParams gp=new GridLayout.LayoutParams();
            gp.width=0;gp.height=62;gp.columnSpec=GridLayout.spec(i%3,1f);gp.rowSpec=GridLayout.spec(i/3,1f);gp.setMargins(6,6,6,6);
            grid.addView(b,gp);
        }
        root.addView(grid,new LinearLayout.LayoutParams(300,220));
        Button unlock=button("Unlock");
        unlock.setOnClickListener(v->verifyPattern());
        root.addView(unlock,new LinearLayout.LayoutParams(300,54));
    }

    void verifyText() {
        String value=credential==null?"":credential.getText().toString();
        if(SecurityStore.verify(this,value)) unlock(); else fail();
    }

    void verifyPattern() {
        StringBuilder s=new StringBuilder();
        for(Integer n:pattern)s.append(n).append('-');
        if(SecurityStore.verify(this,s.toString())) unlock(); else {pattern.clear();build();}
    }

    void fail() {
        message.setText("Incorrect credential.");
        credential.setText("");
        credential.requestFocus();
    }

    void unlock() {
        SecurityStore.unlock(this);
        Intent i=new Intent(this,MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    void authenticateDevice() {
        if(android.os.Build.VERSION.SDK_INT<30)return;
        try{
            BiometricManager bm=getSystemService(BiometricManager.class);
            int can=bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG|BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            if(can!=BiometricManager.BIOMETRIC_SUCCESS){message.setText("Device authentication is not available.");return;}
            Executor executor=getMainExecutor();
            BiometricPrompt prompt=new BiometricPrompt.Builder(this)
                    .setTitle("Unlock METMC OS")
                    .setSubtitle("Use your device security")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG|BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build();
            prompt.authenticate(null,executor,new BiometricPrompt.AuthenticationCallback(){
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){runOnUiThread(()->unlock());}
                @Override public void onAuthenticationError(int code,CharSequence msg){runOnUiThread(()->message.setText(String.valueOf(msg)));}
            });
        }catch(Exception e){message.setText("Authentication unavailable.");}
    }

    @Override public void onBackPressed() {}
}
