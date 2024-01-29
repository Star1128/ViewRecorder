package com.ethan.viewrecorder.part;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.os.Looper;
import android.util.Size;
import android.view.View;

import androidx.annotation.NonNull;

/**
 * Used to record a video with view that can be captured. It also supports to switch views during recording.
 * This class extends {@link SurfaceMediaRecorder} and provides an extra API {@link #setRecordedView(View)}.
 *
 * <p>By default, capture is drawn in the center of canvas in scale if necessary.
 * It is easy to change drawing behavior with {@link #setVideoFrameDrawer(VideoFrameDrawer)}.
 *
 * <p>Main thread is set for drawing as capture is only available in this thread,
 * it's OK to move composing to a background thread with {@link #setWorkerLooper(Looper)},
 * in this case, a capture buffer for multi-thread may be required.
 */
public class ViewRecorder extends SurfaceMediaRecorder {

    private View mRecordedView;

    private Size mVideoSize;

    private String TAG = "ViewRecorder";

    private final VideoFrameDrawer mVideoFrameDrawer = new VideoFrameDrawer() {

        /**
         * 保持 Bitmap 的原始相对位置
         * @param x x 坐标
         * @param y y 坐标
         * @param bw Bitmap 宽度
         * @param bh Bitmap 高度
         * @param vw Video 宽度
         * @param vh Video 高度
         * @return Matrix
         */
        private Matrix getMatrix(float x, float y, int bw, int bh, int vw, int vh) {
            Matrix matrix = new Matrix();
            float scale, scaleX = 1, scaleY = 1, transX, transY;

            if (bw > vw) {
                scaleX = ((float) vw) / bw;
            }
            if (bh > vh) {
                scaleY = ((float) vh) / bh;
            }
            scale = Math.min(scaleX, scaleY);
            transX = (vw - bw * scale) / 2;
            transY = (vh - bh * scale) / 2;

            matrix.postTranslate(x, y);
            matrix.postScale(scale, scale);
            matrix.postTranslate(transX, transY);

            return matrix;
        }

        /**
         * 在 Canvas 上绘制，绘制的内容就是视频帧
         */
        @Override
        public void onDraw(Canvas canvas) {
            mRecordedView.setDrawingCacheEnabled(true);
            Bitmap bitmap = mRecordedView.getDrawingCache();

            int bitmapWidth = bitmap.getWidth();
            int bitmapHeight = bitmap.getHeight();
            int videoWidth = mVideoSize.getWidth();
            int videoHeight = mVideoSize.getHeight();
            canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR);

            // 如果是标准的 View 绘制体系（onMeasure - onLayout - onDraw），都可以直接录制
            Matrix matrix = getMatrix(mRecordedView.getX(), mRecordedView.getY(), bitmapWidth, bitmapHeight, videoWidth, videoHeight);
            canvas.drawBitmap(bitmap, matrix, null);

            // 如果是 TextureView，需要单独录制，比如通过 getBitmap() 获取当前帧的 Bitmap

            mRecordedView.setDrawingCacheEnabled(false);
        }
    };

    @Override
    public void setVideoSize(int width, int height) throws IllegalStateException {
        super.setVideoSize(width, height);
        mVideoSize = new Size(width, height);
    }

    @Override
    public void start() throws IllegalStateException {
        if (isSurfaceAvailable()) {
            if (mVideoSize == null) {
                throw new IllegalStateException("video size is not initialized yet");
            }
            if (mRecordedView == null) {
                throw new IllegalStateException("recorded view is not initialized yet");
            }
            setWorkerLooper(Looper.getMainLooper());
            setVideoFrameDrawer(mVideoFrameDrawer);
        }

        super.start();
    }

    /**
     * Sets recorded view to be captured for video frame composition. Call this method before start().
     * You may change the recorded view with this method during recording.
     *
     * @param view the view to be captured
     */
    public void setRecordedView(@NonNull View view) throws IllegalStateException {
        mRecordedView = view;
    }
}
