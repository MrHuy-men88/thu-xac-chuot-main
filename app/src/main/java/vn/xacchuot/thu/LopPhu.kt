package vn.xacchuot.thu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Lớp trong suốt nằm trên hình camera, vẽ khung quanh vật nghi là xác chuột/mèo. */
class LopPhu(context: Context) : View(context) {

    @Volatile
    private var ketQua: KetQuaQuet? = null

    private val dayDac = context.resources.displayMetrics.density

    private val net = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * dayDac
    }
    private val chu = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * dayDac
        style = Paint.Style.FILL
    }

    fun datKetQua(kq: KetQuaQuet?) {
        ketQua = kq
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val kq = ketQua ?: return
        if (kq.rongAnh <= 0 || kq.caoAnh <= 0) return

        // Hình camera được đặt vừa khít, căn giữa trong màn hình (FIT_CENTER): tính vùng ảnh đó
        val tiLeAnh = kq.rongAnh.toFloat() / kq.caoAnh
        var rong = width.toFloat()
        var cao = rong / tiLeAnh
        if (cao > height) {
            cao = height.toFloat()
            rong = cao * tiLeAnh
        }
        val x0 = (width - rong) / 2f
        val y0 = (height - cao) / 2f

        for (k in kq.khung) {
            val mau = if (k.lop == 0) Color.RED else Color.rgb(0, 150, 255)
            net.color = mau
            chu.color = mau
            val trai = x0 + k.x1 * rong
            val tren = y0 + k.y1 * cao
            val phai = x0 + k.x2 * rong
            val duoi = y0 + k.y2 * cao
            canvas.drawRect(trai, tren, phai, duoi, net)
            val ten = BoPhatHien.TEN_LOP.getOrElse(k.lop) { "?" }
            canvas.drawText("$ten ${"%.2f".format(k.tinCay)}", trai, maxOf(tren - 6f * dayDac, 16f * dayDac), chu)
        }
    }
}
