from pathlib import Path

main = Path('app/src/main/java/com/metmc/os/next/MainActivity.java')
s = main.read_text()
s = s.replace(
    '''        PackageManager pm = getPackageManager();
        LinkedHashMap<String,ResolveInfo> found = new LinkedHashMap<>();
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);''',
    '''        PackageManager pm = getPackageManager();
        LinkedHashMap<String,ResolveInfo> found = new LinkedHashMap<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);''',
    1,
)
s = s.replace(
    'pm.queryIntentActivities(launcher, PackageManager.MATCH_DEFAULT_ONLY)',
    'pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)',
)

old = '''                        if(y>hit.t+54 && y<hit.b-12 && windowScrollMax(hit.title,hit)>0){
                            scrollingWindowTitle=hit.title;
                        }'''
new = '''                        if(y>hit.t+54 && y<hit.b-12 && windowScrollMax(hit.title,hit)>0){
                            scrollingWindowTitle=hit.title;
                        }
                        if(("Applications".equals(hit.title) || "Files".equals(hit.title))
                                && y>hit.t+54 && y<hit.b-12 && windowScrollMax(hit.title,hit)>0){
                            scrollingWindowTitle=hit.title;
                        }'''
if old in s:
    s = s.replace(old, new, 1)

marker = '        WindowState windowAt(float x,float y){'
method = '''        @Override public boolean onGenericMotionEvent(MotionEvent event){
            try{
                if(event.getAction()==MotionEvent.ACTION_SCROLL){
                    float x=event.getX(), y=event.getY();
                    WindowState hit=windowAt(x,y);
                    if(hit!=null && windowScrollMax(hit.title,hit)>0){
                        float delta=event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                        if(Math.abs(delta)>0.01f){
                            if("Applications".equals(hit.title)){
                                applicationsWindowScroll=Math.max(0,Math.min(windowScrollMax(hit.title,hit),applicationsWindowScroll-delta*72f));
                            }else{
                                windowScroll=Math.max(0,Math.min(windowScrollMax(hit.title,hit),windowScroll-delta*72f));
                            }
                            bringToFront(hit.title);
                            invalidate();
                            return true;
                        }
                    }
                }
            }catch(Throwable ex){ Log.w("METMC","Generic scroll failed",ex); }
            return super.onGenericMotionEvent(event);
        }

'''
if marker in s and 'onGenericMotionEvent(MotionEvent event)' not in s:
    s = s.replace(marker, method + marker, 1)
main.write_text(s)

panel = Path('app/src/main/java/com/metmc/os/next/MetmcTerminalPanel.java')
p = panel.read_text()
p = p.replace(
    '"for C in /debug_ramdisk/su /sbin/su /system/bin/su /system/xbin/su; do " +',
    '"for C in /debug_ramdisk/su /sbin/su /system/bin/su /system/xbin/su /system/sbin/su; do " +',
    1,
)
p = p.replace(
    '"if [ -z \\"$SU\\" ] && command -v su >/dev/null 2>&1; then SU=\\"$(command -v su)\\"; fi; " +\n                    "if [ -z \\"$SU\\" ]; then echo \'[METMC] Magisk su was not exposed to this process.\'; exit 1; fi; " +',
    '"if [ -z \\"$SU\\" ] && [ -x /system/bin/magisk ]; then MT=\\"$(/system/bin/magisk --path 2>/dev/null)\\"; [ -x \\"$MT/su\\" ] && SU=\\"$MT/su\\"; fi; " +\n                    "if [ -z \\"$SU\\" ] && command -v su >/dev/null 2>&1; then SU=\\"$(command -v su)\\"; fi; " +\n                    "if [ -z \\"$SU\\" ]; then echo \'[METMC] Magisk su is not exposed to METMC OS NEXT. Grant root access to METMC OS NEXT in Magisk, then reopen Terminal.\'; exit 1; fi; " +',
    1,
)
p = p.replace(
    '''        terminalView.setFocusable(true);
        terminalView.setFocusableInTouchMode(true);
    }''',
    '''        terminalView.setFocusable(true);
        terminalView.setFocusableInTouchMode(true);
        terminalView.setEnabled(true);
        terminalView.setClickable(true);
        terminalView.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN || e.getAction()==MotionEvent.ACTION_UP){
                v.setFocusableInTouchMode(true);
                v.requestFocusFromTouch();
                v.requestFocus();
            }
            return false;
        });
    }''',
    1,
)
panel.write_text(p)

gradle = Path('app/build.gradle')
g = gradle.read_text()
start = g.find('// Keep the current MainActivity source compatible')
end = g.find('dependencies {', start)
if start >= 0 and end > start:
    g = g[:start] + g[end:]
gradle.write_text(g)

workflow = Path('.github/workflows/build.yml')
w = workflow.read_text()
a = w.find('      # BEGIN ONE-TIME SOURCE REPAIR')
b = w.find('      # END ONE-TIME SOURCE REPAIR')
if a >= 0 and b >= a:
    b = w.find('\n', b)
    w = w[:a] + w[b+1:]
workflow.write_text(w)

Path('tools/repair_source.py').unlink()
