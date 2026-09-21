package com.metmc.os.next;

import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public class BrowserActivity extends Activity {
    EditText address;
    WebView web;
    static final int REQUEST_BROWSER_ROLE=7011;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setStatusBarColor(Color.rgb(7,12,18));
        getWindow().setNavigationBarColor(Color.rgb(7,12,18));

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(7,11,16));

        LinearLayout toolbar=new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(8,6,8,6);
        toolbar.setBackgroundColor(Color.rgb(18,27,36));

        Button back=btn("‹"); back.setOnClickListener(v->{if(web.canGoBack())web.goBack();});
        Button forward=btn("›"); forward.setOnClickListener(v->{if(web.canGoForward())web.goForward();});
        Button reload=btn("↻"); reload.setOnClickListener(v->web.reload());
        toolbar.addView(back,new LinearLayout.LayoutParams(48,48));
        toolbar.addView(forward,new LinearLayout.LayoutParams(48,48));
        toolbar.addView(reload,new LinearLayout.LayoutParams(48,48));

        address=new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(0xff8291a4);
        address.setHint("Search or enter address");
        address.setTextSize(13);
        address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        address.setPadding(14,0,14,0);
        toolbar.addView(address,new LinearLayout.LayoutParams(0,48,1));

        Button go=btn("Go");go.setOnClickListener(v->navigate());
        Button role=btn("Default");role.setOnClickListener(v->requestDefaultBrowser());
        toolbar.addView(go,new LinearLayout.LayoutParams(58,48));
        toolbar.addView(role,new LinearLayout.LayoutParams(76,48));
        root.addView(toolbar);

        web=new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){address.setText(url);}
            @Override public void onPageFinished(WebView view,String url){address.setText(url);}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(request!=null && request.getUrl()!=null){view.loadUrl(request.getUrl().toString());return true;}
                return false;
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);

        Uri incoming=getIntent().getData();
        String url=incoming!=null?incoming.toString():"https://www.google.com";
        address.setText(url);
        web.loadUrl(url);
        address.setOnEditorActionListener((v,id,event)->{navigate();return true;});
    }

    Button btn(String s){
        Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setAllCaps(false);b.setMinHeight(0);b.setMinWidth(0);b.setPadding(0,0,0,0);return b;
    }

    void navigate(){
        String value=address.getText().toString().trim();
        if(value.isEmpty())return;
        if(!value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")){
            if(value.contains(" ")||!value.contains("."))value="https://www.google.com/search?q="+Uri.encode(value);
            else value="https://"+value;
        }
        address.setText(value);
        web.loadUrl(value);
    }

    void requestDefaultBrowser(){
        if(android.os.Build.VERSION.SDK_INT<29)return;
        try{
            RoleManager rm=getSystemService(RoleManager.class);
            if(rm!=null && rm.isRoleAvailable(RoleManager.ROLE_BROWSER)){
                if(!rm.isRoleHeld(RoleManager.ROLE_BROWSER)){
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER),REQUEST_BROWSER_ROLE);
                }else Toast.makeText(this,"METMC Browser is already the default.",Toast.LENGTH_SHORT).show();
            }
        }catch(Exception e){Toast.makeText(this,"Browser role unavailable.",Toast.LENGTH_SHORT).show();}
    }

    @Override public void onBackPressed(){
        if(web!=null && web.canGoBack()){web.goBack();return;}
        super.onBackPressed();
    }

    @Override protected void onDestroy(){
        if(web!=null)web.destroy();
        super.onDestroy();
    }
}
