package com.example.floatingmacro

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import androidx.core.app.NotificationCompat

class FloatingCircleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var circleView: View
    private lateinit var params: WindowManager.LayoutParams

    // Kích thước vòng tròn hiện tại (dp -> px), dùng để phóng to/thu nhỏ
    private var circleSizePx = 0
    private val minSizeDp = 50
    private val maxSizeDp = 220

    // Biến theo dõi kéo thả
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isLocked = false

    private lateinit var scaleGestureDetector: ScaleGestureDetector

    companion object {
        const val CHANNEL_ID = "floating_macro_channel"
        const val NOTIFICATION_ID = 1
        // Ngưỡng để phân biệt "chạm" (tap) và "kéo" (drag)
        const val CLICK_DRAG_TOLERANCE = 12
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceWithNotification()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_circle, null)
        circleView = floatingView.findViewById(R.id.circleView)

        val density = resources.displayMetrics.density
        circleSizePx = (120 * density).toInt() // kích thước khởi tạo giống layout xml (120dp)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            circleSizePx,
            circleSizePx,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300

        windowManager.addView(floatingView, params)

        setupScaleGestureDetector()
        setupTouchListener()
    }

    private fun setupScaleGestureDetector() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (isLocked) return true // khi khóa thì không cho resize, tránh đụng tay nhầm

                val density = resources.displayMetrics.density
                val minSizePx = (minSizeDp * density).toInt()
                val maxSizePx = (maxSizeDp * density).toInt()

                var newSize = (circleSizePx * detector.scaleFactor).toInt()
                newSize = newSize.coerceIn(minSizePx, maxSizePx)

                if (newSize != circleSizePx) {
                    circleSizePx = newSize
                    params.width = circleSizePx
                    params.height = circleSizePx
                    windowManager.updateViewLayout(floatingView, params)
                }
                return true
            }
        })
    }

    private fun setupTouchListener() {
        floatingView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (scaleGestureDetector.isInProgress) {
                        // đang chụm để zoom, không xử lý kéo cùng lúc
                        return@setOnTouchListener true
                    }
                    if (isLocked) {
                        // vị trí đã khóa -> không cho di chuyển
                        return@setOnTouchListener true
                    }

                    val dx = (event.rawX - initialTouchX)
                    val dy = (event.rawY - initialTouchY)

                    if (!isDragging && (Math.abs(dx) > CLICK_DRAG_TOLERANCE || Math.abs(dy) > CLICK_DRAG_TOLERANCE)) {
                        isDragging = true
                    }

                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        // Đây là một cú CHẠM (tap) chứ không phải kéo -> chạy macro vuốt lên
                        onCircleTapped()
                    }
                    true
                }

                else -> false
            }
        }

        // Nhấn giữ (long press) để khóa / mở khóa vị trí
        circleView.setOnLongClickListener {
            toggleLock()
            true
        }
    }

    private fun toggleLock() {
        isLocked = !isLocked
        circleView.setBackgroundResource(
            if (isLocked) R.drawable.circle_shape_locked else R.drawable.circle_shape
        )
    }

    private fun onCircleTapped() {
        // Gọi Accessibility Service để thực hiện thao tác vuốt lên tự động
        val service = MyAccessibilityService.instance
        if (service != null) {
            // Lấy toạ độ TUYỆT ĐỐI của tâm vòng tròn trên màn hình
            // -> vuốt sẽ bắt đầu đúng ngay tại vị trí vòng tròn đang hiển thị
            val centerX = params.x + circleSizePx / 2f
            val centerY = params.y + circleSizePx / 2f

            // Quãng đường vuốt lên: mặc định ~350dp, đủ để hầu hết app nhận là 1 lần vuốt
            val density = resources.displayMetrics.density
            val distancePx = 350 * density

            service.performSwipeUp(centerX, centerY, distancePx)
        } else {
            // Accessibility Service chưa được bật trong Cài đặt
            android.widget.Toast.makeText(
                this,
                "Vui lòng bật Accessibility Service trong phần Cài đặt trước",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startForegroundServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Macro Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating Macro đang chạy")
            .setContentText("Vòng tròn nổi đang hiển thị")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
