package com.example.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.res.ResourcesCompat
import com.example.BubbleLogApplication
import com.example.MainActivity
import com.example.R
import com.example.data.TimestampFormatter
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var rootContainer: BackAwareFrameLayout
    private lateinit var bubbleView: FrameLayout
    private lateinit var expandedCard: LinearLayout
    private lateinit var capturedTimeView: TextView
    private lateinit var capturedDateView: TextView
    private lateinit var noteEditText: EditText

    private lateinit var layoutParams: WindowManager.LayoutParams
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var collapsedX = 0
    private var collapsedY = 0
    private var isExpanded = false
    private var pendingCaptureMillis = 0L

    private var downRawX = 0f
    private var downRawY = 0f
    private var startParamX = 0
    private var startParamY = 0
    private var isDragging = false
    private var longPressFired = false
    private var touchSlop = 12

    private val longPressRunnable = Runnable {
        if (!isDragging && !isExpanded) {
            longPressFired = true
            bubbleView.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            bubbleView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            openMainApp()
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        createNotificationChannel()
        startAsForeground()

        initOverlayViews()
        BubbleServiceState.setRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_BUBBLE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_OPEN_CAPTURE -> {
                if (::rootContainer.isInitialized && !isExpanded) {
                    expandNoteCard(System.currentTimeMillis())
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(longPressRunnable)
        serviceScope.cancel()
        BubbleServiceState.setRunning(false)
        if (::rootContainer.isInitialized && rootContainer.isAttachedToWindow) {
            try {
                windowManager.removeView(rootContainer)
            } catch (_: IllegalArgumentException) {
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_STOP_BUBBLE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_body))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, getString(R.string.notification_action_open), openPendingIntent)
            .addAction(0, getString(R.string.notification_action_stop), stopPendingIntent)
            .build()
    }

    private fun initOverlayViews() {
        val metrics = resources.displayMetrics
        val bubbleSizePx = dpToPx(64)
        val marginPx = dpToPx(14)

        collapsedX = (metrics.widthPixels - bubbleSizePx - marginPx).coerceAtLeast(0)
        collapsedY = (metrics.heightPixels / 3).coerceAtLeast(dpToPx(48))

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            COLLAPSED_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = collapsedX
            y = collapsedY
        }

        rootContainer = BackAwareFrameLayout(this) {
            if (isExpanded) {
                collapseToBubble()
                true
            } else {
                false
            }
        }

        bubbleView = createNeonBubbleView(bubbleSizePx)
        expandedCard = createExpandedNoteCard()
        expandedCard.visibility = View.GONE

        rootContainer.addView(bubbleView)
        rootContainer.addView(expandedCard)

        windowManager.addView(rootContainer, layoutParams)
    }

    private fun createNeonBubbleView(bubbleSizePx: Int): FrameLayout {
        val outerGlowDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(COLOR_CYAN, COLOR_VIOLET),
        ).apply {
            shape = GradientDrawable.OVAL
        }

        val container = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(bubbleSizePx, bubbleSizePx)
            background = outerGlowDrawable
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            elevation = dpToPx(10).toFloat()
            contentDescription = getString(R.string.cd_neon_bubble_icon)
        }

        val iconImage = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setImageResource(R.drawable.img_neon_bubble)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = getString(R.string.cd_neon_bubble_icon)
        }
        container.addView(iconImage)

        container.setOnTouchListener { view, event ->
            if (isExpanded) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startParamX = layoutParams.x
                    startParamY = layoutParams.y
                    isDragging = false
                    longPressFired = false
                    view.animate().scaleX(0.91f).scaleY(0.91f).setDuration(90).start()
                    mainHandler.postDelayed(
                        longPressRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong().coerceAtLeast(480L),
                    )
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                        mainHandler.removeCallbacks(longPressRunnable)
                        view.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                    }
                    if (isDragging) {
                        val metrics = resources.displayMetrics
                        val maxX = (metrics.widthPixels - view.width).coerceAtLeast(0)
                        val maxY = (metrics.heightPixels - view.height - dpToPx(24)).coerceAtLeast(0)
                        layoutParams.x = (startParamX + dx.toInt()).coerceIn(0, maxX)
                        layoutParams.y = (startParamY + dy.toInt()).coerceIn(dpToPx(24), maxY)
                        collapsedX = layoutParams.x
                        collapsedY = layoutParams.y
                        windowManager.updateViewLayout(rootContainer, layoutParams)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    if (!isDragging && !longPressFired) {
                        val exactClickMillis = System.currentTimeMillis()
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        expandNoteCard(exactClickMillis)
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    true
                }

                else -> false
            }
        }

        return container
    }

    private fun createExpandedNoteCard(): LinearLayout {
        val spaceFont = ResourcesCompat.getFont(this, R.font.space_grotesk)
        val monoFont = ResourcesCompat.getFont(this, R.font.jetbrains_mono)
        val bodyFont = ResourcesCompat.getFont(this, R.font.plus_jakarta_sans)

        val cardBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(20).toFloat()
            setColor(COLOR_SURFACE)
            setStroke(dpToPx(2), COLOR_CYAN)
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding(dpToPx(16), dpToPx(14), dpToPx(16), dpToPx(16))
            elevation = dpToPx(16).toFloat()
        }

        // Header row: mini bubble icon + captured time badge + date
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        val miniIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(34), dpToPx(34)).apply {
                marginEnd = dpToPx(10)
            }
            setImageResource(R.drawable.img_neon_bubble)
            contentDescription = getString(R.string.cd_neon_bubble_icon)
        }

        val labelColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val capturedLabel = TextView(this).apply {
            text = getString(R.string.overlay_captured_label)
            setTextColor(COLOR_TEXT_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = spaceFont
            letterSpacing = 0.12f
        }

        capturedDateView = TextView(this).apply {
            setTextColor(COLOR_VIOLET_SOFT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = monoFont
        }

        labelColumn.addView(capturedLabel)
        labelColumn.addView(capturedDateView)

        val timeBadgeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(12).toFloat()
            setColor(COLOR_CHIP_BG)
            setStroke(dpToPx(1), COLOR_CYAN)
        }

        capturedTimeView = TextView(this).apply {
            background = timeBadgeBg
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            setTextColor(COLOR_CYAN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = monoFont
        }

        headerRow.addView(miniIcon)
        headerRow.addView(labelColumn)
        headerRow.addView(capturedTimeView)
        card.addView(headerRow)

        // Unlimited text area
        val inputBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(14).toFloat()
            setColor(COLOR_INPUT_BG)
            setStroke(dpToPx(1), COLOR_BORDER)
        }

        noteEditText = EditText(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dpToPx(12)
            }
            background = inputBg
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
            hint = getString(R.string.overlay_note_hint)
            setHintTextColor(COLOR_TEXT_MUTED)
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = bodyFont
            gravity = Gravity.TOP or Gravity.START
            minLines = 3
            maxLines = 6
            filters = emptyArray()
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            isSingleLine = false
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_ALWAYS
            movementMethod = ScrollingMovementMethod.getInstance()
        }
        card.addView(noteEditText)

        // Action row: Cancel + Save
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dpToPx(14)
            }
        }

        val cancelBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(12).toFloat()
            setColor(COLOR_CANCEL_BG)
            setStroke(dpToPx(1), COLOR_VIOLET)
        }

        val cancelBtn = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(48),
                1f,
            ).apply {
                marginEnd = dpToPx(10)
            }
            background = cancelBg
            gravity = Gravity.CENTER
            text = getString(R.string.action_cancel)
            setTextColor(COLOR_TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = spaceFont
            isClickable = true
            isFocusable = true
            setOnClickListener {
                collapseToBubble()
            }
        }

        val saveBg = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(COLOR_CYAN, COLOR_CYAN_DEEP),
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(12).toFloat()
        }

        val saveBtn = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                dpToPx(48),
                1f,
            )
            background = saveBg
            gravity = Gravity.CENTER
            text = getString(R.string.action_save)
            setTextColor(COLOR_DARK_INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = spaceFont
            isClickable = true
            isFocusable = true
            setOnClickListener {
                savePendingCapture()
            }
        }

        actionRow.addView(cancelBtn)
        actionRow.addView(saveBtn)
        card.addView(actionRow)

        return card
    }

    private fun expandNoteCard(captureMillis: Long) {
        pendingCaptureMillis = captureMillis
        isExpanded = true

        val timeText = TimestampFormatter.formatTime(captureMillis)
        val dateText = TimestampFormatter.formatDate(captureMillis)
        capturedTimeView.text = timeText
        capturedDateView.text = dateText
        noteEditText.setText("")

        bubbleView.visibility = View.GONE
        expandedCard.visibility = View.VISIBLE

        val metrics = resources.displayMetrics
        val cardWidth = (metrics.widthPixels * 0.92f).toInt().coerceAtMost(dpToPx(440))

        layoutParams.width = cardWidth
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        layoutParams.x = 0
        layoutParams.y = dpToPx(56)
        layoutParams.flags = EXPANDED_FLAGS
        layoutParams.dimAmount = 0.45f
        layoutParams.softInputMode =
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

        windowManager.updateViewLayout(rootContainer, layoutParams)

        noteEditText.post {
            noteEditText.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(noteEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun savePendingCapture() {
        val millis = pendingCaptureMillis
        val noteText = noteEditText.text?.toString() ?: ""
        val formattedTime = TimestampFormatter.formatTime(millis)
        val repo = (application as BubbleLogApplication).repository

        collapseToBubble()

        serviceScope.launch {
            repo.insertCapture(epochMillis = millis, note = noteText)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    applicationContext,
                    "${getString(R.string.msg_entry_saved)} ($formattedTime)",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun collapseToBubble() {
        if (!isExpanded) return
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(noteEditText.windowToken, 0)

        isExpanded = false
        expandedCard.visibility = View.GONE
        bubbleView.visibility = View.VISIBLE

        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = collapsedX
        layoutParams.y = collapsedY
        layoutParams.flags = COLLAPSED_FLAGS
        layoutParams.dimAmount = 0f
        layoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED

        windowManager.updateViewLayout(rootContainer, layoutParams)
    }

    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
        }
        startActivity(intent)
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics,
        ).toInt()
    }

    private class BackAwareFrameLayout(
        context: Context,
        private val onBackPressed: () -> Boolean,
    ) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (onBackPressed()) return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    companion object {
        const val ACTION_STOP_BUBBLE = "com.example.overlay.ACTION_STOP_BUBBLE"
        const val ACTION_OPEN_CAPTURE = "com.example.overlay.ACTION_OPEN_CAPTURE"
        private const val CHANNEL_ID = "bubble_log_channel"
        private const val NOTIFICATION_ID = 4102

        private const val COLLAPSED_FLAGS =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        private const val EXPANDED_FLAGS =
            WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        private const val COLOR_SURFACE = 0xF5111526.toInt()
        private const val COLOR_INPUT_BG = 0xFF080A12.toInt()
        private const val COLOR_CHIP_BG = 0xFF091B29.toInt()
        private const val COLOR_CANCEL_BG = 0xFF1A1530.toInt()
        private const val COLOR_CYAN = 0xFF00F0FF.toInt()
        private const val COLOR_CYAN_DEEP = 0xFF00C4E8.toInt()
        private const val COLOR_VIOLET = 0xFF9D4EDD.toInt()
        private const val COLOR_VIOLET_SOFT = 0xFFC77DFF.toInt()
        private const val COLOR_BORDER = 0xFF2A3356.toInt()
        private const val COLOR_TEXT_PRIMARY = 0xFFF4F7FF.toInt()
        private const val COLOR_TEXT_MUTED = 0xFF9AA5C4.toInt()
        private const val COLOR_DARK_INK = 0xFF050810.toInt()

        fun start(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
        }
    }
}
