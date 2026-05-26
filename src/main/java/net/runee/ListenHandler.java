package net.runee;

import jouvieje.bass.Bass;
import jouvieje.bass.structures.HSTREAM;
import jouvieje.bass.utils.Pointer;
import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.CombinedAudio;
import net.runee.errors.BassException;
import net.runee.misc.MemoryQueue;
import net.runee.misc.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.dv8tion.jda.api.audio.AudioSendHandler.INPUT_FORMAT;

public class ListenHandler implements AudioReceiveHandler, Closeable {
    private static final Logger logger = LoggerFactory.getLogger(ListenHandler.class);

    public static final int MAX_LAG = 200;
    public static final int PLAYBACK_FLAGS = 0; //BASS_DEVICE.BASS_DEVICE_3D;
    private static final Object activeHandlersLock = new Object();
    private static final List<ListenHandler> activeHandlers = new ArrayList<>();
    private static final Map<Integer, PlaybackResource> playbackResources = new HashMap<>();

    private static class PlaybackResource {
        private final int device;
        private final HSTREAM stream;
        private int refCount;

        private PlaybackResource(int device, HSTREAM stream) {
            this.device = device;
            this.stream = stream;
        }
    }

    private final Object memoryQueueLock = new Object();
    private int playbackDevice;
    private PlaybackResource playbackResource;
    private HSTREAM playbackStream;
    private MemoryQueue memoryQueue;
    private volatile boolean closed;

    public ListenHandler() {
        this.playbackDevice = -1;
    }

    public void openPlaybackDevice(int playbackDevice) throws BassException {
        Utils.closeQuiet(this);

        synchronized (activeHandlersLock) {
            this.playbackDevice = playbackDevice;
            this.memoryQueue = new MemoryQueue();

            PlaybackResource resource = playbackResources.get(playbackDevice);
            if (resource == null) {
                try {
                    if (!Bass.BASS_Init(playbackDevice, (int) OUTPUT_FORMAT.getSampleRate(), PLAYBACK_FLAGS, null, null)) {
                        Utils.checkBassError();
                    }
                    HSTREAM stream = Bass.BASS_StreamCreate((int) OUTPUT_FORMAT.getSampleRate(), OUTPUT_FORMAT.getChannels(), PLAYBACK_FLAGS, ListenHandler::STREAMPROC, null);
                    Utils.checkBassError();
                    Bass.BASS_ChannelPlay(stream.asInt(), false);
                    Utils.checkBassError();

                    resource = new PlaybackResource(playbackDevice, stream);
                    playbackResources.put(playbackDevice, resource);
                } catch (BassException ex) {
                    memoryQueue = null;
                    this.playbackDevice = -1;
                    Bass.BASS_SetDevice(playbackDevice);
                    Bass.BASS_Free();
                    throw ex;
                }
            }

            resource.refCount++;
            this.playbackResource = resource;
            this.playbackStream = resource.stream;
            this.closed = false;
            activeHandlers.add(this);
        }
    }

    @Override
    public boolean canReceiveCombined() {
        return !closed;
    }

    @Override
    public void handleCombinedAudio(@Nonnull CombinedAudio combinedAudio) {
        if (closed) {
            return;
        }
        byte[] data = combinedAudio.getAudioData(1);
        synchronized (memoryQueueLock) {
            if (!closed && memoryQueue != null) {
                memoryQueue.enqueue(data, 0, data.length);
            }
        }
    }

    private static int STREAMPROC(HSTREAM handle, ByteBuffer buffer, int length, Pointer user) {
        try {
            List<ListenHandler> handlers = getActiveHandlers(handle);

            int bytesPerSample = OUTPUT_FORMAT.getSampleSizeInBits() / 8;

            int maxSampleCount = 0; // biggest sample count in handlers
            for (ListenHandler handler : handlers) {
                synchronized (handler.memoryQueueLock) {
                    if (!handler.closed && handler.memoryQueue != null) {
                        maxSampleCount = Math.max(maxSampleCount, handler.memoryQueue.size() / bytesPerSample);
                    }
                }
            }
            int numSamplesRead = length / bytesPerSample; // amount of samples to read

            byte[] sampleBuffer = new byte[bytesPerSample];
            for (int s = 0; s < numSamplesRead; s++) {
                int mixedSample = 0;
                int activeCount = 0;
                for (ListenHandler handler : handlers) {
                    int sample;
                    synchronized (handler.memoryQueueLock) {
                        if (handler.closed || handler.memoryQueue == null) {
                            continue;
                        }
                        int sampleCount = handler.memoryQueue.size() / bytesPerSample;
                        if (s == maxSampleCount - sampleCount) {
                            handler.memoryQueue.dequeue(sampleBuffer, 0, sampleBuffer.length);
                            sample = (short)((sampleBuffer[0] & 0xff) << 8) | ((short) (sampleBuffer[1] & 0xff));
                        } else {
                            sample = 0;
                        }
                    }
                    mixedSample += sample;
                    activeCount++;
                }
                if (activeCount > 0) {
                    mixedSample /= activeCount;
                }
                mixedSample = Math.max(Short.MIN_VALUE, Math.min(mixedSample, Short.MAX_VALUE));
                buffer.putShort((short) mixedSample);
            }

            for (ListenHandler handler : handlers) {
                synchronized (handler.memoryQueueLock) {
                    if (handler.closed || handler.memoryQueue == null) {
                        continue;
                    }
                    float lag = handler.memoryQueue.size() / (INPUT_FORMAT.getChannels() * INPUT_FORMAT.getSampleRate() * (1 / 1000f) * (INPUT_FORMAT.getSampleSizeInBits() >> 3));
                    if (lag >= MAX_LAG) {
                        handler.memoryQueue.clear();
                        logger.warn("ListenHandler is " + (int)lag + " ms behind! Clearing queue...");
                    }
                }
            }

            return numSamplesRead * bytesPerSample;
        } catch (Throwable ex) {
            logger.error("Playback callback failed", ex);
            return length;
        }
    }

    private static List<ListenHandler> getActiveHandlers(HSTREAM handle) {
        List<ListenHandler> matchingHandlers = new ArrayList<>();
        synchronized (activeHandlersLock) {
            for (ListenHandler handler : activeHandlers) {
                if (!handler.closed && handle.equals(handler.playbackStream)) {
                    matchingHandlers.add(handler);
                }
            }
        }
        return matchingHandlers;
    }

    @Override
    public void close() throws IOException {
        PlaybackResource resourceToClose = null;
        synchronized (activeHandlersLock) {
            if (closed && playbackResource == null) {
                return;
            }
            activeHandlers.remove(this);
            PlaybackResource resource = playbackResource;
            if (resource != null) {
                resource.refCount--;
                if (resource.refCount <= 0) {
                    playbackResources.remove(resource.device);
                    resourceToClose = resource;
                }
            }
            closed = true;
            memoryQueue = null;
            playbackStream = null;
            playbackResource = null;
            playbackDevice = -1;
        }

        if (resourceToClose != null) {
            Bass.BASS_ChannelStop(resourceToClose.stream.asInt());
            Bass.BASS_SetDevice(resourceToClose.device);
            Bass.BASS_Free();
            Utils.checkBassError();
        }
    }
}
