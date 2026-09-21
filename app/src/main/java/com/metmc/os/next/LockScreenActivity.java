package com.metmc.os.next;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.Random;
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
        FrameLayout frame=new FrameLayout(this);
        HackerBackdrop backdrop=new HackerBackdrop(this);
        frame.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(34,18,34,18);
        GradientDrawable panelBg=new GradientDrawable();
        panelBg.setColor(0xd9081110);
        panelBg.setStroke(1,0x7739ff88);
        panelBg.setCornerRadius(18);
        panel.setBackground(panelBg);
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(
                Math.min(650,getResources().getDisplayMetrics().widthPixels-48),
                -1,Gravity.CENTER);
        pp.topMargin=18;pp.bottomMargin=18;
        frame.addView(panel,pp);

        TextView brand=text("[ METMC OS NEXT // SECURE SHELL ]",17);
        brand.setTextColor(0xff39ff88);
        brand.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        panel.addView(brand,new LinearLayout.LayoutParams(-1,40));

        TextView status=text("ACCESS CONTROL  •  ENCRYPTED SESSION  •  SYSTEM LOCKED",10);
        status.setTextColor(0xff79b893);
        status.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);
        panel.addView(status,new LinearLayout.LayoutParams(-1,28));

        String time=new java.text.SimpleDateFormat("HH:mm:ss",java.util.Locale.getDefault()).format(new java.util.Date());
        TextView clock=text(time,48);
        clock.setTextColor(0xffd9ffe6);
        clock.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        panel.addView(clock,new LinearLayout.LayoutParams(-1,70));

        TextView date=text(new java.text.SimpleDateFormat("EEE  dd MMM yyyy",java.util.Locale.getDefault()).format(new java.util.Date()),12);
        date.setTextColor(0xff73a88a);
        date.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);
        panel.addView(date,new LinearLayout.LayoutParams(-1,30));

        TextView prompt=text("root@metmc:~$ authenticate --user Dr_TEMMC",12);
        prompt.setGravity(Gravity.CENTER);
        prompt.setTextColor(0xff39ff88);
        prompt.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);
        panel.addView(prompt,new LinearLayout.LayoutParams(-1,34));

        TextView modeText=text("AUTH MODE: "+SecurityStore.mode(this).toUpperCase(java.util.Locale.US)+"   |   FIREWALL: ACTIVE",10);
        modeText.setTextColor(0xff789b86);
        modeText.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);
        panel.addView(modeText,new LinearLayout.LayoutParams(-1,26));

        String mode=SecurityStore.mode(this);
        if(SecurityStore.PATTERN.equals(mode)) buildPattern(panel);
        else buildText(mode,panel);

        if(SecurityStore.deviceAuth(this)) {
            Button device=button("[ USE DEVICE CREDENTIAL / BIOMETRIC ]");
            device.setTextColor(0xff8dffb0);
            device.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
            device.setOnClickListener(v->authenticateDevice());
            panel.addView(device,new LinearLayout.LayoutParams(-1,50));
        }
        message=text("",11);
        message.setTextColor(0xffff6677);
        message.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        panel.addView(message,new LinearLayout.LayoutParams(-1,36));

        TextView footer=text("METMC SECURITY // UNAUTHORIZED ACCESS WILL BE LOGGED",9);
        footer.setTextColor(0xff557663);
        footer.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);
        panel.addView(footer,new LinearLayout.LayoutParams(-1,28));

        setContentView(frame);
    }

    void buildText(String mode,LinearLayout panel) {
        credential=new EditText(this);
        credential.setSingleLine(true);
        credential.setGravity(Gravity.CENTER);
        credential.setTextColor(0xffd9ffe6);
        credential.setHintTextColor(0xff4e8062);
        credential.setHint(SecurityStore.PIN.equals(mode) ? "[ ENTER PIN ]" : "[ ENTER PASSWORD ]");
        credential.setTextSize(16);
        credential.setTypeface(Typeface.MONOSPACE,Typeface.NORMAL);
        credential.setInputType(SecurityStore.PIN.equals(mode) ? InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        GradientDrawable inputBg=new GradientDrawable();
        inputBg.setColor(0xff07100c);inputBg.setStroke(1,0xff2f7048);inputBg.setCornerRadius(8);
        credential.setBackground(inputBg);
        panel.addView(credential,new LinearLayout.LayoutParams(-1,52));

        Button unlock=button("[ AUTHENTICATE ]");
        unlock.setTextColor(0xff39ff88);
        unlock.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        unlock.setOnClickListener(v->verifyText());
        panel.addView(unlock,new LinearLayout.LayoutParams(-1,50));
        credential.setOnEditorActionListener((v,id,event)->{verifyText();return true;});
        credential.requestFocus();
    }

    void buildPattern(LinearLayout panel) {
        TextView hint=text("DRAW ACCESS PATTERN",11);
        hint.setTextColor(0xff79b893);
        hint.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        panel.addView(hint,new LinearLayout.LayoutParams(-1,28));
        GridLayout grid=new GridLayout(this);
        grid.setColumnCount(3);grid.setRowCount(3);
        for(int i=0;i<9;i++){
            final int point=i;
            Button b=button(String.valueOf(i+1));
            b.setTextColor(0xff39ff88);
            b.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
            b.setOnClickListener(v->{if(!pattern.contains(point)){pattern.add(point);b.setText("●");}});
            GradientDrawable bg=new GradientDrawable();
            bg.setColor(0xff07100c);bg.setStroke(1,0xff2f7048);bg.setCornerRadius(8);b.setBackground(bg);
            GridLayout.LayoutParams gp=new GridLayout.LayoutParams();
            gp.width=0;gp.height=52;gp.columnSpec=GridLayout.spec(i%3,1f);gp.rowSpec=GridLayout.spec(i/3,1f);gp.setMargins(5,4,5,4);
            grid.addView(b,gp);
        }
        panel.addView(grid,new LinearLayout.LayoutParams(300,172));
        Button unlock=button("[ AUTHENTICATE ]");
        unlock.setTextColor(0xff39ff88);
        unlock.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        unlock.setOnClickListener(v->verifyPattern());
        panel.addView(unlock,new LinearLayout.LayoutParams(300,50));
    }

    class HackerBackdrop extends View {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        String chars="01ABCDEFGHIJKLMNOPQRSTUVWXYZ#$@%&<>/\\";
        Random random=new Random(41);
        float[] drops;
        HackerBackdrop(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        protected void onSizeChanged(int w,int h,int ow,int oh){
            int count=Math.max(18,w/24); drops=new float[count];
            for(int i=0;i<drops.length;i++) drops[i]=random.nextInt(Math.max(1,h));
        }
        protected void onDraw(Canvas c){
            int w=getWidth(),h=getHeight();
            c.drawColor(Color.rgb(2,7,5));
            p.setTypeface(Typeface.MONOSPACE);p.setTextSize(13);
            for(int i=0;i<drops.length;i++){
                float x=i*24+8,y=drops[i];
                p.setColor((i%5==0)?0xff39ff88:0xff174c2e);
                for(int j=0;j<7;j++){
                    char ch=chars.charAt((i*13+j*7+(int)(y/18))%chars.length());
                    c.drawText(String.valueOf(ch),x,y-j*18,p);
                }
                drops[i]+=2.5f;
                if(drops[i]>h+130)drops[i]=-random.nextInt(260);
            }
            p.setColor(0x2239ff88);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);
            for(int x=0;x<w;x+=48)c.drawLine(x,0,x,h,p);
            for(int y=0;y<h;y+=48)c.drawLine(0,y,w,y,p);
            p.setStyle(Paint.Style.FILL);
            postInvalidateDelayed(80);
        }
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
