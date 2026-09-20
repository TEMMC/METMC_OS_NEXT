package com.metmc.os.next;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    DesktopView desktop;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.rgb(7,10,15));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        desktop = new DesktopView(this);
        setContentView(desktop);
    }

    @Override public void onBackPressed() {
        if (desktop.surface != Surface.DESKTOP) { desktop.surface = Surface.DESKTOP; desktop.invalidate(); return; }
        if (desktop.activeWindow != null) { desktop.activeWindow = null; desktop.invalidate(); return; }
        super.onBackPressed();
    }

    void launchBrowser() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))); }
        catch (Exception ignored) {}
    }

    void launchTerminal() {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.setPackage("com.termux");
            startActivity(i);
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("Terminal")
                    .setMessage("Termux is not installed. METMC Terminal integration is ready for the Linux layer.")
                    .setPositiveButton("OK", null).show();
        }
    }

    enum Surface { DESKTOP, OVERVIEW, APPS, QUICK, SETTINGS, NOTIFICATIONS }

    class DesktopView extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        Surface surface = Surface.DESKTOP;
        String activeWindow = null;
        final ArrayList<String> openWindows = new ArrayList<>();
        final String[] dockApps = {"Files","Terminal","Browser","Settings"};
        long downTime;
        float downX, downY;
        int accent = Color.rgb(95,145,255);

        DesktopView(Context c) {
            super(c);
            p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
            setFocusable(true);
        }

        void fill(Canvas c, int color) { p.setStyle(Paint.Style.FILL); p.setColor(color); p.setShader(null); }
        void stroke(Canvas c, int color, float width) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(width); p.setColor(color); p.setShader(null); }
        void round(Canvas c,float l,float t,float rr,float b,float rad,int color) {
            fill(c,color); c.drawRoundRect(l,t,rr,b,rad,rad,p);
        }
        void text(Canvas c,String s,float x,float y,float size,int color) {
            fill(c,color); p.setTextSize(size); p.setTypeface(Typeface.create("sans",Typeface.NORMAL)); c.drawText(s,x,y,p);
        }
        void bold(Canvas c,String s,float x,float y,float size,int color) {
            fill(c,color); p.setTextSize(size); p.setTypeface(Typeface.create("sans",Typeface.BOLD)); c.drawText(s,x,y,p);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w=getWidth(), h=getHeight();
            drawWallpaper(c,w,h);
            drawTopBar(c,w);
            drawDesktop(c,w,h);
            drawDock(c,w,h);

            switch(surface) {
                case OVERVIEW: drawOverview(c,w,h); break;
                case APPS: drawApps(c,w,h); break;
                case QUICK: drawQuick(c,w,h); break;
                case SETTINGS: drawSettings(c,w,h); break;
                case NOTIFICATIONS: drawNotifications(c,w,h); break;
                default: break;
            }
            if(activeWindow != null && surface == Surface.DESKTOP) drawWindow(c,w,h,activeWindow);
        }

        void drawWallpaper(Canvas c,int w,int h) {
            LinearGradient g = new LinearGradient(0,0,w,h,
                    Color.rgb(19,30,48), Color.rgb(5,8,13), Shader.TileMode.CLAMP);
            p.setShader(g); c.drawRect(0,0,w,h,p); p.setShader(null);
            // subtle desktop light
            RadialGradient glow = new RadialGradient(w*.72f,h*.20f,Math.max(w,h)*.65f,
                    new int[]{Color.argb(42,100,150,255),Color.TRANSPARENT},null,Shader.TileMode.CLAMP);
            p.setShader(glow); c.drawRect(0,0,w,h,p); p.setShader(null);
        }

        void drawTopBar(Canvas c,int w) {
            fill(c,Color.argb(224,10,14,21)); c.drawRect(0,0,w,54,p);
            bold(c,"METMC",22,34,16,Color.WHITE);
            text(c,"Activities",92,34,14,0xffd7deea);
            text(c,"Workspace 1",w/2f-45,34,13,0xffaab5c5);
            text(c,new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date()),w-92,33,14,Color.WHITE);
            text(c,"▴",w-62,33,12,0xffaab5c5);
            text(c,"⌁",w-43,34,15,0xffaab5c5);
            text(c,"▣",w-24,34,12,0xffaab5c5);
        }

        void drawDesktop(Canvas c,int w,int h) {
            if(activeWindow == null) {
                // clean desktop status, not giant placeholder text
                text(c,"METMC OS",32,96,12,0xff8290a4);
                text(c,"Ready",32,118,22,0xffe8edf5);
                text(c,"Native Android desktop",32,141,13,0xff9ca8b9);
            }
        }

        void drawDock(Canvas c,int w,int h) {
            float dockW=Math.min(560,w-34), left=(w-dockW)/2f, top=h-76, bottom=h-12;
            round(c,left-8,top-8,left+dockW+8,bottom+8,24,Color.argb(218,18,23,31));
            stroke(c,0x263f4a5c,1); c.drawRoundRect(left-8,top-8,left+dockW+8,bottom+8,24,24,p);
            drawDockIcon(c,left+8,top,"apps",surface==Surface.APPS);
            float x=left+62;
            for(String app:dockApps) { drawDockIcon(c,x,top,app,false); x+=72; }
            drawDockIcon(c,left+dockW-54,top,"quick",surface==Surface.QUICK);
            drawDockIcon(c,left+dockW-8,top,"notify",surface==Surface.NOTIFICATIONS);
        }

        void drawDockIcon(Canvas c,float x,float y,String name,boolean selected) {
            if(selected) round(c,x,y,x+54,y+54,16,0xff293a56);
            else round(c,x+4,y+4,x+50,y+50,15,0xff202934);
            float cx=x+27, cy=y+27;
            if(name.equals("apps")) {
                fill(c,Color.WHITE); for(int i=0;i<3;i++)for(int j=0;j<3;j++)c.drawCircle(cx-9+j*9,cy-9+i*9,2.2f,p);
            } else if(name.equals("Files")) {
                round(c,cx-13,cy-10,cx+13,cy+11,4,0xffd7e1ef); round(c,cx-10,cy-14,cx+1,cy-8,3,0xffd7e1ef);
            } else if(name.equals("Terminal")) {
                stroke(c,0xffd7e1ef,2); c.drawRoundRect(cx-13,cy-11,cx+13,cy+11,4,4,p); c.drawLine(cx-8,cy-3,cx-2,cy+1,p); c.drawLine(cx-2,cy+1,cx-8,cy+5,p); c.drawLine(cx+2,cy+6,cx+8,cy+6,p);
            } else if(name.equals("Browser")) {
                stroke(c,0xffd7e1ef,2); c.drawCircle(cx,cy,12,p); c.drawLine(cx-12,cy,cx+12,cy,p); c.drawOval(cx-6,cy-12,cx+6,cy+12,p);
            } else if(name.equals("Settings")) {
                stroke(c,0xffd7e1ef,2.5f); c.drawCircle(cx,cy,8,p); c.drawCircle(cx,cy,3,p);
            } else if(name.equals("quick")) {
                fill(c,0xffd7e1ef); c.drawCircle(cx,cy,11,p); fill(c,0xff202934); c.drawCircle(cx+4,cy-4,3,p);
            } else {
                stroke(c,0xffd7e1ef,2); c.drawRect(cx-10,cy-9,cx+10,cy+9,p); c.drawLine(cx-10,cy-2,cx+10,cy-2,p);
            }
        }

        void drawOverview(Canvas c,int w,int h) {
            overlay(c,w,h);
            bold(c,"Overview",28,92,25,Color.WHITE);
            text(c,"Open windows",28,116,13,0xff9daabd);
            if(openWindows.isEmpty()) {
                round(c,28,142,w-28,214,18,0xff151c26);
                text(c,"No open windows",50,177,15,0xffdbe3ef);
                text(c,"Launch an app from the dock or Applications.",50,198,12,0xff8d99aa);
            } else {
                int i=0;
                for(String s:openWindows) {
                    float l=28+(i%2)*(w/2f-18), t=140+(i/2)*118;
                    round(c,l,t,l+w/2f-26,t+96,18,0xff18212c);
                    drawMiniIcon(c,l+22,t+21,s);
                    bold(c,s,l+58,t+29,15,Color.WHITE);
                    text(c,"Workspace 1",l+58,t+51,12,0xff8795a8);
                    round(c,l+w/2f-88,t+57,l+w/2f-42,t+80,10,0xff263447);
                    text(c,"Open",l+w/2f-77,t+73,11,Color.WHITE);
                    i++;
                }
            }
        }

        void drawApps(Canvas c,int w,int h) {
            overlay(c,w,h);
            bold(c,"Applications",28,92,25,Color.WHITE);
            text(c,"Your workspace",28,116,13,0xff9daabd);
            String[] names={"Files","Terminal","Browser","Settings","Media","Linux Apps"};
            int cols=3; float cardW=(w-84)/3f;
            for(int i=0;i<names.length;i++) {
                int col=i%cols,row=i/cols; float l=28+col*(cardW+14), t=138+row*102;
                round(c,l,t,l+cardW,t+86,18,0xff171f2a);
                drawAppIcon(c,l+28,t+18,names[i]);
                bold(c,names[i],l+62,t+33,14,Color.WHITE);
                text(c,appSubtitle(names[i]),l+62,t+55,11,0xff8d9aab);
            }
            round(c,28,h-135,w-28,h-87,16,0xff151d27);
            stroke(c,0x334d5d70,1); c.drawRoundRect(28,h-135,w-28,h-87,16,16,p);
            text(c,"⌕",47,h-104,23,0xffaeb9c8);
            text(c,"Search applications",77,h-106,14,0xff98a5b6);
        }

        String appSubtitle(String s) {
            if(s.equals("Files")) return "File manager";
            if(s.equals("Terminal")) return "Command line";
            if(s.equals("Browser")) return "Web browser";
            if(s.equals("Settings")) return "System controls";
            if(s.equals("Media")) return "Media player";
            return "Linux integration";
        }

        void drawAppIcon(Canvas c,float x,float y,String s) {
            round(c,x,y,x+34,y+34,10,0xff26364b);
            if(s.equals("Files")) { round(c,x+8,y+10,x+26,y+25,3,0xffdce6f3); }
            else if(s.equals("Terminal")) { stroke(c,0xffdce6f3,1.8f); c.drawRect(x+7,y+8,x+27,y+26,p); c.drawLine(x+11,y+14,x+16,y+18,p); }
            else if(s.equals("Browser")) { stroke(c,0xffdce6f3,1.8f); c.drawCircle(x+17,y+17,10,p); c.drawLine(x+7,y+17,x+27,y+17,p); }
            else if(s.equals("Settings")) { stroke(c,0xffdce6f3,2); c.drawCircle(x+17,y+17,7,p); c.drawCircle(x+17,y+17,2,p); }
            else { stroke(c,0xffdce6f3,1.8f); c.drawRect(x+8,y+9,x+26,y+25,p); }
        }

        void drawQuick(Canvas c,int w,int h) {
            overlay(c,w,h);
            float l=w-330;
            round(c,l,68,w-18,h-92,22,0xff151c26);
            bold(c,"Quick Settings",l+24,104,21,Color.WHITE);
            text(c,"System controls",l+24,126,12,0xff8996a8);
            String[] q={"Wi-Fi","Bluetooth","Sound","Rotation","Dark Mode","Lock"};
            for(int i=0;i<q.length;i++) {
                int col=i%2,row=i/2; float x=l+18+col*145,y=148+row*68;
                round(c,x,y,x+130,y+54,15,i<3?0xff29466c:0xff202a35);
                bold(c,q[i],x+14,y+23,12,Color.WHITE);
                text(c,i<3?"On":"Off",x+14,y+41,10,0xff9eacbe);
            }
        }

        void drawSettings(Canvas c,int w,int h) {
            overlay(c,w,h);
            bold(c,"Settings",30,91,26,Color.WHITE);
            text(c,"METMC OS NEXT",30,116,12,0xff8f9bad);
            String[] groups={"Appearance","Desktop","Applications","Linux integration","Security","System Update"};
            String[] desc={"Wallpaper, theme and visual style","Panel, dock and workspace behavior","Default apps and permissions","Debian and native Linux apps","Password, PIN and lock screen","Check for a newer METMC OS build"};
            for(int i=0;i<groups.length;i++) {
                float y=136+i*66;
                round(c,28,y,w-28,y+56,15,0xff151d27);
                drawAppIcon(c,44,y+11,groups[i].equals("System Update")?"Browser":"Settings");
                bold(c,groups[i],92,y+23,14,Color.WHITE);
                text(c,desc[i],92,y+42,11,0xff8996a8);
                text(c,"›",w-50,y+34,23,0xff8d9bad);
            }
        }

        void drawNotifications(Canvas c,int w,int h) {
            overlay(c,w,h);
            float l=w-360;
            round(c,l,68,w-18,h-92,22,0xff151c26);
            bold(c,"Notifications",l+24,105,21,Color.WHITE);
            text(c,"You're all caught up",l+24,135,13,0xff9aa7b8);
            round(c,l+20,158,w-38,218,14,0xff1c2632);
            bold(c,"METMC OS",l+38,184,13,Color.WHITE);
            text(c,"Desktop is ready.",l+38,204,11,0xff8f9cad);
        }

        void drawWindow(Canvas c,int w,int h,String title) {
            overlay(c,w,h);
            float l=42,t=92,rr=w-42,bb=h-102;
            round(c,l,t,rr,bb,20,0xff111821);
            stroke(c,0x553e5064,1); c.drawRoundRect(l,t,rr,bb,20,20,p);
            fill(c,0xff18222e); c.drawRoundRect(l,t,rr,t+54,20,20,p); c.drawRect(l,t+28,rr,t+54,p);
            bold(c,title,l+22,t+34,15,Color.WHITE);
            round(c,rr-112,t+15,rr-80,t+39,8,0xff263342);
            round(c,rr-72,t+15,rr-40,t+39,8,0xff263342);
            text(c,"—",rr-105,t+33,14,0xffc8d2df);
            text(c,"×",rr-65,t+33,15,0xffc8d2df);
            if(title.equals("Linux Apps")) {
                bold(c,"Linux Applications",l+28,t+92,21,Color.WHITE);
                text(c,"Native METMC window",l+28,t+119,12,0xff8f9cad);
                String[] apps={"File Manager","Terminal","Text Editor","Image Viewer","Media Player","PDF Viewer"};
                for(int i=0;i<apps.length;i++) {
                    float x=l+28+(i%3)*155, y=t+145+(i/3)*82;
                    round(c,x,y,x+135,y+64,15,0xff1b2632);
                    drawAppIcon(c,x+14,y+14,apps[i]);
                    text(c,apps[i],x+58,y+37,11,Color.WHITE);
                }
            } else {
                bold(c,title,l+28,t+96,21,Color.WHITE);
                text(c,"METMC native application window",l+28,t+122,12,0xff8f9cad);
                round(c,l+28,t+145,rr-28,bb-28,16,0xff18222d);
            }
        }

        void drawMiniIcon(Canvas c,float x,float y,String s) { drawAppIcon(c,x,y,s); }

        void overlay(Canvas c,int w,int h) {
            fill(c,0x9903080d); c.drawRect(0,54,w,h-82,p);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX(),y=e.getY(); int h=getHeight(),w=getWidth();
            if(e.getAction()==MotionEvent.ACTION_DOWN) { downX=x; downY=y; downTime=System.currentTimeMillis(); return true; }
            if(e.getAction()!=MotionEvent.ACTION_UP) return true;

            // top panel
            if(y<55 && x<180) { surface=surface==Surface.OVERVIEW?Surface.DESKTOP:Surface.OVERVIEW; activeWindow=null; invalidate(); return true; }
            if(y<55 && x>w-150) { surface=Surface.QUICK; activeWindow=null; invalidate(); return true; }

            // dock
            if(y>h-88) {
                float dockW=Math.min(560,w-34), left=(w-dockW)/2f;
                if(x>=left-8 && x<left+54) { surface=surface==Surface.APPS?Surface.DESKTOP:Surface.APPS; activeWindow=null; invalidate(); return true; }
                float pos=x-(left+62);
                if(pos>=0 && pos<288) {
                    int idx=(int)(pos/72);
                    if(idx>=0 && idx<4) {
                        String a=dockApps[idx];
                        if(a.equals("Files")) showWindow("Files");
                        else if(a.equals("Terminal")) launchTerminal();
                        else if(a.equals("Browser")) launchBrowser();
                        else surface=Surface.SETTINGS;
                        invalidate(); return true;
                    }
                }
                if(x>left+dockW-65) { surface=Surface.QUICK; activeWindow=null; invalidate(); return true; }
                if(x>left+dockW-12) { surface=Surface.NOTIFICATIONS; activeWindow=null; invalidate(); return true; }
            }

            if(surface==Surface.APPS) {
                String[] names={"Files","Terminal","Browser","Settings","Media","Linux Apps"};
                float cardW=(w-84)/3f;
                for(int i=0;i<names.length;i++) {
                    int col=i%3,row=i/3; float l=28+col*(cardW+14),t=138+row*102;
                    if(x>=l&&x<=l+cardW&&y>=t&&y<=t+86) {
                        String a=names[i];
                        if(a.equals("Files")) showWindow("Files");
                        else if(a.equals("Terminal")) launchTerminal();
                        else if(a.equals("Browser")) launchBrowser();
                        else if(a.equals("Settings")) surface=Surface.SETTINGS;
                        else if(a.equals("Linux Apps")) showWindow("Linux Apps");
                        else showWindow("Media");
                        invalidate(); return true;
                    }
                }
            }

            if(surface==Surface.OVERVIEW && !openWindows.isEmpty()) {
                int i=0;
                for(String s:openWindows) {
                    float l=28+(i%2)*(w/2f-18),t=140+(i/2)*118;
                    if(x>=l&&x<=l+w/2f-26&&y>=t&&y<=t+96) { activeWindow=s; surface=Surface.DESKTOP; invalidate(); return true; }
                    i++;
                }
            }

            if(surface==Surface.SETTINGS) {
                if(y>=136+5*66 && y<=136+6*66) new Updater(MainActivity.this).check();
            }

            if(activeWindow!=null && surface==Surface.DESKTOP) {
                if(y>=80 && x>w-105) { activeWindow=null; invalidate(); return true; }
            }
            return true;
        }

        void showWindow(String name) {
            if(!openWindows.contains(name)) openWindows.add(name);
            activeWindow=name; surface=Surface.DESKTOP; invalidate();
        }
    }
}
