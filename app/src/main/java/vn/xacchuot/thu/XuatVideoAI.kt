package vn.xacchuot.thu

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.media3.common.Effects
import androidx.media3.common.MediaItem
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.Locale

/** Overlay theo timestamp của file GỐC. Chỉ file _AI.mp4 mới được vẽ khung. */
class KhungTheoThoiGian(private val frames: List<MauKhung>) : CanvasOverlay() {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(205, 0, 0, 0) }

    override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        if (frames.isEmpty()) return
        val ms = presentationTimeUs / 1000L
        var lo = 0
        var hi = frames.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (frames[mid].ms <= ms) { found = mid; lo = mid + 1 }
            else hi = mid - 1
        }
        if (found < 0) return
        val frame = frames[found]
        if (ms - frame.ms > 1200L || frame.boxes.isEmpty()) return
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val font = (minOf(w, h) * 0.038f).coerceIn(15f, 38f)
        txt.textSize = font
        stroke.strokeWidth = (font / 7f).coerceIn(2f, 6f)
        frame.boxes.forEach { b ->
            val x1 = b.x1.coerceIn(0f, 1f) * w
            val y1 = b.y1.coerceIn(0f, 1f) * h
            val x2 = b.x2.coerceIn(0f, 1f) * w
            val y2 = b.y2.coerceIn(0f, 1f) * h
            if (x2 <= x1 || y2 <= y1) return@forEach
            val color = if (b.lop == 0) Color.rgb(255, 55, 55) else Color.rgb(0, 174, 255)
            stroke.color = color
            canvas.drawRect(x1, y1, x2, y2, stroke)
            val title = "${if (b.lop == 0) "XAC CHUOT" else "XAC MEO"} ${String.format(Locale.US, "%.0f%%", b.diem * 100f)}"
            val labelWidth = txt.measureText(title) + font * 0.7f
            val textX = x1.coerceIn(0f, (w - labelWidth).coerceAtLeast(0f))
            val textY = (y1 - font * 0.15f).coerceAtLeast(font * 1.3f)
            canvas.drawRoundRect(textX, textY - font * 1.2f,
                textX + labelWidth, textY + font * .15f, 6f, 6f, bg)
            canvas.drawText(title, textX + font * .3f, textY - font * .18f, txt)
        }
    }
}

/** Ghi RAW xong -> xuất riêng AI.mp4 qua Media3 Transformer, không làm thay đổi RAW. */
class XuatVideoAI(private val context: Context) {
    private var transformer: Transformer? = null

    fun dangXuat(): Boolean = transformer != null

    fun batDau(videoGoc: Uri, duLieuJson: File, tenAI: String, ketQua: (Uri?, String?) -> Unit) {
        if (dangXuat()) { ketQua(null, "Đang xuất video khác"); return }
        val frames = try { DuLieuKhungAI.doc(duLieuJson) }
            catch (e: Exception) { ketQua(null, "Không đọc được dữ liệu khung: ${e.message}"); return }
        if (frames.isEmpty()) { ketQua(null, "Không có dữ liệu AI để ghép vào video"); return }
        val tmp = File(context.cacheDir, "ai_${System.currentTimeMillis()}.mp4")
        val effect = OverlayEffect(listOf(KhungTheoThoiGian(frames)))
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(videoGoc))
            .setEffects(Effects(emptyList(), listOf(effect)))
            .build()
        try {
            val exporter = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        transformer = null
                        try {
                            val outputUri = saoChepVaoThuVien(tmp, tenAI)
                            ketQua(outputUri, null)
                        } catch (e: Exception) {
                            ketQua(null, "Đã có video gốc nhưng không lưu được bản AI: ${e.message}")
                        } finally { tmp.delete() }
                    }
                    override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                        transformer = null
                        tmp.delete()
                        ketQua(null, "Không xuất được video AI: ${exception.message}")
                    }
                })
                .build()
            transformer = exporter
            exporter.start(edited, tmp.absolutePath)
        } catch (e: Exception) {
            transformer = null
            tmp.delete()
            ketQua(null, "Không bắt đầu được việc xuất video AI: ${e.message}")
        }
    }

    fun huy() {
        transformer?.cancel()
        transformer = null
    }

    private fun saoChepVaoThuVien(file: File, name: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ThuXacChuot/AI")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ThuXacChuot/AI")
                if (!folder.exists() && !folder.mkdirs()) error("Không tạo được thư mục AI")
                put(MediaStore.Video.Media.DATA, File(folder, name).absolutePath)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore không tạo được file video AI")
        try {
            context.contentResolver.openOutputStream(uri)?.use { dest ->
                file.inputStream().use { input -> input.copyTo(dest, 65536) }
            } ?: error("Không ghi được MP4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }, null, null)
            }
            return uri
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            throw e
        }
    }
}
