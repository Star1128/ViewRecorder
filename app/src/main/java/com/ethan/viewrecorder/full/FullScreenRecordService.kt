package com.ethan.viewrecorder.full

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.ethan.viewrecorder.BaseApplication
import com.ethan.viewrecorder.R
import com.ethan.viewrecorder.utils.PcmToAacUtil
import com.ethan.viewrecorder.utils.PcmToWavUtil
import com.ethan.viewrecorder.utils.ScreenUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class FullScreenRecordService : Service(), CoroutineScope by CoroutineScope(Dispatchers.IO) {

    companion object {
        private const val TAG: String = "FullScreenRecordService"
        const val RESULT_CODE: String = "resultCode"
        const val RESULT_DATA: String = "data"
        const val mSampleRateInHZ: Int = 16000
        const val mChannel: Int = AudioFormat.CHANNEL_IN_MONO
        const val mEncoding: Int = AudioFormat.ENCODING_PCM_16BIT
    }

    private var mIsRecording: Boolean = false
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mFullMediaRecorder: MediaRecorder? = null
    private var mResultData: Intent? = null
    private var mResultCode: Int? = 0
    private var mMediaProjection: MediaProjection? = null
    private var mScreenDensity: Float? = null
    private var mScreenWidth: Int = 1920
    private var mScreenHeight: Int = 1080
    private var mAudioRecord: AudioRecord? = null
    private var audioOutputFile: File? = null
    private lateinit var aacOutputFile: File
    private lateinit var videoOutputFile: File

    private val mOnErrorListener = MediaRecorder.OnErrorListener { _, what, extra ->
        Log.e(
            TAG, "MediaRecorder error: type = $what, code = $extra"
        )
        mFullMediaRecorder?.reset()
        mFullMediaRecorder?.release()
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        launch {
            mResultCode = intent?.getIntExtra(RESULT_CODE, 1)
            mResultData = intent?.getParcelableExtra(RESULT_DATA)

            getScreenBaseInfo()

            createNotification()
            mMediaProjection = createMediaProjection()
            mFullMediaRecorder = createMediaRecorder()
            mVirtualDisplay =
                createVirtualDisplay() // 必须在mediaRecorder.prepare() 之后调用，否则报错"fail to get surface"

            try {
                mIsRecording = true
                initAudioRecord()
                mFullMediaRecorder?.start()
            } catch (e: Exception) {
                Log.e(TAG, "mFullMediaRecorder start error")
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun createNotification() {
        val notification: Notification = NotificationCompat.Builder(
            this, BaseApplication.channelId
        ).setContentTitle("通话录制中").setContentText("通话录制中")
            .setSmallIcon(R.mipmap.ic_launcher).build()

        startForeground(BaseApplication.notificationId, notification)
    }

    private fun getScreenBaseInfo() {
        mScreenWidth = ScreenUtil.getScreenWidth(this)
        mScreenHeight = ScreenUtil.getScreenHeight(this)
        mScreenDensity = ScreenUtil.getDensity(this)
    }

    private fun createMediaProjection(): MediaProjection? {
        if (mResultCode != null && mResultData != null) {
            return (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(
                mResultCode!!, mResultData!!
            )
        }
        return null
    }

    private fun createMediaRecorder(): MediaRecorder? {
        val mFullMediaRecorder = MediaRecorder()
        mFullMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mFullMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mFullMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mFullMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mFullMediaRecorder.setVideoFrameRate(15) // 帧率
        mFullMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mFullMediaRecorder.setVideoSize(mScreenWidth, mScreenHeight)
        mFullMediaRecorder.setVideoEncodingBitRate(2000 * 1000) // 比特率
        val videoOutputFilePath = getFileStoragePath() + "/" + System.currentTimeMillis() + ".mp4"
        videoOutputFile = File(videoOutputFilePath)
        videoOutputFile.let {
            if (!it.exists()) {
                it.createNewFile()
            }
        }
        mFullMediaRecorder.setOutputFile(videoOutputFilePath)
        mFullMediaRecorder.setOnErrorListener(mOnErrorListener)

        try {
            mFullMediaRecorder.prepare()
        } catch (e: IOException) {
            return null
        }
        return mFullMediaRecorder
    }

    private fun getFileStoragePath(): String? {
        val directory = BaseApplication.appContext.externalCacheDir
        return directory?.absolutePath
    }

    private fun createVirtualDisplay(): VirtualDisplay? {
        return mMediaProjection?.createVirtualDisplay(
            TAG,
            mScreenWidth,
            mScreenHeight,
            mScreenDensity!!.toInt(),
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            mFullMediaRecorder?.surface,
            null,
            null
        )
    }

    /**
     * 初始化录音器
     */
    private fun initAudioRecord() {
        if (mMediaProjection == null) {
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        val minBufferSize = AudioRecord.getMinBufferSize(
            mSampleRateInHZ, mChannel, mEncoding
        )
        // 设置应用程序录制系统音频的能力
        val builder = AudioRecord.Builder()
        builder.setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(mSampleRateInHZ) //设置采样率（一般为可选的三个-> 8000Hz 、16000Hz、44100Hz）
                .setChannelMask(mChannel) //音频通道的配置，可选的有-> AudioFormat.CHANNEL_IN_MONO 单声道，CHANNEL_IN_STEREO为双声道，立体声道，选择单声道就行
                .setEncoding(mEncoding).build()
        ) //音频数据的格式，可选的有-> AudioFormat.ENCODING_PCM_8BIT，AudioFormat.ENCODING_PCM_16BIT
            .setBufferSizeInBytes(minBufferSize) //设置最小缓存区域
        val config = AudioPlaybackCaptureConfiguration.Builder(mMediaProjection!!)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA) //设置捕获多媒体音频
            .addMatchingUsage(AudioAttributes.USAGE_GAME) //设置捕获游戏音频
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN) //设置捕获其他未知音频
            .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .addMatchingUsage(AudioAttributes.USAGE_NOTIFICATION)
            .addMatchingUsage(AudioAttributes.USAGE_ASSISTANT)
            .build()
        //将 AudioRecord 设置为录制其他应用播放的音频
        builder.setAudioPlaybackCaptureConfig(config)
        try {
            // TODO: 需申请 Manifest.permission.RECORD_AUDIO 权限
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mAudioRecord = builder.build()
            }
        } catch (e: Exception) {

        }
        //做完准备工作，就可以开始录音了
        startAudioRecord()
    }

    /**
     * 开始录音
     */
    private fun startAudioRecord() {
        //承接音频数据的字节数组
        val mAudioData = ByteArray(320)
        //保存到本地录音文件名
        val tmpName = System.currentTimeMillis().toString()
        //新建文件，承接音频数据
        val tmpFile = File(getFileStoragePath() + "/" + tmpName + ".pcm")
        if (!tmpFile.exists()) {
            tmpFile.createNewFile()
        }
        //新建文件，后面会把音频数据转换为.wav格式，写入到该文件
        audioOutputFile = File(getFileStoragePath() + "/" + tmpName + ".wav")
        audioOutputFile?.let {
            if (!it.exists()) {
                it.createNewFile()
            }
        }
        aacOutputFile = File(getFileStoragePath() + "/" + tmpName + ".aac")
        aacOutputFile.let {
            if (!it.exists()) {
                it.createNewFile()
            }
        }

        val pcmToAacUtil = PcmToAacUtil(aacOutputFile.absolutePath, audioOutputFile!!.absolutePath)

        //开始录音
        mAudioRecord?.startRecording()
        launch {
            try {
                val outputStream = FileOutputStream(tmpFile.absoluteFile)
                while (mIsRecording) {
                    //循环从音频硬件读取音频数据录制到字节数组中
                    mAudioRecord?.read(mAudioData, 0, mAudioData.size)
                    //将字节数组写入到tmpFile文件
                    outputStream.write(mAudioData)
                    pcmToAacUtil.encodeData(mAudioData)
                }
                outputStream.close()
                PcmToWavUtil(mSampleRateInHZ, mChannel, mEncoding).pcmToWav(
                    tmpFile.absolutePath,
                    audioOutputFile?.absolutePath
                )
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mIsRecording = false
        launch {
            try {
                mAudioRecord?.stop()
                mAudioRecord?.release()
                mAudioRecord = null

                mVirtualDisplay?.release()
                mVirtualDisplay = null

                mFullMediaRecorder?.setOnErrorListener(null)
                mFullMediaRecorder?.stop()
                mFullMediaRecorder?.reset()
                mFullMediaRecorder?.release()
                mFullMediaRecorder = null

                mMediaProjection?.stop()
                mMediaProjection = null

                // TODO: 三轨合流暂未实现
                // AudioUtil.syntheticAudio(
                //     getFileStoragePath()!!,
                //     "${System.currentTimeMillis()}_release",
                //     videoOutputFile,
                //     aacOutputFile
                // )
            } catch (e: Exception) {
                Log.e(TAG, "onDestroy error $e")
            }
        }
    }
}