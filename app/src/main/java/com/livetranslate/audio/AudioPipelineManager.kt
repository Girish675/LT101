package com.livetranslate.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

class AudioPipelineManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    
    private val sampleRate = 16000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private val bufferSizeIn = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat)
    private val bufferSizeOut = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioFormat)

    @SuppressLint("MissingPermission")
    fun startRecording(isEarbudUser: Boolean): Flow<ShortArray> = flow {
        setupRouting(isEarbudUser)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Recommended for AEC
            sampleRate,
            channelConfigIn,
            audioFormat,
            bufferSizeIn
        )

        val sessionId = audioRecord?.audioSessionId ?: -1
        if (sessionId != -1) {
            if (AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(sessionId)
                aec?.enabled = true
                Log.d("AudioPipeline", "AEC Enabled")
            }
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(sessionId)
                ns?.enabled = true
                Log.d("AudioPipeline", "NoiseSuppressor Enabled")
            }
        }

        audioRecord?.startRecording()

        val buffer = ShortArray(bufferSizeIn / 2) // 16-bit PCM

        while (coroutineContext.isActive) {
            val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
            if (readResult > 0) {
                emit(buffer.copyOf(readResult))
            }
        }
    }

    fun playAudio(pcmData: ShortArray, isEarbudUser: Boolean) {
        if (audioTrack == null || audioTrack?.state == AudioTrack.STATE_UNINITIALIZED) {
            setupRouting(isEarbudUser) // Ensure routing is applied before track creation
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfigOut)
                        .build()
                )
                .setBufferSizeInBytes(bufferSizeOut)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
                .build()
        }

        if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack?.play()
        }

        audioTrack?.write(pcmData, 0, pcmData.size)
    }

    private fun setupRouting(isEarbudUser: Boolean) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bluetoothDevice = devices.firstOrNull { 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || 
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO 
        }
        val builtinSpeaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }

        if (isEarbudUser) {
            // Earbud User is speaking: output goes to device speaker so the other person hears
            audioManager.isSpeakerphoneOn = true
            builtinSpeaker?.let { audioManager.setCommunicationDevice(it) }
        } else {
            // Other person is speaking: output goes to earbuds so the Earbud User hears
            audioManager.isSpeakerphoneOn = false
            bluetoothDevice?.let { audioManager.setCommunicationDevice(it) }
        }
    }

    fun stop() {
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        aec?.release()
        aec = null

        ns?.release()
        ns = null
        
        audioManager.clearCommunicationDevice()
        audioManager.mode = AudioManager.MODE_NORMAL
    }
}
