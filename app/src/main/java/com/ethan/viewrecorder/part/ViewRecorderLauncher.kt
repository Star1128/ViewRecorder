package com.ethan.viewrecorder.part

import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.view.View
import com.ethan.viewrecorder.BaseApplication
import java.io.File
import java.io.IOException

/**
 *
 * @author wangxingchen01 2024/1/19
 */
class ViewRecorderLauncher {

    private var mWorkerHandler: Handler? = null
    private var mViewRecorder: ViewRecorder =
        ViewRecorder()
    private var mPartRecording = false

    private val mOnErrorListener =
        MediaRecorder.OnErrorListener { mr, what, extra ->
            mViewRecorder.reset()
            mViewRecorder.release()
        }

    private fun startPartRecord(recorderView: View) {
        val ht = HandlerThread("bg_view_recorder")
        ht.start()
        mWorkerHandler = Handler(ht.looper)
        val directory: File? = BaseApplication.appContext.externalCacheDir
        if (directory != null) {
            directory.mkdirs()
            if (!directory.exists()) {
                return
            }
        }
        mViewRecorder.setAudioSource(MediaRecorder.AudioSource.MIC) // 音频源
        mViewRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mViewRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mViewRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mViewRecorder.setVideoFrameRate(30) // 帧率
        mViewRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mViewRecorder.setVideoSize(720, 1280)
        mViewRecorder.setVideoEncodingBitRate(2000 * 1000)
        mViewRecorder.setOutputFile("${BaseApplication.appContext.externalCacheDir}/${System.currentTimeMillis()}.mp4")
        mViewRecorder.setOnErrorListener(mOnErrorListener)
        mViewRecorder.setRecordedView(recorderView)
        try {
            mViewRecorder.prepare()
            mViewRecorder.start()
        } catch (e: IOException) {
            return
        }
        mPartRecording = true
    }

    private fun stopPartRecord() {
        try {
            mViewRecorder.stop()
            mViewRecorder.reset()
            mViewRecorder.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mPartRecording = false
    }
}