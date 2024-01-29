package com.ethan.viewrecorder.utils;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import com.ethan.viewrecorder.full.FullScreenRecordService;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author wangxingchen01 2024/1/17
 */
public class PcmToAacUtil {

    private String encodeType = MediaFormat.MIMETYPE_AUDIO_AAC;
    private static final int samples_per_frame = 2048;

    private MediaCodec mediaEncode;
    private MediaCodec.BufferInfo encodeBufferInfo;
    private ByteBuffer[] encodeInputBuffers;
    private ByteBuffer[] encodeOutputBuffers;

    private byte[] chunkAudio = new byte[0];
    private BufferedOutputStream out;
    File aacFile;
    File pcmFile;

    public PcmToAacUtil(String aacPath, String pcmPath) {
        aacFile = new File(aacPath);
        pcmFile = new File(pcmPath);
        if (!aacFile.exists()) {
            try {
                aacFile.getParentFile().mkdirs();
                aacFile.createNewFile();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        try {
            out = new BufferedOutputStream(new FileOutputStream(aacFile, false));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        PCMEncoderAAC(FullScreenRecordService.mSampleRateInHZ, new EncoderListener() {
            @Override
            public void encodeAAC(byte[] data) {
                try {
                    out.write(data);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    /**
     * 初始化AAC编码器
     */
    private void initAACMediaEncode() {
        try {
            //参数对应-> mime type、采样率、声道数
            MediaFormat encodeFormat = MediaFormat.createAudioFormat(encodeType, FullScreenRecordService.mSampleRateInHZ, 1);
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, 64000);//比特率
            encodeFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            encodeFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);
            encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, samples_per_frame);//作用于inputBuffer的大小
            mediaEncode = MediaCodec.createEncoderByType(encodeType);
            mediaEncode.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mediaEncode == null) {
            return;
        }
        mediaEncode.start();
        encodeInputBuffers = mediaEncode.getInputBuffers();
        encodeOutputBuffers = mediaEncode.getOutputBuffers();
        encodeBufferInfo = new MediaCodec.BufferInfo();
    }

    /**
     * 编码PCM数据 得到AAC格式的音频文件
     */
    private void dstAudioFormatFromPCM(byte[] pcmData) {

        int inputIndex;
        ByteBuffer inputBuffer;
        int outputIndex;
        ByteBuffer outputBuffer;

        int outBitSize;
        int outPacketSize;
        byte[] PCMAudio;
        PCMAudio = pcmData;

        encodeInputBuffers = mediaEncode.getInputBuffers();
        encodeOutputBuffers = mediaEncode.getOutputBuffers();
        encodeBufferInfo = new MediaCodec.BufferInfo();

        inputIndex = mediaEncode.dequeueInputBuffer(0);
        if (inputIndex != -1) {
            inputBuffer = encodeInputBuffers[inputIndex];
            inputBuffer.clear();
            inputBuffer.limit(PCMAudio.length);
            inputBuffer.put(PCMAudio);//PCM数据填充给inputBuffer
            mediaEncode.queueInputBuffer(inputIndex, 0, PCMAudio.length, 0, 0);//通知编码器 编码

            outputIndex = mediaEncode.dequeueOutputBuffer(encodeBufferInfo, 0);
            while (outputIndex > 0) {
                outBitSize = encodeBufferInfo.size;
                outPacketSize = outBitSize + 7;//7为ADT头部的大小
                outputBuffer = encodeOutputBuffers[outputIndex];//拿到输出Buffer
                outputBuffer.position(encodeBufferInfo.offset);
                outputBuffer.limit(encodeBufferInfo.offset + outBitSize);
                chunkAudio = new byte[outPacketSize];
                addADTStoPacket(chunkAudio, outPacketSize);//添加ADTS
                outputBuffer.get(chunkAudio, 7, outBitSize);//将编码得到的AAC数据 取出到byte[]中

                try {
                    //录制aac音频文件，保存在手机内存中
                    out.write(chunkAudio, 0, chunkAudio.length);
                    out.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                outputBuffer.position(encodeBufferInfo.offset);
                mediaEncode.releaseOutputBuffer(outputIndex, false);
                outputIndex = mediaEncode.dequeueOutputBuffer(encodeBufferInfo, 0);
            }
        }
    }

    /**
     * 添加ADTS头
     */
    //    private void addADTStoPacket(byte[] packet, int packetLen) {
    //        int profile = 2; // AAC LC
    //        int freqIdx = 8; // 16KHz
    //        int chanCfg = 1; // CPE
    //
    //        // fill in ADTS data
    //        packet[0] = (byte) 0xFF;
    //        packet[1] = (byte) 0xF1;
    //        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
    //        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
    //        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
    //        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
    //        packet[6] = (byte) 0xFC;
    //    }

    //比特率
    private final static int KEY_BIT_RATE = 96000;
    //读取数据的最大字节数
    private final static int KEY_MAX_INPUT_SIZE = 1024 * 1024;
    //声道数
    private final static int CHANNEL_COUNT = 1;
    private MediaCodec mediaCodec;
    private EncoderListener encoderListener;

    public void PCMEncoderAAC(int sampleRate, EncoderListener encoderListener) {
        this.encoderListener = encoderListener;
        init(sampleRate);
    }

    /**
     * 初始化AAC编码器
     */
    private void init(int sampleRate) {
        try {
            //参数对应-> mime type、采样率、声道数
            MediaFormat encodeFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                    sampleRate, CHANNEL_COUNT);
            //比特率
            encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, KEY_BIT_RATE);
            encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, KEY_MAX_INPUT_SIZE);
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            mediaCodec.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        } catch (IOException e) {
            e.printStackTrace();
        }

        mediaCodec.start();
        encodeInputBuffers = mediaCodec.getInputBuffers();
        encodeOutputBuffers = mediaCodec.getOutputBuffers();
        encodeBufferInfo = new MediaCodec.BufferInfo();
    }

    /**
     * @param data
     */
    public void encodeData(byte[] data) {
        //dequeueInputBuffer（time）需要传入一个时间值，-1表示一直等待，0表示不等待有可能会丢帧，其他表示等待多少毫秒
        //获取输入缓存的index
        int inputIndex = mediaCodec.dequeueInputBuffer(-1);
        if (inputIndex >= 0) {
            ByteBuffer inputByteBuf = encodeInputBuffers[inputIndex];
            inputByteBuf.clear();
            //添加数据
            inputByteBuf.put(data);
            //限制ByteBuffer的访问长度
            inputByteBuf.limit(data.length);
            //把输入缓存塞回去给MediaCodec
            mediaCodec.queueInputBuffer(inputIndex, 0, data.length, 0, 0);
        }
        //获取输出缓存的index
        int outputIndex = mediaCodec.dequeueOutputBuffer(encodeBufferInfo, 0);
        while (outputIndex >= 0) {
            //获取缓存信息的长度
            int byteBufSize = encodeBufferInfo.size;
            //添加ADTS头部后的长度
            int bytePacketSize = byteBufSize + 7;
            //拿到输出Buffer
            ByteBuffer outPutBuf = encodeOutputBuffers[outputIndex];
            outPutBuf.position(encodeBufferInfo.offset);
            outPutBuf.limit(encodeBufferInfo.offset + encodeBufferInfo.size);

            byte[] aacData = new byte[bytePacketSize];
            //添加ADTS头部
            addADTStoPacket(aacData, bytePacketSize);
            /*
            get（byte[] dst,int offset,int length）:ByteBuffer从position位置开始读，读取length个byte，并写入dst下
            标从offset到offset + length的区域
             */
            outPutBuf.get(aacData, 7, byteBufSize);
            outPutBuf.position(encodeBufferInfo.offset);

            //编码成功
            if (encoderListener != null) {
                encoderListener.encodeAAC(aacData);
            }

            //释放
            mediaCodec.releaseOutputBuffer(outputIndex, false);
            outputIndex = mediaCodec.dequeueOutputBuffer(encodeBufferInfo, 0);
        }
    }

    /**
     * 添加ADTS头
     */
    private void addADTStoPacket(byte[] packet, int packetLen) {
        // AAC LC
        int profile = 2;
        // 44.1KHz
        int freqIdx = 4;
        // CPE
        int chanCfg = 2;
        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }

    public interface EncoderListener {
        void encodeAAC(byte[] data);
    }


}