package com.metmc.os.next;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import java.io.*;
import java.net.*;
import org.json.*;

public class Updater {
    final MainActivity a;
    Updater(MainActivity a){this.a=a;}

    void check(){ check(true); }

    void check(boolean automatic){
        new Thread(()->{
            try{
                HttpURLConnection c=(HttpURLConnection)new URL("https://api.github.com/repos/TEMMC/METMC_OS_NEXT/releases/latest").openConnection();
                c.setRequestProperty("Accept","application/vnd.github+json");
                c.setConnectTimeout(10000); c.setReadTimeout(15000);
                if(c.getResponseCode()!=200) throw new IOException("No published release yet");
                JSONObject o=new JSONObject(read(c.getInputStream()));
                int remoteCode=parseVersionCode(o.optString("tag_name"));
                android.content.pm.PackageInfo pi=a.getPackageManager().getPackageInfo(a.getPackageName(),0);
                int localCode=pi.versionCode;
                if(remoteCode<=localCode){
                    if(!automatic) a.runOnUiThread(()->msg("METMC OS NEXT","You are already running the latest version."));
                    return;
                }
                JSONArray assets=o.optJSONArray("assets");
                String url=null,name=null;
                if(assets!=null) for(int i=0;i<assets.length();i++){
                    JSONObject x=assets.getJSONObject(i);
                    String n=x.optString("name");
                    if(n.endsWith(".apk") && !n.toLowerCase().contains("debug")){
                        name=n; url=x.optString("browser_download_url"); break;
                    }
                }
                final String u=url,n=name,tag=o.optString("tag_name");
                if(u==null){a.runOnUiThread(()->msg("Updater","Release "+tag+" has no production APK."));return;}
                if(automatic) download(u,n,true);
                else a.runOnUiThread(()->new AlertDialog.Builder(a)
                    .setTitle("METMC OS NEXT Update")
                    .setMessage("Version "+tag+" is available. Download and install it?")
                    .setPositiveButton("Update",(d,w)->download(u,n,true))
                    .setNegativeButton("Later",null).show());
            }catch(Exception e){
                if(!automatic) a.runOnUiThread(()->msg("Updater",e.getMessage()==null?"No update information available.":e.getMessage()));
            }
        }).start();
    }

    int parseVersionCode(String tag){
        try{
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\.build\\.(\\d+)$").matcher(tag);
            if(m.find()) return Integer.parseInt(m.group(1));
            String v=tag.startsWith("v")?tag.substring(1):tag;
            String[] p=v.split("\\.");
            int major=Integer.parseInt(p[0]);
            int minor=p.length>1?Integer.parseInt(p[1]):0;
            int patch=p.length>2?Integer.parseInt(p[2].replaceAll("[^0-9].*","")):0;
            return major*10000+minor*100+patch;
        }catch(Exception e){return 0;}
    }

    void download(String url,String name,boolean automatic){
        new Thread(()->{
            File f=new File(a.getExternalFilesDir(null),name==null?"metmc-next-update.apk":name);
            try{
                HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(120000);
                if(c.getResponseCode()!=200) throw new IOException("Download failed: HTTP "+c.getResponseCode());
                try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(f)){
                    byte[] b=new byte[32768]; int n;
                    while((n=in.read(b))!=-1) out.write(b,0,n);
                }
                if(f.length()<100000) throw new IOException("Downloaded APK is incomplete.");
                if(automatic && installAsRoot(f)) return;
                installWithPackageInstaller(f);
            }catch(Exception e){
                try{f.delete();}catch(Exception ignored){}
                a.runOnUiThread(()->msg("Update failed",e.getMessage()==null?e.toString():e.getMessage()));
            }
        }).start();
    }

    boolean installAsRoot(File f){
        try{
            Process p=new ProcessBuilder("su","-c","pm install -r --user 0 "+shellQuote(f.getAbsolutePath())).redirectErrorStream(true).start();
            String output=read(p.getInputStream());
            int code=p.waitFor();
            if(code==0 && output.toLowerCase().contains("success")){
                try{f.delete();}catch(Exception ignored){}
                a.runOnUiThread(()->{
                    new AlertDialog.Builder(a).setTitle("METMC OS NEXT Updated")
                        .setMessage("The update was installed successfully. METMC OS NEXT will restart.")
                        .setPositiveButton("Restart",(d,w)->restart())
                        .setOnDismissListener(d->restart()).show();
                });
                return true;
            }
        }catch(Exception ignored){}
        return false;
    }

    void installWithPackageInstaller(File f){
        try{
            if(Build.VERSION.SDK_INT < 21) throw new IOException("Android PackageInstaller is unavailable.");
            PackageInstaller installer=a.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(a.getPackageName());
            int sessionId=installer.createSession(params);
            PackageInstaller.Session session=installer.openSession(sessionId);
            try(InputStream in=new FileInputStream(f);
                OutputStream out=session.openWrite("METMC-OS-NEXT.apk",0,f.length())){
                byte[] b=new byte[32768]; int n;
                while((n=in.read(b))!=-1) out.write(b,0,n);
                session.fsync(out);
            }
            Intent result=new Intent(a,UpdateInstallReceiver.class);
            result.putExtra("metmc_apk_path",f.getAbsolutePath());
            int flags=PendingIntent.FLAG_UPDATE_CURRENT;
            if(Build.VERSION.SDK_INT>=23) flags|=PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi=PendingIntent.getBroadcast(a,sessionId,result,flags);
            session.commit(pi.getIntentSender());
            session.close();
        }catch(Exception e){
            try{f.delete();}catch(Exception ignored){}
            a.runOnUiThread(()->msg("Install update","Android requires permission to install METMC OS NEXT updates. Enable 'Install unknown apps' for METMC OS NEXT and retry."));
        }
    }

    void restart(){
        try{
            Intent i=a.getPackageManager().getLaunchIntentForPackage(a.getPackageName());
            if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);a.startActivity(i);}
        }catch(Exception ignored){}
        new Handler().postDelayed(()->System.exit(0),500);
    }

    String shellQuote(String s){return "'" + s.replace("'","'\\''") + "'";}

    void msg(String t,String m){new AlertDialog.Builder(a).setTitle(t).setMessage(m).setPositiveButton("OK",null).show();}

    String read(InputStream in)throws Exception{
        BufferedReader r=new BufferedReader(new InputStreamReader(in));
        StringBuilder s=new StringBuilder(); String l;
        while((l=r.readLine())!=null)s.append(l);
        return s.toString();
    }
}