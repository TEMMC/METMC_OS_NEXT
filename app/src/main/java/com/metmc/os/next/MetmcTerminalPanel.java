package com.metmc.os.next;

import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import java.nio.charset.StandardCharsets;
import android.view.inputmethod.InputMethodManager;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

public final class MetmcTerminalPanel extends FrameLayout {
    private static final String ROOTFS="/data/local/linux/rootfs";
    private final TerminalView terminalView;
    private TerminalSession session;
    private boolean ctrl,alt,shift,fn;

    public MetmcTerminalPanel(Context context){
        super(context);
        setBackgroundColor(Color.rgb(5,8,10));
        terminalView=new TerminalView(context,null);
        terminalView.setTextSize(14);
        terminalView.setTerminalViewClient(new ViewClient());
        addView(terminalView,new LayoutParams(-1,-1));
        setFocusable(true);
    }

    public void start(){
        if(session!=null && session.isRunning()) return;
        String command="export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; "+
                "export HOME=/root; export TERM=xterm-256color; export COLORTERM=truecolor; "+
                "export LANG=C.UTF-8; export LC_ALL=C.UTF-8; "+
                "exec chroot "+ROOTFS+" /bin/bash --login";
        String[] args={"-c",command};
        String[] env={
                "TERM=xterm-256color","COLORTERM=truecolor","HOME=/root","LANG=C.UTF-8",
                "LC_ALL=C.UTF-8","PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "SHELL=/bin/bash"
        };
        session=new TerminalSession("/system/bin/su",ROOTFS,args,env,5000,new SessionClient());
        terminalView.attachSession(session);
        terminalView.requestFocus();
        postDelayed(()->{
            InputMethodManager imm=(InputMethodManager)MetmcTerminalPanel.this.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm!=null) imm.showSoftInput(terminalView,InputMethodManager.SHOW_IMPLICIT);
        },300);
    }

    public void stop(){
        if(session!=null) session.finishIfRunning();
        session=null;
    }

    public void send(String text){
        if(session!=null && session.isRunning()) writeBytes(text);
    }

    public void sendCodePoint(int codePoint){
        if(session!=null && session.isRunning()) session.writeCodePoint(false,codePoint);
    }

    private void writeBytes(String text){
        if(session==null||!session.isRunning()||text==null)return;
        byte[] b=text.getBytes(StandardCharsets.UTF_8);
        session.write(b,0,b.length);
    }

    public void sendKey(String name){
        if(session==null || !session.isRunning()) return;
        String s=null;
        if("ESC".equals(name)) s="\u001b";
        else if("TAB".equals(name)) s="\t";
        else if("ENTER".equals(name)) s="\r";
        else if("UP".equals(name)) s="\u001b[A";
        else if("DOWN".equals(name)) s="\u001b[B";
        else if("LEFT".equals(name)) s="\u001b[D";
        else if("RIGHT".equals(name)) s="\u001b[C";
        else if("HOME".equals(name)) s="\u001b[H";
        else if("END".equals(name)) s="\u001b[F";
        else if("PGUP".equals(name)) s="\u001b[5~";
        else if("PGDN".equals(name)) s="\u001b[6~";
        if(s!=null) writeBytes(s);
    }

    public void copySelection(){
        terminalView.performLongClick();
    }

    public void pasteClipboard(){
        android.content.ClipboardManager cm=(android.content.ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if(cm!=null && cm.hasPrimaryClip() && session!=null && session.isRunning()){
            CharSequence text=cm.getPrimaryClip().getItemAt(0).coerceToText(MetmcTerminalPanel.this.getContext());
            if(text!=null) session.getEmulator().paste(text.toString());
        }
    }

    public void clear(){
        if(session!=null && session.isRunning()) writeBytes("\u000c");
    }

    private final class ViewClient implements TerminalViewClient {
        public float onScale(float scale){ return scale; }
        public void onSingleTapUp(MotionEvent e){ terminalView.requestFocus(); }
        public boolean shouldBackButtonBeMappedToEscape(){ return false; }
        public boolean shouldEnforceCharBasedInput(){ return true; }
        public boolean shouldUseCtrlSpaceWorkaround(){ return false; }
        public boolean isTerminalViewSelected(){ return terminalView.hasFocus(); }
        public void copyModeChanged(boolean copyMode){}
        public boolean onKeyDown(int keyCode,KeyEvent e,TerminalSession s){
            if(keyCode==KeyEvent.KEYCODE_BACK) return false;
            return false;
        }
        public boolean onKeyUp(int keyCode,KeyEvent e){ return false; }
        public boolean onLongPress(MotionEvent e){ return false; }
        public boolean readControlKey(){ return ctrl; }
        public boolean readAltKey(){ return alt; }
        public boolean readShiftKey(){ return shift; }
        public boolean readFnKey(){ return fn; }
        public boolean onCodePoint(int codePoint,boolean ctrlDown,TerminalSession s){
            if(ctrlDown) s.writeCodePoint(true,codePoint); else s.writeCodePoint(alt,codePoint);
            return true;
        }
        public void onEmulatorSet(){}
        public void logError(String t,String m){}
        public void logWarn(String t,String m){}
        public void logInfo(String t,String m){}
        public void logDebug(String t,String m){}
        public void logVerbose(String t,String m){}
        public void logStackTraceWithMessage(String t,String m,Exception e){}
        public void logStackTrace(String t,Exception e){}
    }

    private final class SessionClient implements TerminalSessionClient {
        public void onTextChanged(TerminalSession s){ MetmcTerminalPanel.this.postInvalidate(); }
        public void onTitleChanged(TerminalSession s){}
        public void onSessionFinished(TerminalSession s){}
        public void onCopyTextToClipboard(TerminalSession s,String text){
            android.content.ClipboardManager cm=(android.content.ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if(cm!=null) cm.setPrimaryClip(android.content.ClipData.newPlainText("METMC Terminal",text));
        }
        public void onPasteTextFromClipboard(TerminalSession s){ pasteClipboard(); }
        public void onBell(TerminalSession s){}
        public void onColorsChanged(TerminalSession s){}
        public void onTerminalCursorStateChange(boolean state){}
        public Integer getTerminalCursorStyle(){ return 0; }
        public void logError(String t,String m){}
        public void logWarn(String t,String m){}
        public void logInfo(String t,String m){}
        public void logDebug(String t,String m){}
        public void logVerbose(String t,String m){}
        public void logStackTraceWithMessage(String t,String m,Exception e){}
        public void logStackTrace(String t,Exception e){}
    }
}