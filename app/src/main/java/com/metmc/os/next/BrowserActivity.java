package com.metmc.os.next;

import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;

public class BrowserActivity extends Activity {
    EditText address;
    WebView web;
    TextView title;
    TextView security;
    ProgressBar progress;
    static final int REQUEST_BROWSER_ROLE=7011;
    static final int BG=0xff071018;
    static final int BAR=0xff101b25;
    static final int FIELD=0xff182633;
    static final int TEXT=0xffedf4fb;
    static final int MUTED=0xff91a4b6;
    static final int ACCENT=0xff4fd1ff;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout titleBar=new LinearLayout(this);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(14,4,10,4);
        titleBar.setBackgroundColor(BAR);

        TextView brand=label("METMC Browser",14,TEXT);
        brand.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        titleBar.addView(brand,new LinearLayout.LayoutParams(0,38,1));

        title=label("New Tab",11,MUTED);
        title.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        titleBar.addView(title,new LinearLayout.LayoutParams(220,38));
        root.addView(titleBar);

        LinearLayout toolbar=new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(8,7,8,7);
        toolbar.setBackgroundColor(BAR);

        Button back=navButton("‹");
        Button forward=navButton("›");
        Button reload=navButton("↻");
        back.setOnClickListener(v->{if(web.canGoBack())web.goBack();});
        forward.setOnClickListener(v->{if(web.canGoForward())web.goForward();});
        reload.setOnClickListener(v->{if(web.getProgress()<100)web.stopLoading();else web.reload();});
        toolbar.addView(back,new LinearLayout.LayoutParams(44,44));
        toolbar.addView(forward,new LinearLayout.LayoutParams(44,44));
        toolbar.addView(reload,new LinearLayout.LayoutParams(44,44));

        LinearLayout addressBox=new LinearLayout(this);
        addressBox.setGravity(Gravity.CENTER_VERTICAL);
        addressBox.setPadding(10,0,8,0);
        GradientDrawable addressBg=new GradientDrawable();
        addressBg.setColor(FIELD);
        addressBg.setCornerRadius(24);
        addressBox.setBackground(addressBg);

        security=label("●",11,0xff6fdc9a);
        security.setGravity(Gravity.CENTER);
        addressBox.addView(security,new LinearLayout.LayoutParams(28,44));

        address=new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(TEXT);
        address.setHintTextColor(MUTED);
        address.setHint("Search or enter address");
        address.setTextSize(13);
        address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        address.setBackgroundColor(Color.TRANSPARENT);
        address.setPadding(0,0,4,0);
        address.setSelectAllOnFocus(false);
        addressBox.addView(address,new LinearLayout.LayoutParams(0,44,1));
        toolbar.addView(addressBox,new LinearLayout.LayoutParams(0,44,1));

        Button home=navButton("⌂");
        Button menu=navButton("⋮");
        home.setOnClickListener(v->loadHome());
        menu.setOnClickListener(this::showMenu);
        toolbar.addView(home,new LinearLayout.LayoutParams(44,44));
        toolbar.addView(menu,new LinearLayout.LayoutParams(44,44));
        root.addView(toolbar);

        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
        root.addView(progress,new LinearLayout.LayoutParams(-1,2));

        web=new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        web.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap favicon){
                address.setText(url);
                updateSecurity(url);
                progress.setVisibility(View.VISIBLE);
                title.setText("Loading…");
            }

            @Override public void onPageFinished(WebView view,String url){
                address.setText(url);
                updateSecurity(url);
                progress.setVisibility(View.GONE);
                String t=view.getTitle();
                title.setText(t==null||t.trim().isEmpty()?"New Tab":t);
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                return false;
            }

            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
                if(request!=null && request.isForMainFrame()){
                    progress.setVisibility(View.GONE);
                    title.setText("Page unavailable");
                }
            }

            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse errorResponse){
                if(request!=null && request.isForMainFrame()){
                    title.setText("HTTP "+errorResponse.getStatusCode());
                }
            }

            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){
                handler.cancel();
                Toast.makeText(BrowserActivity.this,"Secure connection could not be verified.",Toast.LENGTH_SHORT).show();
            }
        });

        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView view,int newProgress){
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress>=100?View.GONE:View.VISIBLE);
            }

            @Override public void onReceivedTitle(WebView view,String pageTitle){
                if(pageTitle!=null && !pageTitle.trim().isEmpty())title.setText(pageTitle);
            }
        });

        web.setDownloadListener((url,userAgent,contentDisposition,mimeType,contentLength)->{
            try{
                DownloadManager.Request request=new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.addRequestHeader("User-Agent",userAgent);
                String cookies=CookieManager.getInstance().getCookie(url);
                if(cookies!=null)request.addRequestHeader("Cookie",cookies);
                request.setTitle(URLUtil.guessFileName(url,contentDisposition,mimeType));
                request.setDescription("METMC Browser download");
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url,contentDisposition,mimeType));
                DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
                if(dm!=null){dm.enqueue(request);Toast.makeText(this,"Download started",Toast.LENGTH_SHORT).show();}
            }catch(Exception e){Toast.makeText(this,"Download could not be started.",Toast.LENGTH_SHORT).show();}
        });

        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);

        Uri incoming=getIntent().getData();
        String url=incoming!=null?incoming.toString():"https://www.google.com";
        address.setText(url);
        web.loadUrl(url);
        address.setOnEditorActionListener((v,id,event)->{navigate();return true;});
    }

    TextView label(String value,float size,int color){
        TextView v=new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    Button navButton(String value){
        Button b=new Button(this);
        b.setText(value);
        b.setTextColor(TEXT);
        b.setTextSize(18);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinWidth(0);
        b.setPadding(0,0,0,0);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    void updateSecurity(String url){
        try{
            Uri u=Uri.parse(url);
            boolean secure="https".equalsIgnoreCase(u.getScheme());
            security.setText(secure?"●":"!");
            security.setTextColor(secure?0xff6fdc9a:0xffffc857);
        }catch(Exception e){
            security.setText("!");
            security.setTextColor(0xffffc857);
        }
    }

    void navigate(){
        String value=address.getText().toString().trim();
        if(value.isEmpty())return;
        if(!value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")){
            if(value.contains(" ")||!value.contains(".")){
                value="https://www.google.com/search?q="+Uri.encode(value);
            }else{
                value="https://"+value;
            }
        }
        address.setText(value);
        web.loadUrl(value);
        web.requestFocus();
    }

    void loadHome(){
        address.setText("https://www.google.com");
        web.loadUrl("https://www.google.com");
    }

    void showMenu(View anchor){
        PopupMenu popup=new PopupMenu(this,anchor);
        popup.getMenu().add("New tab");
        popup.getMenu().add("Reload");
        popup.getMenu().add("Share page");
        popup.getMenu().add("Set as default browser");
        popup.getMenu().add("Clear browser data");
        popup.setOnMenuItemClickListener(item->{
            String x=item.getTitle().toString();
            if("New tab".equals(x)){address.setText("");web.loadUrl("https://www.google.com");}
            else if("Reload".equals(x))web.reload();
            else if("Share page".equals(x)){
                Intent i=new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT,web.getUrl());
                startActivity(Intent.createChooser(i,"Share page"));
            }else if("Set as default browser".equals(x))requestDefaultBrowser();
            else if("Clear browser data".equals(x)){
                web.clearHistory();
                web.clearCache(true);
                CookieManager.getInstance().removeAllCookies(null);
                Toast.makeText(this,"Browser data cleared.",Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        popup.show();
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
        }catch(Exception e){
            Toast.makeText(this,"Browser role unavailable.",Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onBackPressed(){
        if(web!=null && web.canGoBack()){web.goBack();return;}
        super.onBackPressed();
    }

    @Override protected void onDestroy(){
        if(web!=null){
            web.stopLoading();
            web.setWebChromeClient(null);
            web.setWebViewClient(null);
            web.destroy();
        }
        super.onDestroy();
    }
}
