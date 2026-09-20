package com.metmc.os.next;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.*;
import java.net.*;
import org.json.*;

public class Updater {
    final MainActivity a;
    Updater(MainActivity a){this.a=a;}
    void check(){
        new Thread(()->{
            try{
                HttpURLConnection c=(HttpURLConnection)new URL("https://api.github.com/repos/TEMMC/METMC_OS_NEXT/releases/latest").openConnection();
                c.setRequestProperty("Accept","application/vnd.github+json");
                c.setConnectTimeout(10000); c.setReadTimeout(10000);
                if(c.getResponseCode()!=200) throw new IOException("No published release yet");
                JSONObject o=new JSONObject(read(c.getInputStream()));
                JSONArray assets=o.optJSONArray("assets");
                String url=null,name=null;
                if(assets!=null) for(int i=0;i<assets.length();i++){
                    JSONObject x=assets.getJSONObject(i); String n=x.optString("name");
                    if(n.endsWith(".apk")){name=n;url=x.optString("browser_download_url");break;}
                }
                final String u=url,n=name,tag=o.optString("tag_name");
                a.runOnUiThread(()->{
                    if(u==null){msg("Updater","Release "+tag+" has no APK asset.");return;}
                    new AlertDialog.Builder(a).setTitle("METMC OS NEXT Update")
                        .setMessage("Release "+tag+" is available. Download and install it?")
                        .setPositiveButton("Update",(d,w)->download(u,n))
                        .setNegativeButton("Later",null).show();
                });
            }catch(Exception e){a.runOnUiThread(()->msg("Updater",e.getMessage()==null?"No update information available.":e.getMessage()));}
        }).start();
    }
    void download(String url,String name){
        new Thread(()->{
            try{
                File f=new File(a.getExternalFilesDir(null),name==null?"metmc-next-update.apk":name);
                HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(15000); c.setReadTimeout(60000);
                try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(f)){
                    byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                }
                Uri uri=FileProvider.getUriForFile(a,"com.metmc.os.next.fileprovider",f);
                Intent i=new Intent(Intent.ACTION_VIEW,uri);
                i.setDataAndType(uri,"application/vnd.android.package-archive");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);
                a.runOnUiThread(()->{
                    try{a.startActivity(i);}
                    catch(Exception e){msg("Install update","Enable 'Install unknown apps' for METMC OS NEXT, then run the update again.");}
                });
            }catch(Exception e){a.runOnUiThread(()->msg("Update failed",e.toString()));}
        }).start();
    }
    void msg(String t,String m){new AlertDialog.Builder(a).setTitle(t).setMessage(m).setPositiveButton("OK",null).show();}
    String read(InputStream in)throws Exception{
        BufferedReader r=new BufferedReader(new InputStreamReader(in));StringBuilder s=new StringBuilder();String l;
        while((l=r.readLine())!=null)s.append(l);return s.toString();
    }
}
