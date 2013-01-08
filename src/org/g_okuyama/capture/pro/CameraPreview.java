package org.g_okuyama.capture.pro;

/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Size;

import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore.Images;
import android.util.Log;
import android.view.SurfaceHolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

class CameraPreview implements SurfaceHolder.Callback {
    public static final String TAG = "ContShooting";
    Camera mCamera = null;
    Context mContext = null;

    AutoFocusCallback mFocus = null;
    //private boolean mFocusFlag = false;

    private Size mSize = null;
    private List<Size> mSupportList = null;
    //サポートリストに対する端末の下限値のインデックス
    private int mOffset = 0;
    private File mFile = null;
    PreviewCallback mPreviewCallback = null;
    
    //for setting
	private boolean mSetColor = false;
	private boolean mSetScene = false;
	private boolean mSetWhite = false;
	private boolean mSetSize = false;
	private String mSetValue = null;
	private int mSetInt = 0;
	
	//初期設定
	private String mEffect = null;
	private String mScene = null;
	private String mWhite = null;
	private int mPicIdx = 0;
	private String mSizeStr = null;
	//連写数
	private int mMax = 0;
	//現在の撮影数
	private int mNum = 0;
	
	//ズームサポート
	boolean mZoom = false;
	//スムースズームサポート
	boolean mSmoothZoom = false;
	//最大ズーム
	private int mZoomMax = 0;
	//ズーム状態
	boolean mZoomStopped = true;
	private int mZoomIdx = 0;
	
	//連写間隔
	private int mInterval = 0;
    
    //画面サイズ
    int mWidth = 0;
    int mHeight = 0;
    
    CameraPreview(Context context){
        mContext = context;
	}
	
    public void setField(String effect, String scene, String white, String size, int width, int height){
    	//Log.d(TAG, "enter CameraPreview#setField");

    	mEffect = effect;
        mScene = scene;
        mWhite = white;
        //mPicIdx = size;
        mSizeStr = size;
        //portraitにするため、widthとheightを逆にする
        mWidth = height;
        mHeight = width;
    }
    
    public void surfaceCreated(SurfaceHolder holder) {
    	//Log.d(TAG, "enter CameraPreview#surfaceCreated");

    	if(mCamera == null){
    	    try{
                mCamera = Camera.open();
    	        
    	    }catch(RuntimeException e){
    	        new AlertDialog.Builder(mContext)
    	        .setTitle(R.string.sc_error_title)
    	        .setMessage(mContext.getString(R.string.sc_error_cam))
    	        .setPositiveButton(R.string.sc_error_cam_ok, new DialogInterface.OnClickListener() {
    	            public void onClick(DialogInterface dialog, int which) {
    	                System.exit(0);
    	            }
    	        })
    	        .show();
    	            
    	        try {
    	            this.finalize();
    	        } catch (Throwable t) {
    	            System.exit(0);                 
    	        }
    	        return;
    	    }
    	    
    	    mCamera.setDisplayOrientation(90);
    	}
    	
    	if(mSupportList == null){
    	    createSupportList();
    	}

    	try {
            mCamera.setPreviewDisplay(holder);               
        } catch (IOException e) {
            Log.e(TAG, "IOException in surfaceCreated");
            mCamera.release();
            mCamera = null;
        }
        
        //ズームをサポートしていない場合はViewを見えなくする
        Camera.Parameters params = mCamera.getParameters();
		if(params.isSmoothZoomSupported()){
			//Log.d(TAG, "this terminal supports smooth zoom.");
			mSmoothZoom = true;
			mCamera.setZoomChangeListener(new Camera.OnZoomChangeListener(){
                public void onZoomChange(int zoomValue, boolean stopped, Camera camera) {
                    //Log.d(TAG, "onZoomChange: value = " + zoomValue + " stopped = " + stopped);
                    mZoomStopped = stopped;
                    
                    if((zoomValue != mZoomIdx) && stopped){
                        //指定したズーム処理が追いついていない場合(すばやくスライドさせた場合など)
                        if(mCamera != null){
                            //Log.d(TAG, "onZoomChange->stopped");
                            try{
                                mCamera.startSmoothZoom(mZoomIdx);
                            }catch(Exception e){
                                //何もしない
                            }
                        }
                    }
                }
            });
		}
		else{
			if(params.isZoomSupported()){
				//Log.d(TAG, "this terminal supports zoom.");
				mZoom = true;
			}
			else{
				if(mContext != null){
					((ContShooting)mContext).invisibleZoom();
				}				
			}
		}
    }
    
    private void createSupportList(){
        Camera.Parameters params = mCamera.getParameters();
        mSupportList = Reflect.getSupportedPreviewSizes(params);
           
        if (mSupportList != null && mSupportList.size() > 0) {
            /*
            for(int i=0;i<mSupportList.size();i++){
                Log.d(TAG, "SupportedSize = " + mSupportList.get(i).width + "*" + mSupportList.get(i).height);
            }
            */

            //降順にソート
            Collections.sort(mSupportList, new PreviewComparator());

            /*
            for(int i=0;i<mSupportList.size();i++){
                Log.d(TAG, "SupportedSize = " + mSupportList.get(i).width + "*" + mSupportList.get(i).height);
            }
            */
            
            for(int i = 0; i < mSupportList.size(); i++){
                if(mSupportList.get(i).width > mWidth){
                    continue;
                }
                
                if(mSupportList.get(i).height > mHeight){
                    continue;
                }
                
                mSize = mSupportList.get(i);
                mOffset = i;
                break;
            }
            
            //Log.d(TAG, "size = " + mSize.width + "*" + mSize.height);

            if(mSize == null){
                mSize = mSupportList.get(0);
                mOffset = 0;
            }
            //params.setPreviewSize(mSize.width, mSize.height);
            //mCamera.setParameters(params);     
        }
    }
    
    public void surfaceDestroyed(SurfaceHolder holder) {
        //Log.d(TAG, "enter CameraPreview#surfaceDestroyed");
    	release();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    	//Log.d(TAG, "enter CameraPreview#surfaceChanged");
        
        //Cameraがopen()できなかったとき用
        if(mCamera == null){
            return;
        }

        //止めないでsetParameters()するとエラーとなる場合があるため止める
        mCamera.stopPreview();
        
        //設定画面で設定したとき
        if(mSetValue != null){
        	if(mSetColor == true){
                mEffect = mSetValue;
        	}
        	else if(mSetScene == true){
                mScene = mSetValue;
        	}
        	else if(mSetWhite == true){
                mWhite = mSetValue;
        	}
        	else if(mSetSize == true){
                mPicIdx = mSetInt;
                mSizeStr = getSizeList().get(mPicIdx);
        	}
        	
            mSetValue = null;
            mSetColor = false;
            mSetScene = false;
            mSetWhite = false;
            mSetSize = false;
            mSetInt = 0;
        }
        //設定画面で設定しないとき
        else{
            List<String> list = getSizeList();
            for(int i = 0; i<list.size(); i++){
                if(list.get(i).equals(mSizeStr)){
                    mPicIdx = i;
                }
                //mSizeStrが"0"のときはmPicIdxに値が設定されずに抜ける(=0になる)
            }
        }
        
        setAllParameters();

        //mPreviewCallback = new PreviewCallback(this);
        mCamera.startPreview();
        //focus
        mFocus = new AutoFocusCallback(){
            public void onAutoFocus(boolean success, Camera camera) {
                mPreviewCallback = new PreviewCallback(CameraPreview.this);
            }
        };
        try{
            mCamera.autoFocus(mFocus);
        }catch(Exception e){
            mPreviewCallback = new PreviewCallback(CameraPreview.this);            
        }
    }
    
    private void setAllParameters(){
        Camera.Parameters param = mCamera.getParameters();
        
        //ズームサポートしているがmax_zoomが0の場合は見えなくする
        if(mZoom || mSmoothZoom){
            if(param.getMaxZoom() == 0){
                ((ContShooting)mContext).invisibleZoom();
            }
        }

        //一度に複数のパラメータを設定すると落ちる端末があるため、1つずつ設定する
        try{
            param.setColorEffect(mEffect);            
            mCamera.setParameters(param);                
        }catch(Exception e){
            param = mCamera.getParameters();
        }

        try{
            param.setSceneMode(mScene);
            mCamera.setParameters(param);                
        }catch(Exception e){
            param = mCamera.getParameters();
        }

        try{
            param.setWhiteBalance(mWhite);
            mCamera.setParameters(param);                
        }catch(Exception e){
            param = mCamera.getParameters();
        }

        try{
            int index = 0;
            if(!ContShootingPreference.isHighResolution(mContext)){
                index = mOffset + mPicIdx;
            }
            mSize = mSupportList.get(index);
            param.setPreviewSize(mSize.width, mSize.height);
            mCamera.setParameters(param);
        }catch(Exception e){
            //nothing to do
        }
    }
    
    public void resumeShooting(){
    	if(mPreviewCallback != null){
    		if(mCamera != null){
    			mCamera.startPreview();
    			mCamera.setPreviewCallback(mPreviewCallback);
    		}
    	}

    	//ボタン表示を「停止」に変更する
    	((ContShooting)mContext).displayStop();
    }
    
    public void stopShooting(){
    	//Log.d(TAG, "enter CameraPreview#stopPreview");

    	mCamera.stopPreview();
        mCamera.setPreviewCallback(null);
		//ボタン表示を「開始」に変更する
		((ContShooting)mContext).displayStart();
        mNum = 0;
		//プレビューだけ開始する(画像保存はしない(setPreviewCallbackを呼ばない))
        mCamera.startPreview();
    }
    
    public void startPreview(){
		if(mCamera != null){
    		mCamera.startPreview();
    	}
    }
    
    public void stopPreview(){
    	if(mCamera != null){
    		mCamera.stopPreview();
    	}    	
    }

    void doAutoFocus(){
    	if(mCamera != null && mFocus != null){
            mCamera.setPreviewCallback(null);
    		try{
    			mCamera.autoFocus(mFocus);
    		}catch(Exception e){
    			mPreviewCallback = new PreviewCallback(CameraPreview.this);            
    		}
    	}
    }
    
    public boolean isZoomSupported(){
        if(mSmoothZoom || mZoom){
            Camera.Parameters param = mCamera.getParameters();
            if(param.getMaxZoom() != 0){
                return true;
            }
    	}
    	return false;
    }
    
    public void setZoom(int progress){
    	if(mCamera == null){
    		return;
    	}

		Camera.Parameters params = mCamera.getParameters();
    	if(mZoomMax == 0){
    		mZoomMax = params.getMaxZoom();
    		//Log.d(TAG, "zoom max = " + mZoomMax);
    	}
    	
		mZoomIdx = mZoomMax * progress / 100;
		//Log.d(TAG, "value = " + mZoomIdx);
    	
    	if(mSmoothZoom){
    		try{
    		    if(mZoomStopped){
    		        mCamera.startSmoothZoom(mZoomIdx);
    		    }
    		}catch(Exception e){
    			//何もしない
    		}
    	}
    	else if(mZoom){
    		//mCamera.stopPreview();
    		params.setZoom(mZoomIdx);
    		mCamera.setParameters(params);
    		//mCamera.startPreview();
    	}
    }
    
    List<String> getEffectList(){
        Camera.Parameters param = mCamera.getParameters();
        return param.getSupportedColorEffects();
    }
    
    List<String> getWhiteBalanceList(){
        Camera.Parameters param = mCamera.getParameters();
        return param.getSupportedWhiteBalance();
    }
    
    List<String> getSceneModeList(){
        Camera.Parameters param = mCamera.getParameters();
        return param.getSupportedSceneModes();
    }
    
    List<String> getSizeList(){
    	List<String> list = new ArrayList<String>();
    	for(int i = mOffset; i<mSupportList.size(); i++){
    		String size = mSupportList.get(i).width + "x" + mSupportList.get(i).height;
    		//String size = mSupportList.get(i).height + "x" + mSupportList.get(i).width;
    		list.add(size);
    	}
    	return list;
    }
    
    int getPreviewOffset(){
        return mOffset;
    }
    
    void setColorValue(String value){
    	mSetColor = true;
    	mSetValue = value;
    }
    
    void setSceneValue(String value){
    	mSetScene = true;
    	mSetValue = value;
    }
    
    void setWhiteValue(String value){
    	mSetWhite = true;
    	mSetValue = value;
    }
    
    void setSizeValue(int value){
    	mSetSize = true;
    	mSetInt = value;
    	//マークのみ
    	mSetValue = "hoge";
    }
    
    void setShootNum(int num){
        mMax = num;
    }
    
    void setInterval(int interval){
        mInterval = interval;
    }
    
    void countShoot(){
    	//((ContShooting)mContext).count();

        if(mInterval == 0 && ((ContShooting)mContext).mMode == 1){
            //コールバックを再開
            mCamera.setPreviewCallback(mPreviewCallback);                
        }

        mNum++;
        if(mMax!=0){
            if(mNum >= mMax){
                stopShooting();
                ((ContShooting)mContext).setMode(0);
            }
        }        
    }
    
    void release(){
        if(mCamera != null){
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
        
        ((ContShooting)mContext).setMode(0);
        ((ContShooting)mContext).displayStart();
        
        mNum=0;
        //mSupportList = null;
    }

    class PreviewComparator implements java.util.Comparator {
    	public int compare(Object s, Object t) {
    		//降順
    		return ((Size) t).width - ((Size) s).width;
    	}
    }
    
    public class PreviewCallback implements Camera.PreviewCallback {
        private CameraPreview mPreview = null;

        PreviewCallback(CameraPreview preview){
            mPreview = preview;
        }

        public void onPreviewFrame(byte[] data, Camera camera) {
        	//Log.d(TAG, "enter CameraPreview#onPreviewFrame");
            //Log.d(TAG, "data.length = " + data.length);
        	
            //一旦コールバックを止める
        	camera.setPreviewCallback(null);

        	//撮影間隔設定用のタイマ
            if(mInterval != 0){
                Thread t2 = new Thread(){
                    public void run(){
                        try {
                            Thread.sleep(mInterval * 1000);
                        } catch (InterruptedException e) {
                        }

                        if(mCamera != null){
                            //撮影中のときはコールバックを再開。停止時は再開しない
                            if(((ContShooting)mContext).mMode == 1){
                                mCamera.setPreviewCallback(mPreviewCallback);                                   
                            }
                        }
                    }
                };
                t2.start();
            }

            //convert to "real" preview size. not size setting before.
            Size size = convertPreviewSize(data);

            final int width = size.width;
            final int height = size.height;            
            
            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            ((ContShooting)mContext).count();
            ImageAsyncTask task = new ImageAsyncTask(mContext, CameraPreview.this, data, size);
            task.execute(bmp);
       }
        
        private Size convertPreviewSize(byte[] data){
            double displaysize = data.length / 1.5;
            Size size;
            int x, y;
            
            for(int i=0; i<mSupportList.size(); i++){
                size = mSupportList.get(i);
                x = size.width;
                y = size.height;
                if((x*y) == displaysize){
                    return size;
                }
            }
            return null;
        }
    }
}