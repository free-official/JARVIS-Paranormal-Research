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
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensors: SensorManager
    private lateinit var status: TextView
    private lateinit var magnetic: TextView
    private lateinit var motion: TextView
    private lateinit var sensorList: TextView
    private lateinit var logView: TextView
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private val events = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        buildUi()
        registerSensors()
        logEvent("Session started")
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 24)
            setBackgroundColor(0xFF101820.toInt())
        }
        fun text(value: String, size: Float = 16f) = TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(0xFFEAF2F8.toInt())
            setPadding(0, 8, 0, 8)
        }
        root.addView(text("JARVIS PARANORMAL RESEARCH", 22f))
        status = text("LIVE OBSERVATION • Initializing")
        root.addView(status)
        magnetic = text("Magnetic field: waiting for sensor")
        root.addView(magnetic)
        motion = text("Device motion: waiting for sensor")
        root.addView(motion)
        sensorList = text("Available sensors: checking…")
        root.addView(sensorList)
        val recordButton = Button(this).apply {
            text = "Start audio recording"
            setOnClickListener { if (recorder == null) startAudio(this) else stopAudio(this) }
        }
        root.addView(recordButton)
        root.addView(Button(this).apply {
            text = "Add observation marker"
            setOnClickListener { logEvent("Manual observation marker") }
        })
        root.addView(Button(this).apply {
            text = "Export session log"
            setOnClickListener { exportLog() }
        })
        root.addView(text("EVIDENCE LOG", 18f))
        logView = text("No events yet")
        root.addView(ScrollView(this).apply { addView(logView) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun registerSensors() {
        val available = sensors.getSensorList(Sensor.TYPE_ALL)
        sensorList.text = "Available sensors (" + available.size + "): " +
            available.joinToString { it.name }.ifBlank { "None reported" }
        val mag = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val accel = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        status.text = "LIVE • Magnetic: " + (mag != null) + " | Accelerometer: " +
            (accel != null) + " | Gyroscope: " + (gyro != null)
        mag?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accel?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyro?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> magnetic.text =
                "Magnetic field: X %.1f, Y %.1f, Z %.1f µT | magnitude %.1f µT".format(
                    Locale.US, v[0], v[1], v[2],
                    sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
                )
            Sensor.TYPE_ACCELEROMETER -> motion.text =
                "Acceleration: X %.2f, Y %.2f, Z %.2f m/s²".format(Locale.US, v[0], v[1], v[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun makeRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()

    private fun startAudio(button: Button) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 41)
            Toast.makeText(this, "Allow microphone permission, then tap record again.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val file = File(getExternalFilesDir(null), "observation-" + System.currentTimeMillis() + ".m4a")
            val r = makeRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            recordingFile = file
            button.text = "Stop audio recording"
            logEvent("Audio recording started")
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            Toast.makeText(this, "Recording failed: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun stopAudio(button: Button) {
        try {
            recorder?.stop()
            logEvent("Audio saved: " + (recordingFile?.name ?: "unknown"))
        } catch (_: Exception) {
            logEvent("Audio recording stopped; file may be incomplete")
        } finally {
            recorder?.release()
            recorder = null
            recordingFile = null
            button.text = "Start audio recording"
        }
    }

    private fun logEvent(message: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        events.add("[" + stamp + "] " + message)
        logView.text = events.takeLast(100).joinToString("\n")
    }

    private fun exportLog() {
        val file = File(getExternalFilesDir(null), "evidence-log-" + System.currentTimeMillis() + ".txt")
        file.writeText(events.joinToString("\n"))
        Toast.makeText(this, "Log saved: " + file.absolutePath, Toast.LENGTH_LONG).show()
        logEvent("Evidence log exported: " + file.name)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission granted. Tap record again.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        sensors.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        if (::sensors.isInitialized) registerSensors()
    }

    override fun onDestroy() {
        try { recorder?.stop() } catch (_: Exception) { }
        recorder?.release()
        sensors.unregisterListener(this)
        super.onDestroy()
    }
}