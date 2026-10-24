package vn.xacchuot.thu

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
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
 * Camera + AI lan3 + quay MP4 có khung nhận diện được ghi vĩnh viễn trong video.
 * Nếu điện thoại không hỗ trợ AI + quay + hiệu ứng đồng thời, KHÔNG cho quay
 * video thường dưới tên video AI để tránh bằng chứng sai lệch.
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
    @Volatile private var yeuCauAnhGoc = false

    private val boXuLy = Executors.newSingleThreadExecutor()
    private val luongVe = HandlerThread("VeKhungVaoVideo").apply { start() }
    private var khungVideo: KhungVideoAI? = null
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
            trangThaiQuay.text = "● ĐANG QUAY  %02d:%02d".format(Locale.US, giay / 60, giay % 60) +
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
            text = "Chờ camera và AI • Video sẽ lưu cả khung nhận diện"
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

        nutVideo = taoNut("VIDEO ĐÃ LƯU", Color.rgb(30, 119, 200))
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

    /** Chỉ bật nút quay khi camera hỗ trợ ghi video AI thực sự. */
    private fun bindCameraAI(provider: ProcessCameraProvider, quality: Quality): Boolean {
        var effect: KhungVideoAI? = null
        return try {
            val actualEffect = KhungVideoAI(Handler(luongVe.looper)) { err ->
                runOnUiThread {
                    // Bỏ qua lỗi trễ từ hiệu ứng HD đã đóng khi thử lại ở SD.
                    if (khungVideo !== effect) return@runOnUiThread
                    coTheQuetKhiQuay = false
                    nutQuay.isEnabled = false
                    trangThaiQuay.text = "Lỗi ghi khung AI: ${err.message ?: err.javaClass.simpleName}"
                    // Không tiếp tục ghi video không còn khung AI.
                    quayHienTai?.stop()
                }
            }
            effect = actualEffect
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(quality))
                .build()
            val video = VideoCapture.withOutput(recorder)
            val preview = taoPreview()
            val group = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(taoPhanTich())
                .addUseCase(video)
                .addEffect(actualEffect.hieuUng)
                .build()
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, group)
            khungVideo = actualEffect
            videoCapture = video
            coTheQuetKhiQuay = true
            lopPhu.visibility = View.GONE // Cùng một hiệu ứng được vẽ trên preview và MP4.
            trangThaiQuay.text = "Sẵn sàng • MP4 ghi khung AI (${if (quality == Quality.HD) "HD" else "SD"})"
            true
        } catch (e: Exception) {
            provider.unbindAll()
            runCatching { effect?.close() }
            false
        }
    }

    private fun batDauCamera() {
        val tuongLai = ProcessCameraProvider.getInstance(this)
        tuongLai.addListener({
            try {
                val provider = tuongLai.get()
                cameraProvider = provider
                khungVideo?.close()
                khungVideo = null
                videoCapture = null
                coTheQuetKhiQuay = false
                nutQuay.isEnabled = false

                // Ưu tiên HD; thử SD nếu Samsung đời cũ không ghép đủ use case ở HD.
                if (bindCameraAI(provider, Quality.HD) || bindCameraAI(provider, Quality.SD)) {
                    chuThich.text = "Đang nạp mô hình AI..."
                    nutQuay.isEnabled = true
                } else {
                    // Xem trước và AI vẫn hoạt động, nhưng KHÔNG âm thầm tắt AI khi quay.
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, taoPreview(), taoPhanTich()
                    )
                    lopPhu.visibility = View.VISIBLE
                    trangThaiQuay.text = "Máy không hỗ trợ ghi video có khung AI trực tiếp. Không quay video thường."
                    chuThich.text = "AI vẫn nhận diện trên màn hình, nhưng máy này không hỗ trợ ghi khung vào MP4."
                    nutQuay.isEnabled = false
                }
            } catch (e: Exception) {
                nutQuay.isEnabled = false
                chuThich.text = "LỖI MỞ CAMERA\n${e.javaClass.simpleName}: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
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
        val video = videoCapture ?: run {
            thongBao("Camera chưa sẵn sàng, hãy mở lại ứng dụng.")
            return
        }
        if (!coTheQuetKhiQuay || khungVideo == null) {
            thongBao("Máy không hỗ trợ quay MP4 có khung AI. Không ghi video không có nhận diện.")
            return
        }
        if (boPhatHien == null || loiNap != null) {
            thongBao("AI chưa sẵn sàng hoặc nạp mô hình bị lỗi. Kiểm tra màn hình rồi thử lại.")
            return
        }
        try {
            val ten = "XacChuot_AI_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.mp4"
            val noiDung = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, ten)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ThuXacChuot")
                } else {
                    @Suppress("DEPRECATION")
                    val thuMuc = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "ThuXacChuot")
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
                        nutQuay.text = "■ DỪNG QUAY"
                        nutQuay.isEnabled = coTheQuetKhiQuay
                        trangThaiQuay.text = if (coAmThanh) "● ĐANG QUAY có âm thanh" else "● ĐANG QUAY không âm thanh"
                        boDemGio.removeCallbacks(dongHo)
                        boDemGio.post(dongHo)
                    }
                    is VideoRecordEvent.Finalize -> {
                        boDemGio.removeCallbacks(dongHo)
                        quayHienTai = null
                        val cacMocQuay = chiMucDangQuay?.danhSach().orEmpty()
                        chiMucDangQuay = null
                        quayBatDauLuc = 0L
                        dangDungQuay = false
                        nutQuay.text = "● BẮT ĐẦU QUAY"
                        nutQuay.isEnabled = coTheQuetKhiQuay
                        if (!suKien.hasError()) {
                            val uri = suKien.outputResults.outputUri
                            if (uri != Uri.EMPTY) {
                                themVideoDaLuu(uri, tenVideoDangQuay, cacMocQuay)
                                val daLuuCsv = luuBangMocTuDong(tenVideoDangQuay, cacMocQuay)
                                trangThaiQuay.text = "Đã lưu: $tenVideoDangQuay (${cacMocQuay.size} mốc AI)" +
                                    (if (daLuuCsv) " • Mốc CSV: Download/ThuXacChuot" else " • Chưa lưu CSV, dùng mục Xuất mốc")
                                Toast.makeText(this, "Đã lưu video vào thư viện!", Toast.LENGTH_LONG).show()
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
            nutQuay.isEnabled = coTheQuetKhiQuay
            nutQuay.text = "● BẮT ĐẦU QUAY"
            trangThaiQuay.text = "Không thể bắt đầu quay AI: ${e.message}"
        }
    }

    private fun thongBao(noiDung: String) {
        Toast.makeText(this, noiDung, Toast.LENGTH_LONG).show()
        trangThaiQuay.text = noiDung
    }

    // Danh sách chỉ chứa video do chính ứng dụng này quay, không cần quyền đọc thư viện.
    private data class VideoDaLuu(val uri: Uri, val ten: String, val cacMoc: List<MocAI> = emptyList())
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
                ketQua.add(VideoDaLuu(Uri.parse(item.getString("uri")), item.getString("ten"), moc))
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
            mang.put(JSONObject().put("uri", video.uri.toString()).put("ten", video.ten).put("cacMoc", cacMoc))
        }
        getSharedPreferences(tenPrefs, MODE_PRIVATE).edit().putString(khoaDanhSach, mang.toString()).apply()
    }

    private fun themVideoDaLuu(uri: Uri, ten: String, cacMoc: List<MocAI>) {
        val danhSach = layDanhSachVideo()
        danhSach.add(0, VideoDaLuu(uri, ten, cacMoc))
        luuDanhSachVideo(danhSach)
    }

    private fun moDanhSachVideo() {
        if (quayHienTai != null) {
            thongBao("Hãy dừng và lưu video hiện tại trước khi xem lại.")
            return
        }
        val ds = layDanhSachVideo()
        if (ds.isEmpty()) {
            thongBao("Chưa có video nào. Bấm BẮT ĐẦU QUAY để tạo video.")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Video đã quay (${ds.size})")
            .setItems(ds.map { "${it.ten}  •  ${it.cacMoc.size} mốc" }.toTypedArray()) { _, i -> chonVideo(ds[i]) }
            .setNegativeButton("Đóng", null)
            .show()
    }

    private fun chonVideo(video: VideoDaLuu) {
        AlertDialog.Builder(this)
            .setTitle(video.ten)
            .setItems(arrayOf("▶ Xem + nhảy đến mốc AI", "↗ Chia sẻ video", "Xuất danh sách mốc CSV", "Xóa video")) { _, i ->
                when (i) {
                    0 -> moTrinhPhatCoMoc(video)
                    1 -> try {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, video.uri)
                            clipData = ClipData.newUri(contentResolver, "Video", video.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(intent, "Chia sẻ video"))
                    } catch (e: Exception) { thongBao("Không chia sẻ được: ${e.message}") }
                    2 -> xuatMocCsv(video)
                    3 -> AlertDialog.Builder(this)
                        .setTitle("Xóa video?")
                        .setMessage("Xóa vĩnh viễn ${video.ten} khỏi điện thoại?")
                        .setPositiveButton("Xóa") { _, _ ->
                            try {
                                contentResolver.delete(video.uri, null, null)
                                luuDanhSachVideo(layDanhSachVideo().filterNot { it.uri == video.uri })
                                thongBao("Đã xóa video")
                            } catch (e: Exception) { thongBao("Không xóa được: ${e.message}") }
                        }
                        .setNegativeButton("Hủy", null)
                        .show()
                }
            }
            .setNegativeButton("Đóng", null)
            .show()
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

    /** Phát ngay video trong app; chạm mốc sẽ nhảy đến 2 giây trước lúc AI nhìn thấy chuột. */
    private fun moTrinhPhatCoMoc(video: VideoDaLuu) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val phat = VideoView(this)
        root.addView(phat, LinearLayout.LayoutParams(MATCH_PARENT, dp(225)))
        val dieuKhien = MediaController(this)
        dieuKhien.setAnchorView(phat)
        phat.setMediaController(dieuKhien)
        val moTa = TextView(this).apply {
            text = if (video.cacMoc.isEmpty())
                "Video chưa có chỉ mục AI. Bản cũ không thể tự tạo mốc nếu chưa phân tích lại video."
            else "Chạm mốc để tua trước 2 giây. Mốc do AI dự đoán, chưa xác minh."
            textSize = 13f
            setPadding(dp(6), dp(8), dp(6), dp(6))
        }
        root.addView(moTa)
        val khuMoc = ScrollView(this)
        val dsNut = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val cacMoc = video.cacMoc.sortedBy { it.viTriMs }
        cacMoc.forEachIndexed { index, moc ->
            val btn = Button(this).apply {
                isAllCaps = false
                text = "${index + 1}. ${dinhDangGio(moc.viTriMs)} • ${moc.nhan} (${(moc.tinCay * 100).toInt()}%)"
                textSize = 13f
                setOnClickListener {
                    phat.seekTo((moc.viTriMs - 2000L).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    phat.start()
                }
            }
            dsNut.addView(btn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        khuMoc.addView(dsNut)
        root.addView(khuMoc, LinearLayout.LayoutParams(MATCH_PARENT, dp(190)))
        val hop = AlertDialog.Builder(this)
            .setTitle("Các lần AI phát hiện: ${video.cacMoc.size}")
            .setView(root)
            .setPositiveButton("Đóng", null)
            .create()
        phat.setOnPreparedListener { phat.start() }
        phat.setOnErrorListener { _, _, _ ->
            moTa.text = "Không phát được video này. Bạn vẫn có thể dùng mục Chia sẻ video."
            true
        }
        hop.setOnDismissListener { phat.stopPlayback() }
        hop.show()
        try { phat.setVideoURI(video.uri) }
        catch (e: Exception) { moTa.text = "Không mở được MP4: ${e.message}" }
    }

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
            khungVideo?.capNhat(kq, anh) // Tọa độ cảm biến cho OverlayEffect => lưu vào MP4.
            val diemChuot = kq.khung.asSequence().filter { it.lop == 0 }.maxOfOrNull { it.tinCay }
            chiMucDangQuay?.nhanKetQua(SystemClock.elapsedRealtime(), diemChuot)
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
        runCatching { khungVideo?.close() }
        luongVe.quitSafely()
        super.onDestroy()
    }
}
