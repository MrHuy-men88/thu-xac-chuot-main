package vn.xacchuot.thu

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.os.Handler
import android.os.SystemClock
import androidx.camera.core.CameraEffect
import androidx.camera.core.ImageProxy
import androidx.camera.effects.OverlayEffect
import java.util.Locale

/**
 * Ghép khung AI trực tiếp vào luồng camera TRƯỚC khi CameraX ghi MP4.
 * Không phải lớp View phủ lên màn hình: OverlayEffect nhận cả PREVIEW và VIDEO_CAPTURE.
 *
 * Tọa độ từ mạng AI là chuẩn hóa (0..1) trong ảnh ImageAnalysis đã xoay cho thẳng.
 * Chuyển tọa độ -> buffer thô -> tọa độ cảm biến bằng ma trận CameraX, sau đó
 * dùng Frame.sensorToBufferTransform để chiếu vào khung hình MP4. Nhờ vậy tránh
 * việc khung bị sai chỗ khi cảm biến quay 90/270 độ hay preview bị crop.
 */
class KhungVideoAI(handler: Handler, baoLoi: (Throwable) -> Unit) : AutoCloseable {
    private data class Khung(
        val ten: String,
        val tinCay: Float,
        val lop: Int,
        val camBien: RectF,
    )
    private data class DuLieu(
        val lucXongMs: Long,
        val khung: List<Khung>,
    )

    @Volatile private var ketQuaMoiNhat: DuLieu? = null

    val hieuUng = OverlayEffect(
        CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
        0, // Không giữ frame trong hàng đợi: ghi hình mượt, dùng kết quả AI mới nhất.
        handler,
        { e -> baoLoi(e) }
    )

    init {
        // Được gọi trên luồng vẽ OpenGL riêng của CameraX, không chặn luồng phân tích AI.
        val net = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        val nen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(205, 10, 10, 10)
        }
        val chu = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }
        hieuUng.setOnDrawListener { frame ->
            val canvas = frame.overlayCanvas
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            val bufferW = canvas.width.toFloat()
            val bufferH = canvas.height.toFloat()
            val rotation = ((frame.rotationDegrees % 360) + 360) % 360
            // CameraX ghi video portrait bằng buffer landscape + rotation metadata.
            // Phải xoay cả chữ/nhãn khi VẼ, nếu không chữ nằm ngang trong buffer
            // sẽ thành chữ nằm nghiêng trong MP4 sau khi ứng dụng video xoay ảnh.
            val uprightToBuffer = Matrix().apply {
                setValues(when (rotation) {
                    90 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, bufferH, 0f, 0f, 1f)
                    180 -> floatArrayOf(-1f, 0f, bufferW, 0f, -1f, bufferH, 0f, 0f, 1f)
                    270 -> floatArrayOf(0f, -1f, bufferW, 1f, 0f, 0f, 0f, 0f, 1f)
                    else -> floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
                })
            }
            val bufferToUpright = Matrix()
            uprightToBuffer.invert(bufferToUpright)
            val w = if (rotation == 90 || rotation == 270) bufferH else bufferW
            val h = if (rotation == 90 || rotation == 270) bufferW else bufferH
            val crop = RectF(frame.cropRect)
            bufferToUpright.mapRect(crop)
            val save = canvas.save()
            canvas.concat(uprightToBuffer)
            canvas.clipRect(crop)

            val font = (minOf(w, h) * 0.031f).coerceIn(16f, 31f)
            chu.textSize = font
            net.strokeWidth = (font / 7f).coerceIn(2.5f, 5f)
            val duLieu = ketQuaMoiNhat
            val conMoi = duLieu != null && SystemClock.elapsedRealtime() - duLieu.lucXongMs < 2_000L
            val soKhung = if (conMoi) duLieu!!.khung.size else 0
            val trangThai = when {
                !conMoi -> "AI: chưa có kết quả mới"
                soKhung == 0 -> "AI: không phát hiện"
                else -> "AI: phát hiện $soKhung đối tượng"
            }
            val le = font * 0.6f
            val doRong = chu.measureText(trangThai) + 2f * le
            val bannerX = crop.left + le
            val bannerY = crop.top + le
            canvas.drawRoundRect(bannerX, bannerY, bannerX + doRong, bannerY + font * 1.8f, 9f, 9f, nen)
            canvas.drawText(trangThai, bannerX + le, bannerY + font * 1.24f, chu)

            if (conMoi && duLieu != null) {
                val mapping = frame.sensorToBufferTransform
                duLieu.khung.forEach { k ->
                    val rect = RectF()
                    mapping.mapRect(rect, k.camBien)
                    bufferToUpright.mapRect(rect)
                    if (rect.right <= crop.left || rect.bottom <= crop.top ||
                        rect.left >= crop.right || rect.top >= crop.bottom) return@forEach
                    rect.left = rect.left.coerceIn(crop.left, crop.right)
                    rect.right = rect.right.coerceIn(crop.left, crop.right)
                    rect.top = rect.top.coerceIn(crop.top, crop.bottom)
                    rect.bottom = rect.bottom.coerceIn(crop.top, crop.bottom)
                    val mau = if (k.lop == 0) Color.rgb(255, 45, 45) else Color.rgb(0, 170, 255)
                    net.color = mau
                    canvas.drawRect(rect, net)
                    val nhan = "${k.ten} ${String.format(Locale.US, "%.0f%%", k.tinCay * 100f)}"
                    val nhanW = chu.measureText(nhan)
                    val nx = rect.left.coerceIn(crop.left, (crop.right - nhanW - le).coerceAtLeast(crop.left))
                    val ny = (rect.top - font * 1.5f).coerceAtLeast(bannerY + font * 2.3f)
                    nen.color = Color.argb(225, Color.red(mau) / 4, Color.green(mau) / 4, Color.blue(mau) / 4)
                    canvas.drawRoundRect(nx, ny, nx + nhanW + le, ny + font * 1.35f, 5f, 5f, nen)
                    nen.color = Color.argb(205, 10, 10, 10)
                    canvas.drawText(nhan, nx + le / 2f, ny + font, chu)
                }
            }
            canvas.restoreToCount(save)
            true // KHÔNG bỏ khung hình video khi AI chưa kịp phân tích.
        }
    }

    /** Gọi trong ImageAnalysis trước khi đóng ImageProxy. */
    fun capNhat(kq: KetQuaQuet, anh: ImageProxy) {
        val w = anh.width.toFloat()
        val h = anh.height.toFloat()
        val uprightToRaw = Matrix().apply {
            // Chuyển tọa độ chuẩn hóa trên ảnh đã xoay thành tọa độ pixel buffer gốc.
            val v = when ((anh.imageInfo.rotationDegrees % 360 + 360) % 360) {
                90 -> floatArrayOf(0f, w, 0f, -h, 0f, h, 0f, 0f, 1f)
                180 -> floatArrayOf(-w, 0f, w, 0f, -h, h, 0f, 0f, 1f)
                270 -> floatArrayOf(0f, -w, w, h, 0f, 0f, 0f, 0f, 1f)
                else -> floatArrayOf(w, 0f, 0f, 0f, h, 0f, 0f, 0f, 1f)
            }
            setValues(v)
        }
        val rawToSensor = Matrix()
        if (!anh.imageInfo.sensorToBufferTransformMatrix.invert(rawToSensor)) return
        val ds = kq.khung.mapNotNull { k ->
            val rect = RectF(k.x1, k.y1, k.x2, k.y2)
            uprightToRaw.mapRect(rect)
            rawToSensor.mapRect(rect)
            if (rect.width() <= 0f || rect.height() <= 0f) null else Khung(
                BoPhatHien.TEN_LOP.getOrElse(k.lop) { "đối tượng" }, k.tinCay, k.lop, rect
            )
        }
        ketQuaMoiNhat = DuLieu(SystemClock.elapsedRealtime(), ds)
    }

    override fun close() {
        hieuUng.clearOnDrawListener()
        hieuUng.close()
    }
}
