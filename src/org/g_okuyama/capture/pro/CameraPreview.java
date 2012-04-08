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
	
	//連写間隔
	private int mInterval = 0;
		
    CameraPreview(Context context){
        mContext = context;
	}
	
	public void setField(String effect, String scene, String white, String size){
        mEffect = effect;
        mScene = scene;
        mWhite = white;
        //mPicIdx = size;
        mSizeStr = size;
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
            
            /*
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
            */
            
            //Log.d(TAG, "size = " + mSize.width + "*" + mSize.height);

            //if(mSize == null){
                mSize = mSupportList.get(0);
                mOffset = 0;
            //}
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

        /*
        //if(mFocusFlag){
        	mFocus = new AutoFocusCallback(){
        		public void onAutoFocus(boolean success, Camera camera) {
        			//mCamera.setOneShotPreviewCallback(mPreviewCallback);
        			mCamera.setPreviewCallback(mPreviewCallback);
        		}
        	};
        //}
        */
        
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
        
        /*
        param.setColorEffect(mEffect);            
        param.setSceneMode(mScene);
        param.setWhiteBalance(mWhite);
        mSize = mSupportList.get(mOffset + mPicIdx);        
        param.setPreviewSize(mSize.width, mSize.height);
        mCamera.setParameters(param);
        */

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
            mSize = mSupportList.get(mOffset + mPicIdx);        
            param.setPreviewSize(mSize.width, mSize.height);
            mCamera.setParameters(param);
        }catch(Exception e){
            //nothing to do
        }
    }
    
    public void resumePreview(){
    	//Log.d(TAG, "enter CameraPreview#resumePreview");
    	
    	/*
    	if(mFocusFlag){
    		mCamera.startPreview();
    		mCamera.autoFocus(mFocus);
    	}
    	*/
    	//else{
    		if(mPreviewCallback != null){
    			if(mCamera != null){
    				mCamera.startPreview();
    				mCamera.setPreviewCallback(mPreviewCallback);
    			}
    		}
    	//}
    		
    		//ボタン表示を「停止」に変更する
    		((ContShooting)mContext).displayStop();
    }
    
    public void stopPreview(){
    	//Log.d(TAG, "enter CameraPreview#stopPreview");

    	mCamera.stopPreview();
        mCamera.setPreviewCallback(null);
		//ボタン表示を「開始」に変更する
		((ContShooting)mContext).displayStart();
        mNum = 0;
		//プレビューだけ開始する(画像保存はしない(setPreviewCallbackを呼ばない))
        mCamera.startPreview();
    }

    void doAutoFocus(){
        mCamera.setPreviewCallback(null);
    	if(mCamera != null && mFocus != null){
    		try{
    			mCamera.autoFocus(mFocus);
    		}catch(Exception e){
    			mPreviewCallback = new PreviewCallback(CameraPreview.this);            
    		}
    	}
    }
    
    public void setZoom(boolean flag){
    	/*
    	if(mCamera == null){
    		return;
    	}
    	
        Camera.Parameters params = mCamera.getParameters();

        //if(params.isSmoothZoomSupported() == false){
        //Log.d(TAG, "Zoom is not supported");
        //	return;
        //}
        
        List ZoomRatislist = params.getZoomRatios ();
        for (int i=0;i < ZoomRatislist.size();i++) {
        	Log.d("camera", "list " + i + " = " + ZoomRatislist.get(i));
        }

    	
        int cur = params.getZoom();
        int max = params.getMaxZoom();

        Log.d(TAG, "currentZoom: " + cur);
        Log.d(TAG, "maxZoom: " + max);
        
        if(flag){
        	if(cur < max){
        		//mCamera.startSmoothZoom(++cur);
        		params.setZoom(++cur);
        		mCamera.setParameters(params);
        	}
        }
        else{
        	if(cur > 0){
        		//mCamera.startSmoothZoom(--cur);
        		params.setZoom(--cur);
        		mCamera.setParameters(params);
        	}
        }
        */
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
    		//String size = mSupportList.get(i).width + "x" + mSupportList.get(i).height;
    		String size = mSupportList.get(i).height + "x" + mSupportList.get(i).width;
    		list.add(size);
    	}
    	return list;
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

            ((ContShooting)mContext).count();

            //convert to "real" preview size. not size setting before.
            Size size = convertPreviewSize(data);

            final int width = size.width;
            final int height = size.height;            
            int[] rgb = new int[(width * height)];

            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            decodeYUV420SP(rgb, data, width, height);
            bmp.setPixels(rgb, 0, width, 0, 0, width, height);
            
            
            //回転
            Matrix matrix = new Matrix();
            // 回転させる角度を指定
            matrix.postRotate(90.0f);   
            Bitmap bmp2 = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
            bmp = null;

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp2.compress(Bitmap.CompressFormat.JPEG, 100, out);
            
            savedata(out.toByteArray());
            
            //oneshotのときはコメント外す
            /*
            if(mFocusFlag){
            	camera.autoFocus(mFocus);
            }
            */
            
            if(mInterval == 0){
                //コールバックを再開
                camera.setPreviewCallback(mPreviewCallback);                
            }

            mNum++;
            if(mMax!=0){
                if(mNum >= mMax){
                    mPreview.stopPreview();
                    ((ContShooting)mContext).setMode(0);
                }
            }
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
        
        public void savedata(byte[] data){
        	if(mFile == null){
        		mFile = new File(Environment.getExternalStorageDirectory(), "/ContShooting");
        	}

            FileOutputStream fos = null;
            File savefile = null;
            String datastr = getCurrentDate();
            try{
                if(mFile.exists() == false){
                    mFile.mkdir();
                }
                savefile = new File(mFile.getPath(), datastr + ".jpg");
                fos = new FileOutputStream(savefile);
                fos.write(data);
                fos.flush();
                fos.close();
            }catch(IOException e){
                Log.e(TAG, "IOException in savedata");
                if(fos != null){
                    try {
                        fos.close();
                    } catch (IOException e1) {
                        //do nothing
                    }
                }
                return;
            }

            //ギャラリーへの登録
			ContentValues values = new ContentValues();

			values.put(Images.Media.MIME_TYPE, "image/jpeg");
			values.put(Images.Media.DATA, savefile.getAbsolutePath());
			values.put(Images.Media.SIZE, savefile.length());
			//values.put(Images.Media.TITLE,strFile);
			//values.put(Images.Media.DISPLAY_NAME,strFile);
			values.put(Images.Media.DATE_ADDED, datastr);
			values.put(Images.Media.DATE_TAKEN, datastr);
			values.put(Images.Media.DATE_MODIFIED, datastr);
			//values.put(Images.Media.DESCRIPTION,"");
			//values.put(Images.Media.LATITUDE,0.0);
			//values.put(Images.Media.LONGITUDE,0.0);
			//values.put(Images.Media.ORIENTATION,"");
			((ContShooting)mContext).saveGallery(values);

			/*
			((ContShooting)mContext).count();
			mNum++;
			if(mMax!=0){
			    if(mNum >= mMax){
			        mPreview.stopPreview();
			        ((ContShooting)mContext).setMode(0);
			    }
			}
			*/
        } 
        
        // YUV420 to BMP 
        public void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) { 
            final int frameSize = width * height; 

            for (int j = 0, yp = 0; j < height; j++) { 
                int uvp = frameSize + (j >> 1) * width, u = 0, v = 0; 
                for (int i = 0; i < width; i++, yp++) { 
                    int y = (0xff & ((int) yuv420sp[yp])) - 16; 
                    if (y < 0) y = 0; 
                    if ((i & 1) == 0) { 
                            v = (0xff & yuv420sp[uvp++]) - 128; 
                            u = (0xff & yuv420sp[uvp++]) - 128; 
                    } 

                    int y1192 = 1192 * y; 
                    int r = (y1192 + 1634 * v); 
                    int g = (y1192 - 833 * v - 400 * u); 
                    int b = (y1192 + 2066 * u); 

                    if (r < 0) r = 0; else if (r > 262143) r = 262143; 
                    if (g < 0) g = 0; else if (g > 262143) g = 262143; 
                    if (b < 0) b = 0; else if (b > 262143) b = 262143; 

                    rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff); 
                } 
            }
        }
        
        String getCurrentDate(){
            Calendar cal1 = Calendar.getInstance();
            int year = cal1.get(Calendar.YEAR);
            int mon = cal1.get(Calendar.MONTH) + 1;
            int d = cal1.get(Calendar.DATE);
            int h = cal1.get(Calendar.HOUR_OF_DAY);
            int min = cal1.get(Calendar.MINUTE);
            int sec = cal1.get(Calendar.SECOND);
            int msec = cal1.get(Calendar.MILLISECOND);
            
            String month = Integer.toString(mon);
            if(month.length() == 1){
                month = "0" + month;
            }
            String day = Integer.toString(d);
            if(day.length() == 1){
                day = "0" + day;
            }
            String hour = Integer.toString(h);
            if(hour.length() == 1){
                hour = "0" + hour;
            }
            String minute = Integer.toString(min); 
            if(minute.length() == 1){
                minute = "0" + minute;
            }
            String second = Integer.toString(sec);
            if(second.length() == 1){
                second = "0" + second;
            }
            String millisecond = Integer.toString(msec);
            if(millisecond.length() == 1){
                millisecond = "00" + millisecond;
            }
            else if(millisecond.length() == 2){
                millisecond = "0" + millisecond;
            }            

            return Integer.toString(year) + month + day + hour + minute + second + millisecond;
        }
    }
}