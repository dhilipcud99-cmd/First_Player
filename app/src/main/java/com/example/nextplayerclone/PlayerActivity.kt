package com.example.nextplayerclone

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView

    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var settingsPanel: LinearLayout
    private lateinit var btnPlayPause: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var txtPosition: TextView
    private lateinit var txtDuration: TextView
    private lateinit var txtVideoTitle: TextView
    private lateinit var gestureOverlay: View
    private lateinit var gestureIndicator: LinearLayout
    private lateinit var imgGestureIcon: ImageView
    private lateinit var txtGestureValue: TextView

    private var controlsVisible = true

    private var uris: List<Uri> = emptyList()
    private var titles: List<String> = emptyList()
    private var currentIndex = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { setControlsVisible(false) }
    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            mainHandler.postDelayed(this, 500)
        }
    }

    private lateinit var audioManager: AudioManager
    private var maxVolume = 15
    private var playbackSpeed = 1.0f
    private var sleepTimerRunnable: Runnable? = null

    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) playWithExternalSubtitle(uri)
    }

    // Gesture state
    private var gestureDownX = 0f
    private var gestureDownY = 0f
    private var gestureMode = 0 // 0 none, 1 volume, 2 brightness, 3 seek
    private var seekStartPositionMs = 0L
    private var startVolume = 0f
    private var startBrightness = 0f

    private val hideIndicatorRunnable = Runnable { gestureIndicator.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        setContentView(R.layout.activity_player)
        updateSystemBarsVisibility()

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        bindViews()
        parseIntentData()
        setupPlayer()
        setupControls()
        setupSettingsPanel()
        setupGestures()

        playAt(currentIndex)
        resetHideTimer()
    }

    private fun bindViews() {
        playerView = findViewById(R.id.playerView)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        settingsPanel = findViewById(R.id.settingsPanel)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        seekBar = findViewById(R.id.seekBar)
        txtPosition = findViewById(R.id.txtPosition)
        txtDuration = findViewById(R.id.txtDuration)
        txtVideoTitle = findViewById(R.id.txtVideoTitle)
        gestureOverlay = findViewById(R.id.gestureOverlay)
        gestureIndicator = findViewById(R.id.gestureIndicator)
        imgGestureIcon = findViewById(R.id.imgGestureIcon)
        txtGestureValue = findViewById(R.id.txtGestureValue)
    }

    private fun parseIntentData() {
        val uriStrings = intent.getStringArrayListExtra("uris")
        val titleStrings = intent.getStringArrayListExtra("titles")
        if (uriStrings != null && uriStrings.isNotEmpty()) {
            uris = uriStrings.map { Uri.parse(it) }
            titles = titleStrings ?: uriStrings.map { "" }
            currentIndex = intent.getIntExtra("index", 0).coerceIn(0, uris.size - 1)
        } else {
            val single = intent.data
            if (single != null) {
                uris = listOf(single)
                titles = listOf(intent.getStringExtra("title") ?: "")
                currentIndex = 0
            }
        }
    }

    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    savePlaybackPosition(0L)
                    playNext()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlayPause.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
            }
        })
    }

    private fun playAt(index: Int) {
        if (index !in uris.indices) return
        savePlaybackPosition()
        currentIndex = index
        val mediaItem = MediaItem.fromUri(uris[index])
        player.setMediaItem(mediaItem)
        player.prepare()

        // Restore saved position if any
        val savedPos = getSavedPosition(uris[index])
        if (savedPos > 0) {
            player.seekTo(savedPos)
        }

        player.playWhenReady = true
        player.setPlaybackSpeed(playbackSpeed)
        txtVideoTitle.text = titles.getOrElse(index) { "" }
        mainHandler.post(progressRunnable)
    }

    private fun playNext() {
        if (currentIndex < uris.size - 1) {
            playAt(currentIndex + 1)
        }
    }

    private fun playPrevious() {
        if (currentIndex > 0) {
            playAt(currentIndex - 1)
        }
    }

    private fun savePlaybackPosition(customPos: Long = -1L) {
        try {
            if (uris.indices.contains(currentIndex)) {
                val currentUri = uris[currentIndex].toString()
                val pos = if (customPos >= 0) customPos else player.currentPosition
                val prefs = getSharedPreferences("video_progress", MODE_PRIVATE)
                prefs.edit().putLong(currentUri, pos).apply()
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun getSavedPosition(uri: Uri): Long {
        val prefs = getSharedPreferences("video_progress", MODE_PRIVATE)
        return prefs.getLong(uri.toString(), 0L)
    }

    private fun setupControls() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            savePlaybackPosition()
            finish()
        }

        btnPlayPause.setOnClickListener { togglePlayPause() }

        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { playNext() }
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { playPrevious() }

        findViewById<ImageButton>(R.id.btnForward).setOnClickListener {
            player.seekTo(player.currentPosition + 10_000)
            resetHideTimer()
        }
        findViewById<ImageButton>(R.id.btnRewind).setOnClickListener {
            player.seekTo(max(0, player.currentPosition - 10_000))
            resetHideTimer()
        }

        findViewById<ImageButton>(R.id.btnRotate).setOnClickListener {
            toggleScreenOrientation()
        }
        findViewById<ImageButton>(R.id.btnPip).setOnClickListener {
            enterPipMode()
        }
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            val opening = settingsPanel.visibility != View.VISIBLE
            setControlsVisible(true)
            animateControls(settingsPanel, opening)
            resetHideTimer()
        }
        findViewById<ImageButton>(R.id.btnFullscreen).setOnClickListener { toggleScreenOrientation() }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = player.duration
                    if (duration > 0) {
                        val pos = duration * progress / 1000
                        txtPosition.text = formatTime(pos)
                    }
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                mainHandler.removeCallbacks(hideControlsRunnable)
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                val duration = player.duration
                if (duration > 0) {
                    val pos = duration * (sb?.progress ?: 0) / 1000
                    player.seekTo(pos)
                }
                resetHideTimer()
            }
        })
    }

    private fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
        resetHideTimer()
    }

    private fun setupSettingsPanel() {
        val heading = TextView(this).apply {
            text = getString(R.string.player_settings)
            setTextColor(getColor(R.color.white))
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(4, 2, 4, 12)
        }
        settingsPanel.addView(heading)

        addSettingsRow(R.string.speed, "1.0x") { showSpeedDialog() }
        addSettingsRow(R.string.audio_track, getString(R.string.auto)) { showAudioTrackDialog() }
        addSettingsRow(R.string.subtitles, getString(R.string.auto)) { showSubtitleDialog() }
        addSettingsRow(R.string.aspect_ratio, "Fit") { showAspectRatioDialog() }
        addSettingsRow(R.string.sleep_timer, getString(R.string.off)) { showSleepTimerDialog() }
        addSettingsRow(R.string.video_details, "") { showVideoDetails() }
    }

    private fun addSettingsRow(titleRes: Int, value: String, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(4, 0, 4, 0)
            background = null
            isClickable = true
            isFocusable = true
            setOnClickListener {
                settingsPanel.visibility = View.GONE
                action()
                resetHideTimer()
            }
        }
        val title = TextView(this).apply {
            text = getString(titleRes)
            setTextColor(getColor(R.color.white))
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val valueView = TextView(this).apply {
            text = value
            setTextColor(getColor(R.color.player_accent))
            textSize = 12f
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        row.addView(title)
        row.addView(valueView)
        settingsPanel.addView(row)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun showSleepTimerDialog() {
        val minutes = intArrayOf(5, 10, 15, 30, 45, 60)
        val labels = minutes.map { getString(R.string.minutes_format, it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.sleep_timer)
            .setItems(labels) { dialog, which ->
                sleepTimerRunnable?.let { mainHandler.removeCallbacks(it) }
                val selectedMinutes = minutes[which]
                sleepTimerRunnable = Runnable {
                    player.pause()
                    Toast.makeText(this, R.string.sleep_timer_finished, Toast.LENGTH_SHORT).show()
                }
                mainHandler.postDelayed(sleepTimerRunnable!!, selectedMinutes * 60_000L)
                Toast.makeText(this, getString(R.string.sleep_timer_set, selectedMinutes), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNeutralButton(R.string.cancel_timer) { _, _ ->
                sleepTimerRunnable?.let { mainHandler.removeCallbacks(it) }
                sleepTimerRunnable = null
                Toast.makeText(this, R.string.sleep_timer_canceled, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showVideoDetails() {
        val format = player.videoFormat
        val resolution = if (format != null && format.width > 0 && format.height > 0) {
            "${format.width} x ${format.height}"
        } else {
            getString(R.string.unknown)
        }
        val details = getString(
            R.string.video_details_format,
            uris.getOrNull(currentIndex)?.lastPathSegment ?: getString(R.string.unknown),
            resolution,
            formatTime(player.duration)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.video_details)
            .setMessage(details)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    @Suppress("ClickableViewAccessibility")
    private fun setupGestures() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                toggleControlsVisibility()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val width = gestureOverlay.width
                if (e.x < width / 2f) {
                    player.seekTo(max(0, player.currentPosition - 10_000))
                    showGestureIndicator(android.R.drawable.ic_media_rew, "-10s")
                } else {
                    player.seekTo(player.currentPosition + 10_000)
                    showGestureIndicator(android.R.drawable.ic_media_ff, "+10s")
                }
                return true
            }
        })

        gestureOverlay.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    gestureDownX = event.x
                    gestureDownY = event.y
                    gestureMode = 0
                    seekStartPositionMs = player.currentPosition
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - gestureDownX
                    val dy = event.y - gestureDownY

                    if (gestureMode == 0) {
                        if (abs(dx) > abs(dy) && abs(dx) > 24) {
                            gestureMode = 3 // seek
                            gestureDownX = event.x
                        } else if (abs(dy) > 24) {
                            gestureMode = if (gestureDownX < gestureOverlay.width / 2f) 2 else 1
                            gestureDownY = event.y
                            if (gestureMode == 1) {
                                startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                            } else if (gestureMode == 2) {
                                var b = window.attributes.screenBrightness
                                if (b < 0f) b = 0.5f
                                startBrightness = b
                            }
                        }
                    }

                    when (gestureMode) {
                        1 -> adjustVolume(-dy)
                        2 -> adjustBrightness(-dy)
                        3 -> previewSeek(dx)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (gestureMode == 3) {
                        val dx = event.x - gestureDownX
                        val deltaMs = (dx / gestureOverlay.width) * 90_000
                        val target = (seekStartPositionMs + deltaMs).toLong()
                            .coerceIn(0, max(0, player.duration))
                        player.seekTo(target)
                    }
                    gestureMode = 0
                    hideGestureIndicator()
                }
            }
            true
        }
    }

    private fun adjustVolume(deltaY: Float) {
        val overlayHeight = if (gestureOverlay.height > 0) gestureOverlay.height.toFloat() else 1000f
        val fraction = deltaY / overlayHeight
        val newVolumeFloat = (startVolume + fraction * maxVolume).coerceIn(0f, maxVolume.toFloat())
        val newVolumeInt = newVolumeFloat.roundToInt()

        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (newVolumeInt != currentVolume) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolumeInt, 0)
        }

        val percent = ((newVolumeFloat / maxVolume) * 100).roundToInt().coerceIn(0, 100)
        showGestureIndicator(android.R.drawable.ic_lock_silent_mode_off, "Volume $percent%")
    }

    private fun adjustBrightness(deltaY: Float) {
        val overlayHeight = if (gestureOverlay.height > 0) gestureOverlay.height.toFloat() else 1000f
        val fraction = deltaY / overlayHeight
        val newBrightness = (startBrightness + fraction).coerceIn(0.02f, 1f)

        val lp = window.attributes
        lp.screenBrightness = newBrightness
        window.attributes = lp

        val percent = (newBrightness * 100).roundToInt().coerceIn(0, 100)
        showGestureIndicator(android.R.drawable.ic_menu_view, "Brightness $percent%")
    }

    private fun previewSeek(dx: Float) {
        val deltaMs = (dx / gestureOverlay.width) * 90_000
        val target = (seekStartPositionMs + deltaMs).toLong().coerceIn(0, max(0, player.duration))
        val sign = if (deltaMs >= 0) "+" else "-"
        showGestureIndicator(
            android.R.drawable.ic_media_ff,
            "${formatTime(target)}  ($sign${formatTime(abs(deltaMs).toLong())})"
        )
    }

    private fun showGestureIndicator(iconRes: Int, text: String) {
        mainHandler.removeCallbacks(hideIndicatorRunnable)
        gestureIndicator.visibility = View.VISIBLE
        imgGestureIcon.setImageResource(iconRes)
        txtGestureValue.text = text
    }

    private fun hideGestureIndicator() {
        mainHandler.removeCallbacks(hideIndicatorRunnable)
        mainHandler.postDelayed(hideIndicatorRunnable, 500)
    }

    private fun toggleControlsVisibility() {
        setControlsVisible(!controlsVisible)
    }

    private fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        animateControls(topBar, visible)
        animateControls(bottomBar, visible)
        if (!visible) animateControls(settingsPanel, false)
        mainHandler.removeCallbacks(hideControlsRunnable)
        if (visible) {
            mainHandler.postDelayed(hideControlsRunnable, 4000)
        }
    }

    private fun animateControls(view: View, visible: Boolean) {
        view.animate().cancel()
        if (visible) {
            val wasHidden = view.visibility != View.VISIBLE
            view.visibility = View.VISIBLE
            if (wasHidden) view.alpha = 0f
            view.animate().alpha(1f).setDuration(180).start()
        } else {
            view.animate().alpha(0f).setDuration(220).withEndAction {
                if (!controlsVisible) view.visibility = View.GONE
            }.start()
        }
    }

    private fun resetHideTimer() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.postDelayed(hideControlsRunnable, 4000)
    }

    private fun showSpeedDialog() {
        val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val labels = speeds.map { "${it}x" }.toTypedArray()
        val currentSelection = speeds.indexOfFirst { it == playbackSpeed }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.speed)
            .setSingleChoiceItems(labels, currentSelection) { dialog, which ->
                playbackSpeed = speeds[which]
                player.setPlaybackSpeed(playbackSpeed)
                dialog.dismiss()
            }
            .show()
    }

    private fun showSubtitleDialog() {
        val tracks = player.currentTracks
        val subtitleGroups = tracks.groups.filter {
            it.type == C.TRACK_TYPE_TEXT
        }
        val options = mutableListOf(getString(R.string.none))
        val trackRefs = mutableListOf<Pair<Tracks.Group, Int>?>(null)

        subtitleGroups.forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                options.add(format.language ?: format.label ?: "Track ${trackRefs.size}")
                trackRefs.add(group to i)
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.subtitles)
        if (subtitleGroups.isNotEmpty()) {
            dialog.setItems(options.toTypedArray()) { popup, which ->
                val ref = trackRefs[which]
                val paramsBuilder = player.trackSelectionParameters.buildUpon()
                if (ref == null) {
                    paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                } else {
                    paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    paramsBuilder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    paramsBuilder.setOverrideForType(
                        TrackSelectionOverride(ref.first.mediaTrackGroup, ref.second)
                    )
                }
                player.trackSelectionParameters = paramsBuilder.build()
                popup.dismiss()
            }
        } else {
            dialog.setMessage(R.string.no_embedded_subtitles)
        }
        dialog.setNeutralButton(R.string.add_subtitle_file) { _, _ ->
            subtitlePicker.launch(arrayOf("*/*"))
        }
        dialog.show()
    }

    private fun playWithExternalSubtitle(uri: Uri) {
        val videoUri = uris.getOrNull(currentIndex) ?: return
        val fileName = contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mimeType = when (extension) {
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }

        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // The provider may grant access only for this playback session.
        }

        savePlaybackPosition()
        val position = player.currentPosition
        val subtitle = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mimeType)
            .setLanguage("und")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(videoUri)
            .setSubtitleConfigurations(listOf(subtitle))
            .build()

        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        player.setMediaItem(mediaItem, position)
        player.prepare()
        player.setPlaybackSpeed(playbackSpeed)
        player.play()
        resetHideTimer()
    }

    private fun showSettingsDialog() {
        val options = arrayOf(
            getString(R.string.aspect_ratio),
            getString(R.string.audio_track),
            getString(R.string.speed),
            getString(R.string.subtitles)
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setItems(options) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> showAspectRatioDialog()
                    1 -> showAudioTrackDialog()
                    2 -> showSpeedDialog()
                    3 -> showSubtitleDialog()
                }
            }
            .show()
    }

    private fun showAspectRatioDialog() {
        val options = arrayOf("Fit", "Fill", "Crop / Zoom", "Fixed Width", "Fixed Height")
        val modes = intArrayOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
        )
        val currentSelection = modes.indexOf(playerView.resizeMode).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.aspect_ratio)
            .setSingleChoiceItems(options, currentSelection) { dialog, which ->
                playerView.resizeMode = modes[which]
                dialog.dismiss()
            }
            .show()
    }

    private fun showAudioTrackDialog() {
        val tracks = player.currentTracks
        val audioGroups = tracks.groups.filter {
            it.type == C.TRACK_TYPE_AUDIO
        }

        if (audioGroups.isEmpty()) {
            Toast.makeText(this, "No audio tracks available", Toast.LENGTH_SHORT).show()
            return
        }

        val options = mutableListOf<String>()
        val trackRefs = mutableListOf<Pair<Tracks.Group, Int>>()

        audioGroups.forEach { group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val name = format.language ?: format.label ?: "Audio Track ${trackRefs.size + 1}"
                options.add(name)
                trackRefs.add(group to i)
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.audio_track)
            .setItems(options.toTypedArray()) { dialog, which ->
                val ref = trackRefs[which]
                val paramsBuilder = player.trackSelectionParameters.buildUpon()
                paramsBuilder.setOverrideForType(
                    TrackSelectionOverride(ref.first.mediaTrackGroup, ref.second)
                )
                player.trackSelectionParameters = paramsBuilder.build()
                dialog.dismiss()
            }
            .show()
    }

    private fun updateProgress() {
        val duration = player.duration
        val position = player.currentPosition
        if (duration > 0) {
            seekBar.progress = (position * 1000 / duration).toInt()
            txtDuration.text = "-${formatTime((duration - position).coerceAtLeast(0))}"
        }
        txtPosition.text = formatTime(position)
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "00:00"
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        else String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    private fun updateSystemBarsVisibility() {
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        WindowCompat.setDecorFitsSystemWindows(window, isPortrait)
        if (isPortrait) {
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.show(WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let {
                    it.hide(WindowInsets.Type.systemBars())
                    it.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateSystemBarsVisibility()
    }

    private fun toggleScreenOrientation() {
        val currentOrientation = resources.configuration.orientation
        requestedOrientation = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        updateSystemBarsVisibility()
        val label = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) "Portrait" else "Landscape"
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
        resetHideTimer()
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val format = player.videoFormat
            val width = if (format != null && format.width > 0) format.width else 16
            val height = if (format != null && format.height > 0) format.height else 9
            val rational = Rational(width.coerceAtLeast(1), height.coerceAtLeast(1))
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(rational)
                .build()
            enterPictureInPictureMode(params)
        } else {
            Toast.makeText(this, "Picture-in-Picture is not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player.isPlaying) {
            enterPipMode()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            setControlsVisible(false)
        } else {
            setControlsVisible(true)
        }
    }

    override fun onPause() {
        super.onPause()
        savePlaybackPosition()
        if (isInPictureInPictureMode) {
            // Keep playing in Picture-in-Picture mode
        } else {
            player.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        savePlaybackPosition()
        mainHandler.removeCallbacksAndMessages(null)
        sleepTimerRunnable = null
        player.release()
    }
}
