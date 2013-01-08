package org.g_okuyama.capture.pro;

import java.io.File;
import java.util.List;
import java.util.Locale;

import com.example.android.actionbarcompat.ActionBarActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore.Images.Media;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class ContShooting extends ActionBarActivity {
    private static final String TAG = "ContShooting";
    public static final String URL_JP = "http://www.yahoo.co.jp";
    public static final String URL_OTHER = "http://www.yahoo.com";
    
    static final int MENU_DISP_GALLERY = 1;
    static final int MENU_DISP_SETTING = 2;

    static final int REQUEST_CODE = 1;
    static final int RESPONSE_COLOR_EFFECT = 1;
    static final int RESPONSE_SCENE_MODE = 2;
    static final int RESPONSE_WHITE_BALANCE = 3;
    static final int RESPONSE_PICTURE_SIZE = 4;
    static final int RESPONSE_SHOOT_NUM = 5;
    static final int RESPONSE_INTERVAL = 6;
    static final int RESPONSE_HIDDEN_SIZE = 7;

    SurfaceHolder mHolder;
    private int mCount = 0;
    private TextView mText;
    private CameraPreview mPreview = null;
    //撮影中か否か（0:停止中、1：撮影中）
    public int mMode = 0;
    private boolean mMaskFlag = false;
    private boolean mSleepFlag = false;
    
    private ImageButton mButton = null;
    private ImageButton mMaskButton = null;
    private ImageButton mFocusButton = null;
    private SeekBar mSeekBar = null;
    //private String mNum = null;
    private ContentResolver mResolver;
    
    private WebView mWebView = null;
    //全体の画面サイズ
    int mWidth = 0;
    int mHeight = 0;
    //プレビュー枠のサイズ
    int mPrevWidth = 0;
    int mPrevHeight = 0;
    
    int mHiddenSizeIdx = 0;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	//Log.d(TAG, "enter ContShooting#onCreate");
        super.onCreate(savedInstanceState);
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.main);
        setTitle("");
        
        //mNum = getString(R.string.sc_number);
        mResolver = getContentResolver();
        
        //設定値の取得
        String effect = ContShootingPreference.getCurrentEffect(this);
        String scene = ContShootingPreference.getCurrentSceneMode(this);
        String white = ContShootingPreference.getCurrentWhiteBalance(this);
        String size = ContShootingPreference.getCurrentPictureSize(this);
        mHiddenSizeIdx = Integer.parseInt(ContShootingPreference.getCurrentHiddenSize(this));
        
        //Log.d(TAG, "picsize = " + size);
        
        WindowManager wm = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        Display disp = wm.getDefaultDisplay();
        mWidth = disp.getWidth();
        mHeight = disp.getHeight();
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        SurfaceView sv = (SurfaceView)findViewById(R.id.camera);
        mHolder = sv.getHolder();

        mPreview = new CameraPreview(this);
        mPreview.setField(effect, scene, white, size, mWidth, mHeight);
        mHolder.addCallback(mPreview);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mText = (TextView)findViewById(R.id.text1);
    	mText.setText(/*mNum + System.getProperty("line.separator") + */"0");

    	//連写枚数設定
        String num = ContShootingPreference.getCurrentShootNum(this);
        if(!num.equals("0")){
            mPreview.setShootNum(Integer.valueOf(num));
        }
        
        //連写間隔設定
        String interval = ContShootingPreference.getCurrentInterval(this);
        if(!interval.equals("0")){
            mPreview.setInterval(Integer.valueOf(interval));
        }

        //register UI Listener
    	setListener();        
    }
    
    private void setListener(){
        mButton = (ImageButton)findViewById(R.id.imgbtn);
        mButton.setOnClickListener(new OnClickListener(){
			public void onClick(View v) {
				if(mPreview != null){
					if(mMode == 0){
						mPreview.resumeShooting();
						mMode = 1;
                        //フォーカスボタン、マスクボタン、ズームボタンを見えなくする
                        mFocusButton.setVisibility(View.INVISIBLE);
                        mMaskButton.setVisibility(View.INVISIBLE);
                        if(mPreview.isZoomSupported()){
                        	FrameLayout zoom = (FrameLayout)findViewById(R.id.zoom_layout);
                        	zoom.setVisibility(View.INVISIBLE);
                        }
					}
					else{
						mPreview.stopShooting();
						mMode = 0;
                        //フォーカスボタン、マスクボタン、ズームボタンを見えるようにする
                        mFocusButton.setVisibility(View.VISIBLE);
                        mMaskButton.setVisibility(View.VISIBLE);
                        if(mPreview.isZoomSupported()){
                        	FrameLayout zoom = (FrameLayout)findViewById(R.id.zoom_layout);
                        	zoom.setVisibility(View.VISIBLE);
                        }
					}
				}
			}
        });
        
        mMaskButton = (ImageButton)findViewById(R.id.mask_imgbtn);
        mMaskButton.setOnClickListener(new OnClickListener(){
			public void onClick(View v) {
				if(mPreview != null){
                    if(mMaskFlag){
                        setToNormal();
                    }
                    else{
                        setToHidden();
                    }
				}
			}
        });
        
        mFocusButton = (ImageButton)findViewById(R.id.focusbtn);
        mFocusButton.setOnClickListener(new OnClickListener(){
			public void onClick(View v) {
				if(mPreview != null){
						mPreview.doAutoFocus();
				}
			}
        });
        
        if(ContShootingPreference.isHidden(this)){
            setToHidden();
        }
        
        //seekbar
        mSeekBar = (SeekBar)findViewById(R.id.zoom_seek);
        mSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener(){

            public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
                //Log.d(TAG, "progress = " + progress);
				if(mPreview != null){
	                mPreview.setZoom(progress);
				}
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
            }
            
        });
    }
    
    public void setToNormal(){
    	if(mPreview != null){
    		mPreview.stopPreview();
    	}

        FrameLayout layout = (FrameLayout)findViewById(R.id.linear);
        layout.removeView(mWebView);
        layout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.FILL_PARENT, 
                FrameLayout.LayoutParams.WRAP_CONTENT));
        mWebView.setWebViewClient(null);
        mWebView.destroy();
        mWebView = null;

        FrameLayout frame = (FrameLayout)findViewById(R.id.camera_parent);
        frame.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.FILL_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        
        displayNormalMode();
        mMaskFlag = false;
        //setTitle(R.string.app_name);
        setTitle("");

        if(mPreview != null){
    		mPreview.startPreview();
    	}
    }
    
    public void setToHidden(){
    	if(mPreview != null){
    		mPreview.stopPreview();
    	}

        mWebView = new WebView(ContShooting.this);
        mWebView.setWebViewClient(new WebViewClient());
        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setBuiltInZoomControls(true);
        mWebView.getSettings().setAppCacheEnabled(false);
        FrameLayout layout = (FrameLayout)findViewById(R.id.linear);
        layout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.FILL_PARENT, 
                FrameLayout.LayoutParams.WRAP_CONTENT, 
                1));
        layout.addView(mWebView);
        
        if(Locale.getDefault().equals(Locale.JAPAN)){
            mWebView.loadUrl(URL_JP);
        }
        else{
            mWebView.loadUrl(URL_OTHER);
        }

    	FrameLayout frame = (FrameLayout)findViewById(R.id.camera_parent);
    	/*
        int hide_width = mWidth / 4;
        int hide_height = hide_width * (4/3);
        */
        /*
         * 隠しモードのプレビューサイズ設定
         * 大=1/4, 小=1/6, 無し=1*1
         */
        int denom = 4;
        if(mHiddenSizeIdx == 2){
            denom = 6;
        }
        int hide_width = mWidth / denom;
        int hide_height = hide_width / 3 * 4;

        if(mHiddenSizeIdx == 3){
            hide_height = 1;
            hide_width = 1;            
        }
        frame.setLayoutParams(new FrameLayout.LayoutParams(hide_width, hide_height, Gravity.BOTTOM));

    	displayHideMode();
        mMaskFlag = true;
        setTitle(R.string.sc_hidden);

        if(mPreview != null){
    		mPreview.startPreview();
    	}
    }
    
    public void onStart(){
    	//Log.d(TAG, "enter ContShooting#onStart");
    	
        super.onStart();
        if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            new AlertDialog.Builder(this)
            .setTitle(R.string.sc_alert_title)
            .setMessage(getString(R.string.sc_alert_sd))
            .setPositiveButton(R.string.sc_alert_ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    System.exit(RESULT_OK);
                }
            })
            .show();
        }
    }
    
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main, menu);
        
        super.onCreateOptionsMenu(menu);

        /*
        //オプションメニュー(ギャラリー)
        MenuItem prefGallery = menu.add(0, MENU_DISP_GALLERY, 0, R.string.sc_menu_gallery);
        prefGallery.setIcon(android.R.drawable.ic_menu_gallery);

        //オプションメニュー(設定)
        MenuItem prefSetting = menu.add(0, MENU_DISP_SETTING, 0, R.string.sc_menu_setting);
        prefSetting.setIcon(android.R.drawable.ic_menu_preferences);
        */

        return true;
    }
    
    //オプションメニュー選択時のリスナ
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_gallery:        	
        	startGallery();
            break;
            
        case R.id.menu_setting:
            displaySettings();
        	break;
            
        default:
            //何もしない
        }

        return true;
    }
    
    private void startGallery(){
    	//ギャラリーへのintent
    	//Intent intent = new Intent(Intent.ACTION_PICK);
    	//intent.setType("image/*");
    	//startActivityForResult(intent, REQUEST_PICK_CONTACT);
    	//startActivity(intent);
    	
    	// ギャラリー表示
    	Intent intent = null;
    	try{
    	    // for Honeycomb
    	    intent = new Intent();
    	    intent.setClassName("com.android.gallery3d", "com.android.gallery3d.app.Gallery");
    	    startActivity(intent);
    	    return;
    	}
    	catch(Exception e){
    	    try{
    	        // for Recent device
    	        intent = new Intent();
    	        intent.setClassName("com.cooliris.media", "com.cooliris.media.Gallery");
    	        startActivity(intent);
    	    }
    	    catch(ActivityNotFoundException e1){
    	        try
    	        {
    	            // for Other device except HTC
    	            intent = new Intent(Intent.ACTION_VIEW);
    	            intent.setData(Uri.parse("content://media/external/images/media"));
    	            startActivity(intent);
    	        }
    	        catch (ActivityNotFoundException e2){
    	        	try{
    	        		// for HTC
    	        		intent = new Intent();
    	        		intent.setClassName("com.htc.album", "com.htc.album.AlbumTabSwitchActivity");
    	        		startActivity(intent);
    	        	}
    	        	catch(ActivityNotFoundException e3){
        	        	try{
        	        		// for HTC
        	        		intent = new Intent();
        	        		intent.setClassName("com.htc.album", "com.htc.album.AlbumMain.ActivityMainDropList");
        	        		startActivity(intent);
        	        	}
        	        	catch(ActivityNotFoundException e4){
        	            	Toast.makeText(this, R.string.sc_menu_gallery_ng, Toast.LENGTH_SHORT).show();
        	        	}
    	        	}
    	        }
    	    }
    	}
    }
    
    private void displaySettings(){
        Intent pref_intent = new Intent(this, ContShootingPreference.class);

        //色合い設定のリストを作成する
        List<String> effectList = null;
        if(mPreview != null){
            effectList = mPreview.getEffectList();
        }
        if(effectList != null){
        	//Log.d(TAG, "effect = " + (String[])effectList.toArray(new String[0]));
            pref_intent.putExtra("effect", (String[])effectList.toArray(new String[0]));
        }

        //シーン
        List<String> sceneList = null;
        if(mPreview != null){
            sceneList = mPreview.getSceneModeList();
        }
        if(sceneList != null){
            //Log.d(TAG, "scene = " + (String[])sceneList.toArray(new String[0]));
            pref_intent.putExtra("scene", (String[])sceneList.toArray(new String[0]));
        }

        //ホワイトバランス
        List<String> whiteList = null;
        if(mPreview != null){
            whiteList = mPreview.getWhiteBalanceList();
        }
        if(whiteList != null){
            //Log.d(TAG, "white = " + (String[])whiteList.toArray(new String[0]));
            pref_intent.putExtra("white", (String[])whiteList.toArray(new String[0]));
        }
        
        //画像サイズ
        List<String> sizeList = null;
        if(mPreview != null){
            sizeList = mPreview.getSizeList();
        }
        if(sizeList != null){
            //Log.d(TAG, "size = " + (String[])sizeList.toArray(new String[0]));
            pref_intent.putExtra("size", (String[])sizeList.toArray(new String[0]));
        }

        int offset = 0;
        if(mPreview != null){
            offset = mPreview.getPreviewOffset();
        }
        pref_intent.putExtra("offset", offset);
        
        startActivityForResult(pref_intent, REQUEST_CODE);
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        if(data == null){
            return;
        }
        
        if(requestCode == REQUEST_CODE){
            if(resultCode == RESPONSE_COLOR_EFFECT){
                if(mPreview != null){
                    mPreview.setColorValue(data.getStringExtra("effect"));
                }            	
            }
            if(resultCode == RESPONSE_SCENE_MODE){
            	if(mPreview != null){
                    mPreview.setSceneValue(data.getStringExtra("scene"));
                }            	            	
            }
            if(resultCode == RESPONSE_WHITE_BALANCE){
            	if(mPreview != null){
                    mPreview.setWhiteValue(data.getStringExtra("white"));
                }            	
            }
            if(resultCode == RESPONSE_PICTURE_SIZE){
                if(mPreview != null){
                    mPreview.setSizeValue(data.getIntExtra("size", 0));
                }
            }
            if(resultCode == RESPONSE_SHOOT_NUM){
                if(mPreview != null){
                    mPreview.setShootNum(data.getIntExtra("shoot", 0));
                }
            }
            if(resultCode == RESPONSE_INTERVAL){
                if(mPreview != null){
                    mPreview.setInterval(data.getIntExtra("interval", 0));
                }
            }
            if(resultCode == RESPONSE_HIDDEN_SIZE){
                //隠しモードサイズ設定
                mHiddenSizeIdx = data.getIntExtra("hidden_size", 0);
                //隠しモードの場合は、一旦戻す
                if(mMaskFlag){
                    setToNormal();
                }
            }
        }
    }
    
    public void count(){
    	mText.setText(/*mNum + System.getProperty("line.separator") + */Integer.toString(++mCount));
    }
    
    public void displayStart(){
    	mButton.setImageResource(R.drawable.start);
    }
    
    public void displayStop(){
        mButton.setImageResource(R.drawable.stop);
    }
    
    void displayHideMode(){
        mMaskButton.setImageResource(R.drawable.scale_up);
    }
    
    void displayNormalMode(){
        mMaskButton.setImageResource(R.drawable.scale_down);
    }

    void invisibleZoom(){
        FrameLayout zoom = (FrameLayout)findViewById(R.id.zoom_layout);
        zoom.setVisibility(View.INVISIBLE);
    }
    
    public void saveGallery(ContentValues values){
		mResolver.insert(Media.EXTERNAL_CONTENT_URI, values);
    }
    
    public void setMode(int mode){
        mMode = mode;
    }
    
    public boolean isMask(){
        return mMaskFlag;
    }
    
    protected void onPause(){
        //Log.d(TAG, "enter ContShooting#onPause");    	
    	super.onPause();
    	
        if(mSleepFlag){
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mSleepFlag = false;
        }
    }
    
    protected void onResume(){
        super.onResume();
        
        if(ContShootingPreference.isSleepMode(this)){
            if(!mSleepFlag){
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                mSleepFlag = true;                
            }
        }
        else{
            if(mSleepFlag){
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                mSleepFlag = false;                
            }
        }
    }
    
    protected void onDestroy(){
        //Log.d(TAG, "enter ContShooting#onDestroy");
    	super.onDestroy();
    	if(mPreview != null){
    	    mPreview.release();
    	}
    }
    
    protected void onRestart(){
    	//Log.d(TAG, "enter ContShooting#onRestart");
    	super.onRestart();
    }
    
    public void finish(){
        //アプリのキャッシュ削除
        deleteCache(getCacheDir());
        
		System.exit(RESULT_OK);
    }
    
    public static boolean deleteCache(File dir) {
        if(dir==null) {
            return false;
        }
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteCache(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }
}