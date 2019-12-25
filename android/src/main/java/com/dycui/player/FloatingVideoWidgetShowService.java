package com.dycui.player;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.Display;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.io.IOException;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public class FloatingVideoWidgetShowService extends Service {


    public static final int PROGRESS_UPDATE_INTERVAL_MILLS = 500;
    public static int mDuration;
    private static ReadableMap playingVideo = null; // The video currently playing
    private static ReadableArray videoPlaylist = null; // List of videos
    private static int index = 0; // Index of playing video in videoPlaylist
    private static ReadableMap initData = null;
    WindowManager windowManager;
    View floatingWindow, floatingView, playerWrapper, overlayView;
    SurfaceView videoView;
    ImageButton increaseSize, decreaseSize, playVideo, pauseVideo;
    SeekBar seekBar;
    TextView currentTimeView, durationView;
    WindowManager.LayoutParams params;
    ReactContext reactContext = null;
    private Handler timeoutHandler = new Handler();
    private GestureDetector gestureDetector;
    private int videoWidth = 250; // Default width of floating video player
    private int videoHeight = 180; // Default Height of floating video player
    private IjkMediaPlayer mIjkPlayer;
    private Handler mHandler = new Handler();
    private Runnable progressUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIjkPlayer == null || mDuration == 0) {
                return;
            }
            long currProgress = mIjkPlayer.getCurrentPosition();
            int mCurrProgress = (int) Math.ceil((currProgress * 1.0f) / 1000);
            //ToastUtil.show(reactContext, "" + mCurrProgress);

            currentTimeView.setText(FloatingVideoWidgetShowService.timeParse(mCurrProgress));
            durationView.setText(FloatingVideoWidgetShowService.timeParse((int) Math.ceil(mIjkPlayer.getDuration() / 1000)));
            seekBar.setProgress(mCurrProgress);
            mHandler.postDelayed(progressUpdateRunnable, PROGRESS_UPDATE_INTERVAL_MILLS);
        }
    };

    public FloatingVideoWidgetShowService() {
    }

    public static String timeParse(long duration) {
        String time = "";

        long minute = duration / 60;
        long seconds = duration % 60;

        if (minute < 10) {
            time += "0";
        }
        time += minute + ":";

        if (seconds < 10) {
            time += "0";
        }
        time += seconds;

        return time;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {

                case "ACTION_CLOSE_WIDGET": {
                    long seek = mIjkPlayer.getCurrentPosition();
                    videoView.setKeepScreenOn(false);
                    mIjkPlayer.stop();
                    mIjkPlayer.release();
                    stopSelf();
                    WritableMap args = new Arguments().createMap();
                    args.putInt("index", index);
                    args.putInt("seek", (int) seek);
                    args.putString("type", "close");
                    sendEvent(reactContext, "onClose", args);
                    onDestroy();
                    break;
                }
                case "ACTION_PLAY": {
                    onResume(floatingWindow);
                    break;
                }
                case "ACTION_PAUSE": {
                    onPause(floatingWindow);
                    break;
                }
                case "ACTION_PREV": {
                    //onPrev(floatingWindow);
                    break;
                }
                case "ACTION_NEXT": {
                    //onNext(floatingWindow);
                    break;
                }
                case "ACTION_SET_VIDEO": {
                    ReadableMap data = Arguments.fromBundle(intent.getBundleExtra("DATA"));
                    initData = data;
                    playingVideo = data.getMap("video");
                    videoPlaylist = data.getArray("videos");
                    index = data.getInt("index");
                    int seek = data.getInt("seek");
                    //Uri myUri = Uri.parse(playingVideo.getString("url"));
                    if (!mIjkPlayer.isPlaying()) {
                        try {
                            mIjkPlayer.setDataSource(playingVideo.getString("url"));
                            this.initIjkMediaPlayerListener();
                            this.initSurfaceView();
                            mIjkPlayer.prepareAsync();
//                        mIjkPlayer.seekTo(Seek);
//                        mIjkPlayer.start();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {

                    }
                    videoView.setKeepScreenOn(true);

                    WritableMap args = Arguments.createMap();
                    args.putString("state", "isOpened");
                    args.putString("url", playingVideo.getString("url"));
                    sendEvent(reactContext, "onOpen", args);
                    break;
                }
            }
        }
        return START_STICKY;
    }

    private void initSurfaceView() {
        this.videoView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                //Log.i(TAG, "surface created");
                mIjkPlayer.setDisplay(holder);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                //Log.i(TAG, "surface changed");
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                //Log.i(TAG, "surface destroyed");
            }
        });
    }

    private void initIjkMediaPlayer() {
        mIjkPlayer = new IjkMediaPlayer();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        final ReactInstanceManager reactInstanceManager = getReactNativeHost().getReactInstanceManager();
        ReactContext getReactContext = reactInstanceManager.getCurrentReactContext();
        gestureDetector = new GestureDetector(this, new SingleTapConfirm());
        reactContext = getReactContext;
        floatingWindow = LayoutInflater.from(this).inflate(R.layout.floating_widget_layout, null);

        // Define the layout flag according to android version.

        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }
        // Setting layout params for floating video

        params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);


        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        assert windowManager != null;
        windowManager.addView(floatingWindow, params);

        // Define all the video view and its components
        this.initIjkMediaPlayer();
        floatingView = floatingWindow.findViewById(R.id.Layout_Expended);
        playerWrapper = floatingWindow.findViewById(R.id.view_wrapper);
        overlayView = floatingWindow.findViewById(R.id.overlay_view);
        videoView = (SurfaceView) floatingWindow.findViewById(R.id.videoView);
        seekBar = floatingView.findViewById(R.id.seekbar);
        currentTimeView = floatingView.findViewById(R.id.tvCurrentTime);
        durationView = floatingView.findViewById(R.id.tvDuration);
        playVideo = (ImageButton) floatingWindow.findViewById(R.id.app_video_play);
        pauseVideo = (ImageButton) floatingWindow.findViewById(R.id.app_video_pause);


        // Setting the on error Listener


        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mIjkPlayer.seekTo(seekBar.getProgress() * 1000);
            }
        });

        floatingWindow.findViewById(R.id.app_video_crop).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                long seek = mIjkPlayer.getCurrentPosition();
                videoView.setKeepScreenOn(false);
                mIjkPlayer.stop();
                mIjkPlayer.release();
                stopSelf();
                WritableMap args = new Arguments().createMap();
                args.putInt("index", index);
                args.putInt("seek", (int) seek);
                args.putString("url", playingVideo.getString("url"));
                args.putString("type", "close");
                sendEvent(reactContext, "onClose", args);
                onDestroy();
            }
        });


        floatingWindow.findViewById(R.id.Layout_Expended).setOnTouchListener(new View.OnTouchListener() {
            int X_Axis, Y_Axis;
            float TouchX, TouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {


                if (gestureDetector.onTouchEvent(event)) {

                    if (overlayView.getVisibility() == View.VISIBLE) {

                        overlayView.setVisibility(View.GONE);
                        timeoutHandler.removeCallbacksAndMessages(null);

                    } else {
                        overlayView.setVisibility(View.VISIBLE);

                        timeoutHandler.postDelayed(new Runnable() {
                            public void run() {

                                overlayView.setVisibility(View.GONE);
                            }
                        }, 5000);
                    }

                } else {

                    int touches = event.getPointerCount();

                    if (touches > 1) {
                    }

                    switch (event.getAction()) {

                        case MotionEvent.ACTION_DOWN:

                            X_Axis = params.x;
                            Y_Axis = params.y;
                            TouchX = event.getRawX();
                            TouchY = event.getRawY();
                            return true;

                        case MotionEvent.ACTION_UP:

                            floatingView.setVisibility(View.VISIBLE);
                            return true;

                        case MotionEvent.ACTION_MOVE:

                            params.x = X_Axis + (int) (event.getRawX() - TouchX);
                            params.y = Y_Axis + (int) (event.getRawY() - TouchY);
                            windowManager.updateViewLayout(floatingWindow, params);
                            return true;
                    }

                }

                return false;

            }
        });
    }

    private void initIjkMediaPlayerListener() {
        mIjkPlayer.setOnErrorListener((iMediaPlayer, i, i1) -> {
            long seek = mIjkPlayer.getCurrentPosition();
            WritableMap args = new Arguments().createMap();
            args.putInt("index", index);
            args.putInt("seek", (int) seek);
            args.putString("url", playingVideo.getString("url"));
            args.putString("type", "error");

            sendEvent(reactContext, "onError", args);

            Toast.makeText(reactContext, "An Error occured, please try again", Toast.LENGTH_LONG).show();
            return false;
        });

        // Changes the video size when new video is loaded

        mIjkPlayer.setOnVideoSizeChangedListener(new IMediaPlayer.OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(IMediaPlayer iMediaPlayer, int intrinsicWidth, int intrinsicHeight, int i2, int i3) {
                final float scale = reactContext.getResources().getDisplayMetrics().density;

                videoWidth = intrinsicWidth;
                videoHeight = intrinsicHeight;

                RelativeLayout relativeLayout = (RelativeLayout) floatingWindow.findViewById(R.id.view_wrapper);
                double aspectRatio = (double) videoWidth / (double) videoHeight;

                if (videoHeight > videoWidth) {
                    int height = (int) (200 * scale + 0.5f);
                    double width = height * aspectRatio;

                    relativeLayout.getLayoutParams().width = (int) width;
                    relativeLayout.getLayoutParams().height = height;

                } else {
                    int width = (int) (250 * scale + 0.5f);
                    double height = width / aspectRatio;
                    relativeLayout.getLayoutParams().width = width;
                    relativeLayout.getLayoutParams().height = (int) height;

                }
            }
        });

        mIjkPlayer.setOnPreparedListener(iMediaPlayer -> {
            mDuration = (int) Math.ceil(iMediaPlayer.getDuration() / 1000);
            mHandler.post(progressUpdateRunnable);
            seekBar.setMax(mDuration);
//            ToastUtil.show(reactContext, mDuration + "");
            durationView.setText(FloatingVideoWidgetShowService.timeParse(mDuration));
            //seekBar.setMin(0);

            int seek = initData.getInt("seek");
            mIjkPlayer.seekTo(seek * 1000);
        });
    }

    private void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
    }

    public void increaseWindowSize(View view) {
        final float scale = reactContext.getResources().getDisplayMetrics().density;
        RelativeLayout relativeLayout = (RelativeLayout) floatingWindow.findViewById(R.id.view_wrapper);
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int densityX = size.x; // default height width of screen

        double aspectRatio = (double) videoWidth / (double) videoHeight;

        if (videoHeight > videoWidth) {
            int height = (int) (400 * scale + 0.5f);
            double width = height * aspectRatio;

            relativeLayout.getLayoutParams().width = (int) width;
            relativeLayout.getLayoutParams().height = height;

        } else {
            int width = densityX;
            double height = width / aspectRatio;
            relativeLayout.getLayoutParams().width = densityX;
            relativeLayout.getLayoutParams().height = (int) height;

        }
        increaseSize.setVisibility(View.GONE);
        decreaseSize.setVisibility(View.VISIBLE);

    }

    public void returnToApp(View view) {
        long seek = mIjkPlayer.getCurrentPosition();
        Intent intent = getPackageManager().getLaunchIntentForPackage(reactContext.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        videoView.setKeepScreenOn(false);
        stopSelf();
        WritableMap args = new Arguments().createMap();
        args.putInt("index", index);
        args.putInt("seek", (int) seek);
        args.putString("type", "close");
        args.putString("url", playingVideo.getString("url"));

        sendEvent(reactContext, "onClose", args);
        onDestroy();
    }

    public void decreaseWindowSize(View view) {
        final float scale = reactContext.getResources().getDisplayMetrics().density;
        RelativeLayout relativeLayout = (RelativeLayout) floatingWindow.findViewById(R.id.view_wrapper);

        double aspectRatio = (double) videoWidth / (double) videoHeight;

        if (videoHeight > videoWidth) {
            int height = (int) (200 * scale + 0.5f);
            double width = height * aspectRatio;

            relativeLayout.getLayoutParams().width = (int) width;
            relativeLayout.getLayoutParams().height = height;

        } else {
            int width = (int) (250 * scale + 0.5f);
            double height = width / aspectRatio;
            relativeLayout.getLayoutParams().width = width;
            relativeLayout.getLayoutParams().height = (int) height;

        }

        increaseSize.setVisibility(View.VISIBLE);
        decreaseSize.setVisibility(View.GONE);

    }

    public void onPause(View view) {
        long seek = mIjkPlayer.getCurrentPosition();
        playVideo.setVisibility(ImageButton.VISIBLE);
        pauseVideo.setVisibility(ImageButton.GONE);
        mIjkPlayer.pause();
        WritableMap args = Arguments.createMap();
        args.putInt("index", index);
        args.putInt("seek", (int) seek);
        args.putString("type", "paused");
        args.putString("url", playingVideo.getString("url"));
        sendEvent(reactContext, "onPause", args);
        mHandler.removeCallbacks(progressUpdateRunnable);
    }

    public void onResume(View view) {
        long seek = mIjkPlayer.getCurrentPosition();
        playVideo.setVisibility(ImageButton.GONE);
        pauseVideo.setVisibility(ImageButton.VISIBLE);
        mIjkPlayer.start();
        WritableMap args = Arguments.createMap();
        args.putInt("index", index);
        args.putInt("seek", (int) seek);
        args.putString("type", "resume");
        args.putString("url", playingVideo.getString("url"));
        sendEvent(reactContext, "onPlay", args);
        mHandler.post(progressUpdateRunnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingWindow != null)
            windowManager.removeView(floatingWindow);
    }

    protected ReactNativeHost getReactNativeHost() {
        return ((ReactApplication) getApplication()).getReactNativeHost();
    }

    private class SingleTapConfirm extends SimpleOnGestureListener {

        @Override
        public boolean onSingleTapUp(MotionEvent event) {
            return true;
        }
    }

}
