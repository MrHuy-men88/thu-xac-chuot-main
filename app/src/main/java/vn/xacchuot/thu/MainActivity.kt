package vn.xacchuot.thu

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.VideoView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Camera + lan3: quay video GỐC sạch, lưu mốc/khung AI và xuất bản MP4 AI riêng.
 * Không làm mất video GỐC nếu bộ xử lý xuất MP4 AI của máy cũ bị lỗi.
 */
class MainActivity : ComponentActivity() {

    private lateinit var xemTruoc: PreviewView
    private lateinit var lopPhu: LopPhu
    private lateinit var chuThich: TextView
    private lateinit var trangThaiQuay: TextView
    private lateinit var nutDen: Button
    private lateinit var nutToc: Button
    private lateinit var nutQuay: Button
    private lateinit var nutVideo: Button
    private lateinit var nutAnhGoc: Button
    private lateinit var nutBip: Button
    @Volatile private var yeuCauAnhGoc = false

    private val boXuLy = Executors.newSingleThreadExecutor()
    private val luongVe = HandlerThread("VeKhungVaoVideo").apply { start() }
    private val xuatVideo by lazy { XuatVideoAI(applicationContext) }
    @Volatile private var duLieuDangQuay: DuLieuKhungAI? = null
    private val locBip = BoLocBip()
    private var bipBat = true
    private val amBao by lazy { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull() }
    private val henBip = Handler(Looper.getMainLooper())
    private val boDemGio = Handler(Looper.getMainLooper())
    @Volatile private var boPhatHien: BoPhatHien? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var camera: Camera? = null
    private var quayHienTai: Recording? = null
    private var quayBatDauLuc = 0L
    @Volatile private var chiMucDangQuay: ChiMucAI? = null
    private var tenVideoDangQuay = ""
    private var ghiAmDangBat = false
    @Volatile private var coTheQuetKhiQuay = false
    private var denBat = false
    private var dangDungQuay = false

    @Volatile private var loiNap: String? = null
    private val cacMucToc = listOf(
        0L to "tối đa",
        100L to "10 lần/giây",
        200L to "5 lần/giây",
        500L to "2 lần/giây",
    )
    @Volatile private var chiSoToc = 0
    private var lanQuetCuoi = 0L

    private val batDau = SystemClock.elapsedRealtime()
    private var tbXuLyAnh = 0f
    private var tbAI = 0f
    private var tbLoc = 0f
    private var demQuet = 0
    private var mocDem = SystemClock.elapsedRealtime()
    private var quetMoiGiay = 0f
    private var lanCapNhatChu = 0L
    private var nhietDau = -1f

    private val dongHo = object : Runnable {
        override fun run() {
            if (quayHienTai == null || quayBatDauLuc <= 0L) return
            val giay = (SystemClock.elapsedRealtime() - quayBatDauLuc) / 1000L
            trangThaiQuay.text = "● ĐANG QUAY GỐC  %02d:%02d".format(Locale.US, giay / 60, giay % 60) +
                (if (ghiAmDangBat) "  • Có tiếng" else "  • Không tiếng") +
                "  • ${chiMucDangQuay?.danhSach()?.size ?: 0} mốc xác chuột"
            boDemGio.postDelayed(this, 500L)
        }
    }

    private val xinQuyenCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { duoc ->
            if (duoc) batDauCamera()
            else chuThich.text = "Cần cấp quyền CAMERA để sử dụng ứng dụng."
        }

    private val xinQuyenAmThanh =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { duoc ->
            // Người dùng từ chối mic vẫn có thể quay video không có tiếng.
            batDauQuay(duoc)
        }

    private val xinQuyenLuu =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { duoc ->
            if (duoc) kiemTraQuyenAmThanh()
            else thongBao("Để lưu video vào thư viện trên Android 7–9, hãy cấp quyền Bộ nhớ.")
        }

    private val xinQuyenAnhGoc =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { duoc ->
            if (duoc) datYeuCauAnhGoc()
            else thongBao("Cần quyền Bộ nhớ để lưu ảnh gốc vào thư viện trên Android 7–9.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        dungGiaoDien()
        if (coQuyen(Manifest.permission.CAMERA)) batDauCamera()
        else xinQuyenCamera.launch(Manifest.permission.CAMERA)
    }

    private fun coQuyen(quyen: String): Boolean =
        ContextCompat.checkSelfPermission(this, quyen) == PackageManager.PERMISSION_GRANTED

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun taoNut(nhan: String, mau: Int): Button = Button(this).apply {
        text = nhan
        setTextColor(Color.WHITE)
        textSize = 13f
        isAllCaps = false
        backgroundTintList = ColorStateList.valueOf(mau)
    }

    private fun dungGiaoDien() {
        val goc = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        xemTruoc = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
        goc.addView(xemTruoc, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        lopPhu = LopPhu(this)
        goc.addView(lopPhu, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        chuThich = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setBackgroundColor(Color.argb(135, 0, 0, 0))
            setPadding(dp(8), dp(6), dp(8), dp(6))
            text = "Đang mở camera..."
        }
        goc.addView(chuThich, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))

        val bangNut = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(5), dp(6), dp(5))
            setBackgroundColor(Color.argb(190, 0, 0, 0))
        }
        trangThaiQuay = TextView(this).apply {
            text = "1 lần quay: video GỐC sạch + video AI đánh dấu"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(3), dp(3), dp(5))
        }
        bangNut.addView(trangThaiQuay, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val hangQuay = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nutQuay = taoNut("● BẮT ĐẦU QUAY", Color.rgb(210, 43, 57))
        nutQuay.setOnClickListener { bamNutQuay() }
        nutQuay.isEnabled = false
        hangQuay.addView(nutQuay, LinearLayout.LayoutParams(0, WRAP_CONTENT, 2f))

        nutVideo = taoNut("2 VIDEO / MỐC", Color.rgb(30, 119, 200))
        nutVideo.setOnClickListener { moDanhSachVideo() }
        hangQuay.addView(nutVideo, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.4f))
        bangNut.addView(hangQuay, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val hangPhu = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nutDen = taoNut("Đèn pin: tắt", Color.rgb(210, 115, 14))
        nutDen.setOnClickListener { batTatDen() }
        hangPhu.addView(nutDen, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        nutToc = taoNut("Quét: ${cacMucToc[0].second}", Color.rgb(0, 132, 130))
        nutToc.setOnClickListener { doiToc() }
        hangPhu.addView(nutToc, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bangNut.addView(hangPhu, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        nutAnhGoc = taoNut("📷 CHỤP ẢNH GỐC ĐỂ HUẤN LUYỆN", Color.rgb(48, 118, 78))
        nutAnhGoc.setOnClickListener { yeuCauChupAnhGoc() }
        bangNut.addView(nutAnhGoc, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        nutBip = taoNut("🔊 Bíp cảnh báo: BẬT", Color.rgb(115, 80, 160))
        nutBip.setOnClickListener {
            bipBat = !bipBat
            nutBip.text = if (bipBat) "🔊 Bíp cảnh báo: BẬT" else "🔇 Bíp cảnh báo: TẮT"
        }
        bangNut.addView(nutBip, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        goc.addView(bangNut, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        setContentView(goc)
    }

    private fun taoPreview(): Preview = Preview.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build()
        ).build().also { it.setSurfaceProvider(xemTruoc.surfaceProvider) }

    private fun taoPhanTich(): ImageAnalysis = ImageAnalysis.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(
                    ResolutionStrategy(Size(640, 360), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                )
                .build()
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .build().also { it.setAnalyzer(boXuLy) { anh -> phanTichKhung(anh) } }

    /** Ghi GỐC không gắn hiệu ứng lên camera; dữ liệu khung AI lưu riêng để xuất bản thứ hai. */
    private fun bindCameraGoc(provider: ProcessCameraProvider, quality: Quality): Boolean {
        return try {
            val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(quality)).build()
            val video = VideoCapture.withOutput(recorder)
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                taoPreview(), taoPhanTich(), video)
            videoCapture = video
            coTheQuetKhiQuay = true
            lopPhu.visibility = View.VISIBLE
            trangThaiQuay.text = "Sẵn sàng: GỐC sạch + AI xuất sau khi dừng (${if (quality == Quality.HD) "HD" else "SD"})"
            true
        } catch (_: Exception) {
            provider.unbindAll()
            videoCapture = null
            false
        }
    }

    private fun batDauCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                cameraProvider = provider
                videoCapture = null
                coTheQuetKhiQuay = false
                nutQuay.isEnabled = false
                if (bindCameraGoc(provider, Quality.HD) || bindCameraGoc(provider, Quality.SD)) {
                    chuThich.text = "Đang nạp mô hình AI..."
                    nutQuay.isEnabled = true
                } else {
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                        taoPreview(), taoPhanTich())
                    lopPhu.visibility = View.VISIBLE
                    trangThaiQuay.text = "Máy không hỗ trợ quay + AI đồng thời. Chỉ xem trước."
                    nutQuay.isEnabled = false
                }
            } catch (e: Exception) {
                nutQuay.isEnabled = false
                chuThich.text = "LỖI MỞ CAMERA\n${e.javaClass.simpleName}: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun phatBip(soTieng: Int) {
        if (!bipBat || soTieng == 0) return
        henBip.post {
            if (!bipBat || isDestroyed) return@post
            val tone = amBao ?: return@post
            for (i in 0 until soTieng.coerceIn(1, 3)) {
                henBip.postDelayed({
                    if (bipBat && !isDestroyed) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 140)
                }, i * 270L)
            }
        }
    }

    private fun bamNutQuay() {
        if (quayHienTai != null) {
            if (!dangDungQuay) {
                dangDungQuay = true
                nutQuay.isEnabled = false
                nutQuay.text = "ĐANG LƯU..."
                quayHienTai?.stop()
            }
            return
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && !coQuyen(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            xinQuyenLuu.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            kiemTraQuyenAmThanh()
        }
    }

    private fun kiemTraQuyenAmThanh() {
        if (coQuyen(Manifest.permission.RECORD_AUDIO)) batDauQuay(true)
        else xinQuyenAmThanh.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun batDauQuay(coAmThanh: Boolean) {
        if (quayHienTai != null || isFinishing || isDestroyed) return
        if (xuatVideo.dangXuat()) { thongBao("Đang tạo video AI từ lần quay trước, hãy giữ ứng dụng mở."); return }
        val video = videoCapture ?: run {
            thongBao("Camera chưa sẵn sàng, hãy mở lại ứng dụng.")
            return
        }
        if (!coTheQuetKhiQuay) {
            thongBao("Máy không hỗ trợ quay cùng lúc với phân tích AI.")
            return
        }
        if (boPhatHien == null || loiNap != null) {
            thongBao("AI chưa sẵn sàng hoặc nạp mô hình bị lỗi. Kiểm tra màn hình rồi thử lại.")
            return
        }
        try {
            val ten = "XacChuot_GOC_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.mp4"
            val noiDung = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, ten)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ThuXacChuot/GOC")
                } else {
                    @Suppress("DEPRECATION")
                    val thuMuc = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ThuXacChuot/GOC")
                    if (!thuMuc.exists() && !thuMuc.mkdirs()) error("Không thể tạo thư mục lưu video")
                    put(MediaStore.Video.Media.DATA, File(thuMuc, ten).absolutePath)
                }
            }
            val dich = MediaStoreOutputOptions.Builder(
                contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(noiDung).build()
            var pending = video.output.prepareRecording(this, dich)
            if (coAmThanh && coQuyen(Manifest.permission.RECORD_AUDIO)) {
                pending = pending.withAudioEnabled()
            }
            tenVideoDangQuay = ten
            ghiAmDangBat = coAmThanh && coQuyen(Manifest.permission.RECORD_AUDIO)
            nutQuay.isEnabled = false
            quayHienTai = pending.start(ContextCompat.getMainExecutor(this)) { suKien ->
                when (suKien) {
                    is VideoRecordEvent.Start -> {
                        quayBatDauLuc = SystemClock.elapsedRealtime()
                        chiMucDangQuay = ChiMucAI(quayBatDauLuc)
                        duLieuDangQuay = DuLieuKhungAI()
                        nutQuay.text = "■ DỪNG QUAY"
                        nutQuay.isEnabled = coTheQuetKhiQuay
                        trangThaiQuay.text = if (ghiAmDangBat) "● ĐANG QUAY GỐC có âm thanh" else "● ĐANG QUAY GỐC không âm thanh"
                        boDemGio.removeCallbacks(dongHo)
                        boDemGio.post(dongHo)
                    }
                    is VideoRecordEvent.Finalize -> {
                        boDemGio.removeCallbacks(dongHo)
                        quayHienTai = null
                        val cacMocQuay = chiMucDangQuay?.danhSach().orEmpty()
                        val duLieu = duLieuDangQuay
                        duLieuDangQuay = null
                        chiMucDangQuay = null
                        quayBatDauLuc = 0L
                        dangDungQuay = false
                        nutQuay.text = "● BẮT ĐẦU QUAY"
                        nutQuay.isEnabled = coTheQuetKhiQuay
                        if (!suKien.hasError()) {
                            val uri = suKien.outputResults.outputUri
                            if (uri != Uri.EMPTY) {
                                val duongDan = File(filesDir, "khung_ai/${tenVideoDangQuay.removeSuffix(".mp4")}.json")
                                val coMau = runCatching { duLieu?.luu(duongDan); duLieu?.lay()?.isNotEmpty() == true }.getOrDefault(false)
                                themVideoDaLuu(uri, tenVideoDangQuay, cacMocQuay, if (coMau) duongDan.absolutePath else "")
                                luuBangMocTuDong(tenVideoDangQuay, cacMocQuay)
                                trangThaiQuay.text = "Đã lưu GỐC • đang tạo bản MP4 AI..."
                                Toast.makeText(this, "Đã giữ video gốc sạch. Đang xuất bản AI.", Toast.LENGTH_LONG).show()
                                if (coMau) xuatBanAI(uri, tenVideoDangQuay, duongDan)
                                else trangThaiQuay.text = "Đã lưu GỐC; không có khung AI để tạo bản thứ hai."
                                nutQuay.isEnabled = !xuatVideo.dangXuat() && coTheQuetKhiQuay
                            } else {
                                trangThaiQuay.text = "Không nhận được đường dẫn video đã lưu"
                            }
                        } else {
                            trangThaiQuay.text = "Lỗi lưu video (${suKien.error}): ${suKien.cause?.message ?: "không rõ"}"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            quayHienTai = null
            chiMucDangQuay = null
            duLieuDangQuay = null
            nutQuay.isEnabled = coTheQuetKhiQuay
            nutQuay.text = "● BẮT ĐẦU QUAY"
            trangThaiQuay.text = "Không thể bắt đầu quay GỐC: ${e.message}"
        }
    }

    private fun thongBao(noiDung: String) {
        Toast.makeText(this, noiDung, Toast.LENGTH_LONG).show()
        trangThaiQuay.text = noiDung
    }

    // Danh sách chỉ chứa video do chính ứng dụng này quay, không cần quyền đọc thư viện.
    private data class VideoDaLuu(
        val uri: Uri, val ten: String, val cacMoc: List<MocAI> = emptyList(),
        val uriAI: Uri? = null, val duLieuKhung: String = ""
    )
    private val tenPrefs = "thu_xac_chuot_video"
    private val khoaDanhSach = "danh_sach"

    private fun layDanhSachVideo(): MutableList<VideoDaLuu> {
        val ketQua = mutableListOf<VideoDaLuu>()
        val raw = getSharedPreferences(tenPrefs, MODE_PRIVATE).getString(khoaDanhSach, "[]") ?: "[]"
        try {
            val mang = JSONArray(raw)
            for (i in 0 until mang.length()) {
                val item = mang.getJSONObject(i)
                val mocJson = item.optJSONArray("cacMoc") ?: JSONArray()
                val moc = mutableListOf<MocAI>()
                for (j in 0 until mocJson.length()) {
                    val obj = mocJson.optJSONObject(j) ?: continue
                    moc.add(MocAI(obj.optLong("viTriMs", 0L), obj.optDouble("tinCay", 0.0).toFloat(), obj.optString("nhan", "chuot_chet")))
                }
                val uAI = item.optString("uriAI", "").takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                ketQua.add(VideoDaLuu(Uri.parse(item.getString("uri")), item.getString("ten"), moc,
                    uAI, item.optString("duLieuKhung", "")))
            }
        } catch (_: Exception) { /* Nội dung cũ bị lỗi: bỏ qua, không crash */ }
        return ketQua
    }

    private fun luuDanhSachVideo(danhSach: List<VideoDaLuu>) {
        val mang = JSONArray()
        danhSach.forEach { video ->
            val cacMoc = JSONArray()
            video.cacMoc.forEach { m -> cacMoc.put(JSONObject()
                .put("viTriMs", m.viTriMs).put("tinCay", m.tinCay.toDouble()).put("nhan", m.nhan)) }
            mang.put(JSONObject().put("uri", video.uri.toString()).put("ten", video.ten)
                .put("cacMoc", cacMoc).put("uriAI", video.uriAI?.toString() ?: "")
                .put("duLieuKhung", video.duLieuKhung))
        }
        getSharedPreferences(tenPrefs, MODE_PRIVATE).edit().putString(khoaDanhSach, mang.toString()).apply()
    }

    private fun themVideoDaLuu(uri: Uri, ten: String, cacMoc: List<MocAI>, duLieuKhung: String) {
        val danhSach = layDanhSachVideo()
        danhSach.add(0, VideoDaLuu(uri, ten, cacMoc, null, duLieuKhung))
        luuDanhSachVideo(danhSach)
    }

    private fun xuatBanAI(rawUri: Uri, rawName: String, file: File) {
        if (xuatVideo.dangXuat()) { thongBao("Đang xử lý một video khác. Video gốc vẫn đã lưu."); return }
        if (!file.isFile) { thongBao("Không tìm được dữ liệu khung của video. Còn giữ bản gốc."); return }
        val tenAI = rawName.replace("_GOC_", "_AI_")
        nutQuay.isEnabled = false
        trangThaiQuay.text = "Đang tạo bản AI (tốn thời gian và pin trên Samsung S7 Edge). Giữ app mở."
        xuatVideo.batDau(rawUri, file, tenAI) { uriAI, error ->
            if (uriAI != null) {
                val ds = layDanhSachVideo()
                luuDanhSachVideo(ds.map { if (it.uri == rawUri) it.copy(uriAI = uriAI) else it })
                trangThaiQuay.text = "ĐÃ LƯU CẢ 2 VIDEO: GỐC + AI. Mở 2 VIDEO / MỐC để xem."
                Toast.makeText(this, "Đã có 2 file MP4: GỐC và AI", Toast.LENGTH_LONG).show()
            } else {
                trangThaiQuay.text = "Đã giữ video GỐC. Bản AI chưa tạo được: $error. Vào danh sách để thử lại."
            }
            nutQuay.isEnabled = coTheQuetKhiQuay
        }
    }

    private fun moDanhSachVideo() {
        if (quayHienTai != null) { thongBao("Hãy dừng quay trước khi xem lại."); return }
        val ds = layDanhSachVideo()
        if (ds.isEmpty()) { thongBao("Chưa có video nào."); return }
        AlertDialog.Builder(this)
            .setTitle("Các lần quay — mỗi lần có GỐC và AI")
            .setItems(ds.map {
                val status = if (!it.ten.contains("_GOC_")) "video bản cũ (KHÔNG CÓ GỐC SẠCH)"
                    else if (it.uriAI != null) "2/2 MP4" else "chưa xuất AI"
                "${it.ten.removeSuffix(".mp4")} • ${it.cacMoc.size} mốc • $status"
            }.toTypedArray()) { _, i -> chonVideo(ds[i]) }
            .setNegativeButton("Đóng", null).show()
    }

    private fun moVideoLon(video: VideoDaLuu, chonAI: Boolean) {
        val uri = if (chonAI) video.uriAI else video.uri
        if (uri == null) { thongBao("Bản AI chưa xuất xong. Mở danh sách và chọn Tạo lại AI."); return }
        val arr = JSONArray()
        video.cacMoc.forEach { arr.put(JSONObject().put("viTriMs", it.viTriMs)
            .put("tinCay", it.tinCay.toDouble()).put("nhan", it.nhan)) }
        startActivity(Intent(this, XemVideoActivity::class.java).apply {
            putExtra("video", uri.toString())
            putExtra("markers", arr.toString())
            putExtra("name", if (chonAI) "VIDEO AI: ${video.ten}" else "VIDEO GỐC: ${video.ten}")
        })
    }

    private fun chiaSeVideo(video: VideoDaLuu, chonAI: Boolean) {
        val uri = if (chonAI) video.uriAI else video.uri
        if (uri == null) { thongBao("Chưa có file AI"); return }
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(contentResolver, "Video", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Chia sẻ video"))
        } catch (e: Exception) { thongBao("Không chia sẻ được: ${e.message}") }
    }

    private fun chonVideo(video: VideoDaLuu) {
        val laVideoMoi = video.ten.contains("_GOC_")
        val opts = arrayOf("▶ XEM VIDEO AI + MỐC (MÀN HÌNH LỚN)",
            if (laVideoMoi) "▶ XEM VIDEO GỐC (KHÔNG KHUNG)" else "▶ XEM VIDEO BẢN CŨ (KHÔNG PHẢI GỐC SẠCH)",
            "↗ Chia sẻ MP4 AI", if (laVideoMoi) "↗ Chia sẻ MP4 GỐC để trích ảnh" else "↗ Chia sẻ video cũ",
            "Xuất mốc CSV", "Tạo lại MP4 AI từ GỐC + mốc", "Xóa CẢ HAI video")
        AlertDialog.Builder(this).setTitle(video.ten)
            .setItems(opts) { _, idx ->
                when(idx) {
                    0 -> moVideoLon(video, true)
                    1 -> moVideoLon(video, false)
                    2 -> chiaSeVideo(video, true)
                    3 -> chiaSeVideo(video, false)
                    4 -> xuatMocCsv(video)
                    5 -> {
                        if (!laVideoMoi) thongBao("Video bản cũ không có GỐC sạch và dữ liệu khung đầy đủ để tái xuất.")
                        else if (video.uriAI != null) thongBao("Bản AI đã có. Muốn xuất lại hãy dùng bản GỐC và dữ liệu gốc trên máy tính.")
                        else if (video.duLieuKhung.isEmpty()) thongBao("Video cũ không có dữ liệu khung theo thời gian.")
                        else xuatBanAI(video.uri, video.ten, File(video.duLieuKhung))
                    }
                    6 -> AlertDialog.Builder(this)
                        .setTitle("Xóa CẢ HAI video?")
                        .setMessage("Xóa vĩnh viễn bản GỐC và AI. Dữ liệu chưa sao lưu sẽ mất.")
                        .setPositiveButton("Xóa") { _, _ ->
                            try {
                                contentResolver.delete(video.uri, null, null)
                                video.uriAI?.let { contentResolver.delete(it, null, null) }
                                if (video.duLieuKhung.isNotEmpty()) File(video.duLieuKhung).delete()
                                luuDanhSachVideo(layDanhSachVideo().filterNot { it.uri == video.uri })
                                thongBao("Đã xóa")
                            } catch (e: Exception) { thongBao("Lỗi xóa: ${e.message}") }
                        }.setNegativeButton("Hủy", null).show()
                }
            }.setNegativeButton("Đóng", null).show()
    }

    private fun yeuCauChupAnhGoc() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            !coQuyen(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            xinQuyenAnhGoc.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else datYeuCauAnhGoc()
    }

    private fun datYeuCauAnhGoc() {
        yeuCauAnhGoc = true
        thongBao("Đã yêu cầu chụp: ảnh gốc sẽ được lưu ở khung camera tiếp theo, không có khung AI.")
    }

    /** Ảnh RGB lấy từ ImageAnalysis KHÔNG bị OverlayEffect vẽ đè, dùng làm dữ liệu gán nhãn. */
    private fun luuAnhGoc(anh: Bitmap, xoayDo: Int) {
        var uri: Uri? = null
        try {
            val ten = "Goc_Chuot_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, ten)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ThuXacChuot/DuLieuHuanLuyen")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    @Suppress("DEPRECATION")
                    val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        "ThuXacChuot/DuLieuHuanLuyen")
                    if (!folder.exists() && !folder.mkdirs()) error("Không thể tạo thư mục ảnh")
                    put(MediaStore.Images.Media.DATA, File(folder, ten).absolutePath)
                }
            }
            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Không thể tạo ảnh trong MediaStore")
            val doXoay = ((xoayDo % 360) + 360) % 360
            val thang = if (doXoay == 0) anh else Bitmap.createBitmap(
                anh, 0, 0, anh.width, anh.height, Matrix().apply { postRotate(doXoay.toFloat()) }, true)
            contentResolver.openOutputStream(uri)?.use { stream ->
                if (!thang.compress(Bitmap.CompressFormat.JPEG, 92, stream)) error("Không nén được JPEG")
            } ?: error("Không ghi được file ảnh")
            if (thang !== anh) thang.recycle()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null)
            }
            hienThongBaoAnh("Đã lưu ảnh GỐC: Thư viện → Pictures/ThuXacChuot/DuLieuHuanLuyen")
        } catch (e: Exception) {
            uri?.let { runCatching { contentResolver.delete(it, null, null) } }
            hienThongBaoAnh("Không lưu được ảnh huấn luyện: ${e.message}")
        }
    }

    private fun hienThongBaoAnh(s: String) = runOnUiThread { if (!isDestroyed) thongBao(s) }

    private fun dinhDangGio(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0)
        return "%02d:%02d:%02d".format(Locale.US, s / 3600L, (s / 60L) % 60L, s % 60L)
    }

    /** CSV do AI tạo chỉ là gợi ý, không phải nhãn huấn luyện do người xác minh. */
    private fun taoCsv(tenVideo: String, danhSach: List<MocAI>) = buildString {
        append("video,time_seconds,label,confidence,verified\n")
        danhSach.forEach { m ->
            append("\"").append(tenVideo.replace("\"", "\"\"")).append("\",")
            append("%.3f".format(Locale.US, m.viTriMs / 1000.0)).append(',')
            append(m.nhan).append(',')
            append("%.3f".format(Locale.US, m.tinCay)).append(",no\n")
        }
    }

    /** Lưu bản sao chỉ mục dễ sao chép về máy tính (không nhúng vào MP4). */
    private fun luuBangMocTuDong(tenVideo: String, moc: List<MocAI>): Boolean {
        val ten = tenVideo.removeSuffix(".mp4") + "_MocAI.csv"
        val csv = taoCsv(tenVideo, moc)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val data = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, ten)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/ThuXacChuot")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, data)
                    ?: error("Không thể tạo CSV")
                try {
                    contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(csv) }
                        ?: error("Không thể ghi CSV")
                    contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                } catch (e: Exception) {
                    runCatching { contentResolver.delete(uri, null, null) }
                    throw e
                }
            } else {
                @Suppress("DEPRECATION")
                val thuMuc = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ThuXacChuot")
                if (!thuMuc.exists() && !thuMuc.mkdirs()) error("Không tạo được thư mục CSV")
                File(thuMuc, ten).writeText(csv, Charsets.UTF_8)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun xuatMocCsv(video: VideoDaLuu) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Mốc AI - ${video.ten}")
            putExtra(Intent.EXTRA_TEXT, taoCsv(video.ten, video.cacMoc))
        }
        try { startActivity(Intent.createChooser(intent, "Gửi/sao chép bảng mốc (CSV)")) }
        catch (e: Exception) { thongBao("Không xuất được mốc: ${e.message}") }
    }

    // Chạy trên một luồng riêng, giữ logic AI hiện tại không thay đổi.
    private fun phanTichKhung(anh: ImageProxy) {
        try {
            val bay = SystemClock.elapsedRealtime()
            val khoang = cacMucToc[chiSoToc].first
            if (khoang > 0 && bay - lanQuetCuoi < khoang) return
            lanQuetCuoi = bay
            val bo = layBoPhatHien() ?: return
            val anhBitmap = anh.toBitmap()
            if (yeuCauAnhGoc) {
                yeuCauAnhGoc = false
                luuAnhGoc(anhBitmap, anh.imageInfo.rotationDegrees)
            }
            val kq = bo.quet(anhBitmap, anh.imageInfo.rotationDegrees)
            val thoiGian = SystemClock.elapsedRealtime()
            if (quayHienTai != null && quayBatDauLuc > 0L) {
                duLieuDangQuay?.them(thoiGian - quayBatDauLuc, kq)
            }
            val diemChuot = kq.khung.asSequence().filter { it.lop == 0 }.maxOfOrNull { it.tinCay }
            chiMucDangQuay?.nhanKetQua(thoiGian, diemChuot)
            phatBip(locBip.kiemTra(thoiGian, diemChuot))
            capNhatSoDo(kq, bo)
        } catch (e: Throwable) {
            loiNap = "${e.javaClass.simpleName}: ${e.message}"
            hienChu("LỖI KHI QUÉT\n$loiNap")
            runOnUiThread {
                nutQuay.isEnabled = false
                if (quayHienTai != null && !dangDungQuay) {
                    dangDungQuay = true
                    quayHienTai?.stop()
                    trangThaiQuay.text = "AI bị lỗi, đã dừng ghi để không tạo bằng chứng sai."
                }
            }
        } finally {
            anh.close()
        }
    }

    private fun layBoPhatHien(): BoPhatHien? {
        boPhatHien?.let { return it }
        if (loiNap != null) return null
        return try {
            BoPhatHien(applicationContext).also { boPhatHien = it }
        } catch (e: Throwable) {
            loiNap = "${e.javaClass.simpleName}: ${e.message}"
            hienChu("LỖI NẠP MÔ HÌNH\n$loiNap")
            null
        }
    }

    private fun lamMuot(cu: Float, moi: Float): Float = if (cu == 0f) moi else cu * 0.9f + moi * 0.1f

    private fun capNhatSoDo(kq: KetQuaQuet, bo: BoPhatHien) {
        val bay = SystemClock.elapsedRealtime()
        tbXuLyAnh = lamMuot(tbXuLyAnh, kq.msXuLyAnh)
        tbAI = lamMuot(tbAI, kq.msAI)
        tbLoc = lamMuot(tbLoc, kq.msLoc)
        demQuet++
        if (bay - mocDem >= 2000) {
            quetMoiGiay = demQuet * 1000f / (bay - mocDem)
            demQuet = 0
            mocDem = bay
        }
        if (!coTheQuetKhiQuay) lopPhu.datKetQua(kq)
        if (bay - lanCapNhatChu < 500) return
        lanCapNhatChu = bay
        val nhiet = docNhietPin()
        if (nhietDau < 0f) nhietDau = nhiet
        val giay = (bay - batDau) / 1000
        val thay = if (kq.khung.isEmpty()) "không thấy gì" else
            "${kq.khung.size} khung, cao nhất ${"%.2f".format(kq.khung.maxOf { it.tinCay })}"
        val kieuToaDo = if (kq.toaDoChuanHoa) "0-1" else "điểm ảnh"
        hienChu(
            "Mô hình: ${bo.nguonMoHinh}\n" +
                "Quét: ${"%.1f".format(quetMoiGiay)} lần/giây | đã chạy ${giay / 60} phút ${giay % 60} giây\n" +
                "Xử lý ảnh ${tbXuLyAnh.toInt()} ms | AI ${tbAI.toInt()} ms | lọc ${tbLoc.toInt()} ms\n" +
                "Nhiệt độ pin: ${"%.1f".format(nhietDau)}°C → ${"%.1f".format(nhiet)}°C\n" +
                "Đang thấy: $thay | tọa độ: $kieuToaDo"
        )
    }

    private fun docNhietPin(): Float {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val t = i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        return if (t < 0) -1f else t / 10f
    }

    private fun hienChu(s: String) { runOnUiThread { if (!isDestroyed) chuThich.text = s } }

    private fun batTatDen() {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) {
            nutDen.text = "Máy không có đèn"
            return
        }
        denBat = !denBat
        cam.cameraControl.enableTorch(denBat)
        nutDen.text = if (denBat) "Đèn pin: BẬT" else "Đèn pin: tắt"
    }

    private fun doiToc() {
        chiSoToc = (chiSoToc + 1) % cacMucToc.size
        nutToc.text = "Quét: ${cacMucToc[chiSoToc].second}"
    }

    override fun onStop() {
        super.onStop()
        // Không hỗ trợ ghi hình ngầm: kết thúc video khi rời khỏi ứng dụng.
        if (quayHienTai != null && !dangDungQuay) {
            dangDungQuay = true
            runCatching { quayHienTai?.stop() }
        }
    }

    override fun onDestroy() {
        boDemGio.removeCallbacks(dongHo)
        runCatching { quayHienTai?.stop() }
        boXuLy.execute { boPhatHien?.dong() }
        boXuLy.shutdown()
        runCatching { cameraProvider?.unbindAll() }
        if (::nutQuay.isInitialized) runCatching { xuatVideo.huy() }
        henBip.removeCallbacksAndMessages(null)
        runCatching { amBao?.release() }
        luongVe.quitSafely()
        super.onDestroy()
    }
}
