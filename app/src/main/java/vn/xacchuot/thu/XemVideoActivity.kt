package vn.xacchuot.thu

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import android.net.Uri
import org.json.JSONArray
import java.util.Locale

/** Phát video lớn, danh sách các mốc phía dưới; có nút xem toàn màn hình thực sự. */
class XemVideoActivity : Activity() {
    private lateinit var phat: VideoView
    private lateinit var danhSachMoc: LinearLayout
    private lateinit var thanhTren: LinearLayout
    private var toanManHinh = false
    private var viTriLuu = 0

    private fun dp(i: Int) = (i * resources.displayMetrics.density).toInt()
    private fun fmt(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0L)
        return "%02d:%02d:%02d".format(Locale.US, s / 3600, s / 60 % 60, s % 60)
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        viTriLuu = state?.getInt("position", 0) ?: 0
        val uri = intent.getStringExtra("video") ?: run { finish(); return }
        val title = intent.getStringExtra("name") ?: "Video"
        val items = try { JSONArray(intent.getStringExtra("markers") ?: "[]") } catch (_: Exception) { JSONArray() }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        thanhTren = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = Button(this).apply { text = "‹ Trở lại"; isAllCaps = false; setOnClickListener { finish() } }
        thanhTren.addView(back, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        val t = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            maxLines = 2
        }
        thanhTren.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val large = Button(this).apply {
            text = "⛶ Toàn màn hình"
            textSize = 12f
            isAllCaps = false
            setOnClickListener { chuyenToanManHinh() }
        }
        thanhTren.addView(large, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        root.addView(thanhTren, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val container = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        phat = VideoView(this)
        container.addView(phat, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER))
        val controls = MediaController(this)
        controls.setAnchorView(phat)
        phat.setMediaController(controls)
        root.addView(container, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        danhSachMoc = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(6))
            setBackgroundColor(Color.rgb(25, 25, 25))
        }
        val note = TextView(this).apply {
            text = "${items.length()} mốc xác chuột • Chạm mốc để tua đến trước 2 giây • AI chưa được người kiểm tra"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        danhSachMoc.addView(note)
        val scroll = ScrollView(this)
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (i in 0 until items.length()) {
            val marker = items.optJSONObject(i) ?: continue
            val pos = marker.optLong("viTriMs", 0L)
            val score = marker.optDouble("tinCay", 0.0)
            val btn = Button(this).apply {
                isAllCaps = false
                text = "${i + 1}. ${fmt(pos)}   Xác chuột • ${"%.0f".format(Locale.US, score * 100)}%"
                textSize = 14f
                setOnClickListener {
                    phat.seekTo((pos - 2000L).coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                    phat.start()
                }
            }
            buttons.addView(btn, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        scroll.addView(buttons)
        danhSachMoc.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, dp(175)))
        root.addView(danhSachMoc, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(root)

        phat.setOnPreparedListener {
            if (viTriLuu > 0) phat.seekTo(viTriLuu)
            phat.start()
        }
        phat.setOnErrorListener { _, _, _ ->
            note.text = "Không phát được video này. Hãy thử mở bằng ứng dụng Thư viện."
            true
        }
        phat.setVideoURI(Uri.parse(uri))
    }

    private fun chuyenToanManHinh() {
        toanManHinh = !toanManHinh
        danhSachMoc.visibility = if (toanManHinh) View.GONE else View.VISIBLE
        thanhTren.visibility = if (toanManHinh) View.GONE else View.VISIBLE
        if (toanManHinh) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            phat.setOnClickListener { chuyenToanManHinh() }
        } else {
            window.decorView.systemUiVisibility = 0
            phat.setOnClickListener(null)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("position", phat.currentPosition)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() { if (::phat.isInitialized) phat.pause(); super.onPause() }
    override fun onDestroy() { if (::phat.isInitialized) phat.stopPlayback(); super.onDestroy() }
    override fun onBackPressed() { if (toanManHinh) chuyenToanManHinh() else super.onBackPressed() }
}
