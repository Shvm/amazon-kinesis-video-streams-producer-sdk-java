package com.amazonaws.kinesisvideo.java.mediasource.camera;

import com.amazonaws.kinesisvideo.client.mediasource.MediaSourceState;
import com.amazonaws.kinesisvideo.client.mediasource.CameraMediaSourceConfiguration;
import com.amazonaws.kinesisvideo.common.exception.KinesisVideoException;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceConfiguration;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSource;
import com.amazonaws.kinesisvideo.internal.client.mediasource.MediaSourceSink;
import com.amazonaws.kinesisvideo.mediasource.OnFrameDataAvailable;
import com.amazonaws.kinesisvideo.producer.KinesisVideoFrame;
import com.amazonaws.kinesisvideo.producer.StreamCallbacks;
import com.amazonaws.kinesisvideo.producer.StreamInfo;
import com.amazonaws.kinesisvideo.producer.Tag;
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Dimension;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import static com.amazonaws.kinesisvideo.producer.StreamInfo.NalAdaptationFlags.NAL_ADAPTATION_FLAG_NONE;
import static com.amazonaws.kinesisvideo.util.StreamInfoConstants.*;

public class CameraMediaSource implements MediaSource {

    private static final int FRAME_FLAG_KEY_FRAME = 1;
    private static final int FRAME_FLAG_NONE = 0;
    private static final long HUNDREDS_OF_NANOS_IN_MS = 10 * 1000;
    private static final long FRAME_DURATION_20_MS = 20L;

    private static final byte[] AVCC_EXTRA_DATA = {
            (byte) 0x01, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0xFF, (byte) 0xE1, (byte) 0x00, (byte) 0x22,
            (byte) 0x27, (byte) 0x42, (byte) 0x00, (byte) 0x1E, (byte) 0x89, (byte) 0x8B, (byte) 0x60, (byte) 0x50,
            (byte) 0x1E, (byte) 0xD8, (byte) 0x08, (byte) 0x80, (byte) 0x00, (byte) 0x13, (byte) 0x88,
            (byte) 0x00, (byte) 0x03, (byte) 0xD0, (byte) 0x90, (byte) 0x70, (byte) 0x30, (byte) 0x00, (byte) 0x5D,
            (byte) 0xC0, (byte) 0x00, (byte) 0x17, (byte) 0x70, (byte) 0x5E, (byte) 0xF7, (byte) 0xC1, (byte) 0xF0,
            (byte) 0x88, (byte) 0x46, (byte) 0xE0, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x28, (byte) 0xCE,
            (byte) 0x1F, (byte) 0x20};

    private CameraMediaSourceConfiguration cameraMediaSourceConfiguration;
    private MediaSourceState mediaSourceState;
    private MediaSourceSink mediaSourceSink;
    private CameraFrameSource cameraFrameSource;
    private int frameIndex;
    private CompletableFuture<Boolean> future;
    private final String streamName;
    Webcam webcam = null;

    public CameraMediaSource(@Nonnull final String streamName, CompletableFuture<Boolean> future) {
        this.streamName = streamName;
        this.future = future;
    }

    public CameraMediaSource(@Nonnull final String streamName) {
        this(streamName, new CompletableFuture<Boolean>());
    }

    @Override
    public StreamInfo getStreamInfo() throws KinesisVideoException {
        return new StreamInfo(VERSION_ZERO,
                streamName,
                StreamInfo.StreamingType.STREAMING_TYPE_REALTIME,
                VIDEO_CONTENT_TYPE,
                NO_KMS_KEY_ID,
                RETENTION_ONE_HOUR,
                NOT_ADAPTIVE,
                MAX_LATENCY_ZERO,
                DEFAULT_GOP_DURATION,
                KEYFRAME_FRAGMENTATION,
                USE_FRAME_TIMECODES,
                RELATIVE_TIMECODES,
                REQUEST_FRAGMENT_ACKS,
                RECOVER_ON_FAILURE,
                VIDEO_CODEC_ID,
                "test-track",
                DEFAULT_BITRATE,
                cameraMediaSourceConfiguration.getFrameRate(),
                DEFAULT_BUFFER_DURATION,
                DEFAULT_REPLAY_DURATION,
                DEFAULT_STALENESS_DURATION,
                DEFAULT_TIMESCALE,
                RECALCULATE_METRICS,
                AVCC_EXTRA_DATA,
                new Tag[] {
                        new Tag("device", "Test Device"),
                        new Tag("stream", "Test Stream") },
                NAL_ADAPTATION_FLAG_NONE);
    }

    @Override
    public MediaSourceSink getMediaSourceSink() {
        return mediaSourceSink;
    }

    @Nullable
    @Override
    public StreamCallbacks getStreamCallbacks() {
        return null;
    }

    public void setupWebCam(Webcam webcam) {
    	this.webcam = webcam;
    	Dimension size = WebcamResolution.VGA.getSize();
        webcam.setViewSize(size);
        webcam.open(true);
    }

	@Override
	public MediaSourceState getMediaSourceState() {
		// TODO Auto-generated method stub
		return mediaSourceState;
	}

	@Override
	public MediaSourceConfiguration getConfiguration() {
		// TODO Auto-generated method stub
		return cameraMediaSourceConfiguration;
	}

	@Override
	public void initialize(MediaSourceSink mediaSourceSink) throws KinesisVideoException {
		// TODO Auto-generated method stub
		this.mediaSourceSink = mediaSourceSink;
	}

	@Override
	public void configure(MediaSourceConfiguration configuration) {
		// TODO Auto-generated method stub
		
		if (!(configuration instanceof CameraMediaSourceConfiguration)) {
            throw new IllegalStateException("Configuration must be an instance of OpenCvMediaSourceConfiguration");
        }
        this.cameraMediaSourceConfiguration = (CameraMediaSourceConfiguration) configuration;
        this.frameIndex = 0;
		
	}

	@Override
	public void start() throws KinesisVideoException {
		// TODO Auto-generated method stub
		mediaSourceState = MediaSourceState.RUNNING;
		cameraFrameSource = new CameraFrameSource(cameraMediaSourceConfiguration, webcam);
		cameraFrameSource.onBytesAvailable(createKinesisVideoFrameAndPushToProducer());
		cameraFrameSource.start();
		
	}

	private OnFrameDataAvailable createKinesisVideoFrameAndPushToProducer() {
		// TODO Auto-generated method stub
        return new OnFrameDataAvailable() {
            @Override
            public void onFrameDataAvailable(final ByteBuffer data) {
                final long currentTimeMs = System.currentTimeMillis();

                final int flags = isKeyFrame() 
                		? FRAME_FLAG_KEY_FRAME
                		: FRAME_FLAG_NONE;

                if (data != null) {
                    final KinesisVideoFrame frame = new KinesisVideoFrame(
                            frameIndex++,
                            flags,
                            currentTimeMs * HUNDREDS_OF_NANOS_IN_MS,
                            currentTimeMs * HUNDREDS_OF_NANOS_IN_MS,
                            FRAME_DURATION_20_MS * HUNDREDS_OF_NANOS_IN_MS,
                            data);

                    if (frame.getSize() == 0) {
                        return;
                    }

                    putFrame(frame);
                    
                } else {
                	System.out.println("Data not received from frame");
                }

            }
        };
	}
	
    private void putFrame(final KinesisVideoFrame kinesisVideoFrame) {
        try {
            mediaSourceSink.onFrame(kinesisVideoFrame);
        } catch (final KinesisVideoException ex) {
            throw new RuntimeException(ex);
        }
    }

    
    private boolean isKeyFrame() {
        return frameIndex % 19 == 0;
    }
    
	@Override
	public void stop() throws KinesisVideoException {
		// TODO Auto-generated method stub
        if (cameraFrameSource != null) {
        	cameraFrameSource.stop();
        }

        mediaSourceState = MediaSourceState.STOPPED;
        webcam.close();
		
	}

	@Override
	public boolean isStopped() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void free() throws KinesisVideoException {
		// TODO Auto-generated method stub
		
	}

}
