package vn.xacchuot.thu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** Môi trường chạy AI: tạo một lần cho cả ứng dụng. */
private val moiTruongAI by lazy { Environment.create() }

/** Một khung phát hiện. Tọa độ 0..1, tính trên ảnh đã xoay thẳng (đúng chiều người nhìn). */
class KhungPhatHien(
    val lop: Int,
    val tinCay: Float,
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)

/** Kết quả một lần quét, kèm thời gian từng bước (mili giây) để đo tốc độ. */
class KetQuaQuet(
    val khung: List<KhungPhatHien>,
    val rongAnh: Int,
    val caoAnh: Int,
    val msXuLyAnh: Float,
    val msAI: Float,
    val msLoc: Float,
    val toaDoChuanHoa: Boolean,
)

class BoPhatHien(context: Context) {

    companion object {
        const val CO_ANH = 640            // phải bằng cỡ ảnh lúc huấn luyện lan3
        val TEN_LOP = arrayOf("chuot_chet", "meo_chet")
        const val NGUONG = 0.2f           // giống ô quét thử trên Colab
        const val NGUONG_TRUNG = 0.45f    // hai khung cùng lớp chồng nhau hơn mức này thì bỏ khung yếu
        const val TOI_DA_KHUNG = 20
        const val TEN_FILE_TRONG_APP = "lan3.tflite"
    }

    private val model: CompiledModel
    val nguonMoHinh: String
    private val dauVao: List<TensorBuffer>
    private val dauRa: List<TensorBuffer>

    // Bộ nhớ dùng lại cho mỗi lần quét, đỡ phải cấp phát mới
    private val anhVuong = Bitmap.createBitmap(CO_ANH, CO_ANH, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(anhVuong)
    private val son = Paint(Paint.FILTER_BITMAP_FLAG)
    private val diemAnh = IntArray(CO_ANH * CO_ANH)
    private val mangVao = FloatArray(3 * CO_ANH * CO_ANH)

    init {
        // Nếu trong máy có file model.tflite (thư mục Android/data/vn.xacchuot.thu/files) thì dùng nó,
        // không thì dùng file lan3.tflite gắn sẵn trong app.
        val fileNgoai = File(context.getExternalFilesDir(null), "model.tflite")
        val tuyChon = CompiledModel.Options(Accelerator.CPU)
        if (fileNgoai.exists()) {
            model = CompiledModel.create(fileNgoai.absolutePath, tuyChon, moiTruongAI)
            nguonMoHinh = "model.tflite trong máy"
        } else {
            model = CompiledModel.create(context.assets, TEN_FILE_TRONG_APP, tuyChon, moiTruongAI)
            nguonMoHinh = "$TEN_FILE_TRONG_APP gắn sẵn trong app"
        }
        dauVao = model.createInputBuffers()
        dauRa = model.createOutputBuffers()

        // Chạy thử 1 lần cho "nóng máy", để lần đầu chậm không bị tính vào số đo
        dauVao[0].writeFloat(mangVao)
        model.run(dauVao, dauRa)
        dauRa[0].readFloat()
    }

    /**
     * anh: ảnh RGBA lấy từ camera (chưa xoay).
     * xoayDo: số độ phải xoay theo chiều kim đồng hồ để ảnh thẳng đứng (0, 90, 180, 270).
     */
    fun quet(anh: Bitmap, xoayDo: Int): KetQuaQuet {
        val t0 = System.nanoTime()

        val rong0 = anh.width
        val cao0 = anh.height
        val doiChieu = (xoayDo % 180) != 0
        val rong = if (doiChieu) cao0 else rong0     // kích thước ảnh sau khi xoay thẳng
        val cao = if (doiChieu) rong0 else cao0
        val tiLe = min(CO_ANH.toFloat() / rong, CO_ANH.toFloat() / cao)
        val rongThuc = rong * tiLe
        val caoThuc = cao * tiLe
        val lechX = (CO_ANH - rongThuc) / 2f         // dải đệm xám hai bên (letterbox)
        val lechY = (CO_ANH - caoThuc) / 2f

        // Xoay + thu nhỏ + đặt vào giữa ảnh vuông 640x640, phần thừa tô xám 114 (giống lúc huấn luyện)
        val m = Matrix()
        m.postTranslate(-rong0 / 2f, -cao0 / 2f)
        m.postRotate(xoayDo.toFloat())
        m.postScale(tiLe, tiLe)
        m.postTranslate(CO_ANH / 2f, CO_ANH / 2f)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(anh, m, son)

        // Đổi sang số thực 0..1, xếp theo thứ tự: toàn bộ kênh đỏ, rồi xanh lá, rồi xanh dương
        anhVuong.getPixels(diemAnh, 0, CO_ANH, 0, 0, CO_ANH, CO_ANH)
        val n = CO_ANH * CO_ANH
        for (i in 0 until n) {
            val p = diemAnh[i]
            mangVao[i] = ((p shr 16) and 255) / 255f
            mangVao[n + i] = ((p shr 8) and 255) / 255f
            mangVao[2 * n + i] = (p and 255) / 255f
        }
        dauVao[0].writeFloat(mangVao)
        val t1 = System.nanoTime()

        model.run(dauVao, dauRa)
        val ra = dauRa[0].readFloat()
        val t2 = System.nanoTime()

        // Đầu ra: 6 hàng (x tâm, y tâm, rộng, cao, điểm chuột, điểm mèo) x 8400 ứng viên
        val soKenh = 4 + TEN_LOP.size
        val soUngVien = ra.size / soKenh
        val ungVien = ArrayList<FloatArray>()
        var lonNhat = 0f
        for (i in 0 until soUngVien) {
            var diem = 0f
            var lop = 0
            for (c in TEN_LOP.indices) {
                val s = ra[(4 + c) * soUngVien + i]
                if (s > diem) {
                    diem = s
                    lop = c
                }
            }
            if (diem >= NGUONG) {
                val cx = ra[i]
                val cy = ra[soUngVien + i]
                val w = ra[2 * soUngVien + i]
                val h = ra[3 * soUngVien + i]
                ungVien.add(floatArrayOf(cx, cy, w, h, diem, lop.toFloat()))
                lonNhat = max(lonNhat, max(max(cx, cy), max(w, h)))
            }
        }

        // Tọa độ có thể là 0..1 hoặc tính bằng điểm ảnh (0..640). Một khung rộng dưới 1,5 điểm ảnh
        // thì không thể có, nên nếu mọi số đều <= 1,5 thì đó là tọa độ 0..1.
        val chuanHoa = lonNhat <= 1.5f
        val heSo = if (chuanHoa) CO_ANH.toFloat() else 1f

        val tatCa = ungVien.map { u ->
            val cx = u[0] * heSo
            val cy = u[1] * heSo
            val w = u[2] * heSo
            val h = u[3] * heSo
            KhungPhatHien(
                lop = u[5].toInt(),
                tinCay = u[4],
                x1 = ((cx - w / 2f - lechX) / rongThuc).coerceIn(0f, 1f),
                y1 = ((cy - h / 2f - lechY) / caoThuc).coerceIn(0f, 1f),
                x2 = ((cx + w / 2f - lechX) / rongThuc).coerceIn(0f, 1f),
                y2 = ((cy + h / 2f - lechY) / caoThuc).coerceIn(0f, 1f),
            )
        }.sortedByDescending { it.tinCay }

        // Lọc các khung trùng nhau (cùng lớp, chồng lên nhau nhiều): giữ khung có điểm cao hơn
        val giuLai = ArrayList<KhungPhatHien>()
        for (k in tatCa) {
            if (giuLai.size >= TOI_DA_KHUNG) break
            var trung = false
            for (g in giuLai) {
                if (g.lop == k.lop && giaoTrenHop(g, k) > NGUONG_TRUNG) {
                    trung = true
                    break
                }
            }
            if (!trung) giuLai.add(k)
        }
        val t3 = System.nanoTime()

        return KetQuaQuet(
            khung = giuLai,
            rongAnh = rong,
            caoAnh = cao,
            msXuLyAnh = (t1 - t0) / 1_000_000f,
            msAI = (t2 - t1) / 1_000_000f,
            msLoc = (t3 - t2) / 1_000_000f,
            toaDoChuanHoa = chuanHoa,
        )
    }

    private fun giaoTrenHop(a: KhungPhatHien, b: KhungPhatHien): Float {
        val gx = max(0f, min(a.x2, b.x2) - max(a.x1, b.x1))
        val gy = max(0f, min(a.y2, b.y2) - max(a.y1, b.y1))
        val giao = gx * gy
        val hop = (a.x2 - a.x1) * (a.y2 - a.y1) + (b.x2 - b.x1) * (b.y2 - b.y1) - giao
        return if (hop <= 0f) 0f else giao / hop
    }

    fun dong() {
        runCatching { dauVao.forEach { it.close() } }
        runCatching { dauRa.forEach { it.close() } }
        runCatching { model.close() }
    }
}
