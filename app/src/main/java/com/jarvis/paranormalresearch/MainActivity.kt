package com.jarvis.paranormalresearch

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), SensorEventListener {
    private lateinit var manager: SensorManager
    private lateinit var sensorStatus: TextView
    private lateinit var readingView: TextView
    private lateinit var logView: TextView
    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
    private val logs = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(SENSOR_SERVICE) as SensorManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "JARVIS Paranormal Research"
            textSize = 23f
        })
        sensorStatus = TextView(this).apply { textSize = 14f }
        readingView = TextView(this).apply { textSize = 16f }
        root.addView(sensorStatus)
        root.addView(readingView)
        root.addView(Button(this).apply {
            text = "Start audio recording"
            setOnClickListener {
                if (recorder == null) startRecording() else stopRecording()
                text = if (recorder == null) "Start audio recording" else "Stop audio recording"
            }
        })
        root.addView(Button(this).apply {
            text = "Add observation log"
            setOnClickListener { addLog("Manual observation recorded.") }
        })
        root.addView(TextView(this).apply {
            text = "Observation history"
            textSize = 18f
        })
        logView = TextView(this).apply { textSize = 13f }
        root.addView(logView)
        setContentView(ScrollView(this).apply { addView(root) })
        showSensors()
    }

    private fun showSensors() {
        val sensors = manager.getSensorList(Sensor.TYPE_ALL)
        val list = sensors.joinToString("\n") { "• ${it.name} (type ${it.type})" }
        sensorStatus.text = "Detected sensors: ${sensors.size}\n$list"
        val accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnet = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        readingView.text = "Accelerometer: ${if (accel == null) "not available" else "available"}\n" +
            "Magnetometer: ${if (magnet == null) "not available" else "available"}\n" +
            "Live readings will appear here. These measurements do not detect or prove paranormal activity."
        accel?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        magnet?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val name = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> "Acceleration (m/s²)"
            Sensor.TYPE_MAGNETIC_FIELD -> "Magnetic field (µT)"
            else -> return
        }
        val values = event.values.joinToString(", ") { String.format(Locale.US, "%.2f", it) }
        val old = readingView.text.toString().substringBefore("\nLive readings:")
        readingView.text = "$old\nLive readings:\n$name: [$values]\nNearby electronics, wiring, metal, and movement can affect readings."
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun startRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
            return
        }
        try {
            val file = File(filesDir, "audio_${System.currentTimeMillis()}.m4a")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            audioFile = file
            addLog("Audio recording started: ${file.name}")
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            addLog("Recording could not start: ${e.javaClass.simpleName}")
        }
    }

    private fun stopRecording() {
        try {
            recorder?.stop()
            addLog("Audio saved in app-private storage: ${audioFile?.name ?: "file"}")
        } catch (_: Exception) {
            addLog("Recording stopped; audio file may be incomplete.")
        } finally {
            recorder?.release()
            recorder = null
            audioFile = null
        }
    }

    private fun addLog(message: String) {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        logs.add(0, "[$time] $message")
        logView.text = logs.joinToString("\n")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else if (requestCode == 41) {
            addLog("Microphone permission denied.")
        }
    }

    override fun onDestroy() {
        manager.unregisterListener(this)
        try { recorder?.stop() } catch (_: Exception) { }
        recorder?.release()
        recorder = null
        super.onDestroy()
    }
}
