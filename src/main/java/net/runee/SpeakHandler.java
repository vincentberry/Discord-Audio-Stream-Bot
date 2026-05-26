package net.runee;

import jouvieje.bass.Bass;
import jouvieje.bass.defines.BASS_RECORD;
import jouvieje.bass.defines.BASS_STREAM;
import jouvieje.bass.structures.HRECORD;
import jouvieje.bass.utils.Pointer;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.runee.errors.BassException;
import net.runee.misc.MemoryQueue;
import net.runee.misc.Utils;
import net.runee.model.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static jouvieje.bass.defines.BASS_ACTIVE.*;
import static jouvieje.bass.defines.BASS_ERROR.BASS_ERROR_HANDLE;

public class SpeakHandler implements AudioSendHandler, Closeable {
    private static final Logger logger = LoggerFactory.getLogger(SpeakHandler.class);
    public static final int FRAME_MILLIS = 20;
    public static final int MAX_LAG = 200;
    private static final Object activeHandlersLock = new Object();
    private static final List<SpeakHandler> activeHandlers = new ArrayList<>();
    private static final Map<Integer, RecordingResource> recordingResources = new HashMap<>();

    private static class RecordingResource {
        private final int device;
        private final HRECORD stream;
        private int refCount;

        private RecordingResource(int device, HRECORD stream) {
            this.device = device;
            this.stream = stream;
        }
    }

    private final Object memoryQueueLock = new Object();
    private int recordingDevice;
    private RecordingResource recordingResource;
    private HRECORD recordingStream;
    private MemoryQueue memoryQueue;
    private byte[] buffer;
    private volatile boolean closed;
    private volatile boolean playing;

    public SpeakHandler() {
        this.recordingDevice = -1;
        this.closed = true;
        this.buffer = new byte[INPUT_FORMAT.getChannels() * (int) (INPUT_FORMAT.getSampleRate() * (FRAME_MILLIS / 1000f)) * (INPUT_FORMAT.getSampleSizeInBits() / 8)];
    }

    public void openRecordingDevice(int recordingDevice, boolean setPlaying) throws BassException {
        Utils.closeQuiet(this);

        synchronized (activeHandlersLock) {
            this.recordingDevice = recordingDevice;
            this.memoryQueue = new MemoryQueue();

            RecordingResource resource = recordingResources.get(recordingDevice);
            if (resource == null) {
                try {
                    if (!Bass.BASS_RecordInit(recordingDevice)) {
                        Utils.checkBassError();
                    }
                    int flags = BASS_STREAM.BASS_STREAM_AUTOFREE | BASS_RECORD.BASS_RECORD_PAUSE;
                    HRECORD stream = Bass.BASS_RecordStart((int) INPUT_FORMAT.getSampleRate(), INPUT_FORMAT.getChannels(), flags, SpeakHandler::RECORDPROC, null);
                    Utils.checkBassError();

                    resource = new RecordingResource(recordingDevice, stream);
                    recordingResources.put(recordingDevice, resource);
                } catch (BassException ex) {
                    memoryQueue = null;
                    this.recordingDevice = -1;
                    Bass.BASS_RecordSetDevice(recordingDevice);
                    Bass.BASS_RecordFree();
                    throw ex;
                }
            }

            resource.refCount++;
            this.recordingResource = resource;
            this.recordingStream = resource.stream;
            this.closed = false;
            this.playing = setPlaying;
            activeHandlers.add(this);
            updateSharedPlayingState(resource);
        }
    }

    public void setPlaying(boolean playing) throws BassException {
        try {
            synchronized (activeHandlersLock) {
                if (recordingResource == null || recordingStream == null || closed) {
                    return;
                }
                this.playing = playing;
                updateSharedPlayingState(recordingResource);
            }
        } catch (BassException ex) {
            if(ex.getError() == BASS_ERROR_HANDLE) {
                logger.warn("Workaround: Restarting recording stream", ex);
                openRecordingDevice(recordingDevice, playing);
            } else {
                throw ex;
            }
        }
    }

    public float getLag() {
        synchronized (memoryQueueLock) {
            return memoryQueue != null ? (memoryQueue.size() / (float) buffer.length) * FRAME_MILLIS : 0;
        }
    }

    private static boolean RECORDPROC(HRECORD handle, ByteBuffer buffer, int length, Pointer user) {
        try {
            Config cfg = DiscordAudioStreamBot.getConfig();
            List<SpeakHandler> handlers = getActiveHandlers(handle);
            byte[] sampleBuffer = new byte[2];
            int numSamplesToWrite = length / sampleBuffer.length;
            for (int s = 0; s < numSamplesToWrite; s++) {
                short sample = buffer.getShort();
                if(cfg.getSpeakThresholdEnabled() && Math.abs((double)sample) <= cfg.getSpeakThreshold() * Short.MAX_VALUE) {
                    sample = 0;
                }
                sampleBuffer[0] = (byte) ((sample >> 8) & 0xff);
                sampleBuffer[1] = (byte) (sample & 0xff);
                for (SpeakHandler handler : handlers) {
                    synchronized (handler.memoryQueueLock) {
                        if (!handler.closed && handler.memoryQueue != null) {
                            handler.memoryQueue.enqueue(sampleBuffer, 0, sampleBuffer.length);
                        }
                    }
                }
            }
        } catch (Throwable ex) {
            logger.error("Recording callback failed", ex);
        }
        return true;
    }

    private static List<SpeakHandler> getActiveHandlers(HRECORD handle) {
        List<SpeakHandler> matchingHandlers = new ArrayList<>();
        synchronized (activeHandlersLock) {
            for (SpeakHandler handler : activeHandlers) {
                if (!handler.closed && handle.equals(handler.recordingStream)) {
                    matchingHandlers.add(handler);
                }
            }
        }
        return matchingHandlers;
    }

    private static void updateSharedPlayingState(RecordingResource resource) throws BassException {
        boolean shouldPlay = false;
        for (SpeakHandler handler : activeHandlers) {
            if (!handler.closed && handler.recordingResource == resource && handler.playing) {
                shouldPlay = true;
                break;
            }
        }

        switch (Bass.BASS_ChannelIsActive(resource.stream.asInt())) {
            case BASS_ACTIVE_PLAYING:
            case BASS_ACTIVE_STALLED:
                if (!shouldPlay) {
                    Bass.BASS_ChannelPause(resource.stream.asInt());
                }
                break;
            case BASS_ACTIVE_PAUSED:
            case BASS_ACTIVE_STOPPED:
                if (shouldPlay) {
                    Bass.BASS_ChannelPlay(resource.stream.asInt(), false);
                }
                break;
            default:
                break;
        }
        Utils.checkBassError();
    }

    @Override
    public boolean canProvide() {
        synchronized (memoryQueueLock) {
            if (closed || memoryQueue == null) {
                return false;
            }
        }
        float lag = getLag();
        if (lag >= MAX_LAG) {
            synchronized (memoryQueueLock) {
                if (memoryQueue != null) {
                    memoryQueue.clear();
                }
            }
            logger.warn("SpeakHandler is " + (int)lag + " ms behind! Clearing queue...");
        }
        synchronized (memoryQueueLock) {
            return memoryQueue != null && memoryQueue.size() >= buffer.length;
        }
    }

    @Nullable
    @Override
    public ByteBuffer provide20MsAudio() {
        int numBytesRead;
        Arrays.fill(buffer, (byte) 0);
        synchronized (memoryQueueLock) {
            if (closed || memoryQueue == null) {
                return null;
            }
            numBytesRead = memoryQueue.dequeue(buffer, 0, buffer.length);
        }
        return ByteBuffer.wrap(buffer, 0, buffer.length);
    }

    @Override
    public boolean isOpus() {
        return false;
    }

    @Override
    public void close() throws IOException {
        RecordingResource resourceToClose = null;
        synchronized (activeHandlersLock) {
            if (closed && recordingResource == null) {
                return;
            }
            activeHandlers.remove(this);
            RecordingResource resource = recordingResource;
            if (resource != null) {
                resource.refCount--;
                if (resource.refCount <= 0) {
                    recordingResources.remove(resource.device);
                    resourceToClose = resource;
                } else {
                    try {
                        updateSharedPlayingState(resource);
                    } catch (BassException ex) {
                        logger.warn("Failed to update shared recording state while closing", ex);
                    }
                }
            }
            closed = true;
            playing = false;
            memoryQueue = null;
            recordingStream = null;
            recordingResource = null;
            recordingDevice = -1;
        }

        if (resourceToClose != null) {
            Bass.BASS_ChannelStop(resourceToClose.stream.asInt());
            Bass.BASS_RecordSetDevice(resourceToClose.device);
            Bass.BASS_RecordFree();
            Utils.checkBassError();
        }
    }
}
