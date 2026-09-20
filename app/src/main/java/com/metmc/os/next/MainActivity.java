package com.metmc.os.next;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {
    DesktopView desktop;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(8,10,13));
        getWindow().setNavigationBarColor(Color.rgb(8,10,13));
        desktop = new DesktopView(this);
        setContentView(desktop);
    }

    @Override public void onBackPressed() {
        if (desktop.panel != 0) { desktop.panel = 0; desktop.invalidate(); }
        else if (desktop.overview) { desktop.overview = false; desktop.invalidate(); }
        else desktop.showWindow("Desktop");
    }

    void launchBrowser() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))); }
        catch(Exception ignored) {}
    }

    void launchTerminal() {
        try {
            Intent i=new Intent(Intent.ACTION_MAIN);
            i.setPackage("com.termux");
            startActivity(i);
        } catch(Exception e) {
            new AlertDialog.Builder(this).setTitle("METMC Terminal")
                .setMessage("The native METMC terminal will be connected to the separate Debian environment in the next Linux integration layer.")
                .setPositiveButton("OK",null).show();
        }
    }

    class DesktopView extends View {
        Paint p=new Paint(3);
        int panel=0; boolean overview=false; String active="Desktop";
        ArrayList<String> windows=new ArrayList<>();
        RectF startRect=new RectF(), taskRect=new RectF(), quickRect=new RectF();

        DesktopView(Context c){super(c); p.setTypeface(Typeface.create("sans",0)); setFocusable(true);}
        void txt(Canvas c,String s,float x,float y,float size,int color){p.setTextSize(size);p.setColor(color);p.setTypeface(Typeface.create("sans",0));c.drawText(s,x,y,p);}
        void box(Canvas c,float l,float t,float r,float b,int color,float rad){p.setColor(color);c.drawRoundRect(l,t,r,b,rad,rad,p);}
        @Override protected void onDraw(Canvas c){
            int w=getWidth(),h=getHeight();
            c.drawColor(Color.rgb(10,13,17));
            // desktop background
            p.setShader(new LinearGradient(0,0,w,h,Color.rgb(17,25,38),Color.rgb(7,10,14),Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p); p.setShader(null);
            // top panel
            p.setColor(Color.argb(235,18,21,27)); c.drawRect(0,0,w,54,p);
            txt(c,"METMC",20,35,19,Color.WHITE);
            txt(c,"Activities",92,34,15,Color.LTGRAY);
            String clock=new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date());
            txt(c,clock,w-75,34,16,Color.WHITE);
            // workspace
            txt(c,"Workspace 1",24,92,14,0xffaeb8c8);
            if(!windows.isEmpty()){
                int y=125;
                for(String s:windows){ windowCard(c,s,25,y,w-25, y+86); y+=100; }
            } else {
                txt(c,"METMC OS NEXT",w/2-70,h/2-10,20,0xffdce6f5);
                txt(c,"Native desktop",w/2-55,h/2+18,13,0xff8e9aaa);
            }
            // taskbar
            int th=64; p.setColor(0xff12161d);c.drawRect(0,h-th,w,h,p);
            startRect.set(10,h-th+9,62,h-9); box(c,startRect.left,startRect.top,startRect.right,startRect.bottom,0xff273242,13);
            txt(c,"◉",26,h-28,23,Color.WHITE);
            String[] apps={"Files","Terminal","Browser","Settings"};
            float x=78; for(String a:apps){box(c,x,h-th+10,x+90,h-10,0xff1b222c,12);txt(c,a,x+15,h-27,13,Color.WHITE);x+=98;}
            txt(c,"▣",w-110,h-28,22,0xffb8c4d6);
            txt(c,"⚙",w-72,h-28,21,0xffb8c4d6);
            txt(c,"☰",w-35,h-28,21,0xffb8c4d6);
            if(overview) drawOverview(c,w,h);
            if(panel==1) drawLauncher(c,w,h);
            if(panel==2) drawQuick(c,w,h);
            if(panel==3) drawSettings(c,w,h);
            if(panel==4) drawNotifications(c,w,h);
        }
        void windowCard(Canvas c,String title,float l,float t,float r,float b){
            box(c,l,t,r,b,0xff1b222c,14); txt(c,title,l+18,t+30,16,Color.WHITE);
            txt(c,"Native METMC window",l+18,t+54,12,0xff8f9bad);
            box(c,r-95,t+20,r-20,t+58,0xff283342,10);txt(c,"Open",r-75,t+45,12,Color.WHITE);
        }
        void drawOverview(Canvas c,int w,int h){
            p.setColor(0xaa000000);c.drawRect(0,54,w,h-64,p);
            txt(c,"Overview",28,92,25,Color.WHITE);
            txt(c,"Windows and workspaces",28,117,13,0xff9aa6b7);
        }
        void drawLauncher(Canvas c,int w,int h){
            p.setColor(0xf0181c23);c.drawRect(0,54,w,h-64,p);
            txt(c,"Applications",28,94,27,Color.WHITE);
            String[] a={"Files","Terminal","Browser","Media","Settings","Linux Apps"};
            int i=0; for(String s:a){int col=i%3,row=i/3;float l=28+col*115,t=125+row*88;box(c,l,t,l+100,t+70,0xff242c38,14);txt(c,s,l+12,t+42,13,Color.WHITE);i++;}
            box(c,28,h-115,w-28,h-78,0xff252d39,12);txt(c,"Search applications…",45,h-91,14,0xffaeb8c8);
        }
        void drawQuick(Canvas c,int w,int h){
            p.setColor(0xf0181c23);c.drawRect(w-300,54,w,h-64,p);txt(c,"Quick Settings",w-275,95,23,Color.WHITE);
            String[] q={"Wi-Fi","Bluetooth","Sound","Rotation","Dark Mode","Lock"};
            int y=130;for(String s:q){box(c,w-280,y,w-35,y+48,0xff252e3a,12);txt(c,s,w-262,y+30,13,Color.WHITE);y+=58;}
        }
        void drawSettings(Canvas c,int w,int h){
            p.setColor(0xf0161a21);c.drawRect(70,70,w-70,h-85,p);
            txt(c,"Settings",98,112,27,Color.WHITE);
            String[] s={"Appearance","Wallpaper","Themes","Password / PIN / Pattern","Display","Applications","Linux integration","System Update"};
            int y=150;for(String a:s){box(c,95,y,w-100,y+42,0xff232b36,10);txt(c,a,112,y+27,14,Color.WHITE);y+=50;}
        }
        void drawNotifications(Canvas c,int w,int h){
            p.setColor(0xf0181c23);c.drawRect(40,70,w-40,h-85,p);txt(c,"Notifications",68,112,26,Color.WHITE);
            txt(c,"No new notifications",68,155,14,0xffaeb8c8);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            float x=e.getX(),y=e.getY();int h=getHeight(),w=getWidth();
            if(y>h-70 && x<70){panel=panel==1?0:1;invalidate();return true;}
            if(y>h-70 && x>w-100){panel=panel==2?0:2;invalidate();return true;}
            if(y<60 && x<180){overview=!overview;panel=0;invalidate();return true;}
            if(panel==1 && y>120 && y<400){
                int col=(int)((x-28)/115),row=(int)((y-125)/88),idx=row*3+col;
                if(idx==0)showWindow("Files"); else if(idx==1)launchTerminal(); else if(idx==2)launchBrowser(); else if(idx==4)panel=3;
                else if(idx==5)showWindow("Linux Apps");
                invalidate();return true;
            }
            if(panel==3){ if(y>485 && y<540){ new Updater(MainActivity.this).check(); } }
            return true;
        }
        void showWindow(String s){ if(!windows.contains(s))windows.add(s); active=s; panel=0; overview=false;invalidate(); }
    }
}
