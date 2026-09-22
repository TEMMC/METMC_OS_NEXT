package com.metmc.os.next;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import androidx.core.content.FileProvider;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    DesktopView desktop;
    FrameLayout root;
    EditText terminalInput;
    TextView terminalOutput;
    TextView terminalPrompt;
    BufferedWriter terminalStdin;
    final ArrayList<String> terminalHistory = new ArrayList<>();
    int terminalHistoryIndex = -1;
    java.lang.Process terminalShell;
    LinearLayout terminalToolbar;
    boolean interactiveTerminalMode=false;
    boolean suppressTerminalBridge=false;
    static final int PICK_WALLPAPER = 9001;
    static final String ROOTFS = "/data/local/linux/rootfs";
    static final String DEFAULT_SEARCH = "https://search.yahoo.com/search?p=";
    static final String DEFAULT_HOME = "https://search.yahoo.com/";
    android.content.SharedPreferences prefs;
    boolean sessionRestored = false;
    boolean internalTransition = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.rgb(5,8,12));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        hideSystemBars();
        prefs = getSharedPreferences("metmc", MODE_PRIVATE);
        root = new FrameLayout(this);
        desktop = new DesktopView(this);
        root.addView(desktop, new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        requestNotificationPermission();
        if (desktop != null) desktop.restoreSession();
        loadSavedWallpaper();
        if (SecurityStore.locked(this)) {
            startActivity(new Intent(this, LockScreenActivity.class));
        }
        new Handler().postDelayed(() -> new Updater(MainActivity.this).check(), 2500);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                c.hide(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    void loadSavedWallpaper() {
        try {
            if (desktop == null || prefs.getInt("wallpaper",0) != 4 || desktop.customWallpaper != null) return;
            String saved=prefs.getString("custom_wallpaper","");
            if (saved.isEmpty()) return;
            Uri u=Uri.parse(saved);
            InputStream in=getContentResolver().openInputStream(u);
            if(in!=null){ desktop.customWallpaper=BitmapFactory.decodeStream(in); in.close(); }
        } catch(Exception ignored) {}
    }

    void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7401);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_WALLPAPER && resultCode==RESULT_OK && data!=null && data.getData()!=null){ try { Uri u=data.getData(); try { getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored) {} prefs.edit().putString("custom_wallpaper",u.toString()).putInt("wallpaper",4).apply(); if(desktop!=null){ InputStream in=getContentResolver().openInputStream(u); desktop.customWallpaper=BitmapFactory.decodeStream(in); if(in!=null) in.close(); desktop.surface=Surface.DESKTOP; desktop.invalidate(); } } catch(Exception e) { Toast.makeText(this,"Wallpaper load failed: "+e.getMessage(),Toast.LENGTH_SHORT).show(); } }
    }

    @Override protected void onPause() {
        super.onPause();
        if (desktop != null) desktop.saveSession();
    }

    @Override protected void onResume() {
        super.onResume();
        internalTransition = false;
        if (SecurityStore.locked(this)) {
            startActivity(new Intent(this, LockScreenActivity.class));
            return;
        }
        if (desktop != null) { desktop.refreshAndroidApps(); desktop.refreshFileEntries(); desktop.invalidate(); }
    }

    @Override public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!internalTransition && SecurityStore.enabled(this) && SecurityStore.autoLock(this)) {
            SecurityStore.lock(this);
        }
    }

    void lockDesktop() {
        if (!SecurityStore.enabled(this)) {
            Toast.makeText(this, "Set a PIN, password, or pattern in Security first.", Toast.LENGTH_SHORT).show();
            return;
        }
        SecurityStore.lock(this);
        internalTransition = true;
        startActivity(new Intent(this, LockScreenActivity.class));
    }

    void openSecurity() {
        internalTransition = true;
        startActivity(new Intent(this, SecurityActivity.class));
    }

    void requestDefaultBrowser() {
        if (Build.VERSION.SDK_INT < 29) return;
        try {
            android.app.role.RoleManager rm = getSystemService(android.app.role.RoleManager.class);
            if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)
                    && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
                internalTransition = true;
                startActivityForResult(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER), 7012);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Browser role is unavailable on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onBackPressed() {
        if (desktop == null) { super.onBackPressed(); return; }
        if (SecurityStore.enabled(this)) {
            lockDesktop();
            return;
        }
        if (desktop.surface != Surface.DESKTOP) {
            desktop.surface = Surface.DESKTOP;
            desktop.invalidate();
            syncTerminalOverlay();
            return;
        }
        if (desktop.activeWindow != null) {
            String name = desktop.activeWindow;
            desktop.minimizeWindow(name);
            return;
        }
        super.onBackPressed();
    }

    ArrayList<ResolveInfo> getAndroidApps() {
        PackageManager pm = getPackageManager();
        LinkedHashMap<String,ResolveInfo> found = new LinkedHashMap<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        try {
            for (ResolveInfo r : pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)) {
                if (!getPackageName().equals(r.activityInfo.packageName)) found.put(r.activityInfo.packageName + "/" + r.activityInfo.name, r);
            }
        } catch (Exception ignored) {}
        try {
            for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.MATCH_ALL)) {
                if (getPackageName().equals(ai.packageName)) continue;
                ResolveInfo r = new ResolveInfo();
                r.activityInfo = new ActivityInfo();
                r.activityInfo.applicationInfo = ai;
                r.activityInfo.packageName = ai.packageName;
                Intent li = pm.getLaunchIntentForPackage(ai.packageName);
                if (li != null && li.getComponent() != null) r.activityInfo.name = li.getComponent().getClassName();
                else r.activityInfo.name = null;
                found.put(ai.packageName, r);
            }
        } catch (Exception ignored) {}
        ArrayList<ResolveInfo> list = new ArrayList<>(found.values());
        Collections.sort(list, (a,b) -> {
            String an = String.valueOf(a.loadLabel(pm));
            String bn = String.valueOf(b.loadLabel(pm));
            int x = an.compareToIgnoreCase(bn);
            return x != 0 ? x : a.activityInfo.packageName.compareToIgnoreCase(b.activityInfo.packageName);
        });
        return list;
    }

    void launchAndroidApp(ResolveInfo info) {        try {
            if (info.activityInfo.name == null || info.activityInfo.name.isEmpty()) {
                Intent details = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + info.activityInfo.packageName));
                startActivity(details);
                return;
            }
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
            // Request a separate task/instance so several Android apps can remain open.
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                    | Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS);
            if (Build.VERSION.SDK_INT >= 24) {
                int sw = Math.max(1, desktop.getWidth());
                int sh = Math.max(1, desktop.getHeight());
                int left = Math.max(12, sw/2 - 300), top = 70;
                android.app.ActivityOptions o = android.app.ActivityOptions.makeBasic();
                o.setLaunchBounds(new Rect(left, top, Math.min(sw-12,left+Math.min(620,sw-24)), Math.min(sh-88,top+Math.min(460,sh-110))));
                try { startActivity(i, o.toBundle()); return; } catch (Exception ignored) {}
            }
            startActivity(i);
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Android App").setMessage("Unable to open " + info.loadLabel(getPackageManager()) + "\n\n" + e.getMessage()).setPositiveButton("OK", null).show();
        }
    }

    void showGlobalSearch() {
        final EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setHint("Search applications, files and open windows");
        input.setPadding(18,4,18,4);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(18,10,18,6);
        box.addView(input,new LinearLayout.LayoutParams(-1,56));
        final TextView results=new TextView(this);
        results.setTextColor(Color.WHITE);
        results.setTextSize(13);
        results.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        results.setPadding(18,12,18,12);
        box.addView(results,new LinearLayout.LayoutParams(-1,260));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("METMC Search").setView(box).setNegativeButton("Close",null).create();
        Runnable update=()->{
            String q=input.getText().toString().trim().toLowerCase(Locale.US);
            StringBuilder out=new StringBuilder();
            if(q.isEmpty()){out.append("Start typing to search apps, files and windows.");}
            else{
                int count=0;
                for(String n:new ArrayList<>(desktop.recentItems)){
                    if(n.toLowerCase(Locale.US).contains(q)){out.append("RECENT  ").append(n).append("\n");if(++count>=8)break;}
                }
                for(ResolveInfo r:desktop.androidApps){
                    String n=String.valueOf(r.loadLabel(getPackageManager()));
                    if(n.toLowerCase(Locale.US).contains(q)){out.append("APP  ").append(n).append("\n");if(++count>=12)break;}
                }
                for(String path:new ArrayList<>(desktop.fileEntries.values())){
                    String n=new File(path).getName();
                    if(n.toLowerCase(Locale.US).contains(q)){out.append("FILE  ").append(n).append("\n");if(++count>=16)break;}
                }
                if(count==0)out.append("No matching items.");
            }
            results.setText(out.toString());
        };
        input.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){update.run();}
            public void afterTextChanged(android.text.Editable e){}
        });
        dialog.setOnShowListener(d->{update.run();input.requestFocus();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);});
        dialog.show();
    }

    void launchBrowser() {
        try {
            desktop.showWindow("Browser");
            internalTransition = true;
            Intent i = new Intent(this, BrowserActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS);
            i.setData(Uri.parse(DEFAULT_HOME));
            if (Build.VERSION.SDK_INT >= 24) {
                int sw = Math.max(1, desktop.getWidth());
                int sh = Math.max(1, desktop.getHeight());
                int bw = Math.min(920, Math.max(620, sw - 120));
                int bh = Math.min(560, Math.max(420, sh - 150));
                int left = Math.max(12, (sw - bw) / 2);
                int top = Math.max(66, (sh - bh) / 2);
                android.app.ActivityOptions options = android.app.ActivityOptions.makeBasic();
                options.setLaunchBounds(new Rect(left, top,
                        Math.min(sw - 12, left + bw),
                        Math.min(sh - 92, top + bh)));
                try {
                    startActivity(i, options.toBundle());
                    return;
                } catch (Exception ignored) {}
            }
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "METMC Browser could not start: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    void launchTerminal() { desktop.showWindow("Terminal"); startTerminalShell(); }

    void launchLinuxTool(String command) {
        desktop.showWindow("Terminal");
        buildTerminalOverlay();
        syncTerminalOverlay();
        final boolean interactive = command.equals("vim") || command.equals("nano") || command.equals("htop") || command.equals("python3");
        startTerminalShell();
        final Handler handler = new Handler(Looper.getMainLooper());
        final long deadline = SystemClock.uptimeMillis() + 8000;
        Runnable send = new Runnable() {
            @Override public void run() {
                if (terminalStdin != null && terminalShell != null && terminalShell.isAlive()) {
                    try {
                        terminalStdin.write(command);
                        terminalStdin.newLine();
                        terminalStdin.flush();
                        interactiveTerminalMode = interactive;
                        if (terminalInput != null) {
                            suppressTerminalBridge=true;
                            terminalInput.setText("");
                            suppressTerminalBridge=false;
                            terminalInput.requestFocus();
                        }
                    } catch(Exception e) {
                        appendTerminal("\n[METMC] Could not start Linux program: "+e+"\n");
                    }
                } else if (SystemClock.uptimeMillis() < deadline) {
                    handler.postDelayed(this, 150);
                } else {
                    appendTerminal("\n[METMC] Linux terminal did not become ready.\n");
                }
            }
        };
        handler.post(send);
    }

    void startTerminalShell() {
        if (terminalShell != null && terminalShell.isAlive()) return;
        if (terminalOutput != null) terminalOutput.setText("METMC Debian Terminal\n");
        terminalHistoryIndex = -1;
        interactiveTerminalMode = false;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String script = "R='" + ROOTFS + "'; " +
                        "test -x \"$R/bin/bash\" || { echo '[METMC] Debian rootfs /bin/bash is missing.'; exit 1; }; " +
                        "mount --bind /dev \"$R/dev\" 2>/dev/null || true; " +
                        "mount --bind /dev/pts \"$R/dev/pts\" 2>/dev/null || true; " +
                        "mount -t proc proc \"$R/proc\" 2>/dev/null || true; " +
                        "mount -t sysfs sys \"$R/sys\" 2>/dev/null || true; " +
                        "mkdir -p \"$R/tmp\" \"$R/run\"; " +
                        "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; " +
                        "export HOME=/root; export TERM=xterm-256color; export LANG=C.UTF-8; export LC_ALL=C.UTF-8; " +
                        "export PS1='root@debian:~$ '; export PS2='> '; " +
                        "if test -x \"$R/usr/bin/script\"; then " +
                        "exec chroot \"$R\" /usr/bin/script -qefc '/bin/bash -l' /dev/null; " +
                        "else exec chroot \"$R\" /bin/bash -l; fi";
                terminalShell = new ProcessBuilder("su","-c",script).redirectErrorStream(true).start();
                terminalStdin = new BufferedWriter(new OutputStreamWriter(terminalShell.getOutputStream()));
                BufferedReader br = new BufferedReader(new InputStreamReader(terminalShell.getInputStream()));
                char[] b = new char[4096]; int n;
                while ((n=br.read(b)) != -1) {
                    String s = new String(b,0,n);
                    runOnUiThread(() -> appendTerminal(s));
                }
                interactiveTerminalMode=false;
            } catch (Exception e) {
                interactiveTerminalMode=false;
                runOnUiThread(() -> appendTerminal("\n[METMC] Terminal startup failed: " + e + "\n"));
            }
        });
    }

    String cleanTerminalText(String s) {
        if (s == null || s.isEmpty()) return "";
        String out = s.replace("\u0000", "");
        out = out.replaceAll("\\u001B\\][^\\u0007]*(?:\\u0007|\\u001B\\\\)", "");
        out = out.replaceAll("\\u001B\\[[0-?]*[ -/]*[@-~]", "");
        out = out.replaceAll("\\u001B[()][0-2A-Za-z]", "");
        out = out.replace("\u001B", "");
        out = out.replace("\u0007", "");
        out = out.replace("\r", "");
        out = out.replace("\b", "");
        return out;
    }

    void appendTerminal(String s) {
        if (terminalOutput != null) {
            String clean = cleanTerminalText(s);
            if (clean.isEmpty()) return;
            terminalOutput.append(clean);
            terminalOutput.post(() -> {
                if (terminalOutput.getParent() instanceof ScrollView) ((ScrollView)terminalOutput.getParent()).fullScroll(View.FOCUS_DOWN);
            });
        }
    }

    void sendTerminalCommand() {
        if (terminalStdin == null || terminalInput == null) return;
        String cmd = terminalInput.getText().toString();
        if (cmd.trim().isEmpty()) return;
        terminalHistory.remove(cmd);
        terminalHistory.add(cmd);
        while (terminalHistory.size() > 100) terminalHistory.remove(0);
        terminalHistoryIndex = terminalHistory.size();
        appendTerminal(cmd + "\n");
        try {
            terminalStdin.write(cmd);
            terminalStdin.newLine();
            terminalStdin.flush();
            terminalInput.setText("");
            terminalInput.requestFocus();
        } catch (Exception e) {
            appendTerminal("[METMC] command input failed: " + e + "\n");
        }
    }

    void terminalInterrupt() {
        try {
            if (terminalStdin != null) {
                terminalStdin.write("\u0003");
                terminalStdin.flush();
            }
        } catch (Exception ignored) {}
    }

    void terminalHistoryMove(int direction) {
        if (terminalInput == null || terminalHistory.isEmpty()) return;
        terminalHistoryIndex = Math.max(0, Math.min(terminalHistory.size(), terminalHistoryIndex + direction));
        terminalInput.setText(terminalHistoryIndex < terminalHistory.size() ? terminalHistory.get(terminalHistoryIndex) : "");
        terminalInput.setSelection(terminalInput.length());
    }

    void closeTerminalShell() {
        try { if (terminalStdin != null) { terminalStdin.write("exit"); terminalStdin.newLine(); terminalStdin.flush(); } } catch(Exception ignored) {}
        try { if (terminalShell != null) terminalShell.destroy(); } catch(Exception ignored) {}
        terminalShell = null; terminalStdin = null;
    }

    void buildTerminalOverlay() {
        if (terminalInput != null) return;

        terminalOutput = new TextView(this);
        terminalOutput.setTextColor(0xffd8f7df);
        terminalOutput.setTextSize(14);
        terminalOutput.setTypeface(Typeface.MONOSPACE);
        terminalOutput.setLineSpacing(0f,1.0f);
        terminalOutput.setIncludeFontPadding(false);
        terminalOutput.setPadding(14,10,14,10);
        terminalOutput.setGravity(Gravity.TOP|Gravity.START);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xff080c0d);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setScrollbarFadingEnabled(false);
        scroll.addView(terminalOutput, new ScrollView.LayoutParams(-1,-2));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1,-1);
        sp.leftMargin=32; sp.rightMargin=32; sp.topMargin=108; sp.bottomMargin=92;
        root.addView(scroll, sp);

        terminalPrompt = new TextView(this);
        terminalPrompt.setText("root@debian:~$");
        terminalPrompt.setTextColor(0xff39ff88);
        terminalPrompt.setTextSize(14);
        terminalPrompt.setTypeface(Typeface.MONOSPACE);
        terminalPrompt.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        terminalPrompt.setPadding(12,0,0,0);
        terminalPrompt.setBackgroundColor(0xff0d1418);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(112,48);
        pp.leftMargin=32; pp.gravity=Gravity.TOP; pp.topMargin=54;
        root.addView(terminalPrompt,pp);

        terminalInput = new EditText(this);
        terminalInput.setSingleLine(true);
        terminalInput.setTextColor(Color.WHITE);
        terminalInput.setHintTextColor(0xff536575);
        terminalInput.setHint("type a command");
        terminalInput.setTextSize(14);
        terminalInput.setTypeface(Typeface.MONOSPACE);
        terminalInput.setIncludeFontPadding(false);
        terminalInput.setHorizontallyScrolling(true);
        terminalInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        terminalInput.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        terminalInput.setSingleLine(true);
        terminalInput.setHint("");
        terminalInput.setPadding(6,0,8,0);
        terminalInput.setSelectAllOnFocus(false);
        terminalInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        terminalInput.setBackgroundColor(Color.TRANSPARENT);
        terminalInput.setOnEditorActionListener((v,id,event)->{
            if(interactiveTerminalMode){
                try{ if(terminalStdin!=null){ terminalStdin.write("\n"); terminalStdin.flush(); } }catch(Exception ignored){}
                suppressTerminalBridge=true; terminalInput.setText(""); suppressTerminalBridge=false; return true;
            }
            sendTerminalCommand(); return true;
        });
        terminalInput.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){
                if(interactiveTerminalMode && !suppressTerminalBridge && count>after){
                    try{ if(terminalStdin!=null){ for(int i=0;i<count-after;i++) terminalStdin.write(127); terminalStdin.flush(); } }catch(Exception ignored){}
                }
            }
            public void onTextChanged(CharSequence s,int start,int before,int count){
                if(interactiveTerminalMode && !suppressTerminalBridge && count>0){
                    try{ if(terminalStdin!=null){ terminalStdin.write(s.subSequence(start,start+count).toString()); terminalStdin.flush(); } }catch(Exception ignored){}
                    suppressTerminalBridge=true; terminalInput.setText(""); suppressTerminalBridge=false;
                }
            }
            public void afterTextChanged(android.text.Editable e){}
        });
        terminalInput.setOnFocusChangeListener((v,has)->{ if(has) terminalInput.post(()->terminalInput.setSelection(terminalInput.length())); });
        terminalInput.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=KeyEvent.ACTION_DOWN) return false;
            if(key==KeyEvent.KEYCODE_DPAD_UP){ terminalHistoryMove(-1); return true; }
            if(key==KeyEvent.KEYCODE_DPAD_DOWN){ terminalHistoryMove(1); return true; }
            if(key==KeyEvent.KEYCODE_C && event.isCtrlPressed()){ terminalInterrupt(); return true; }
            return false;
        });
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-1,48);
        ip.leftMargin=140; ip.rightMargin=12; ip.gravity=Gravity.TOP; ip.topMargin=54;
        root.addView(terminalInput,ip);
        terminalInput.requestFocus();
    }
    void removeTerminalOverlay() {
        if (terminalInput != null) {
            ViewParent p=terminalInput.getParent(); if(p instanceof ViewGroup) ((ViewGroup)p).removeView(terminalInput);
            terminalInput=null;
        }
        if (terminalPrompt != null) {
            ViewParent p=terminalPrompt.getParent(); if(p instanceof ViewGroup) ((ViewGroup)p).removeView(terminalPrompt);
            terminalPrompt=null;
        }
        if (terminalOutput != null) {
            ViewParent p=terminalOutput.getParent();
            if(p instanceof ViewGroup) {
                ViewGroup parent=(ViewGroup)p;
                parent.removeView(terminalOutput);
                ViewParent gp=parent.getParent();
                if(gp instanceof ViewGroup) ((ViewGroup)gp).removeView(parent);
            }
            terminalOutput=null;
        }
        closeTerminalShell();
    }

    enum Surface { DESKTOP, OVERVIEW, APPS, QUICK, SETTINGS, NOTIFICATIONS, WALLPAPER, CLIPBOARD }

    class DesktopView extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Surface surface=Surface.DESKTOP;
        String activeWindow=null;
        final ArrayList<String> openWindows=new ArrayList<>();
        final LinkedHashMap<String,WindowState> windows=new LinkedHashMap<>();
        final String[] dockApps={"Files","Terminal","Browser","Settings"};
        final ArrayList<ResolveInfo> androidApps=new ArrayList<>();
        String draggingWindow=null;
        boolean dragging=false,resizing=false;
        float dragOffsetX,dragOffsetY;
        final float MIN_W=280f, MIN_H=190f;
        int currentWorkspace=1;
        final LinkedHashMap<Integer,ArrayList<String>> workspaceWindows=new LinkedHashMap<>();
        final ArrayList<String> recentItems=new ArrayList<>();
        final ArrayList<String> notifications=new ArrayList<>();
        final ArrayList<String> clipboardHistory=new ArrayList<>();
        boolean wifiOn=true, bluetoothOn=false, soundOn=true, rotationOn=false, darkMode=true;
        String desktopSearch="";
        boolean showSearch=false;
        android.content.ClipboardManager clipboardManager;
        final LinkedHashMap<String,String> fileEntries=new LinkedHashMap<>();
        float downX,downY,appsScroll=0,lastTouchY=0;
        float appsContentHeight=0;
        float surfaceScroll=0;
        float windowScroll=0;
        boolean scrollingSurface=false;
        boolean scrollingWindow=false;
        String scrollingWindowTitle=null;
        long lastWindowTapTime=0;
        String lastWindowTapTitle=null;
        int accent=0xff39ff88;
        String fileDirectory="/storage/emulated/0";
        String fileParentDirectory=null;

        DesktopView(Context c){ super(c); setFocusable(true); clipboardManager=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE); refreshAndroidApps(); refreshFileEntries();
            if(clipboardManager!=null) clipboardManager.addPrimaryClipChangedListener(() -> captureClipboard());
            notifications.add("METMC OS NEXT started");
        }
        void captureClipboard(){
            try{
                if(clipboardManager==null || !clipboardManager.hasPrimaryClip()) return;
                CharSequence cs=clipboardManager.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this);
                if(cs==null) return;
                String v=cs.toString().trim(); if(v.isEmpty()) return;
                clipboardHistory.remove(v); clipboardHistory.add(0,v); while(clipboardHistory.size()>20) clipboardHistory.remove(clipboardHistory.size()-1);
                notifications.add(0,"Clipboard updated"); while(notifications.size()>30) notifications.remove(notifications.size()-1);
                invalidate();
            }catch(Exception ignored){}
        }
        void refreshFileEntries(){
            fileEntries.clear();
            File dir=new File(fileDirectory);
            if(!dir.isDirectory()) { fileDirectory="/storage/emulated/0"; dir=new File(fileDirectory); }
            File parent=dir.getParentFile();
            if(parent!=null) { fileParentDirectory=parent.getAbsolutePath(); fileEntries.put("..",parent.getAbsolutePath()); }
            File[] fs=dir.listFiles();
            if(fs!=null){
                Arrays.sort(fs,(a,b)->{
                    if(a.isDirectory()!=b.isDirectory()) return a.isDirectory()?-1:1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                for(File f:fs) fileEntries.put(f.getAbsolutePath(),f.getAbsolutePath());
            }
        }

        void refreshAndroidApps(){
            androidApps.clear(); androidApps.addAll(getAndroidApps());
            float rows=(float)Math.ceil((6+androidApps.size())/3.0);
            appsContentHeight=rows*122f;
            float max=Math.max(0,appsContentHeight-(getHeight()-270));
            if(surfaceScroll>max) surfaceScroll=max;
        }
        void resetSurfaceScroll(){ surfaceScroll=0; }
        float surfaceContentHeight(){
            if(surface==Surface.APPS) return 138f+(float)Math.ceil((6+androidApps.size())/3.0)*122f+90f;
            if(surface==Surface.SETTINGS) return 136f+8f*57f+70f;
            if(surface==Surface.NOTIFICATIONS) return 136f+Math.max(1,Math.min(30,notifications.size()))*42f+80f;
            if(surface==Surface.CLIPBOARD) return 136f+Math.max(1,Math.min(20,clipboardHistory.size()))*48f+80f;
            if(surface==Surface.WALLPAPER) return 140f+3f*150f+80f;
            if(surface==Surface.OVERVIEW) return 140f+((openWindows.size()+1)/2)*142f+170f;
            return getHeight()-82;
        }
        float maxSurfaceScroll(){ return Math.max(0,surfaceContentHeight()-(getHeight()-82)); }
        void clampSurfaceScroll(){ surfaceScroll=Math.max(0,Math.min(maxSurfaceScroll(),surfaceScroll)); }

        void saveSession(){
            try{
                StringBuilder out=new StringBuilder();
                for(String title:windows.keySet()){
                    WindowState ws=windows.get(title); if(ws==null) continue;
                    if(out.length()>0) out.append("|");
                    out.append(title.replace("|","")).append(",").append(ws.l).append(",").append(ws.t).append(",").append(ws.r).append(",").append(ws.b).append(",").append(ws.minimized).append(",").append(ws.maximized);
                }
                prefs.edit().putInt("workspace",currentWorkspace).putString("windows",out.toString()).putString("recent",joinList(recentItems)).putString("clipboard",joinList(clipboardHistory)).apply();
            }catch(Exception ignored){}
        }
        void restoreSession(){
            if(sessionRestored) return; sessionRestored=true;
            try{
                currentWorkspace=Math.max(1,Math.min(4,prefs.getInt("workspace",1)));
                restoreList(recentItems,prefs.getString("recent",""));
                restoreList(clipboardHistory,prefs.getString("clipboard",""));
                String saved=prefs.getString("windows","");
                if(saved.isEmpty()) return;
                for(String item:saved.split("\\|")){
                    String[] a=item.split(",",-1); if(a.length<7) continue;
                    String title=a[0]; WindowState ws=windowFor(title);
                    ws.l=Float.parseFloat(a[1]); ws.t=Float.parseFloat(a[2]); ws.r=Float.parseFloat(a[3]); ws.b=Float.parseFloat(a[4]);
                    ws.minimized=Boolean.parseBoolean(a[5]); ws.maximized=Boolean.parseBoolean(a[6]); clampWindow(ws);
                    if("Applications".equals(title)) refreshAndroidApps();
            windowScroll=0;
            ArrayList<String> list=workspaceWindows.get(currentWorkspace); if(list==null){list=new ArrayList<>();workspaceWindows.put(currentWorkspace,list);}
                    if(!list.contains(title)) list.add(title);
                    if(!ws.minimized) openWindows.add(title);
                }
                if(!openWindows.isEmpty()) activeWindow=openWindows.get(openWindows.size()-1);
            }catch(Exception ignored){}
        }
        String joinList(ArrayList<String> list){StringBuilder b=new StringBuilder();for(String v:list){if(b.length()>0)b.append("\n");b.append(v.replace("\\n"," "));}return b.toString();}
        void restoreList(ArrayList<String> list,String value){if(value==null||value.isEmpty())return;for(String v:value.split("\\n",-1)){if(!v.isEmpty()&&!list.contains(v))list.add(v);}}
        void addNotification(String message){notifications.add(0,message);while(notifications.size()>30)notifications.remove(notifications.size()-1);invalidate();}
        void fill(Canvas c,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setShader(null);}
        void stroke(Canvas c,int color,float w){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w);p.setColor(color);p.setShader(null);}
        void round(Canvas c,float l,float t,float rr,float b,float rad,int color){fill(c,color);c.drawRoundRect(l,t,rr,b,rad,rad,p);}
        void text(Canvas c,String s,float x,float y,float size,int color){fill(c,color);p.setTypeface(Typeface.create("sans",Typeface.NORMAL));p.setTextSize(size);c.drawText(s,x,y,p);}
        void bold(Canvas c,String s,float x,float y,float size,int color){fill(c,color);p.setTypeface(Typeface.create("sans",Typeface.BOLD));p.setTextSize(size);c.drawText(s,x,y,p);}

        @Override protected void onDraw(Canvas c){
            int w=getWidth(),h=getHeight();
            drawWallpaper(c,w,h); drawTopBar(c,w); drawWorkspaceSwitcher(c,w,h);
            if(surface==Surface.DESKTOP) drawDesktopIcons(c,w,h);
            if(surface==Surface.OVERVIEW) drawOverview(c,w,h);
            else if(surface==Surface.APPS) drawApps(c,w,h);
            else if(surface==Surface.QUICK) drawQuick(c,w,h);
            else if(surface==Surface.SETTINGS) drawSettings(c,w,h);
            else if(surface==Surface.NOTIFICATIONS) drawNotifications(c,w,h);
            else if(surface==Surface.WALLPAPER) drawWallpaperManager(c,w,h);
            else if(surface==Surface.CLIPBOARD) drawClipboard(c,w,h);
            if(surface==Surface.DESKTOP) { drawAllWindows(c,w,h); drawDock(c,w,h); }
        }

        Bitmap customWallpaper;
        int wallpaper(){return prefs.getInt("wallpaper",0);}
        void pickWallpaper(){ try { Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*"); MainActivity.this.startActivityForResult(i,PICK_WALLPAPER); } catch(Exception e) { Toast.makeText(MainActivity.this,"No media picker available",Toast.LENGTH_SHORT).show(); } }
        void drawWallpaper(Canvas c,int w,int h){
            int style=wallpaper();
            if(style==4 && customWallpaper!=null){ p.setShader(null); c.drawBitmap(customWallpaper,null,new Rect(0,0,w,h),p); return; }
            if(style==0){
                LinearGradient g=new LinearGradient(0,0,w,h,0xff132238,0xff05080d,Shader.TileMode.CLAMP);
                p.setShader(g);c.drawRect(0,0,w,h,p);p.setShader(null);
            } else if(style==1){
                fill(c,0xff030806);c.drawRect(0,0,w,h,p);
                stroke(c,0x5539ff88,1);
                for(int x=0;x<w;x+=32)c.drawLine(x,54,x,h,p);
                for(int y=54;y<h;y+=32)c.drawLine(0,y,w,y,p);
                bold(c,"METMC // SECURE DESKTOP",34,h/2f-18,24,0xff39ff88);
                text(c,"ACCESS :: LOCAL // ROOTED // OFFLINE-FIRST",34,h/2f+10,12,0xff63b5ff);
                text(c,"01001101 01000101 01010100 01001101 01000011",34,h/2f+38,11,0xff1e9f5c);
            } else if(style==2){
                fill(c,0xff02040a);c.drawRect(0,0,w,h,p);
                Random rnd=new Random(7);
                for(int i=0;i<90;i++){float x=rnd.nextInt(Math.max(1,w)),y=55+rnd.nextInt(Math.max(1,h-55));fill(c,0x5539ff88);c.drawCircle(x,y,1.2f,p);}
                stroke(c,0x4439ff88,1);c.drawCircle(w*.75f,h*.42f,150,p);c.drawCircle(w*.75f,h*.42f,105,p);
                bold(c,"NEXUS",w*.68f,h*.43f,34,0xff39ff88);
                text(c,"METMC OS NEXT",w*.68f,h*.48f,13,0xff8ab7a0);
            } else if(style==3) {
                LinearGradient g=new LinearGradient(0,0,w,h,0xff061016,0xff170a20,Shader.TileMode.CLAMP);
                p.setShader(g);c.drawRect(0,0,w,h,p);p.setShader(null);
                stroke(c,0x4439ff88,1);
                for(int i=0;i<10;i++)c.drawCircle(w*.18f+i*85,h*.35f,35+i*5,p);
                bold(c,"NIGHT//OPS",34,h-125,27,0xff39ff88);
            } else {
                fill(c,0xff05080d); c.drawRect(0,0,w,h,p); bold(c,"MEDIA WALLPAPER",34,h-125,22,0xff39ff88); text(c,"Choose an image from your device",34,h-98,12,0xff9aa7b8);
            }
        }

        void drawTopBar(Canvas c,int w){
            fill(c,0xe50a1017);c.drawRect(0,0,w,54,p);
            bold(c,"METMC",22,34,16,Color.WHITE);
            text(c,"Activities",92,34,14,0xffd7deea);
            round(c,188,10,Math.min(390,w-190),44,12,0xff151e27);
            text(c,"⌕  Search applications, files and windows",202,33,11,0xff8fa0b2);
            text(c,"METMC OS NEXT",Math.max(400,w/2f-48),34,11,0xff8da0b4);
            text(c,new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()),w-92,33,14,Color.WHITE);
            text(c,"⌁",w-44,34,15,0xff8da0b4);
            if(!notifications.isEmpty()) { fill(c,0xffff5c6c); c.drawCircle(w-26,13,4,p); }
        }

        void switchWorkspace(int target){
            if(target<1||target>4||target==currentWorkspace)return;
            workspaceWindows.put(currentWorkspace,new ArrayList<>(openWindows));
            saveSession();
            currentWorkspace=target;
            openWindows.clear();
            ArrayList<String> list=workspaceWindows.get(target);
            if(list!=null) for(String name:list) if(windows.containsKey(name)) openWindows.add(name);
            activeWindow=openWindows.isEmpty()?null:openWindows.get(openWindows.size()-1);
            if(!openWindows.contains("Terminal")) hideTerminalOverlay();
            syncTerminalOverlay(); invalidate();
        }

        void drawWorkspaceSwitcher(Canvas c,int w,int h){
            float l=w/2f-105;
            for(int i=1;i<=4;i++){
                boolean active=i==currentWorkspace;
                round(c,l+(i-1)*52,8,l+34+(i-1)*52,42,10,active?0xff284534:0xff17212b);
                text(c,String.valueOf(i),l+13+(i-1)*52,30,12,active?0xff39ff88:0xffaab6c4);
            }
        }

        void drawDesktopIcons(Canvas c,int w,int h){
            String[][] icons={{"Files","Files"},{"Terminal","Terminal"},{"Browser","Browser"},{"Apps","apps"}};
            for(int i=0;i<icons.length;i++){
                float x=18+(i%2)*86,y=86+(i/2)*92;
                drawDockIcon(c,x,y,icons[i][1],false);
                text(c,icons[i][0],x,y+68,10,Color.WHITE);
            }
        }

        void drawDock(Canvas c,int w,int h){
            float dw=Math.min(590,w-28),left=(w-dw)/2f,top=h-76;
            round(c,left-8,top-8,left+dw+8,h-12,24,0xe5151c25);
            drawDockIcon(c,left+8,top,"apps",surface==Surface.APPS); float x=left+62;
            for(String a:dockApps){drawDockIcon(c,x,top,a,false);x+=72;}
            drawDockIcon(c,left+dw-54,top,"quick",surface==Surface.QUICK);
            drawDockIcon(c,left+dw-100,top,"clipboard",surface==Surface.CLIPBOARD);
            drawDockIcon(c,left+dw-8,top,"notify",surface==Surface.NOTIFICATIONS);
        }

        void drawDockIcon(Canvas c,float x,float y,String n,boolean sel){
            if(sel)round(c,x,y,x+54,y+54,16,0xff243d31);else round(c,x+4,y+4,x+50,y+50,15,0xff202934);
            float cx=x+27,cy=y+27; stroke(c,0xffdce6f3,2);
            if(n.equals("apps")){fill(c,0xff39ff88);for(int i=0;i<3;i++)for(int j=0;j<3;j++)c.drawCircle(cx-9+j*9,cy-9+i*9,2,p);}
            else if(n.equals("Files")){round(c,cx-13,cy-9,cx+13,cy+11,4,0xffd7e1ef);}
            else if(n.equals("Terminal")){c.drawRect(cx-13,cy-11,cx+13,cy+11,p);c.drawLine(cx-8,cy-2,cx-2,cy+2,p);c.drawLine(cx-2,cy+2,cx-8,cy+6,p);}
            else if(n.equals("Browser")){c.drawCircle(cx,cy,12,p);c.drawLine(cx-12,cy,cx+12,cy,p);}
            else if(n.equals("Settings")){c.drawCircle(cx,cy,8,p);c.drawCircle(cx,cy,3,p);}
            else if(n.equals("quick")){fill(c,0xffd7e1ef);c.drawCircle(cx,cy,11,p);fill(c,0xff202934);c.drawCircle(cx+4,cy-4,3,p);}            else c.drawRect(cx-10,cy-9,cx+10,cy+9,p);
        }

        void overlay(Canvas c,int w,int h){fill(c,0xb003080d);c.drawRect(0,54,w,h-82,p);}

        void drawApps(Canvas c,int w,int h){
            overlay(c,w,h);bold(c,"Applications",28,92,25,Color.WHITE);
            text(c,(6+androidApps.size())+" METMC + Android applications",28,116,13,0xff9daabd);
            String[] n={"Files","Terminal","Browser","Settings","Media","Linux Apps"};
            int total=n.length+androidApps.size();float cw=(w-112)/3f,top=138-surfaceScroll;
            c.save();c.clipRect(20,130,w-20,h-88);
            for(int i=0;i<total;i++){
                int col=i%3,row=i/3;float l=28+col*(cw+24),t=top+row*122;
                if(t>h-90||t+104<130)continue;
                round(c,l,t,l+cw,t+104,16,0xff171f2a);
                if(i<n.length){
                    drawAppIcon(c,l+18,t+24,n[i]);
                    bold(c,n[i],l+70,t+35,14,Color.WHITE);
                    text(c,appSubtitle(n[i]),l+70,t+57,10,0xff8d9aab);
                } else {
                    ResolveInfo r=androidApps.get(i-n.length);
                    String name=String.valueOf(r.loadLabel(getPackageManager()));
                    drawAndroidIcon(c,l+18,t+24,r);
                    bold(c,name,l+70,t+35,14,Color.WHITE);
                    text(c,r.activityInfo.packageName,l+70,t+57,9,0xff718093);
                }
            }
            c.restore();
            round(c,28,h-76,w-28,h-34,14,0xff151d27);
            text(c,"⌕",47,h-48,20,0xffaeb9c8);
            text(c,"Installed Android + METMC applications",77,h-50,13,0xff98a5b6);
            if(maxSurfaceScroll()>0){
                float trackTop=132,trackBottom=h-92,trackH=trackBottom-trackTop;
                float thumbH=Math.max(34,trackH*(trackH/surfaceContentHeight()));
                float thumbY=trackTop+(trackH-thumbH)*(surfaceScroll/maxSurfaceScroll());
                round(c,w-16,trackTop,w-10,trackBottom,3,0x334f6478);
                round(c,w-16,thumbY,w-10,thumbY+thumbH,3,0xff6d8195);
            }
        }

        String appSubtitle(String s){if(s.equals("Files"))return"File manager";if(s.equals("Terminal"))return"Native Debian terminal";if(s.equals("Browser"))return"Web browser";if(s.equals("Settings"))return"System controls";if(s.equals("Media"))return"Media player";return"Linux integration";}

        void drawAndroidIcon(Canvas c,float x,float y,ResolveInfo r){
            Drawable d=r.loadIcon(getPackageManager()); if(d!=null){d.setBounds((int)x,(int)y,(int)x+40,(int)y+40); d.draw(c);} else drawAppIcon(c,x,y,"Android");
        }

        void drawAppIcon(Canvas c,float x,float y,String s){
            round(c,x,y,x+40,y+40,11,0xff182432);
            stroke(c,0xff71879d,1.5f);
            float cx=x+20,cy=y+20;
            if(s.equals("Files")){
                fill(c,0xffd9e7f5);
                Path folder=new Path(); folder.moveTo(x+8,y+13);folder.lineTo(x+17,y+13);folder.lineTo(x+20,y+16);folder.lineTo(x+32,y+16);folder.lineTo(x+32,y+30);folder.lineTo(x+8,y+30);folder.close();c.drawPath(folder,p);
            } else if(s.equals("Terminal")){
                stroke(c,0xff39ff88,2.2f);c.drawRect(x+7,y+8,x+33,y+32,p);
                c.drawLine(x+12,y+16,x+18,y+20,p);c.drawLine(x+18,y+20,x+12,y+24,p);c.drawLine(x+21,y+25,x+28,y+25,p);
            } else if(s.equals("Browser")){
                stroke(c,0xff62b7ff,2);c.drawCircle(cx,cy,13,p);c.drawLine(x+7,y+20,x+33,y+20,p);
                c.drawOval(x+14,y+7,x+26,y+33,p);
            } else if(s.equals("Settings")){
                stroke(c,0xffd9e7f5,2.5f);c.drawCircle(cx,cy,9,p);fill(c,0xff39ff88);c.drawCircle(cx,cy,3,p);
                for(int i=0;i<8;i++){double a=i*Math.PI/4;float x1=cx+(float)Math.cos(a)*11,y1=cy+(float)Math.sin(a)*11;float x2=cx+(float)Math.cos(a)*14,y2=cy+(float)Math.sin(a)*14;c.drawLine(x1,y1,x2,y2,p);}
            } else if(s.equals("Media")){
                fill(c,0xff7c8cff);c.drawCircle(cx,cy,13,p);fill(c,0xff0d141c);Path tri=new Path();tri.moveTo(x+17,y+13);tri.lineTo(x+17,y+27);tri.lineTo(x+29,y+20);tri.close();c.drawPath(tri,p);
            } else if(s.equals("Linux Apps")){
                stroke(c,0xffffc857,2);c.drawCircle(cx,cy,13,p);text(c,"L",x+15,y+26,16,0xffffc857);
            } else {
                fill(c,0xff53677d);c.drawCircle(cx,cy,12,p);text(c,"A",x+14,y+26,14,Color.WHITE);
            }
        }

        void drawOverview(Canvas c,int w,int h){
            overlay(c,w,h);
            bold(c,"Overview",28,92,25,Color.WHITE);
            text(c,"Workspace "+currentWorkspace+"  •  "+openWindows.size()+" windows",28,116,12,0xff8f9bad);
            if(openWindows.isEmpty()){
                round(c,28,142,w-28,214,18,0xff151c26);
                text(c,"No open METMC windows",50,177,15,0xffdbe3ef);
            } else {
                for(int i=0;i<openWindows.size();i++){
                    String s=openWindows.get(i); WindowState ws=windows.get(s);
                    float l=28+(i%2)*(w/2f-18), t=140+(i/2)*142, r=l+w/2f-26, b=t+118;
                    round(c,l,t,r,b,18,0xff18212c);
                    c.save(); c.clipRect(l+10,t+10,r-10,t+78);
                    if(ws!=null) drawWindow(c,w,h,ws,s);
                    c.restore();
                    round(c,l+10,t+84,r-10,t+108,10,0xff0d141b);
                    bold(c,s,l+20,t+101,12,Color.WHITE);
                    text(c,ws!=null&&ws.minimized?"MINIMIZED":"ACTIVE",r-82,t+101,9,0xff39ff88);
                }
            }
            if(!recentItems.isEmpty()){
                bold(c,"Recent",28,h-145,14,Color.WHITE);
                StringBuilder recent=new StringBuilder();
                for(int i=0;i<Math.min(5,recentItems.size());i++){if(i>0)recent.append("  •  ");recent.append(recentItems.get(i));}
                text(c,recent.toString(),28,h-122,10,0xff9aa7b8);
            }
        }

        void drawQuick(Canvas c,int w,int h){
            overlay(c,w,h); float l=w-330;
            round(c,l,68,w-18,h-92,22,0xff151c26);
            bold(c,"Quick Settings",l+24,104,21,Color.WHITE);
            String[] q={"Wi-Fi","Bluetooth","Sound","Rotation","Dark Mode","Lock"};
            boolean[] state={wifiOn,bluetoothOn,soundOn,rotationOn,darkMode,false};
            for(int i=0;i<q.length;i++){
                int col=i%2,row=i/2; float x=l+18+col*145,y=148+row*68;
                round(c,x,y,x+130,y+54,15,state[i]?0xff294b39:0xff202a35);
                bold(c,q[i],x+14,y+23,12,Color.WHITE);
                text(c,state[i]?"ON":"OFF",x+14,y+41,10,state[i]?0xff39ff88:0xff9eacbe);
            }
        }

        void drawSettings(Canvas c,int w,int h){
            overlay(c,w,h); bold(c,"Settings",30,91,26,Color.WHITE);
            text(c,"METMC OS NEXT control center",30,116,12,0xff8f9bad);
            String[] groups={"Appearance","Desktop & Workspaces","Applications","Linux integration","Security","System Update","Clipboard","Session"};
            String[] desc={"Wallpaper and visual themes","Workspaces, dock and window behavior","Installed Android + METMC apps","Debian terminal and Linux applications","Lock-screen and security controls","Check for a newer build","Clipboard history and paste tools","Restore windows after restart"};
            for(int i=0;i<groups.length;i++){
                float y=136+i*57;
                round(c,28,y,w-28,y+49,14,0xff151d27);
                drawAppIcon(c,44,y+8,"Settings"); bold(c,groups[i],92,y+21,14,Color.WHITE);
                text(c,desc[i],92,y+39,10,0xff8996a8); text(c,"›",w-50,y+31,22,0xff8d9bad);
            }
        }

        void drawWallpaperManager(Canvas c,int w,int h){
            overlay(c,w,h);bold(c,"Wallpaper Manager",30,91,26,Color.WHITE);text(c,"Built-in METMC hacker wallpapers",30,116,12,0xff8f9bad);
            String[] names={"Midnight Grid","Cyber Nexus","Night Ops","Deep Blue","Media Picker"};
            for(int i=0;i<5;i++){float l=28+(i%2)*(w/2f-20),t=140+(i/2)*150;round(c,l,t,l+w/2f-32,t+130,18,0xff10171e);drawWallpaperPreview(c,l+8,t+8,w/2f-48,114,i);bold(c,names[i],l+20,t+102,13,Color.WHITE);text(c,wallpaper()==i?"ACTIVE":"APPLY",l+w/2f-105,t+102,11,wallpaper()==i?0xff39ff88:0xff9eacbe);}
            text(c,"Tap a wallpaper to apply it instantly.",30,h-105,12,0xff9aa7b8);
        }

        void drawWallpaperPreview(Canvas c,float x,float y,float ww,float hh,int style){fill(c,0xff05080d);c.drawRect(x,y,x+ww,y+hh,p);stroke(c,0x8839ff88,1);for(int gx=(int)x;gx<x+ww;gx+=20)c.drawLine(gx,y,gx,y+hh,p);for(int gy=(int)y;gy<y+hh;gy+=20)c.drawLine(x,gy,x+ww,gy,p);bold(c,style==1?"NEXUS":style==2?"OPS":"METMC",x+16,y+42,20,0xff39ff88);}

        void drawNotifications(Canvas c,int w,int h){
            overlay(c,w,h); float l=w-360;
            round(c,l,68,w-18,h-92,22,0xff151c26);
            bold(c,"Notifications",l+24,105,21,Color.WHITE);
            if(notifications.isEmpty()) text(c,"You're all caught up",l+24,139,13,0xff9aa7b8);
            else {
                int n=Math.min(8,notifications.size());
                for(int i=0;i<n;i++){
                    float y=145+i*55; round(c,l+16,y,w-34,y+45,12,0xff202a35);
                    text(c,notifications.get(i),l+30,y+27,11,Color.WHITE);
                }
            }
            text(c,"Tap outside to close",l+24,h-112,10,0xff8795a8);
        }

        void drawClipboard(Canvas c,int w,int h){
            overlay(c,w,h); float l=70,r=w-70;
            round(c,l,70,r,h-92,22,0xff151c26);
            bold(c,"Clipboard",l+24,108,22,Color.WHITE);
            text(c,"Recent copied text",l+24,133,11,0xff8f9bad);
            if(clipboardHistory.isEmpty()) text(c,"No clipboard history yet.",l+24,175,13,0xffaab5c4);
            for(int i=0;i<Math.min(8,clipboardHistory.size());i++){
                String v=clipboardHistory.get(i).replace("\n"," ");
                if(v.length()>72)v=v.substring(0,69)+"...";
                float y=150+i*48; round(c,l+18,y,r-18,y+38,10,0xff202a35);
                text(c,v,l+30,y+24,10,Color.WHITE);
            }
        }

        class WindowState {
            String title;
            float l,t,r,b;
            boolean minimized=false,maximized=false;
            float restoreL,restoreT,restoreR,restoreB;
            WindowState(String title,float l,float t,float r,float b){
                this.title=title;this.l=l;this.t=t;this.r=r;this.b=b;
                restoreL=l;restoreT=t;restoreR=r;restoreB=b;
            }
        }

        WindowState windowFor(String title){
            WindowState ws=windows.get(title);
            if(ws==null){
                float ww=Math.min(620,getWidth()-36);
                float hh=Math.min(title.equals("Settings") ? 560 : 430,getHeight()-150);
                float l=Math.max(18,(getWidth()-ww)/2f), t=72;
                ws=new WindowState(title,l,t,l+ww,t+hh);
                windows.put(title,ws);
            }
            return ws;
        }

        void bringToFront(String title){
            if(!openWindows.contains(title)) openWindows.add(title);
            openWindows.remove(title);
            openWindows.add(title);
            activeWindow=title;
            WindowState ws=windowFor(title);
            ws.minimized=false;
        }

        void drawAllWindows(Canvas c,int w,int h){
            if(openWindows.isEmpty()) return;
            overlay(c,w,h);
            c.save();
            c.clipRect(0,54,w,h-82);
            for(String title:new ArrayList<>(openWindows)){
                WindowState ws=windows.get(title);
                if(ws!=null && !ws.minimized) drawWindow(c,w,h,ws,title);
            }
            c.restore();            drawWindowTaskbar(c,w,h);
        }

        void drawWindow(Canvas c,int w,int h,WindowState ws,String title){
            float l=ws.l,t=ws.t,rr=ws.r,bb=ws.b;
            int border=title.equals(activeWindow)?0xff39ff88:0x66465a6d;
            round(c,l,t,rr,bb,18,0xff0b1118);
            stroke(c,border,title.equals(activeWindow)?2f:1f);
            c.drawRoundRect(l,t,rr,bb,18,18,p);
            round(c,l,t,rr,t+50,18,title.equals(activeWindow)?0xff192630:0xff141d26);
            c.drawRect(l,t+26,rr,t+50,p);

            drawAppIcon(c,l+14,t+9,title);
            bold(c,title,l+52,t+32,14,Color.WHITE);

            // Minimize / maximize / close controls.
            round(c,rr-108,t+12,rr-80,t+38,7,0xff263342);
            text(c,"—",rr-101,t+30,13,0xffd7e0eb);
            round(c,rr-76,t+12,rr-48,t+38,7,0xff263342);
            text(c,ws.maximized?"❐":"□",rr-69,t+30,11,0xffd7e0eb);
            round(c,rr-44,t+12,rr-16,t+38,7, title.equals(activeWindow)?0xff6b2630:0xff263342);
            text(c,"×",rr-37,t+30,14,0xffffffff);

            c.save();
            c.clipRect(l+10,t+52,rr-10,bb-10);
            if(!title.equals("Terminal")) c.translate(0,-windowScroll);
            if(title.equals("Terminal")){
                text(c,"DEBIAN  •  /bin/bash  •  /data/local/linux/rootfs",l+28,t+82,10,0xff7f9b8b);
            } else if(title.equals("Files")){
                bold(c,"METMC File Manager",l+28,t+82,20,Color.WHITE);
                text(c,"Root filesystem and shared storage",l+28,t+107,12,0xff8f9cad);
                drawFileWindowPreview(c,l,t,rr,bb);
            } else if(title.equals("Linux Apps")){
                drawLinuxAppsWindowContent(c,l,t,rr,bb);
            } else if(title.equals("Media")){
                drawMediaWindowContent(c,l,t,rr,bb);
            } else if(title.equals("Settings")){
                drawSettingsWindowContent(c,l,t,rr,bb);
            } else if(title.equals("Applications")){
                drawApplicationsWindowContent(c,l,t,rr,bb);
            } else if(title.equals("Quick Settings") || title.equals("Notifications")
                    || title.equals("Clipboard") || title.equals("Wallpaper Manager") || title.equals("Overview")
                    || title.equals("Desktop & Workspaces") || title.equals("System Update") || title.equals("Session Manager")){
                drawUtilityWindowContent(c,l,t,rr,bb,title);
            } else {
                bold(c,title,l+28,t+82,20,Color.WHITE);
                text(c,"Native METMC application window",l+28,t+107,12,0xff8f9cad);
            }

            c.restore();
            if(!title.equals("Terminal") && windowScrollMax(title,ws)>0){
                float trackTop=t+58,trackBottom=bb-14,trackH=trackBottom-trackTop;
                float thumbH=Math.max(28,trackH*(trackH/windowContentHeight(title,ws)));
                float thumbY=trackTop+(trackH-thumbH)*(windowScroll/windowScrollMax(title,ws));
                round(c,rr-12,trackTop,rr-7,trackBottom,3,0x334f6478);
                round(c,rr-12,thumbY,rr-7,thumbY+thumbH,3,0xff6d8195);
            }
            // Resize grip.
            stroke(c,0xff536476,1.2f);
            for(int i=0;i<3;i++){c.drawLine(rr-18-i*6,bb-5,rr-5,bb-18-i*6,p);}
        }

        float windowContentHeight(String title,WindowState ws){
            if("Settings".equals(title)) return 62f+8f*52f+40f;
            if("Applications".equals(title)) return 62f+(float)Math.ceil((6+androidApps.size())/3.0)*116f+50f;
            if("Notifications".equals(title)) return 68f+Math.max(1,Math.min(30,notifications.size()))*42f+40f;
            if("Clipboard".equals(title)) return 68f+Math.max(1,Math.min(20,clipboardHistory.size()))*40f+40f;
            if("Wallpaper Manager".equals(title)) return 68f+3f*96f+40f;
            if("Overview".equals(title)) return 92f+((openWindows.size()+1)/2)*82f+60f;
            if("Files".equals(title)) return 142f+Math.max(1,(fileEntries.size()+2)/3)*72f+30f;
            if("Linux Apps".equals(title)) return 360f;
            if("Media".equals(title)) return 300f;
            if("Desktop & Workspaces".equals(title)) return 430f;
            if("System Update".equals(title)) return 300f;
            if("Session Manager".equals(title)) return 300f;
            return Math.max(1,ws.b-ws.t-70);
        }
        float windowScrollMax(String title,WindowState ws){ return Math.max(0,windowContentHeight(title,ws)-(ws.b-ws.t-62)); }

        void drawSettingsWindowContent(Canvas c,float l,float t,float r,float b){
            String[] groups={"Appearance","Desktop & Workspaces","Applications","Linux integration","Security","System Update","Clipboard","Session"};
            String[] desc={"Wallpaper and visual themes","Workspaces, dock and window behavior","Installed Android + METMC apps","Debian terminal and Linux applications","Lock-screen and security controls","Check for a newer build","Clipboard history and paste tools","Restore windows after restart"};
            c.save();
            c.clipRect(l+14,t+54,r-14,b-12);
            for(int i=0;i<groups.length;i++){
                float y=t+62+i*52;
                if(y+46>b-12) break;
                round(c,l+16,y,r-16,y+46,12,0xff151d27);
                drawAppIcon(c,l+28,y+5,"Settings");
                bold(c,groups[i],l+76,y+19,13,Color.WHITE);
                text(c,desc[i],l+76,y+36,9,0xff8996a8);
                text(c,"›",r-42,y+29,20,0xff8d9bad);
            }
            c.restore();
        }

        void drawApplicationsWindowContent(Canvas c,float l,float t,float r,float b){
            String[] n={"Files","Terminal","Browser","Settings","Media","Linux Apps"};
            float cw=(r-l-48)/3f, top=t+62;
            int total=n.length+androidApps.size();
            c.save();
            c.clipRect(l+12,t+54,r-12,b-12);
            for(int i=0;i<total;i++){
                int col=i%3,row=i/3;
                float x=l+16+col*(cw+8), y=top+row*116;
                if(y+104<b-12 && y+104>=t+54){
                    round(c,x,y,x+cw,y+104,12,0xff151d27);
                    if(i<n.length){
                        drawAppIcon(c,x+10,y+25,n[i]);
                        bold(c,n[i],x+60,y+37,12,Color.WHITE);
                        text(c,appSubtitle(n[i]),x+60,y+58,8,0xff8d9aab);
                        text(c,"CLICK TO OPEN",x+60,y+78,7,0xff39ff88);
                    } else {
                        ResolveInfo ri=androidApps.get(i-n.length);
                        String name=String.valueOf(ri.loadLabel(getPackageManager()));
                        drawAndroidIcon(c,x+10,y+25,ri);
                        bold(c,name,x+60,y+37,11,Color.WHITE);
                        text(c,ri.activityInfo.packageName,x+60,y+58,7,0xff718093);
                        text(c,"CLICK TO LAUNCH",x+60,y+78,7,0xff39ff88);
                    }
                }
            }
            c.restore();
        }

        void drawLinuxAppsWindowContent(Canvas c,float l,float t,float r,float b){
            bold(c,"Linux Applications",l+28,t+82,20,Color.WHITE);
            text(c,"Run installed Debian programs directly in the METMC Terminal",l+28,t+107,11,0xff8f9cad);
            String[] names={"Python 3","Vim","Nano","Htop","Bash","Python shell"};
            String[] cmds={"python3","vim","nano","htop","bash","python3"};
            String[] desc={"Interactive Python interpreter","Terminal editor","Simple terminal editor","Process monitor","Debian shell","Python interactive shell"};
            for(int i=0;i<names.length;i++){
                int col=i%2,row=i/2;
                float bw=(r-l-54)/2f, x=l+18+col*(bw+18), y=t+128+row*66;
                round(c,x,y,x+bw,y+54,12,0xff151d27);
                drawAppIcon(c,x+10,y+7,"Linux Apps");
                bold(c,names[i],x+60,y+22,12,Color.WHITE);
                text(c,desc[i],x+60,y+40,9,0xff8d9aab);
            }
        }

        void drawMediaWindowContent(Canvas c,float l,float t,float r,float b){
            bold(c,"Media",l+28,t+82,20,Color.WHITE);
            text(c,"Open local audio, video and image files",l+28,t+107,11,0xff8f9cad);
            round(c,l+24,t+130,r-24,t+250,16,0xff101821);
            drawAppIcon(c,(l+r)/2f-20,t+170,"Media");
            bold(c,"METMC Media Player",l+40,t+275,14,Color.WHITE);
            text(c,"Choose a file from Files to open it with an installed Android media viewer.",l+40,t+296,10,0xff8d9aab);
        }

        void drawUtilityWindowContent(Canvas c,float l,float t,float r,float b,String title){
            if(title.equals("Quick Settings")){
                String[] q={"Wi-Fi","Bluetooth","Sound","Rotation","Dark Mode","Lock"};
                boolean[] state={wifiOn,bluetoothOn,soundOn,rotationOn,darkMode,false};
                for(int i=0;i<q.length;i++){
                    int col=i%2,row=i/2;
                    float x=l+18+col*((r-l-54)/2f+12), y=t+70+row*58;
                    float bw=(r-l-54)/2f;
                    round(c,x,y,x+bw,y+46,12,state[i]?0xff294b39:0xff202a35);
                    bold(c,q[i],x+12,y+20,11,Color.WHITE);
                    text(c,state[i]?"ON":"OFF",x+12,y+36,9,state[i]?0xff39ff88:0xff9eacbe);
                }
            } else if(title.equals("Notifications")){
                if(notifications.isEmpty()) text(c,"You're all caught up",l+22,t+88,12,0xff9aa7b8);
                for(int i=0;i<Math.min(8,notifications.size());i++){
                    float y=t+68+i*42;
                    round(c,l+16,y,r-16,y+34,9,0xff202a35);
                    String v=notifications.get(i);
                    if(v.length()>48)v=v.substring(0,45)+"...";
                    text(c,v,l+28,y+22,9,Color.WHITE);
                }
            } else if(title.equals("Clipboard")){
                if(clipboardHistory.isEmpty()) text(c,"No clipboard history yet.",l+22,t+88,12,0xffaab5c4);
                for(int i=0;i<Math.min(8,clipboardHistory.size());i++){
                    String v=clipboardHistory.get(i).replace("\n"," ");
                    if(v.length()>56)v=v.substring(0,53)+"...";
                    float y=t+68+i*40;
                    round(c,l+16,y,r-16,y+32,9,0xff202a35);
                    text(c,v,l+28,y+21,9,Color.WHITE);
                }
            } else if(title.equals("Wallpaper Manager")){
                String[] names={"Midnight Grid","Cyber Nexus","Night Ops","Deep Blue","Media Picker"};
                float bw=(r-l-48)/2f;
                for(int i=0;i<5;i++){
                    int col=i%2,row=i/2;
                    float x=l+16+col*(bw+16),y=t+68+row*96;
                    if(y+82>b-12) break;
                    round(c,x,y,x+bw,y+82,10,0xff10171e);
                    drawWallpaperPreview(c,x+6,y+6,bw-12,58,i);
                    text(c,names[i],x+10,y+75,8,Color.WHITE);
                }
            } else if(title.equals("Desktop & Workspaces")){
                bold(c,"Desktop & Workspaces",l+28,t+82,20,Color.WHITE);
                text(c,"Configure the METMC desktop session",l+28,t+107,11,0xff8f9cad);
                String[] rows={"Workspace 1","Workspace 2","Workspace 3","Workspace 4","Window gaps","Restore session"};
                for(int i=0;i<rows.length;i++){
                    float y=t+130+i*42;
                    round(c,l+20,y,r-20,y+34,9,0xff18212c);
                    text(c,rows[i],l+34,y+22,10,Color.WHITE);
                    text(c,i<4?(currentWorkspace==i+1?"ACTIVE":"SWITCH"):"OPEN",r-86,y+22,8,0xff39ff88);
                }
            } else if(title.equals("System Update")){
                bold(c,"System Update",l+28,t+82,20,Color.WHITE);
                text(c,"METMC OS NEXT update service",l+28,t+107,11,0xff8f9cad);
                round(c,l+22,t+130,r-22,t+178,10,0xff18212c);
                text(c,"Check GitHub for the latest signed release",l+38,t+159,10,Color.WHITE);
                round(c,l+22,t+192,r-22,t+238,10,0xff294b39);
                bold(c,"CHECK FOR UPDATES",l+38,t+221,10,Color.WHITE);
            } else if(title.equals("Session Manager")){
                bold(c,"Session Manager",l+28,t+82,20,Color.WHITE);
                text(c,"Save or restore your METMC desktop session",l+28,t+107,11,0xff8f9cad);
                round(c,l+22,t+132,r-22,t+180,10,0xff294b39);
                bold(c,"SAVE CURRENT SESSION",l+38,t+162,10,Color.WHITE);
                round(c,l+22,t+192,r-22,t+240,10,0xff18212c);
                bold(c,"RESTORE SAVED SESSION",l+38,t+222,10,Color.WHITE);
            } else if(title.equals("Overview")){
                text(c,"Workspace "+currentWorkspace+"  •  "+openWindows.size()+" windows",l+20,t+76,10,0xff8f9bad);
                for(int i=0;i<openWindows.size();i++){
                    float x=l+16+(i%2)*((r-l-48)/2f+16), y=t+92+(i/2)*82;
                    float bw=(r-l-48)/2f;
                    if(y+68>b-12) break;
                    round(c,x,y,x+bw,y+68,10,0xff18212c);
                    drawAppIcon(c,x+8,y+14,openWindows.get(i));
                    text(c,openWindows.get(i),x+56,y+29,10,Color.WHITE);
                    text(c,windows.get(openWindows.get(i))!=null&&windows.get(openWindows.get(i)).minimized?"MIN":"OPEN",x+56,y+47,8,0xff39ff88);
                }
            }
        }

        void drawFileWindowPreview(Canvas c,float l,float t,float r,float b){
            c.save();
            c.clipRect(l+18,t+118,r-18,b-12);
            text(c,"PATH  "+fileDirectory,l+28,t+128,10,0xff6f8296);
            float contentTop=t+142;
            float available=Math.max(1,r-l-48);
            int columns=Math.max(2,Math.min(4,(int)(available/150f)));
            float gap=10f, tileW=(available-gap*(columns-1))/columns;
            float tileH=62f, step=72f;
            ArrayList<String> dirs=new ArrayList<>(fileEntries.values());
            int visible=Math.min(dirs.size(),Math.max(1,(int)((b-contentTop-12)/step))*columns);
            for(int i=0;i<visible;i++){
                int col=i%columns,row=i/columns;
                float x=l+24+col*(tileW+gap), yy=contentTop+row*step;
                round(c,x,yy,x+tileW,yy+tileH,12,0xff151f29);
                File f=new File(dirs.get(i)); drawAppIcon(c,x+10,yy+11,f.isDirectory()?"Files":"Browser");
                String name=f.getName().isEmpty()?f.getAbsolutePath():f.getName();
                if(dirs.get(i).equals(fileParentDirectory)) name="..";
                if(name.length()>18) name=name.substring(0,17)+"…";
                text(c,name,x+58,yy+27,10,0xffd6dfeb);
                text(c,f.isDirectory()?"FOLDER":"FILE",x+58,yy+44,8,0xff7f9b8b);
            }
            c.restore();
        }

        void openFileEntry(String path){
            if(fileParentDirectory!=null && fileParentDirectory.equals(path)) { fileDirectory=fileParentDirectory; refreshFileEntries(); invalidate(); return; }
            File f=new File(path);
            if(f.isDirectory()){ fileDirectory=f.getAbsolutePath(); refreshFileEntries(); invalidate(); return; }
            String mime="application/octet-stream";
            String n=f.getName().toLowerCase(Locale.US);
            if(n.endsWith(".pdf")) mime="application/pdf";
            else if(n.endsWith(".png")||n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".webp")) mime="image/*";
            else if(n.endsWith(".txt")||n.endsWith(".log")||n.endsWith(".json")||n.endsWith(".xml")||n.endsWith(".md")||n.endsWith(".csv")) mime="text/plain";
            try{
                Uri uri=FileProvider.getUriForFile(MainActivity.this,"com.metmc.os.next.fileprovider",f);
                Intent i=new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri,mime);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
                if(Build.VERSION.SDK_INT>=24){
                    int sw=Math.max(1,desktop.getWidth()), sh=Math.max(1,desktop.getHeight());
                    int fw=Math.min(760,Math.max(500,sw-140)), fh=Math.min(520,Math.max(360,sh-170));
                    int fl=Math.max(12,(sw-fw)/2), ft=Math.max(66,(sh-fh)/2);
                    ActivityOptions o=ActivityOptions.makeBasic();
                    o.setLaunchBounds(new Rect(fl,ft,Math.min(sw-12,fl+fw),Math.min(sh-92,ft+fh)));
                    try{ MainActivity.this.startActivity(i,o.toBundle()); return; }catch(Exception ignored){}
                }
                MainActivity.this.startActivity(i);
            }catch(Exception e){ Toast.makeText(MainActivity.this,"No app can open "+f.getName(),Toast.LENGTH_SHORT).show(); }
        }

        void drawWindowTaskbar(Canvas c,int w,int h){
            if(openWindows.isEmpty()) return;
            float y=h-126, x=24;
            for(String title:new ArrayList<>(openWindows)){
                WindowState ws=windows.get(title);
                if(ws==null) continue;
                float bw=Math.min(150,Math.max(92,p.measureText(title)+58));
                if(x+bw>w-24) break;
                round(c,x,y,x+bw,y+38,12,title.equals(activeWindow)?0xff284534:0xff1b2530);
                drawAppIcon(c,x+7,y+5,title);
                text(c,title,x+48,y+24,11,Color.WHITE);
                if(ws.minimized){text(c,"•",x+bw-17,y+23,12,0xff8b98a9);}
                x+=bw+8;
            }
        }

        boolean isDockApp(String title){
            for(String a:dockApps) if(a.equals(title)) return true;
            return false;
        }

        void drawAppIconSimple(Canvas c,float x,float y){drawAppIcon(c,x,y,"Android");}

        @Override public boolean onTouchEvent(MotionEvent e){
            float x=e.getX(),y=e.getY();int h=getHeight(),w=getWidth();
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                downX=x;downY=y;lastTouchY=y;
                scrollingSurface=false;scrollingWindow=false;scrollingWindowTitle=null;
                if(surface==Surface.DESKTOP){
                    // Shell chrome is a higher interaction layer than application windows.
                    // Prevent an application window from stealing dock/task-switcher taps.
                    if(y>h-136){
                        float dw=Math.min(590,w-28),left=(w-dw)/2f;
                        float taskY=h-126;
                        if(y>=taskY && y<=taskY+38){
                            String task=windowTaskAt(x,y);
                            if(task!=null){ if(task.equals(activeWindow)) minimizeWindow(task); else bringToFront(task); invalidate(); syncTerminalOverlay(); return true; }
                        }
                        if(y>h-88){
                            if(x>=left-8&&x<left+54){showWindow("Applications");return true;}
                            float pos=x-(left+62);
                            if(pos>=0&&pos<288){
                                int idx=(int)(pos/72);
                                if(idx<4){
                                    String a=dockApps[idx];
                                    if(a.equals("Terminal"))launchTerminal();
                                    else if(a.equals("Browser"))launchBrowser();
                                    else if(a.equals("Files"))showWindow("Files");
                                    else showWindow("Settings");
                                    invalidate();return true;
                                }
                            }
                            if(x>left+dw-112 && x<=left+dw-65){showWindow("Clipboard");return true;}
                            if(x>left+dw-44){showWindow("Notifications");return true;}
                            if(x>left+dw-65){showWindow("Quick Settings");return true;}
                            if(x>left+dw-12){showWindow("Notifications");return true;}
                        }
                    }
                    WindowState files=windows.get("Files");
                    WindowState hit=windowAt(x,y);                    if(hit!=null && "Files".equals(hit.title) && files!=null&&!files.minimized){
                        float contentTop=files.t+142-windowScroll, contentBottom=files.b-12;
                        float available=Math.max(1,files.r-files.l-48);
                        int columns=Math.max(2,Math.min(4,(int)(available/150f)));
                        float gap=10f,tileW=(available-gap*(columns-1))/columns,step=72f,tileH=62f;
                        if(x>=files.l+24&&x<=files.r-24&&y>=contentTop&&y<=contentBottom){
                            int col=(int)((x-(files.l+24))/(tileW+gap)), row=(int)((y-contentTop)/step);
                            float tileX=files.l+24+col*(tileW+gap),tileY=contentTop+row*step;
                            int idx=row*columns+col;
                            ArrayList<String> entries=new ArrayList<>(fileEntries.values());
                            if(col>=0&&col<columns&&row>=0&&idx>=0&&idx<entries.size()
                                    &&x>=tileX&&x<=tileX+tileW&&y>=tileY&&y<=tileY+tileH){
                                openFileEntry(entries.get(idx)); return true;
                            }
                        }
                    }
                    if(hit!=null){
                        if(!hit.title.equals(scrollingWindowTitle)) windowScroll=0;
                        bringToFront(hit.title);
                        float rr=hit.r,bb=hit.b;
                        if(y>hit.t+54 && y<hit.b-12 && windowScrollMax(hit.title,hit)>0){
                            scrollingWindow=true; scrollingWindowTitle=hit.title;
                        }
                        if("Settings".equals(hit.title) && y>hit.t+52 && y<hit.b-10){
                            int setting=(int)((y-(hit.t+62)+windowScroll)/52f);
                            if(setting==0) showWindow("Wallpaper Manager");
                            else if(setting==2) { refreshAndroidApps(); showWindow("Applications"); }
                            else if(setting==3) showWindow("Linux Apps");
                            else if(setting==4) openSecurity();
                            else if(setting==5) new Updater(MainActivity.this).check();
                            else if(setting==6) showWindow("Clipboard");
                            else if(setting==7) { saveSession(); addNotification("Session saved"); }
                            invalidate();
                            return true;
                        }
                        if("Quick Settings".equals(hit.title) && y>hit.t+58 && y<hit.b-10){
                            float bw=(hit.r-hit.l-54)/2f;
                            int col=x < hit.l+18+bw+12 ? 0 : 1;
                            int row=(int)((y-(hit.t+70))/58f);
                            if(row>=0 && row<3){
                                int qi=row*2+col;
                                try {
                                    if(qi==0){
                                        wifiOn=true;
                                        if(Build.VERSION.SDK_INT>=29) startActivity(new Intent(android.provider.Settings.Panel.ACTION_WIFI));
                                        else startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
                                    } else if(qi==1){
                                        startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
                                    } else if(qi==2){
                                        android.media.AudioManager am=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);
                                        if(am!=null){ am.adjustVolume(android.media.AudioManager.ADJUST_SAME, android.media.AudioManager.FLAG_SHOW_UI); soundOn=am.getRingerMode()!=android.media.AudioManager.RINGER_MODE_SILENT; }
                                    } else if(qi==3){
                                        rotationOn=!rotationOn;
                                        setRequestedOrientation(rotationOn?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                                    } else if(qi==4){
                                        darkMode=!darkMode;
                                        if(Build.VERSION.SDK_INT>=29){ android.app.UiModeManager um=getSystemService(UiModeManager.class); if(um!=null) um.setNightMode(darkMode?UiModeManager.MODE_NIGHT_YES:UiModeManager.MODE_NIGHT_NO); }
                                    }
                                } catch(Exception ex){ addNotification("Quick setting unavailable: "+ex.getMessage()); }
                                if(qi==5) { lockDesktop(); return true; }
                                addNotification(qi==5?"Desktop locked":"Quick setting changed");
                                invalidate();
                            }
                            return true;
                        }
                        if("Applications".equals(hit.title) && y>hit.t+54 && y<hit.b-10){
                            float cw=(hit.r-hit.l-48)/3f;
                            float localY=y-hit.t-62+windowScroll;
                            int col=(int)((x-(hit.l+16))/(cw+8));
                            int row=(int)(localY/116f);
                            int ai=row*3+col;
                            float cardX=hit.l+16+col*(cw+8), cardY=hit.t+62+row*116-windowScroll;
                            if(col>=0&&col<3&&row>=0&&ai>=0&&ai<6+androidApps.size()
                                    &&x>=cardX&&x<=cardX+cw&&y>=cardY&&y<=cardY+104){
                                String[] appNames={"Files","Terminal","Browser","Settings","Media","Linux Apps"};
                                if(ai<appNames.length){
                                    String a=appNames[ai];
                                    if(a.equals("Terminal")) launchTerminal();
                                    else if(a.equals("Browser")) launchBrowser();
                                    else if(a.equals("Files")) showWindow("Files");
                                    else if(a.equals("Settings")) showWindow("Settings");
                                    else if(a.equals("Media")) showWindow("Media");
                                    else showWindow("Linux Apps");
                                } else {
                                    launchAndroidApp(androidApps.get(ai-appNames.length));
                                }
                                return true;
                            }
                            return true;
                        }
                        if("Linux Apps".equals(hit.title) && y>hit.t+116 && y<hit.b-12){
                            float bw=(hit.r-hit.l-54)/2f;
                            int col=x < hit.l+18+bw+9 ? 0 : 1;
                            int row=(int)((y-(hit.t+128))/66f);
                            int li=row*2+col;
                            String[] cmds={"python3","vim","nano","htop","bash","python3"};
                            if(li>=0 && li<cmds.length){ launchLinuxTool(cmds[li]); return true; }
                            return true;
                        }
                        if("Media".equals(hit.title) && y>hit.t+125 && y<hit.b-10){
                            showWindow("Files");
                            return true;
                        }
                        if("Clipboard".equals(hit.title) && y>hit.t+58 && y<hit.b-10){
                            int ci=(int)((y-(hit.t+68)+windowScroll)/40f);
                            if(ci>=0 && ci<Math.min(8,clipboardHistory.size())){
                                try{
                                    ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                                    cm.setPrimaryClip(ClipData.newPlainText("METMC",clipboardHistory.get(ci)));
                                    addNotification("Clipboard item copied");
                                }catch(Exception ignored){}
                            }
                            return true;
                        }
                        if("Media".equals(hit.title) && y>hit.t+125 && y<hit.b-10){
                            showWindow("Files"); return true;
                        }
                        if("Wallpaper Manager".equals(hit.title) && y>hit.t+58 && y<hit.b-10){
                            int col=x < hit.l+(hit.r-hit.l)/2f ? 0 : 1;
                            int row=(int)((y-(hit.t+68))/96f);
                            int wi=row*2+col;
                            if(wi==4) pickWallpaper();
                            else if(wi>=0 && wi<4){ prefs.edit().putInt("wallpaper",wi).apply(); invalidate(); }
                            return true;
                        }
                        if("Overview".equals(hit.title) && y>hit.t+78 && y<hit.b-10){
                            int col=x < hit.l+(hit.r-hit.l)/2f ? 0 : 1;
                            int row=(int)((y-(hit.t+92))/82f);
                            int oi=row*2+col;
                            if(oi>=0 && oi<openWindows.size()){
                                bringToFront(openWindows.get(oi)); invalidate(); syncTerminalOverlay();
                            }
                            return true;
                        }
                        if(y>=hit.t&&y<=hit.t+50){
                            if(x>=rr-112&&x<rr-78){ minimizeWindow(hit.title); return true; }
                            if(x>=rr-78&&x<rr-44){ toggleMaximize(hit.title); return true; }
                            if(x>=rr-46&&x<=rr){ closeWindow(hit.title); return true; }
                            long now=e.getEventTime();
                            if(hit.title.equals(lastWindowTapTitle) && now-lastWindowTapTime<350){
                                lastWindowTapTime=0;
                                toggleMaximize(hit.title);
                                return true;
                            }
                            lastWindowTapTitle=hit.title;
                            lastWindowTapTime=now;
                            draggingWindow=hit.title; dragging=true;
                            dragOffsetX=x-hit.l; dragOffsetY=y-hit.t;
                            return true;
                        }
                        if(x>=rr-34&&y>=bb-34){
                            if(hit.maximized){
                                hit.l=hit.restoreL; hit.t=hit.restoreT; hit.r=hit.restoreR; hit.b=hit.restoreB; hit.maximized=false; clampWindow(hit);
                            }
                            draggingWindow=hit.title; resizing=true; return true;
                        }
                        return true;
                    }
                    String task=windowTaskAt(x,y);
                    if(task!=null){ bringToFront(task); invalidate(); syncTerminalOverlay(); return true; }
                }
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                float dy=lastTouchY-y;
                if(surface!=Surface.DESKTOP && !scrollingSurface && Math.abs(y-downY)>10) scrollingSurface=true;
                if(surface!=Surface.DESKTOP && scrollingSurface){
                    surfaceScroll+=dy; clampSurfaceScroll(); lastTouchY=y; invalidate(); return true;
                }
                if(surface==Surface.DESKTOP&&scrollingWindow && scrollingWindowTitle!=null){
                    WindowState sws=windows.get(scrollingWindowTitle);
                    if(sws!=null){
                        windowScroll+=dy; windowScroll=Math.max(0,Math.min(windowScrollMax(scrollingWindowTitle,sws),windowScroll));
                        lastTouchY=y; invalidate(); return true;
                    }
                }
                if(surface==Surface.DESKTOP&&draggingWindow!=null){
                    WindowState ws=windows.get(draggingWindow);
                    if(ws!=null&&!ws.maximized){
                        if(dragging){
                            float ww=ws.r-ws.l,hh=ws.b-ws.t;
                            ws.l=Math.max(10,Math.min(w-ww-10,x-dragOffsetX));
                            ws.t=Math.max(56,Math.min(h-hh-92,y-dragOffsetY));
                            ws.r=ws.l+ww;ws.b=ws.t+hh;
                        } else if(resizing){
                            ws.r=Math.max(ws.l+MIN_W,Math.min(w-10,x));
                            ws.b=Math.max(ws.t+MIN_H,Math.min(h-92,y));
                        }
                        invalidate();syncTerminalOverlay();
                    }
                    return true;
                }
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_CANCEL||e.getAction()==MotionEvent.ACTION_UP){
                if(scrollingSurface){ scrollingSurface=false; scrollingWindow=false; scrollingWindowTitle=null; return true; }
                if(scrollingWindow){ scrollingWindow=false; scrollingWindowTitle=null; return true; }
                if(draggingWindow!=null){
                    String moved=draggingWindow;
                    if(e.getAction()==MotionEvent.ACTION_UP && dragging){
                        WindowState ws=windows.get(moved);
                        if(ws!=null&&!ws.maximized){
                            if(x<=18) snapWindow(moved,0);
                            else if(x>=w-18) snapWindow(moved,1);
                            else if(y<=66) toggleMaximize(moved);
                        }
                    }
                    draggingWindow=null;dragging=false;resizing=false;syncTerminalOverlay();
                    if(e.getAction()==MotionEvent.ACTION_UP&&Math.abs(x-downX)>8)return true;
                }
            }

            if(surface==Surface.QUICK){
                float ql=w-330;
                if(x>=ql+18&&x<=ql+148&&y>=148&&y<=202){wifiOn=!wifiOn;addNotification("Wi-Fi "+(wifiOn?"enabled":"disabled"));invalidate();return true;}
                if(x>=ql+163&&x<=ql+293&&y>=148&&y<=202){bluetoothOn=!bluetoothOn;addNotification("Bluetooth "+(bluetoothOn?"enabled":"disabled"));invalidate();return true;}
                if(x>=ql+18&&x<=ql+148&&y>=216&&y<=270){soundOn=!soundOn;addNotification("Sound "+(soundOn?"enabled":"muted"));invalidate();return true;}
                if(x>=ql+163&&x<=ql+293&&y>=216&&y<=270){rotationOn=!rotationOn;addNotification("Rotation "+(rotationOn?"enabled":"locked"));invalidate();return true;}
                if(x>=ql+18&&x<=ql+148&&y>=284&&y<=338){darkMode=!darkMode;invalidate();return true;}
                if(x>=ql+163&&x<=ql+293&&y>=284&&y<=338){lockDesktop();return true;}
                if(y<55 && x>w-150){
                    surface=Surface.DESKTOP;
                    invalidate();
                    return true;
                }
                if(!(x>=ql&&x<=w-18&&y>=68&&y<=h-92)){
                    surface=Surface.DESKTOP;
                    invalidate();
                }
                return true;
            }

            if(surface==Surface.WALLPAPER){
                for(int i=0;i<5;i++){float l=28+(i%2)*(w/2f-20),t=140+(i/2)*150;if(x>=l&&x<=l+w/2f-32&&y>=t&&y<=t+130){if(i==4){pickWallpaper();}else{prefs.edit().putInt("wallpaper",i).apply();surface=Surface.DESKTOP;invalidate();}return true;}}
                return true;
            }
            if(y<55&&x>=w/2f-110&&x<=w/2f+110){
                int target=Math.max(1,Math.min(4,1+(int)((x-(w/2f-105))/52f)));
                switchWorkspace(target); return true;
            }
            if(y<55&&x>=188&&x<=Math.min(390,w-190)){showGlobalSearch();return true;}
            if(y<55&&x<180){
                if(surface==Surface.DESKTOP) showWindow("Overview"); else { surface=Surface.DESKTOP; invalidate(); }
                return true;
            }
            if(y<55&&x>w-150){
                if(surface==Surface.DESKTOP) showWindow("Quick Settings"); else { surface=Surface.DESKTOP; invalidate(); }
                return true;
            }

            if(surface==Surface.DESKTOP && y>=86 && y<=270 && x<=190 && windowAt(x,y)==null){
                int col=x<105?0:1, row=(int)((y-86)/92f), idx=row*2+col;
                if(idx==0){showWindow("Files");return true;}
                if(idx==1){launchTerminal();return true;}
                if(idx==2){launchBrowser();return true;}
                if(idx==3){showWindow("Applications");return true;}
            }
            if(surface==Surface.OVERVIEW){
                for(int i=0;i<openWindows.size();i++){
                    float l=28+(i%2)*(w/2f-18),t=140+(i/2)*142,r=l+w/2f-26,b=t+118;
                    if(x>=l&&x<=r&&y>=t&&y<=b){bringToFront(openWindows.get(i));surface=Surface.DESKTOP;invalidate();syncTerminalOverlay();return true;}
                }
            }
            if(surface==Surface.CLIPBOARD){
                float l=70,r=w-70;
                for(int i=0;i<Math.min(8,clipboardHistory.size());i++){
                    float yy=150+i*48;
                    if(x>=l+18&&x<=r-18&&y>=yy&&y<=yy+38){
                        try{ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("METMC",clipboardHistory.get(i)));addNotification("Clipboard item pasted");}catch(Exception ignored){}
                        surface=Surface.DESKTOP;invalidate();return true;
                    }
                }
                if(x<l||x>r||y<70||y>h-92){surface=Surface.DESKTOP;invalidate();return true;}
            }
            if(surface==Surface.APPS){
                refreshAndroidApps();
                if(scrollingSurface) return true;
                String[] n={"Files","Terminal","Browser","Settings","Media","Linux Apps"};float cw=(w-112)/3f,top=138-surfaceScroll;int total=n.length+androidApps.size();
                for(int i=0;i<total;i++){
                    int col=i%3,row=i/3;float l=28+col*(cw+24),t=top+row*122;
                    if(x>=l-8&&x<=l+cw+8&&y>=t-8&&y<=t+104){
                        if(i<n.length){
                            String a=n[i];
                            if(a.equals("Terminal"))launchTerminal();
                            else if(a.equals("Browser"))launchBrowser();
                            else if(a.equals("Files"))showWindow("Files");
                            else if(a.equals("Settings"))showWindow("Settings");
                            else if(a.equals("Linux Apps"))showWindow("Linux Apps");
                            else if(a.equals("Media"))showWindow("Media");
                        }
                        else launchAndroidApp(androidApps.get(i-n.length));
                        invalidate();return true;
                    }
                }
            }

            if(surface==Surface.SETTINGS){
                for(int i=0;i<8;i++){float yy=136+i*57;if(y>=yy&&y<=yy+49){if(i==0)showWindow("Wallpaper Manager");else if(i==1)showWindow("Desktop & Workspaces");else if(i==2)showWindow("Applications");else if(i==3)showWindow("Linux Apps");else if(i==4)openSecurity();else if(i==5)showWindow("System Update");else if(i==6)showWindow("Clipboard");else if(i==7)showWindow("Session Manager");invalidate();return true;}}
            }

            return true;
        }

        WindowState windowAt(float x,float y){
            for(int i=openWindows.size()-1;i>=0;i--){
                WindowState ws=windows.get(openWindows.get(i));
                if(ws!=null&&!ws.minimized&&x>=ws.l&&x<=ws.r&&y>=ws.t&&y<=ws.b)return ws;
            }
            return null;
        }

        String windowTaskAt(float x,float y){
            float tx=24,ty=getHeight()-126;
            for(String title:new ArrayList<>(openWindows)){
                if(isDockApp(title)) continue;
                WindowState ws=windows.get(title);
                if(ws==null)continue;
                float bw=Math.min(150,Math.max(92,title.length()*8f+58));
                if(x>=tx&&x<=tx+bw&&y>=ty&&y<=ty+38)return title;
                tx+=bw+8;
            }
            return null;
        }

        void minimizeWindow(String name){
            WindowState ws=windowFor(name);ws.minimized=true;
            if(name.equals("Terminal"))hideTerminalOverlay();
            activeWindow=null;
            for(int i=openWindows.size()-1;i>=0;i--){WindowState n=windows.get(openWindows.get(i));if(n!=null&&!n.minimized){activeWindow=n.title;break;}}
            invalidate();syncTerminalOverlay();
        }

        void toggleMaximize(String name){
            WindowState ws=windowFor(name);
            if(ws.maximized){
                ws.l=ws.restoreL;ws.t=ws.restoreT;ws.r=ws.restoreR;ws.b=ws.restoreB;ws.maximized=false;
                clampWindow(ws);
            }else{
                ws.restoreL=ws.l;ws.restoreT=ws.t;ws.restoreR=ws.r;ws.restoreB=ws.b;
                ws.l=10;ws.t=58;ws.r=Math.max(ws.l+MIN_W,getWidth()-10);ws.b=Math.max(ws.t+MIN_H,getHeight()-92);ws.maximized=true;
            }
            bringToFront(name);invalidate();syncTerminalOverlay();
        }

        void clampWindow(WindowState ws){
            float top=58,bottom=Math.max(top+MIN_H,getHeight()-92),left=10,right=Math.max(left+MIN_W,getWidth()-10);
            float ww=Math.min(ws.r-ws.l,right-left),hh=Math.min(ws.b-ws.t,bottom-top);
            ws.l=Math.max(left,Math.min(ws.l,right-ww)); ws.t=Math.max(top,Math.min(ws.t,bottom-hh));
            ws.r=ws.l+ww; ws.b=ws.t+hh;
        }

        void snapWindow(String name,int side){
            WindowState ws=windowFor(name);
            if(ws.maximized) return;
            ws.restoreL=ws.l;ws.restoreT=ws.t;ws.restoreR=ws.r;ws.restoreB=ws.b;
            float top=58,bottom=getHeight()-92,mid=getWidth()/2f;
            if(side==0){ws.l=10;ws.t=top;ws.r=mid-5;ws.b=bottom;}
            else {ws.l=mid+5;ws.t=top;ws.r=getWidth()-10;ws.b=bottom;}
            ws.maximized=false;
            bringToFront(name);invalidate();syncTerminalOverlay();
        }

        void closeWindow(String name){
            if(name.equals("Terminal"))removeTerminalOverlay();
            ArrayList<String> list=workspaceWindows.get(currentWorkspace);
            if(list!=null) list.remove(name);
            openWindows.remove(name);windows.remove(name); addNotification(name+" closed"); saveSession();
            activeWindow=null;
            if(!openWindows.isEmpty()){
                for(int i=openWindows.size()-1;i>=0;i--){WindowState ws=windows.get(openWindows.get(i));if(ws!=null&&!ws.minimized){activeWindow=ws.title;break;}}
            }
            invalidate();syncTerminalOverlay();
        }

        void hideTerminalOverlay(){
            if(terminalPrompt!=null)terminalPrompt.setVisibility(View.GONE);
            if(terminalInput!=null)terminalInput.setVisibility(View.GONE);
            if(terminalOutput!=null&&terminalOutput.getParent()!=null)((View)terminalOutput.getParent()).setVisibility(View.GONE);
        }

        void syncTerminalOverlay(){
            WindowState ws=windows.get("Terminal");
            if(ws==null||ws.minimized||!"Terminal".equals(activeWindow)||surface!=Surface.DESKTOP){hideTerminalOverlay();return;}
            if(terminalInput==null||terminalOutput==null||terminalPrompt==null)return;
            terminalPrompt.setVisibility(View.VISIBLE);
            terminalInput.setVisibility(View.VISIBLE);
            if(terminalOutput.getParent()!=null)((View)terminalOutput.getParent()).setVisibility(View.VISIBLE);

            ViewParent terminalParent=terminalOutput.getParent();
            FrameLayout.LayoutParams sp=(FrameLayout.LayoutParams)((View)terminalParent).getLayoutParams();
            sp.width=Math.max(1,(int)(ws.r-ws.l-24));
            sp.height=Math.max(1,(int)(ws.b-ws.t-166));
            sp.leftMargin=(int)ws.l+12;
            sp.topMargin=(int)ws.t+108;
            sp.rightMargin=0; sp.bottomMargin=0;
            ((View)terminalParent).setLayoutParams(sp);

            FrameLayout.LayoutParams pp=(FrameLayout.LayoutParams)terminalPrompt.getLayoutParams();
            pp.width=132; pp.height=50;
            pp.leftMargin=(int)ws.l+12; pp.topMargin=(int)ws.t+54;
            pp.rightMargin=0; pp.bottomMargin=0; pp.gravity=Gravity.TOP|Gravity.LEFT;
            terminalPrompt.setLayoutParams(pp);

            FrameLayout.LayoutParams ip=(FrameLayout.LayoutParams)terminalInput.getLayoutParams();
            ip.width=Math.max(1,(int)(ws.r-ws.l-150)); ip.height=50;
            ip.leftMargin=(int)ws.l+140; ip.topMargin=(int)ws.t+54;
            ip.rightMargin=0; ip.bottomMargin=0; ip.gravity=Gravity.TOP|Gravity.LEFT;
            terminalInput.setLayoutParams(ip);
        }
        void showWindow(String name){
            if("Files".equals(name) && android.os.Build.VERSION.SDK_INT>=30 && !android.os.Environment.isExternalStorageManager()){
                try{
                    Intent i=new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    i.setData(Uri.parse("package:"+getPackageName()));
                    startActivity(i);
                }catch(Exception ignored){}
            }
            ArrayList<String> list=workspaceWindows.get(currentWorkspace);
            if(list==null){ list=new ArrayList<>(); workspaceWindows.put(currentWorkspace,list); }
            if(!list.contains(name)) list.add(name);
            WindowState ws=windowFor(name);
            if(!openWindows.contains(name))openWindows.add(name);
            recentItems.remove(name); recentItems.add(0,name);
            addNotification(name+" opened");
            while(recentItems.size()>12)recentItems.remove(recentItems.size()-1);
            bringToFront(name);
            surface=Surface.DESKTOP;
            resetSurfaceScroll();
            if(name.equals("Terminal"))postDelayed(()->{buildTerminalOverlay();syncTerminalOverlay();},80);
            invalidate();
        }
    }
}