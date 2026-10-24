package vn.xacchuot.thu

/** Cùng một quy tắc cho khung camera trực tiếp và khung ghi vào video AI.
 * Điểm số là điểm mô hình, KHÔNG phải xác suất chính xác được kiểm định.
 */
object QuyTacKhungAI {
    // Có thể đổi trên camera. Không giảm dưới 0.20f do bộ phát hiện đã loại các ứng viên dưới mức đó.
    @Volatile var NGUONG_HIEN_THI = 0.30f
    const val TOI_DA_KHUNG = 3

    fun loc(kq: KetQuaQuet): List<KhungPhatHien> = kq.khung.asSequence()
        .filter { it.tinCay.isFinite() && it.tinCay >= NGUONG_HIEN_THI }
        .filter { it.x2 > it.x1 && it.y2 > it.y1 }
        .sortedByDescending { it.tinCay }
        .take(TOI_DA_KHUNG)
        .toList()
}
