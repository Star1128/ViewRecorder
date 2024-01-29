package com.ethan.viewrecorder.full

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ethan.viewrecorder.BaseApplication
import com.ethan.viewrecorder.R

class FullScreenRecordActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_PROJECTION = 1
    }

    private var mFullRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_full_screen_record)
    }

    fun onSelectedFullRecord(isStart: Boolean) {
        mFullRecording = isStart
        if (isStart) {
            // 每次开启都要申请录屏服务系统权限
            val mediaProjectionManager: MediaProjectionManager =
                BaseApplication.appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent: Intent? = mediaProjectionManager.createScreenCaptureIntent()
            if (captureIntent != null) {
                startActivityForResult(
                    captureIntent,
                    REQUEST_CODE_PROJECTION,
                    null
                )
            }
        } else {
            stopService(Intent(this, FullScreenRecordService::class.java))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                val service = Intent(this, FullScreenRecordService::class.java)
                service.putExtra(FullScreenRecordService.RESULT_CODE, resultCode)
                service.putExtra(FullScreenRecordService.RESULT_DATA, data)
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                    startForegroundService(service)
                } else {
                    startService(service)
                }
                mFullRecording = true
            } else {
                mFullRecording = false
            }
        }
    }
}