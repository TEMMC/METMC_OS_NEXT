package com.metmc.os.next;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
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
    BufferedWriter terminalStdin;
    java.lang.Process terminalShell;
    static final String ROOTFS = "/data/local/linux/rootfs";
    android.content.SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.rgb(5,8,12));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        prefs = getSharedPreferences("metmc", MODE_PRIVATE);
        root = new FrameLayout(this);
        desktop = new DesktopView(this);
        root.addView(desktop, new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        new Handler().postDelayed(() -> new Updater(MainActivity.this).check(), 2500);
    }

    @Override protected void onResume() {
        super.onResume();
        if (desktop != null) { desktop.refreshAndroidApps(); desktop.invalidate(); }
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
                Intent li = pm.getLaunchIntentForPackage(ai.packageName);
                if (li != null) {
                    ResolveInfo r = pm.resolveActivity(li, PackageManager.MATCH_ALL);
                    if (r != null) found.put(ai.packageName + "/" + r.activityInfo.name, r);
                }
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

    void launchAndroidApp(ResolveInfo info) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
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

    void launchBrowser() {
        try { desktop.showWindow("Browser"); startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))); }
        catch (Exception e) { desktop.showWindow("Browser"); }
    }

    void launchTerminal() { desktop.showWindow("Terminal"); startTerminalShell(); }

    void startTerminalShell() {
        if (terminalShell != null && terminalShell.isAlive()) return;
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
                        "exec chroot \"$R\" /bin/bash -l";
                terminalShell = new ProcessBuilder("su","-c",script).redirectErrorStream(true).start();
                terminalStdin = new BufferedWriter(new OutputStreamWriter(terminalShell.getOutputStream()));
                BufferedReader br = new BufferedReader(new InputStreamReader(terminalShell.getInputStream()));
                char[] b = new char[4096]; int n;
                while ((n=br.read(b)) != -1) {
                    String s = new String(b,0,n);
                    runOnUiThread(() -> appendTerminal(s));
                }
            } catch (Exception e) {
                runOnUiThread(() -> appendTerminal("\n[METMC] Terminal startup failed: " + e + "\n"));
            }
        });
    }

    void appendTerminal(String s) {
        if (terminalOutput != null) {
            terminalOutput.append(s);
            terminalOutput.post(() -> {
                if (terminalOutput.getParent() instanceof ScrollView) ((ScrollView)terminalOutput.getParent()).fullScroll(View.FOCUS_DOWN);
            });
        }
    }

    void sendTerminalCommand() {
        if (terminalStdin == null || terminalInput == null) return;
        String cmd = terminalInput.getText().toString();
        if (cmd.trim().isEmpty()) return;
        try { terminalStdin.write(cmd); terminalStdin.newLine(); terminalStdin.flush(); terminalInput.setText(""); }
        catch (Exception e) { appendTerminal("\n[Command failed] " + e + "\n"); }
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
        terminalOutput.setTextSize(12);
        terminalOutput.setTypeface(Typeface.MONOSPACE);
        terminalOutput.setPadding(12,8,12,8);
        terminalOutput.setGravity(Gravity.TOP|Gravity.START);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xff080c0d);
        scroll.addView(terminalOutput, new ScrollView.LayoutParams(-1,-1));
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1,0);
        sp.leftMargin=32; sp.rightMargin=32; sp.topMargin=205; sp.bottomMargin=130;
        root.addView(scroll, sp);

        terminalInput = new EditText(this);
        terminalInput.setSingleLine(true);
        terminalInput.setTextColor(Color.WHITE);
        terminalInput.setHintTextColor(0xff738294);
        terminalInput.setHint("root@debian:~$ command");
        terminalInput.setTextSize(12);
        terminalInput.setTypeface(Typeface.MONOSPACE);
        terminalInput.setPadding(12,0,12,0);
        terminalInput.setBackgroundColor(0xff151e27);
        terminalInput.setOnEditorActionListener((v,id,event)->{ sendTerminalCommand(); return true; });
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-1,52);
        ip.leftMargin=32; ip.rightMargin=100; ip.gravity=Gravity.BOTTOM; ip.bottomMargin=98;
        root.addView(terminalInput,ip);

        Button run = new Button(this);
        run.setText("RUN"); run.setTextColor(Color.WHITE); run.setAllCaps(false);
        run.setOnClickListener(v -> sendTerminalCommand());
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(72,52);
        rp.gravity=Gravity.RIGHT|Gravity.BOTTOM; rp.rightMargin=28; rp.bottomMargin=98;
        root.addView(run,rp);
        terminalInput.requestFocus();
    }

    void removeTerminalOverlay() {
        if (terminalInput != null) {
            ViewParent p=terminalInput.getParent(); if(p instanceof ViewGroup) ((ViewGroup)p).removeView(terminalInput);
            terminalInput=null;
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

    enum Surface { DESKTOP, OVERVIEW, APPS, QUICK, SETTINGS, NOTIFICATIONS, WALLPAPER }

    class DesktopView extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Surface surface=Surface.DESKTOP;
        String activeWindow=null;
        final ArrayList<String> openWindows=new ArrayList<>();
        final String[] dockApps={"Files","Terminal","Browser","Settings"};
        final ArrayList<ResolveInfo> androidApps=new ArrayList<>();
        float downX,downY,appsScroll=0;
        float appsContentHeight=0;
        int accent=0xff39ff88;

        DesktopView(Context c){ super(c); setFocusable(true); refreshAndroidApps(); }

        void refreshAndroidApps(){
            androidApps.clear(); androidApps.addAll(getAndroidApps());
            float rows=(float)Math.ceil((6+androidApps.size())/3.0);
            appsContentHeight=rows*102f;
            float max=Math.max(0,appsContentHeight-(getHeight()-270));
            if(appsScroll>max) appsScroll=max;
        }

        void fill(Canvas c,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setShader(null);}
        void stroke(Canvas c,int color,float w){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w);p.setColor(color);p.setShader(null);}
        void round(Canvas c,float l,float t,float rr,float b,float rad,int color){fill(c,color);c.drawRoundRect(l,t,rr,b,rad,rad,p);}
        void text(Canvas c,String s,float x,float y,float size,int color){fill(c,color);p.setTypeface(Typeface.create("sans",Typeface.NORMAL));p.setTextSize(size);c.drawText(s,x,y,p);}
        void bold(Canvas c,String s,float x,float y,float size,int color){fill(c,color);p.setTypeface(Typeface.create("sans",Typeface.BOLD));p.setTextSize(size);c.drawText(s,x,y,p);}

        @Override protected void onDraw(Canvas c){
            int w=getWidth(),h=getHeight();
            drawWallpaper(c,w,h); drawTopBar(c,w); drawDock(c,w,h);
            if(surface==Surface.OVERVIEW) drawOverview(c,w,h);
            else if(surface==Surface.APPS) drawApps(c,w,h);
            else if(surface==Surface.QUICK) drawQuick(c,w,h);
            else if(surface==Surface.SETTINGS) drawSettings(c,w,h);
            else if(surface==Surface.NOTIFICATIONS) drawNotifications(c,w,h);
            else if(surface==Surface.WALLPAPER) drawWallpaperManager(c,w,h);
            if(activeWindow!=null && surface==Surface.DESKTOP) drawWindow(c,w,h,activeWindow);
        }

        int wallpaper(){return prefs.getInt("wallpaper",0);}
        void drawWallpaper(Canvas c,int w,int h){
            int style=wallpaper();
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
            } else {
                LinearGradient g=new LinearGradient(0,0,w,h,0xff061016,0xff170a20,Shader.TileMode.CLAMP);
                p.setShader(g);c.drawRect(0,0,w,h,p);p.setShader(null);
                stroke(c,0x4439ff88,1);
                for(int i=0;i<10;i++)c.drawCircle(w*.18f+i*85,h*.35f,35+i*5,p);
                bold(c,"NIGHT//OPS",34,h-125,27,0xff39ff88);
            }
        }

        void drawTopBar(Canvas c,int w){
            fill(c,0xe50a1017);c.drawRect(0,0,w,54,p);
            bold(c,"METMC",22,34,16,Color.WHITE); text(c,"Activities",92,34,14,0xffd7deea);
            text(c,"WORKSPACE 1",w/2f-47,34,11,0xff8da0b4);
            text(c,new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()),w-92,33,14,Color.WHITE);
            text(c,"⌁",w-44,34,15,0xff8da0b4);
        }

        void drawDock(Canvas c,int w,int h){
            float dw=Math.min(590,w-28),left=(w-dw)/2f,top=h-76;
            round(c,left-8,top-8,left+dw+8,h-12,24,0xe5151c25);
            drawDockIcon(c,left+8,top,"apps",surface==Surface.APPS); float x=left+62;
            for(String a:dockApps){drawDockIcon(c,x,top,a,false);x+=72;}
            drawDockIcon(c,left+dw-54,top,"quick",surface==Surface.QUICK);
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
            else if(n.equals("quick")){fill(c,0xffd7e1ef);c.drawCircle(cx,cy,11,p);fill(c,0xff202934);c.drawCircle(cx+4,cy-4,3,p);}
            else c.drawRect(cx-10,cy-9,cx+10,cy+9,p);
        }

        void overlay(Canvas c,int w,int h){fill(c,0xb003080d);c.drawRect(0,54,w,h-82,p);}

        void drawApps(Canvas c,int w,int h){
            overlay(c,w,h);bold(c,"Applications",28,92,25,Color.WHITE);
            text(c,androidApps.size()+" launchable Android apps detected",28,116,13,0xff9daabd);
            String[] n={"Files","Terminal","Browser","Settings","Media","Linux Apps"};
            int total=n.length+androidApps.size();float cw=(w-84)/3f,top=138-appsScroll;
            c.save();c.clipRect(20,130,w-20,h-88);
            for(int i=0;i<total;i++){
                int col=i%3,row=i/3;float l=28+col*(cw+14),t=top+row*102;
                if(t>h-82||t+86<130)continue;
                round(c,l,t,l+cw,t+86,18,0xff171f2a);
                if(i<n.length){drawAppIcon(c,l+28,t+18,n[i]);bold(c,n[i],l+62,t+33,14,Color.WHITE);text(c,appSubtitle(n[i]),l+62,t+55,11,0xff8d9aab);}
                else {ResolveInfo r=androidApps.get(i-n.length);String s=String.valueOf(r.loadLabel(getPackageManager()));drawAppIcon(c,l+28,t+18,"Android");bold(c,s,l+62,t+33,14,Color.WHITE);text(c,r.activityInfo.packageName,l+62,t+55,9,0xff718093);}
            }
            c.restore();
            round(c,28,h-76,w-28,h-34,14,0xff151d27);text(c,"⌕",47,h-48,20,0xffaeb9c8);text(c,"Installed Android + METMC applications",77,h-50,13,0xff98a5b6);
        }

        String appSubtitle(String s){if(s.equals("Files"))return"File manager";if(s.equals("Terminal"))return"Native Debian terminal";if(s.equals("Browser"))return"Web browser";if(s.equals("Settings"))return"System controls";if(s.equals("Media"))return"Media player";return"Linux integration";}

        void drawAppIcon(Canvas c,float x,float y,String s){
            round(c,x,y,x+34,y+34,10,0xff26364b);stroke(c,0xffdce6f3,1.8f);
            if(s.equals("Files"))round(c,x+8,y+10,x+26,y+25,3,0xffdce6f3);
            else if(s.equals("Terminal")){c.drawRect(x+7,y+8,x+27,y+26,p);c.drawLine(x+11,y+14,x+16,y+18,p);}
            else if(s.equals("Browser")){c.drawCircle(x+17,y+17,10,p);c.drawLine(x+7,y+17,x+27,y+17,p);}
            else if(s.equals("Settings")){c.drawCircle(x+17,y+17,7,p);c.drawCircle(x+17,y+17,2,p);}
            else c.drawRect(x+8,y+9,x+26,y+25,p);
        }

        void drawOverview(Canvas c,int w,int h){overlay(c,w,h);bold(c,"Overview",28,92,25,Color.WHITE);if(openWindows.isEmpty()){round(c,28,142,w-28,214,18,0xff151c26);text(c,"No open METMC windows",50,177,15,0xffdbe3ef);}else{for(int i=0;i<openWindows.size();i++){String s=openWindows.get(i);float l=28+(i%2)*(w/2f-18),t=140+(i/2)*118;round(c,l,t,l+w/2f-26,t+96,18,0xff18212c);drawAppIcon(c,l+22,t+21,s);bold(c,s,l+58,t+29,15,Color.WHITE);text(c,"Workspace 1",l+58,t+51,12,0xff8795a8);}}}

        void drawQuick(Canvas c,int w,int h){overlay(c,w,h);float l=w-330;round(c,l,68,w-18,h-92,22,0xff151c26);bold(c,"Quick Settings",l+24,104,21,Color.WHITE);String[] q={"Wi-Fi","Bluetooth","Sound","Rotation","Dark Mode","Lock"};for(int i=0;i<q.length;i++){int col=i%2,row=i/2;float x=l+18+col*145,y=148+row*68;round(c,x,y,x+130,y+54,15,0xff202a35);bold(c,q[i],x+14,y+23,12,Color.WHITE);text(c,"Tap to toggle",x+14,y+41,10,0xff9eacbe);}}

        void drawSettings(Canvas c,int w,int h){
            overlay(c,w,h);bold(c,"Settings",30,91,26,Color.WHITE);text(c,"Professional METMC control center",30,116,12,0xff8f9bad);
            String[] groups={"Appearance","Desktop","Applications","Linux integration","Security","System Update"};
            String[] desc={"Wallpaper manager, themes and hacker styles","Panel, dock and workspace behavior","Refresh and launch installed Android apps","Debian terminal and Linux applications","Lock-screen and security controls","Check for a newer METMC OS build"};
            for(int i=0;i<groups.length;i++){float y=136+i*66;round(c,28,y,w-28,y+56,15,0xff151d27);drawAppIcon(c,44,y+11,"Settings");bold(c,groups[i],92,y+23,14,Color.WHITE);text(c,desc[i],92,y+42,11,0xff8996a8);text(c,"›",w-50,y+34,23,0xff8d9bad);}
        }

        void drawWallpaperManager(Canvas c,int w,int h){
            overlay(c,w,h);bold(c,"Wallpaper Manager",30,91,26,Color.WHITE);text(c,"Built-in METMC hacker wallpapers",30,116,12,0xff8f9bad);
            String[] names={"Midnight Grid","Cyber Nexus","Night Ops","Deep Blue"};
            for(int i=0;i<4;i++){float l=28+(i%2)*(w/2f-20),t=140+(i/2)*150;round(c,l,t,l+w/2f-32,t+130,18,0xff10171e);drawWallpaperPreview(c,l+8,t+8,w/2f-48,114,i);bold(c,names[i],l+20,t+102,13,Color.WHITE);text(c,wallpaper()==i?"ACTIVE":"APPLY",l+w/2f-105,t+102,11,wallpaper()==i?0xff39ff88:0xff9eacbe);}
            text(c,"Tap a wallpaper to apply it instantly.",30,h-105,12,0xff9aa7b8);
        }

        void drawWallpaperPreview(Canvas c,float x,float y,float ww,float hh,int style){fill(c,0xff05080d);c.drawRect(x,y,x+ww,y+hh,p);stroke(c,0x8839ff88,1);for(int gx=(int)x;gx<x+ww;gx+=20)c.drawLine(gx,y,gx,y+hh,p);for(int gy=(int)y;gy<y+hh;gy+=20)c.drawLine(x,gy,x+ww,gy,p);bold(c,style==1?"NEXUS":style==2?"OPS":"METMC",x+16,y+42,20,0xff39ff88);}

        void drawNotifications(Canvas c,int w,int h){overlay(c,w,h);float l=w-360;round(c,l,68,w-18,h-92,22,0xff151c26);bold(c,"Notifications",l+24,105,21,Color.WHITE);text(c,"You're all caught up",l+24,135,13,0xff9aa7b8);}

        void drawWindow(Canvas c,int w,int h,String title){
            overlay(c,w,h);float l=18,t=64,rr=w-18,bb=h-88;round(c,l,t,rr,bb,20,0xff0d141b);stroke(c,0x663e5064,1);c.drawRoundRect(l,t,rr,bb,20,20,p);round(c,l,t,rr,t+54,20,0xff18222e);c.drawRect(l,t+28,rr,t+54,p);bold(c,title,l+22,t+34,15,Color.WHITE);
            round(c,rr-78,t+15,rr-46,t+39,8,0xff263342);text(c,"×",rr-68,t+33,15,0xffd7e0eb);
            if(title.equals("Terminal")){bold(c,"Debian shell",l+28,t+88,18,0xff39ff88);text(c,"root@debian • chroot • native METMC window",l+28,t+112,11,0xff7f9b8b);}
            else if(title.equals("Files")){bold(c,"METMC File Manager",l+28,t+90,20,Color.WHITE);text(c,"Root filesystem and shared storage",l+28,t+115,12,0xff8f9cad);}
            else if(title.equals("Linux Apps")){bold(c,"Linux Applications",l+28,t+90,20,Color.WHITE);text(c,"Native METMC application surface",l+28,t+115,12,0xff8f9cad);}
            else {bold(c,title,l+28,t+90,20,Color.WHITE);text(c,"Native METMC application window",l+28,t+115,12,0xff8f9cad);}
        }

        void drawAppIconSimple(Canvas c,float x,float y){drawAppIcon(c,x,y,"Android");}

        @Override public boolean onTouchEvent(MotionEvent e){
            float x=e.getX(),y=e.getY();int h=getHeight(),w=getWidth();
            if(e.getAction()==MotionEvent.ACTION_DOWN){downX=x;downY=y;return true;}
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;

            if(surface==Surface.WALLPAPER){
                for(int i=0;i<4;i++){float l=28+(i%2)*(w/2f-20),t=140+(i/2)*150;if(x>=l&&x<=l+w/2f-32&&y>=t&&y<=t+130){prefs.edit().putInt("wallpaper",i).apply();surface=Surface.DESKTOP;invalidate();return true;}}
                return true;
            }
            if(y<55&&x<180){surface=surface==Surface.OVERVIEW?Surface.DESKTOP:Surface.OVERVIEW;invalidate();return true;}
            if(y<55&&x>w-150){surface=Surface.QUICK;invalidate();return true;}

            if(y>h-88){
                float dw=Math.min(590,w-28),left=(w-dw)/2f;
                if(x>=left-8&&x<left+54){surface=surface==Surface.APPS?Surface.DESKTOP:Surface.APPS;invalidate();return true;}
                float pos=x-(left+62);
                if(pos>=0&&pos<288){int idx=(int)(pos/72);if(idx<4){String a=dockApps[idx];if(a.equals("Terminal"))launchTerminal();else if(a.equals("Browser"))launchBrowser();else if(a.equals("Files"))showWindow("Files");else surface=Surface.SETTINGS;invalidate();return true;}}
                if(x>left+dw-65){surface=Surface.QUICK;invalidate();return true;}
                if(x>left+dw-12){surface=Surface.NOTIFICATIONS;invalidate();return true;}
            }

            if(surface==Surface.APPS){
                if(Math.abs(y-downY)>24){appsScroll+=downY-y;float max=Math.max(0,appsContentHeight-(h-270));appsScroll=Math.max(0,Math.min(max,appsScroll));invalidate();return true;}
                String[] n={"Files","Terminal","Browser","Settings","Media","Linux Apps"};float cw=(w-84)/3f,top=138-appsScroll;int total=n.length+androidApps.size();
                for(int i=0;i<total;i++){int col=i%3,row=i/3;float l=28+col*(cw+14),t=top+row*102;if(x>=l&&x<=l+cw&&y>=t&&y<=t+86){if(i<n.length){String a=n[i];if(a.equals("Terminal"))launchTerminal();else if(a.equals("Browser"))launchBrowser();else if(a.equals("Files"))showWindow("Files");else if(a.equals("Settings"))surface=Surface.SETTINGS;else if(a.equals("Linux Apps"))showWindow("Linux Apps");else showWindow("Media");}else launchAndroidApp(androidApps.get(i-n.length));invalidate();return true;}}
            }

            if(surface==Surface.SETTINGS){
                for(int i=0;i<6;i++){float yy=136+i*66;if(y>=yy&&y<=yy+56){if(i==0)surface=Surface.WALLPAPER;else if(i==1)surface=Surface.QUICK;else if(i==2){refreshAndroidApps();surface=Surface.APPS;}else if(i==3)showWindow("Linux Apps");else if(i==4)showWindow("Security");else new Updater(MainActivity.this).check();invalidate();return true;}}
            }

            if(activeWindow!=null&&surface==Surface.DESKTOP&&y>=64&&x>w-105){if(activeWindow.equals("Terminal"))removeTerminalOverlay();activeWindow=null;invalidate();return true;}
            return true;
        }

        void showWindow(String name){if(!openWindows.contains(name))openWindows.add(name);activeWindow=name;surface=Surface.DESKTOP;if(name.equals("Terminal"))postDelayed(()->buildTerminalOverlay(),80);invalidate();}
    }
}
