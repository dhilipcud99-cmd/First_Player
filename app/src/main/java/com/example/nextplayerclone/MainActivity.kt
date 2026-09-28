package com.example.nextplayerclone

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerVideos: RecyclerView
    private lateinit var recyclerContinueWatching: RecyclerView
    private lateinit var layoutContinueWatching: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var etSearch: EditText

    private lateinit var chipAll: MaterialButton

    private lateinit var btnSort: MaterialButton
    private lateinit var btnFilter: MaterialButton

    private lateinit var videoAdapter: VideoAdapter
    private lateinit var continueWatchingAdapter: ContinueWatchingAdapter

    private var allVideos: MutableList<VideoItem> = mutableListOf()
    private var displayedVideos: MutableList<VideoItem> = mutableListOf()
    private var continueWatchingVideos: MutableList<VideoItem> = mutableListOf()

    private var currentCategory = "All"
    private var currentSearchQuery = ""
    private var currentSortOrder = "Date"
    private var hasHandledInitialResume = false
    private var isLoadingVideos = false

    private val videoScanExecutor = Executors.newSingleThreadExecutor()

    private val requiredPermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadVideos()
        } else {
            showEmptyState()
        }
    }

    private val allFilesAccessLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasPermission()) loadVideos() else showEmptyState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))

        bindViews()
        setupAdapters()
        setupListeners()

        checkPermissionAndLoad()
        setupAccentColorPicker()
    }

    override fun onResume() {
        super.onResume()
        applyAccentColor()
        if (!hasHandledInitialResume) {
            hasHandledInitialResume = true
        } else if (hasPermission()) {
            loadVideos()
        }
    }

    private fun bindViews() {
        recyclerVideos = findViewById(R.id.recyclerVideos)
        recyclerContinueWatching = findViewById(R.id.recyclerContinueWatching)
        layoutContinueWatching = findViewById(R.id.layoutContinueWatching)
        emptyState = findViewById(R.id.emptyState)
        etSearch = findViewById(R.id.etSearch)

        chipAll = findViewById(R.id.chipAllVideos)

        btnSort = findViewById(R.id.btnSort)
        btnFilter = findViewById(R.id.btnFilter)
    }

    private fun setupAdapters() {
        videoAdapter = VideoAdapter(mutableListOf(), { video ->
            openPlayer(video)
        }, { video, view ->
            showVideoOptionsDialog(video)
        })

        recyclerVideos.layoutManager = LinearLayoutManager(this)
        recyclerVideos.adapter = videoAdapter

        continueWatchingAdapter = ContinueWatchingAdapter(mutableListOf(), { video ->
            openPlayer(video)
        }, { video, view ->
            showVideoOptionsDialog(video)
        })

        recyclerContinueWatching.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerContinueWatching.adapter = continueWatchingAdapter
    }

    private fun setupListeners() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString() ?: ""
                filterAndSortVideos()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        chipAll.setOnClickListener { selectCategory("All", chipAll) }

        btnSort.setOnClickListener { showSortDialog() }
        btnFilter.setOnClickListener { showFilterDialog() }
        findViewById<Button>(R.id.btnGrantPermission)?.setOnClickListener {
            checkPermissionAndLoad()
        }
    }

    private fun selectCategory(category: String, activeChip: MaterialButton) {
        currentCategory = category
        val primaryColor = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getInt("accent_color", Color.parseColor("#1565C0"))
        val surfaceColor = ContextCompat.getColor(this, R.color.card_background)
        val secondaryTextColor = ContextCompat.getColor(this, R.color.text_secondary)

        listOf(chipAll).forEach { chip ->
            chip.setBackgroundColor(surfaceColor)
            chip.setTextColor(secondaryTextColor)
        }

        activeChip.setBackgroundColor(primaryColor)
        activeChip.setTextColor(Color.WHITE)

        filterAndSortVideos()
    }

    private fun showSortDialog() {
        val options = arrayOf("Date Added (Newest)", "Name (A-Z)", "Size (Largest)", "Duration (Longest)")
        AlertDialog.Builder(this)
            .setTitle("Sort Videos")
            .setItems(options) { _, which ->
                currentSortOrder = when (which) {
                    0 -> "Date"
                    1 -> "Name"
                    2 -> "Size"
                    3 -> "Duration"
                    else -> "Date"
                }
                filterAndSortVideos()
            }
            .show()
    }

    private fun showFilterDialog() {
        val options = arrayOf("All", "Duration > 10 mins", "Duration < 10 mins")
        AlertDialog.Builder(this)
            .setTitle("Filter Videos")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> selectCategory("All", chipAll)
                    1 -> {
                        currentCategory = "Long"
                        filterAndSortVideos()
                    }
                    2 -> {
                        currentCategory = "Short"
                        filterAndSortVideos()
                    }
                }
            }
            .show()
    }

    private fun showVideoOptionsDialog(video: VideoItem) {
        val options = arrayOf(
            if (video.isFavorite) "Remove from Favorites" else "Add to Favorites",
            "File Info",
            "Delete"
        )
        AlertDialog.Builder(this)
            .setTitle(video.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        video.isFavorite = !video.isFavorite
                        saveFavoriteStatus(video)
                        filterAndSortVideos()
                        Toast.makeText(this, if (video.isFavorite) "Added to favorites" else "Removed from favorites", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        val sizeMb = video.sizeBytes / (1024 * 1024)
                        val info = "Title: ${video.title}\nSize: $sizeMb MB\nResolution: ${video.width}x${video.height}\nDuration: ${video.durationMs / 1000}s"
                        AlertDialog.Builder(this).setTitle("File Info").setMessage(info).setPositiveButton("OK", null).show()
                    }
                    2 -> {
                        Toast.makeText(this, "Delete not supported in demo", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun saveFavoriteStatus(video: VideoItem) {
        val prefs = getSharedPreferences("video_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("fav_${video.id}", video.isFavorite).apply()
    }

    private fun loadFavoriteStatus(video: VideoItem) {
        val prefs = getSharedPreferences("video_prefs", MODE_PRIVATE)
        video.isFavorite = prefs.getBoolean("fav_${video.id}", false)
    }

    private fun loadSavedProgress(video: VideoItem) {
        val prefs = getSharedPreferences("video_progress", MODE_PRIVATE)
        video.lastPositionMs = prefs.getLong(video.uri.toString(), 0L)
    }

    private fun setupAccentColorPicker() {
        val btnAccentColor: ImageButton = findViewById(R.id.btnAccentColor)
        btnAccentColor.setOnClickListener {
            showAccentColorDialog()
        }
        applyAccentColor()
    }

    private fun showAccentColorDialog() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val originalColor = prefs.getInt("accent_color", AccentColorPickerDialog.DEFAULT_COLOR)
        AccentColorPickerDialog(
            context = this,
            initialColor = originalColor,
            onPreview = { color -> applyAccentColor(color) },
            onSave = { color ->
                prefs.edit().putInt("accent_color", color).apply()
                applyAccentColor(color)
            },
            onCancel = { applyAccentColor(originalColor) }
        ).show()
    }

    private fun applyAccentColor(
        accentColor: Int = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getInt("accent_color", AccentColorPickerDialog.DEFAULT_COLOR)
    ) {
        val foregroundColor = contrastColor(accentColor)
        findViewById<AppBarLayout>(R.id.appBarLayout)?.setBackgroundColor(accentColor)
        findViewById<Toolbar>(R.id.toolbar)?.apply {
            setBackgroundColor(accentColor)
            setTitleTextColor(foregroundColor)
        }
        chipAll.setBackgroundColor(accentColor)
        chipAll.setTextColor(foregroundColor)
        listOf(R.id.btnSearchTop, R.id.btnFavoritesTop, R.id.btnFolderTop, R.id.btnAccentColor)
            .forEach { id -> findViewById<ImageButton>(id)?.setColorFilter(foregroundColor) }
    }

    private fun contrastColor(color: Int): Int {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        val luminance = 0.2126 * linear(Color.red(color)) +
            0.7152 * linear(Color.green(color)) +
            0.0722 * linear(Color.blue(color))
        return if (luminance > 0.179) Color.BLACK else Color.WHITE
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, requiredPermission) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissionAndLoad() {
        if (hasPermission()) {
            loadVideos()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            allFilesAccessLauncher.launch(settingsIntent)
        } else {
            permissionLauncher.launch(requiredPermission)
        }
    }

    private fun openPlayer(video: VideoItem) {
        val allList = displayedVideos
        val index = allList.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
        val uris = ArrayList(allList.map { it.uri.toString() })
        val titles = ArrayList(allList.map { it.title })

        val intent = Intent(this, PlayerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = video.uri
            putStringArrayListExtra("uris", uris)
            putStringArrayListExtra("titles", titles)
            putExtra("index", index)
        }
        startActivity(intent)
    }

    private fun loadVideos() {
        if (isLoadingVideos) return
        isLoadingVideos = true
        videoScanExecutor.execute {
            val videos = queryVideos().toMutableList()
            for (video in videos) {
                loadFavoriteStatus(video)
                loadSavedProgress(video)
            }
            runOnUiThread {
                isLoadingVideos = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                allVideos = videos
                filterAndSortVideos()
            }
        }
    }

    private fun filterAndSortVideos() {
        var filtered = allVideos.asSequence()

        // Search filter
        if (currentSearchQuery.isNotBlank()) {
            filtered = filtered.filter { it.title.contains(currentSearchQuery, ignoreCase = true) }
        }

        // Category filter
        filtered = when (currentCategory) {
            "Long" -> filtered.filter { it.durationMs >= 10 * 60 * 1000L }
            "Short" -> filtered.filter { it.durationMs < 10 * 60 * 1000L }
            else -> filtered
        }

        // Sort
        val sorted = when (currentSortOrder) {
            "Name" -> filtered.sortedBy { it.title }.toMutableList()
            "Size" -> filtered.sortedByDescending { it.sizeBytes }.toMutableList()
            "Duration" -> filtered.sortedByDescending { it.durationMs }.toMutableList()
            else -> filtered.toList().toMutableList() // Date Added is default order from query
        }

        displayedVideos = sorted

        if (allVideos.isEmpty()) {
            showEmptyState()
        } else {
            recyclerVideos.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
            videoAdapter.updateData(displayedVideos)
        }

        // Continue Watching items (progress > 0 and < 95%)
        continueWatchingVideos = allVideos.filter { it.lastPositionMs > 5000L && it.progressPercent < 95 }
            .toMutableList()

        if (continueWatchingVideos.isNotEmpty()) {
            layoutContinueWatching.visibility = View.VISIBLE
            continueWatchingAdapter.updateData(continueWatchingVideos)
        } else {
            layoutContinueWatching.visibility = View.GONE
        }
    }

    private fun showEmptyState() {
        recyclerVideos.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        layoutContinueWatching.visibility = View.GONE
    }

    private fun queryVideos(): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()

        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri: Uri = ContentUris.withAppendedId(collection, id)
                videos.add(
                    VideoItem(
                        id = id,
                        uri = uri,
                        title = cursor.getString(nameCol) ?: "Unknown",
                        durationMs = cursor.getLong(durationCol),
                        sizeBytes = cursor.getLong(sizeCol),
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol)
                    )
                )
            }
        }
        return videos
    }

    override fun onDestroy() {
        videoScanExecutor.shutdownNow()
        super.onDestroy()
    }
}
