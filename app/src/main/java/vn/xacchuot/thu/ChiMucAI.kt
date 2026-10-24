package vn.xacchuot.thu

/** Một đoạn video có ít nhất hai lần quét xác nhận cùng đối tượng. Thời gian tính từ đầu MP4. */
data class MocAI(
    val viTriMs: Long,
    val tinCay: Float,
    val nhan: String = "chuot_chet"
)

/**
 * Tạo chỉ mục xác chuột trực tiếp khi ghi hình, không tự xem mọi frame MP4 sau khi quay.
 * Chỉ mục là GỢI Ý từ AI, chưa được người dùng xác minh. Mốc có thể chênh vài giây so với MP4.
 */
class ChiMucAI(private val batDauMs: Long) {
    companion object {
        const val NGUONG_TIN_CAY = 0.45f
        const val KHOANG_QUET_LIEN_TIEP_MS = 2_000L
        const val KHOANG_GOP_SU_KIEN_MS = 5_000L
    }

    private val cacMoc = mutableListOf<MocAI>()
    private var lanCoChuotCuoi = -1L
    private var lanTaoNhomCuoi = -1L
    private var soLanLienTiep = 0

    @Synchronized
    fun nhanKetQua(lucMs: Long, tinCayChuot: Float?) {
        if (lucMs < batDauMs || tinCayChuot == null || tinCayChuot < NGUONG_TIN_CAY) {
            soLanLienTiep = 0
            return
        }
        soLanLienTiep = if (lanCoChuotCuoi >= 0 && lucMs - lanCoChuotCuoi <= KHOANG_QUET_LIEN_TIEP_MS)
            soLanLienTiep + 1 else 1
        lanCoChuotCuoi = lucMs
        if (soLanLienTiep < 2) return

        if (cacMoc.isNotEmpty() && lanTaoNhomCuoi >= 0 &&
            lucMs - lanTaoNhomCuoi <= KHOANG_GOP_SU_KIEN_MS) {
            // Một xác chuột xuất hiện nhiều frame chỉ tính là một sự kiện.
            val cu = cacMoc.last()
            if (tinCayChuot > cu.tinCay) cacMoc[cacMoc.lastIndex] = cu.copy(tinCay = tinCayChuot)
        } else {
            // Đánh dấu tại lúc chuỗi phát hiện bắt đầu, mở video sớm hơn 2 giây khi xem lại.
            val lucDau = (lucMs - batDauMs).coerceAtLeast(0L)
            cacMoc.add(MocAI(lucDau, tinCayChuot))
        }
        lanTaoNhomCuoi = lucMs
    }

    @Synchronized fun danhSach(): List<MocAI> = cacMoc.toList()
}
