package vn.xacchuot.thu

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Tọa độ khung AI 0..1, tính trên hình thẳng đứng, không vẽ vào video gốc. */
data class BoxLuu(val lop: Int, val diem: Float, val x1: Float, val y1: Float, val x2: Float, val y2: Float)
data class MauKhung(val ms: Long, val boxes: List<BoxLuu>)

/** Giữ mẫu kết quả AI ~4 lần/giây, kể cả mẫu rỗng để biết lúc nào hết phát hiện. */
class DuLieuKhungAI {
    private val khung = ArrayList<MauKhung>()
    private var lucCuoi = -1L

    @Synchronized fun them(ms: Long, kq: KetQuaQuet) {
        if (ms < 0 || (lucCuoi >= 0 && ms - lucCuoi < 250L)) return
        lucCuoi = ms
        khung.add(MauKhung(ms, kq.khung.map {
            BoxLuu(it.lop, it.tinCay, it.x1, it.y1, it.x2, it.y2)
        }))
    }

    @Synchronized fun lay(): List<MauKhung> = khung.toList()

    @Synchronized fun luu(file: File) {
        val ar = JSONArray()
        khung.forEach { m ->
            val items = JSONArray()
            m.boxes.forEach { b ->
                items.put(JSONArray().put(b.lop).put(b.diem.toDouble())
                    .put(b.x1.toDouble()).put(b.y1.toDouble())
                    .put(b.x2.toDouble()).put(b.y2.toDouble()))
            }
            ar.put(JSONObject().put("ms", m.ms).put("boxes", items))
        }
        file.parentFile?.mkdirs()
        file.writeText(ar.toString(), Charsets.UTF_8)
    }

    companion object {
        fun doc(file: File): List<MauKhung> {
            if (!file.isFile) return emptyList()
            val ar = JSONArray(file.readText(Charsets.UTF_8))
            val result = ArrayList<MauKhung>(ar.length())
            for (i in 0 until ar.length()) {
                val item = ar.getJSONObject(i)
                val ds = item.getJSONArray("boxes")
                val boxes = ArrayList<BoxLuu>(ds.length())
                for (j in 0 until ds.length()) {
                    val d = ds.getJSONArray(j)
                    boxes.add(BoxLuu(d.getInt(0), d.getDouble(1).toFloat(),
                        d.getDouble(2).toFloat(), d.getDouble(3).toFloat(),
                        d.getDouble(4).toFloat(), d.getDouble(5).toFloat()))
                }
                result.add(MauKhung(item.getLong("ms"), boxes))
            }
            return result
        }
    }
}

/** Còi cho chuột chết, kích hoạt khi ít nhất hai lần quét gần nhau đều có nghi vấn. */
class BoLocBip {
    private var lanGap = -1L
    private var soLan = 0
    private var mucDaBao = 0
    private var lanBaoCuoi = -1L

    @Synchronized fun kiemTra(now: Long = SystemClock.elapsedRealtime(), diem: Float?): Int {
        if (diem == null || diem < 0.45f) {
            if (lanGap >= 0 && now - lanGap > 2_000L) {
                soLan = 0; mucDaBao = 0
            }
            return 0
        }
        if (lanGap < 0 || now - lanGap > 2_000L) {
            soLan = 1; mucDaBao = 0
        } else soLan++
        lanGap = now
        if (soLan < 2) return 0
        // Đây là NGƯỠNG điểm mô hình, không phải xác suất đúng đã kiểm định.
        val muc = if (diem >= 0.80f) 3 else 1
        if (muc <= mucDaBao) return 0
        // Đang báo nghi ngờ thì cho phép nâng cấp lên 3 bíp ngay khi đủ tin cậy.
        if (mucDaBao == 0 && lanBaoCuoi >= 0 && now - lanBaoCuoi < 6_000L) return 0
        mucDaBao = muc
        lanBaoCuoi = now
        return muc
    }
}
