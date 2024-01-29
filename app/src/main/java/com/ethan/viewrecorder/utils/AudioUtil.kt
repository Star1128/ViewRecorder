package com.ethan.viewrecorder.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer


/**
 *
 * @author wangxingchen01 2024/1/16
 */
object AudioUtil {

    const val TIME_OUT = 1000L;
    fun syntheticAudio(
        savePath: String,
        saveName: String,
        videoFile: File,
        audioFile: File,
        videoDuration: Long? = 0,
        audioDuration: Long? = 0
    ) {
        val newFile = File(savePath, "$saveName.mp4")
        if (newFile.exists()) {
            newFile.delete()
        }

        try {
            newFile.createNewFile()
            val videoExtractor = MediaExtractor()
            videoExtractor.setDataSource(videoFile.absolutePath)
            val audioExtractor = MediaExtractor()
            audioExtractor.setDataSource(audioFile.absolutePath)

            val muxer =
                MediaMuxer(newFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            videoExtractor.selectTrack(0)
            val videoFormat = videoExtractor.getTrackFormat(0)
            val videoTrack = muxer.addTrack(videoFormat)

            audioExtractor.selectTrack(0)
            val audioFormat = audioExtractor.getTrackFormat(0)
            val audioTrack = muxer.addTrack(audioFormat)

            var sawEOS = false
            var frameCount = 0
            val offset = 100
            val sampleSize = 1000 * 1024
            val videoBuf = ByteBuffer.allocate(sampleSize)
            val audioBuf = ByteBuffer.allocate(sampleSize)
            val videoBufferInfo = MediaCodec.BufferInfo()
            val audioBufferInfo = MediaCodec.BufferInfo()

            videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            muxer.start()

            // 每秒多少帧
            // 实测 OPPO R9em 垃圾手机，拿出来的没有 MediaFormat.KEY_FRAME_RATE
            val frameRate = if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
            } else {
                15
            }
            // 得出平均每一帧间隔多少微妙
            val videoSampleTime = 1000 * 1000 / frameRate
            while (!sawEOS) {
                videoBufferInfo.offset = offset
                videoBufferInfo.size = videoExtractor.readSampleData(videoBuf, offset)
                if (videoBufferInfo.size < 0) {
                    sawEOS = true
                    videoBufferInfo.size = 0
                } else {
                    videoBufferInfo.presentationTimeUs += videoSampleTime
                    videoBufferInfo.flags = videoExtractor.sampleFlags
                    muxer.writeSampleData(videoTrack, videoBuf, videoBufferInfo)
                    videoExtractor.advance()
                    frameCount++
                }
            }
            var sawEOS2 = false
            var frameCount2 = 0
            while (!sawEOS2) {
                frameCount2++
                audioBufferInfo.offset = offset
                audioBufferInfo.size = audioExtractor.readSampleData(audioBuf, offset)

                if (audioBufferInfo.size < 0) {
                    sawEOS2 = true
                    audioBufferInfo.size = 0
                } else {
                    audioBufferInfo.presentationTimeUs = audioExtractor.sampleTime
                    audioBufferInfo.flags = audioExtractor.sampleFlags
                    muxer.writeSampleData(audioTrack, audioBuf, audioBufferInfo)
                    audioExtractor.advance()
                }
            }
            muxer.stop()
            muxer.release()
            videoExtractor.release()
            audioExtractor.release()

            // 删除无声视频文件
            videoFile.delete()
        } catch (e: Exception) {
            // 视频添加音频合成失败，直接保存视频
            videoFile.renameTo(newFile)
        }
    }

//    private fun mixVideoAndMusic(
//        videoInput: String,
//        output: String,
//        startTimeUs: Int,
//        endTimeUs: Int?,
//        wavFile: File
//    ) {
//        val mediaMuxer = MediaMuxer(output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
//        //先取视频
//        val mediaExtractor = MediaExtractor()
//        mediaExtractor.setDataSource(videoInput)
//        val videoFormat = mediaExtractor.getTrackFormat(0)
//        mediaMuxer.addTrack(videoFormat)
//        val audioFormat = mediaExtractor.getTrackFormat(0)
//        val audioBitrate = audioFormat.getInteger(MediaFormat.KEY_BIT_RATE)
//        audioFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC)
//        val muxerAudioIndex = mediaMuxer.addTrack(audioFormat)
//        mediaMuxer.start()
//
//        //音频的wav
//        val pcmExtractor = MediaExtractor()
//        pcmExtractor.setDataSource(wavFile.absolutePath)
//        pcmExtractor.selectTrack(0)
//        val pcmTrackFormat = pcmExtractor.getTrackFormat(0)
//        var maxBufferSize = 0
//        try {
//            if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
//                maxBufferSize = pcmTrackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
//            }
//        } catch (e: java.lang.Exception) {
//            maxBufferSize = 100 * 1000
//        }
//        val encodeFormat = MediaFormat.createAudioFormat(
//            MediaFormat.MIMETYPE_AUDIO_AAC,
//            16000, 1
//        ) //参数对应-> mime type、采样率、声道数
//        encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate) //比特率
//        encodeFormat.setInteger(
//            MediaFormat.KEY_AAC_PROFILE,
//            MediaCodecInfo.CodecProfileLevel.AACObjectLC
//        ) //音质等级
//        encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxBufferSize)
//        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
//        encoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
//        encoder.start()
//        var buffer = ByteBuffer.allocateDirect(maxBufferSize)
//        val info = MediaCodec.BufferInfo()
//        var encodeDone = false
//        while (!encodeDone) {
//            val inputBufferIndex = encoder.dequeueInputBuffer(10000)
//            if (inputBufferIndex >= 0) {
//                val sampleTime = pcmExtractor.sampleTime
//                if (sampleTime < 0) {
//                    //文件末尾
//                    encoder.queueInputBuffer(
//                        inputBufferIndex,
//                        0,
//                        0,
//                        0,
//                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
//                    )
//                } else {
//                    val flags = pcmExtractor.sampleFlags
//                    val size = pcmExtractor.readSampleData(buffer, 0)
//                    val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
//                    inputBuffer!!.clear()
//                    inputBuffer.put(buffer)
//                    inputBuffer.position(0)
//                    encoder.queueInputBuffer(inputBufferIndex, 0, size, sampleTime, flags)
//                    pcmExtractor.advance()
//                }
//            }
//
//            //获取编码完的数据
//            var outputBufferIndex = encoder.dequeueOutputBuffer(info, TIME_OUT)
//            while (outputBufferIndex >= 0) {
//                if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
//                    encodeDone = true
//                    break
//                }
//                val encodeOutputBuffer = encoder.getOutputBuffer(outputBufferIndex)
//                //将编码好的数据 压缩 aac
//                mediaMuxer.writeSampleData(muxerAudioIndex, encodeOutputBuffer!!, info)
//                encodeOutputBuffer.clear()
//                encoder.releaseOutputBuffer(outputBufferIndex, false)
//                outputBufferIndex = encoder.dequeueOutputBuffer(info, TIME_OUT)
//            }
//        }
//        if (audioTrack >= 0) {
//            mediaExtractor.unselectTrack(audioTrack)
//        }
//
//        //视频
//        mediaExtractor.selectTrack(0)
//        mediaExtractor.seekTo(startTimeUs.toLong(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
//        maxBufferSize = videoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
//        buffer = ByteBuffer.allocateDirect(maxBufferSize)
//        while (true) {
//            val sampleTimeUs = mediaExtractor.sampleTime
//            if (sampleTimeUs == -1L) {
//                break
//            }
//            if (sampleTimeUs < startTimeUs) {
//                mediaExtractor.advance()
//                continue
//            }
//            if (endTimeUs != null && sampleTimeUs > endTimeUs) {
//                break
//            }
//            info.presentationTimeUs = sampleTimeUs - startTimeUs + 600
//            info.flags = mediaExtractor.sampleFlags
//            info.size = mediaExtractor.readSampleData(buffer, 0)
//            if (info.size < 0) {
//                break
//            }
//            mediaMuxer.writeSampleData(0, buffer, info)
//            mediaExtractor.advance()
//        }
//        try {
//            pcmExtractor.release()
//            mediaExtractor.release()
//            encoder.stop()
//            encoder.release()
//            mediaMuxer.release()
//        } catch (e: java.lang.Exception) {
//        }
//    }

}