package com.example.floatingmacro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Service này chỉ có nhiệm vụ: nhận lệnh và thực hiện thao tác vuốt lên (swipe up)
 * bằng API dispatchGesture của Android. Đây là cách hợp lệ, được Android hỗ trợ
 * chính thức để tự động hoá thao tác chạm/vuốt trên màn hình.
 */
class MyAccessibilityService : AccessibilityService() {

    companion object {
        // Giữ tham chiếu instance để FloatingCircleService gọi trực tiếp
        var instance: MyAccessibilityService? = null
        private const val TAG = "MyAccessibilityService"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility Service đã kết nối")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Không cần xử lý sự kiện, service này chỉ dùng để dispatch gesture
    }

    override fun onInterrupt() {}

    /**
     * Thực hiện vuốt lên bắt đầu từ CHÍNH vị trí hiện tại của vòng tròn nổi.
     * Vì đây là gesture được hệ thống Android injection thật (dispatchGesture),
     * app đang mở phía dưới sẽ nhận được y hệt như một cú vuốt tay thật của bạn.
     *
     * @param startX vị trí X bắt đầu vuốt (thường là tâm vòng tròn), tính bằng px
     * @param startY vị trí Y bắt đầu vuốt (thường là tâm vòng tròn), tính bằng px
     * @param distancePx quãng đường vuốt lên, tính bằng px
     * @param durationMs thời gian vuốt (ms), càng nhỏ vuốt càng nhanh/giật, càng lớn càng mượt
     */
    fun performSwipeUp(
        startX: Float,
        startY: Float,
        distancePx: Float,
        durationMs: Long = 300
    ) {
        val metrics = DisplayMetrics()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        val screenHeight = metrics.heightPixels

        // Đảm bảo điểm kết thúc không vượt quá mép trên màn hình (>= 0)
        val endY = (startY - distancePx).coerceAtLeast(screenHeight * 0.02f)

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        val gestureBuilder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, durationMs)
        gestureBuilder.addStroke(strokeDescription)

        val dispatched = dispatchGesture(gestureBuilder.build(), null, null)
        Log.d(TAG, "Đã gửi lệnh vuốt lên từ ($startX,$startY) tới ($startX,$endY): $dispatched")
    }
}
