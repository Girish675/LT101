package com.livetranslate.audio

/**
 * A lock-free circular buffer for 16kHz PCM audio.
 * Design §6.2: "Microphone captures 16kHz PCM audio into a circular buffer."
 *
 * Stores up to [capacityInSamples] samples. When full, oldest samples are overwritten.
 */
class CircularAudioBuffer(private val capacityInSamples: Int) {

    private val buffer = ShortArray(capacityInSamples)
    private var writePos = 0
    private var available = 0
    private val lock = Any()

    /**
     * Write [data] into the buffer, overwriting oldest samples if necessary.
     */
    fun write(data: ShortArray) {
        synchronized(lock) {
            for (sample in data) {
                buffer[writePos] = sample
                writePos = (writePos + 1) % capacityInSamples
                if (available < capacityInSamples) {
                    available++
                }
            }
        }
    }

    /**
     * Read the last [numSamples] samples from the buffer.
     * Returns fewer samples if the buffer has not been filled that far yet.
     */
    fun readLast(numSamples: Int): ShortArray {
        synchronized(lock) {
            val count = minOf(numSamples, available)
            if (count == 0) return ShortArray(0)

            val result = ShortArray(count)
            val startPos = (writePos - count + capacityInSamples) % capacityInSamples
            for (i in 0 until count) {
                result[i] = buffer[(startPos + i) % capacityInSamples]
            }
            return result
        }
    }

    /**
     * Drain and return all available samples, resetting the buffer.
     */
    fun drainAll(): ShortArray {
        synchronized(lock) {
            val result = readLast(available)
            available = 0
            writePos = 0
            return result
        }
    }

    fun availableSamples(): Int = synchronized(lock) { available }

    fun clear() {
        synchronized(lock) {
            available = 0
            writePos = 0
        }
    }
}
