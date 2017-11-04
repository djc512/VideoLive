package com.library.vd;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.library.stream.BaseRecive;
import com.library.util.WriteMp4;
import com.library.util.data.ByteTurn;
import com.library.util.data.Value;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class VDDecoder implements SurfaceHolder.Callback, VideoInformationInterface {
    public static final String H264 = MediaFormat.MIMETYPE_VIDEO_AVC;
    public static final String H265 = MediaFormat.MIMETYPE_VIDEO_HEVC;
    //解码格式
    private String MIME_TYPE = H264;

    //解码分辨率
    private SurfaceHolder holder;
    //解码器
    private MediaCodec mCodec;
    private MediaFormat mediaFormat = null;
    //是否播放
    private boolean isdecoder = false;
    //控制标志
    private boolean isdestroyed = false;
    //销毁标志
    private boolean star = true;
    private final static int TIME_INTERNAL = 5;

    private BaseRecive baseRecive;
    //解码器配置信息
    private byte[] information = null;

    private WriteMp4 writeMp4;

    /**
     * 初始化解码器
     */
    public VDDecoder(SurfaceView surfaceView, String codetype, BaseRecive baseRecive, WriteMp4 writeMp4) {
        this.holder = surfaceView.getHolder();
        this.writeMp4 = writeMp4;
        MIME_TYPE = codetype;
        try {
            //根据需要解码的类型创建解码器
            mCodec = MediaCodec.createDecoderByType(MIME_TYPE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        holder.addCallback(this);
        // 设置该组件让屏幕不会自动关闭
        holder.setKeepScreenOn(true);
        this.baseRecive = baseRecive;
        baseRecive.setInformaitonInterface(this);
        startCodec();
    }

    /*
    回调包含解码器配置信息的byte，比如h264的sps,pps等（后面还包部分视频数据）
     */
    @Override
    public void Information(byte[] important) {
        if (information == null) {
            information = important;
            if (mediaFormat == null) {
                beginCodec();
            }
        } else if (!Arrays.equals(important, information)) {
            information = important;
        }
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (information != null) {
            beginCodec();
        }
    }

    private void beginCodec() {
        //初始化MediaFormat
        if (mediaFormat == null) {
            mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, 0, 0);//分辨率等信息由sps提供，这里可以随便设置
        }
        if (MIME_TYPE.equals(H264)) {
            mediaFormat.setByteBuffer("csd-0", getH264SPS());
            mediaFormat.setByteBuffer("csd-1", getH264PPS());

        } else if (MIME_TYPE.equals(H265)) {
            mediaFormat.setByteBuffer("csd-0", getH265information());
        }

        writeMp4.addTrack(mediaFormat, WriteMp4.video);

        //配置MediaFormat以及需要显示的surface
        mCodec.configure(mediaFormat, holder.getSurface(), null, 0);
        mCodec.start();
        isdestroyed = true;
    }

    private ByteBuffer getH264SPS() {
        for (int i = 5; i < information.length; i++) {
            if (information[i] == (byte) 0x00
                    && information[i + 1] == (byte) 0x00
                    && information[i + 2] == (byte) 0x00
                    && information[i + 3] == (byte) 0x01
                    && information[i + 4] == (byte) 0x68) {
                byte[] bytes = new byte[i];
                System.arraycopy(information, 0, bytes, 0, i);
                Log.d("VDDecoder_information", "h264 sps" + ByteTurn.byte_to_16(bytes));
                return ByteBuffer.wrap(bytes);
            }
        }
        return null;
    }

    private ByteBuffer getH264PPS() {
        for (int i = 5; i < information.length; i++) {
            if (information[i] == (byte) 0x00
                    && information[i + 1] == (byte) 0x00
                    && information[i + 2] == (byte) 0x00
                    && information[i + 3] == (byte) 0x01
                    && information[i + 4] == (byte) 0x68) {
                for (int j = i + 5; j < information.length; j++) {
                    if (information[j] == (byte) 0x00
                            && information[j + 1] == (byte) 0x00
                            && information[j + 2] == (byte) 0x00
                            && information[j + 3] == (byte) 0x01
                            && information[j + 4] == (byte) 0x65) {
                        byte[] bytes = new byte[j - i];
                        System.arraycopy(information, i, bytes, 0, j - i);
                        Log.d("VDDecoder_information", "h264 pps" + ByteTurn.byte_to_16(bytes));
                        return ByteBuffer.wrap(bytes);
                    }
                }
            }
        }
        return null;
    }

    private ByteBuffer getH265information() {
        for (int i = 5; i < information.length; i++) {
            if (information[i] == (byte) 0x00
                    && information[i + 1] == (byte) 0x00
                    && information[i + 2] == (byte) 0x00
                    && information[i + 3] == (byte) 0x01
                    && information[i + 4] == (byte) 0x26) {
                byte[] bytes = new byte[i];
                System.arraycopy(information, 0, bytes, 0, i);
                Log.d("VDDecoder_information", "h265信息" + ByteTurn.byte_to_16(bytes));
                return ByteBuffer.wrap(bytes);
            }
        }
        return null;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isdestroyed = false;
        mCodec.stop();
    }

    public void star() {
        isdecoder = true;
    }

    /**
     * 停止解码
     */
    public void stop() {
        isdecoder = false;
    }

    public void destroy() {
        star = false;
        isdestroyed = false;
        isdecoder = false;
        mCodec.stop();
        mCodec.release();
        mCodec = null;
    }


    private void startCodec() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] poll;
                while (star) {
                    poll = baseRecive.getVideo();
                    if (poll != null) {
                        //写文件
                        writeFile(poll, poll.length);
                        if (isdecoder && isdestroyed) {
                            onFrame(poll, poll.length);
                        }
                    } else {
                        try {
                            Thread.sleep(Value.sleepTime);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }).start();
    }


    int mCount = 0;

    private boolean onFrame(byte[] buf, int length) {
        // 获取输入buffer index
        ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
        //-1表示一直等待；0表示不等待；其他大于0的参数表示等待毫秒数
        int inputBufferIndex = mCodec.dequeueInputBuffer(50);
        if (inputBufferIndex >= 0) {
            ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
            //清空buffer
            inputBuffer.clear();
            //put需要解码的数据
            inputBuffer.put(buf, 0, length);
            //解码
            mCodec.queueInputBuffer(inputBufferIndex, 0, length, mCount * TIME_INTERNAL, 0);
            mCount++;

        } else {
            return false;
        }
        // 获取输出buffer index
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 50);

        //循环解码，直到数据全部解码完成
        while (outputBufferIndex >= 0) {
            //logger.d("outputBufferIndex = " + outputBufferIndex);
            //true : 将解码的数据显示到surface上
            mCodec.releaseOutputBuffer(outputBufferIndex, true);
            outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 0);
        }
        return true;
    }

    private MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

    /*
    写入文件
     */
    private void writeFile(byte[] output, int length) {
        bufferInfo.offset = 0;
        bufferInfo.presentationTimeUs = Value.getFPS();
        if (MIME_TYPE.equals(H264)) {
//        AVC 00 00 00 01 67 42 80 15 da 05 03 da 52 0a 04 04 0d a1 42 6a 00 00 00 01 68 ce 06 e2后面00 00 00 01 65为帧数据开始，普通帧为41
            if (output[4] == (byte) 0x67) {//KEY
                for (int i = 5; i < length; i++) {
                    if (output[i] == (byte) 0x00
                            && output[i + 1] == (byte) 0x00
                            && output[i + 2] == (byte) 0x00
                            && output[i + 3] == (byte) 0x01
                            && output[i + 4] == (byte) 0x65) {
                        byte[] iframe = new byte[length - i];
                        System.arraycopy(output, i, iframe, 0, iframe.length);
                        bufferInfo.size = iframe.length;
                        bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                        writeMp4.write(WriteMp4.video, ByteBuffer.wrap(iframe), bufferInfo);
                        break;
                    }
                }
            } else {//NO KEY
                bufferInfo.size = length;
                bufferInfo.flags = MediaCodec.CRYPTO_MODE_UNENCRYPTED;
                writeMp4.write(WriteMp4.video, ByteBuffer.wrap(output), bufferInfo);
            }
        } else if (MIME_TYPE.equals(H265)) {
//        HEVC[00 00 00 01 40 01 0c 01 ff ff 01 60 00 00 03 00 b0 00 00 03 00 00 03 00 3f ac 59 00 00 00 01 42 01 01 01 60 00 00 03 00 b0 00 00 03 00 00 03 00 3f a0 0a 08 07 85 96 bb 93 24 bb 94 82 81 01 01 76 85 09 40 00 00 00 01 44 01 c0 f1 80 04 20]后面00 00 00 01 26为帧数据开始，普通帧为00 00 00 01 02            if (output[4] == (byte) 0x40) {
            if (output[4] == (byte) 0x40) {//KEY
                for (int i = 5; i < length; i++) {
                    if (output[i] == (byte) 0x00
                            && output[i + 1] == (byte) 0x00
                            && output[i + 2] == (byte) 0x00
                            && output[i + 3] == (byte) 0x01
                            && output[i + 4] == (byte) 0x26) {
                        byte[] iframe = new byte[length - i];
                        System.arraycopy(output, i, iframe, 0, iframe.length);
                        bufferInfo.size = iframe.length;
                        bufferInfo.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                        writeMp4.write(WriteMp4.video, ByteBuffer.wrap(iframe), bufferInfo);
                        break;
                    }
                }
            } else {//NO KEY
                bufferInfo.size = length;
                bufferInfo.flags = MediaCodec.CRYPTO_MODE_UNENCRYPTED;
                writeMp4.write(WriteMp4.video, ByteBuffer.wrap(output), bufferInfo);
            }
        }
    }
}