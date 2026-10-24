package vn.xacchuot.thu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import android.view.View
import java.util.Locale

/** Khung AI trên camera TRỰC TIẾP, hoàn toàn độc lập với bản MP4 AI xuất sau khi dừng. */
class LopPhu(context: Context) : View(context) {
    companion object {
        // Khi camera/AI bị chậm hoặc dừng quét, KHÔNG để khung cũ nằm lại trên màn hình.
        private const val TU_AN_SAU_MS = 1_000L
    }

    private var ketQua: KetQuaQuet? = null
    private val matDo = context.resources.displayMetrics.density
    private val net = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * matDo
    }
    private val chu = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * matDo
        style = Paint.Style.FILL
    }
    private val tuAn = Runnable {
        ketQua = null
        invalidate()
    }

    /** Gọi mỗi lần phân tích xong, kể cả khi KHÔNG tìm được đối tượng. */
    fun datKetQua(kq: KetQuaQuet?) {
        // View và Handler của View được phép cập nhật trên main thread.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            post { datKetQua(kq) }
            return
        }
        removeCallbacks(tuAn)
        // Nếu lần quét mới là kết quả rỗng/điểm thấp, xóa khung NGAY LẬP TỨC.
        ketQua = kq?.takeIf { QuyTacKhungAI.loc(it).isNotEmpty() }
        invalidate() // redraw canvas từ đầu, không tích tụ khung cũ.
        if (ketQua != null) postDelayed(tuAn, TU_AN_SAU_MS)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val kq = ketQua ?: return
        if (kq.rongAnh <= 0 || kq.caoAnh <= 0) return

        // FIT_CENTER: ảnh camera hiển thị theo tỉ lệ nguyên gốc, giữa vùng preview.
        val tiLe = kq.rongAnh.toFloat() / kq.caoAnh
        var rong = width.toFloat()
        var cao = rong / tiLe
        if (cao > height) {
            cao = height.toFloat()
            rong = cao * tiLe
        }
        val x0 = (width - rong) / 2f
        val y0 = (height - cao) / 2f

        // Giới hạn tối đa 3 khung và bỏ kết quả dưới ngưỡng để không che hết hình.
        for (k in QuyTacKhungAI.loc(kq)) {
            val mau = if (k.lop == 0) Color.RED else Color.rgb(0, 150, 255)
            net.color = mau
            chu.color = mau
            val trai = x0 + k.x1 * rong
            val tren = y0 + k.y1 * cao
            val phai = x0 + k.x2 * rong
            val duoi = y0 + k.y2 * cao
            canvas.drawRect(trai, tren, phai, duoi, net)
            val ten = BoPhatHien.TEN_LOP.getOrElse(k.lop) { "doi_tuong" }
            val nhan = "$ten ${String.format(Locale.US, "%.0f%%", k.tinCay * 100f)}"
            canvas.drawText(nhan, trai, maxOf(tren - 6f * matDo, 16f * matDo), chu)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(tuAn)
        ketQua = null
        super.onDetachedFromWindow()
    }
}
