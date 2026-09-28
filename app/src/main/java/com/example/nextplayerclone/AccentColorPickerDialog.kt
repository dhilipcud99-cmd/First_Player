package com.example.nextplayerclone

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal class AccentColorPickerDialog(
    private val context: Context,
    private val initialColor: Int,
    private val onPreview: (Int) -> Unit,
    private val onSave: (Int) -> Unit,
    private val onCancel: () -> Unit
) {
    fun show() {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        var selectedColor = initialColor
        var updatingFields = false
        var committed = false
        var dialogToDismiss: AlertDialog? = null

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(12))
        }
        val wheel = AccentColorWheelView(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(228))
            contentDescription = "Color wheel"
        }
        content.addView(wheel)

        val preview = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = previewBackground(initialColor, dp(12).toFloat())
        }
        val previewTitle = TextView(context).apply {
            text = "Preview"
            textSize = 14f
        }
        val previewSample = TextView(context).apply {
            text = "Accent color sample"
            textSize = 12f
        }
        preview.addView(previewTitle)
        preview.addView(previewSample)
        content.addView(preview, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })

        val hexLabel = label("HEX")
        content.addView(hexLabel, topMarginParams(dp(12)))
        val hexInput = EditText(context).apply {
            setSingleLine(true)
            hint = "#1565C0"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(InputFilter.LengthFilter(7))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = inputBackground(dp(8).toFloat())
        }
        content.addView(hexInput, LinearLayout.LayoutParams(-1, dp(48)))

        content.addView(label("RGB"), topMarginParams(dp(8)))
        val rgbInputs = listOf("R", "G", "B").map { channel ->
            EditText(context).apply {
                setSingleLine(true)
                hint = channel
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(3))
                gravity = android.view.Gravity.CENTER
                setPadding(dp(4), dp(8), dp(4), dp(8))
                background = inputBackground(dp(8).toFloat())
            }
        }
        val rgbRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            rgbInputs.forEachIndexed { index, input ->
                addView(input, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    if (index > 0) marginStart = dp(8)
                })
            }
        }
        content.addView(rgbRow)

        content.addView(label("Presets"), topMarginParams(dp(12)))
        val presets = intArrayOf(
            Color.parseColor("#1565C0"), Color.parseColor("#00796B"),
            Color.parseColor("#388E3C"), Color.parseColor("#E65100"),
            Color.parseColor("#D32F2F"), Color.parseColor("#C2185B"),
            Color.parseColor("#7B1FA2"), Color.parseColor("#0097A7"),
            Color.parseColor("#202124"), Color.parseColor("#F5F5F5")
        )
        val presetScroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        val presetRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        var updateColor: (Int) -> Unit = {}
        presets.forEachIndexed { index, color ->
            Button(context).apply {
                contentDescription = "Preset color ${index + 1}"
                text = ""
                minWidth = dp(40)
                minimumWidth = dp(40)
                minHeight = dp(40)
                minimumHeight = dp(40)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    setStroke(dp(2), if (color == initialColor) Color.WHITE else Color.TRANSPARENT)
                }
                setOnClickListener { updateColor(color) }
                presetRow.addView(this, LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    marginEnd = dp(8)
                })
            }
        }
        presetScroller.addView(presetRow)
        content.addView(presetScroller, topMarginParams(dp(6)))

        val resetButton = Button(context).apply {
            text = "Reset to Default"
            setOnClickListener {
                updateColor(DEFAULT_COLOR)
                committed = true
                onSave(DEFAULT_COLOR)
                dialogToDismiss?.dismiss()
            }
        }
        content.addView(resetButton, topMarginParams(dp(8)))

        fun setFieldValue(field: EditText, value: String) {
            if (field.text.toString() != value) field.setText(value)
        }

        updateColor = { color ->
            selectedColor = color
            wheel.setColor(color)
            preview.background = previewBackground(color, dp(12).toFloat())
            val textColor = contrastColor(color)
            previewTitle.setTextColor(textColor)
            previewSample.setTextColor(textColor)
            updatingFields = true
            setFieldValue(hexInput, String.format("#%06X", color and 0xFFFFFF))
            setFieldValue(rgbInputs[0], Color.red(color).toString())
            setFieldValue(rgbInputs[1], Color.green(color).toString())
            setFieldValue(rgbInputs[2], Color.blue(color).toString())
            updatingFields = false
            onPreview(color)
        }

        wheel.onColorChanged = { color -> updateColor(color) }
        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (updatingFields) return
                val value = s?.toString().orEmpty()
                if (value.matches(Regex("#[0-9A-Fa-f]{6}"))) {
                    updateColor(Color.parseColor(value))
                }
            }
        })
        rgbInputs.forEach { input ->
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (updatingFields) return
                    val values = rgbInputs.map { it.text.toString().toIntOrNull() }
                    if (values.all { it != null && it in 0..255 }) {
                        updateColor(Color.rgb(values[0]!!, values[1]!!, values[2]!!))
                    }
                }
            })
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Accent Color")
            .setView(ScrollView(context).apply { addView(content) })
            .setNegativeButton("Cancel") { _, _ ->
                if (!committed) onCancel()
            }
            .setPositiveButton("Save", null)
            .create()
        dialogToDismiss = dialog
        dialog.setOnCancelListener {
            if (!committed) onCancel()
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                committed = true
                onSave(selectedColor)
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(contrastColor(selectedColor))
        }
        updateColor(initialColor)
        dialog.show()
    }

    private fun label(text: String) = TextView(context).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.LTGRAY)
        setPadding(0, dp(2), 0, dp(4))
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private fun topMarginParams(margin: Int) = LinearLayout.LayoutParams(-1, -2).apply {
        topMargin = margin
    }

    private fun previewBackground(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
    }

    private fun inputBackground(radius: Float) = GradientDrawable().apply {
        setColor(Color.rgb(42, 44, 48))
        cornerRadius = radius
        setStroke(1, Color.rgb(86, 90, 96))
    }

    private fun contrastColor(color: Int): Int {
        fun linear(channel: Int): Double {
            val value = channel / 255.0
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        val luminance = 0.2126 * linear(Color.red(color)) +
            0.7152 * linear(Color.green(color)) +
            0.0722 * linear(Color.blue(color))
        val whiteContrast = 1.05 / (luminance + 0.05)
        val blackContrast = (luminance + 0.05) / 0.05
        return if (whiteContrast >= blackContrast) Color.WHITE else Color.BLACK
    }

    companion object {
        const val DEFAULT_COLOR = 0xFF1565C0.toInt()
    }
}

private class AccentColorWheelView(context: Context) : View(context) {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private var wheelBitmap: Bitmap? = null
    private val hsv = floatArrayOf(210f, 0.8f, 0.75f)
    var onColorChanged: ((Int) -> Unit)? = null

    init {
        isFocusable = true
        contentDescription = "Choose hue and saturation"
    }

    fun setColor(color: Int) {
        Color.colorToHSV(color, hsv)
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width > 0 && height > 0) createWheelBitmap()
    }

    private fun createWheelBitmap() {
        val size = 320
        val center = (size - 1) / 2f
        val radius = center
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val dy = y - center
            for (x in 0 until size) {
                val dx = x - center
                val distance = sqrt(dx * dx + dy * dy)
                if (distance <= radius) {
                    val hue = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0).toFloat()
                    val saturation = (distance / radius).coerceIn(0f, 1f)
                    pixels[y * size + x] = Color.HSVToColor(floatArrayOf(hue, saturation, 1f))
                }
            }
        }
        wheelBitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = wheelBitmap ?: return
        val radius = min(width, height) / 2f - 8f * resources.displayMetrics.density
        val centerX = width / 2f
        val centerY = height / 2f
        val bounds = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        canvas.drawBitmap(bitmap, null, bounds, bitmapPaint)

        val angle = Math.toRadians(hsv[0].toDouble())
        val markerRadius = radius * hsv[1]
        val markerX = centerX + cos(angle).toFloat() * markerRadius
        val markerY = centerY + sin(angle).toFloat() * markerRadius
        markerPaint.color = Color.BLACK
        canvas.drawCircle(markerX, markerY, 10f * resources.displayMetrics.density, markerPaint)
        markerPaint.color = Color.WHITE
        canvas.drawCircle(markerX, markerY, 7f * resources.displayMetrics.density, markerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_MOVE) return true
        val radius = min(width, height) / 2f - 8f * resources.displayMetrics.density
        val dx = event.x - width / 2f
        val dy = event.y - height / 2f
        val distance = sqrt(dx * dx + dy * dy)
        hsv[0] = ((Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0).toFloat()
        hsv[1] = (distance / radius).coerceIn(0f, 1f)
        val color = Color.HSVToColor(hsv)
        invalidate()
        onColorChanged?.invoke(color)
        return true
    }
}