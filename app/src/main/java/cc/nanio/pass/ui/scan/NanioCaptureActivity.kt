package cc.nanio.pass.ui.scan




import cc.nanio.pass.Graph
import cc.nanio.pass.R
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

/**
 * 二维码扫描页（竖屏 + 正方形取景框）。
 *
 * 使用 Compose 绘制遮罩/取景框/按钮，BarcodeView 通过 AndroidView 嵌入；
 * 不再依赖 layout XML 与自绘 View。
 */
class NanioCaptureActivity : ComponentActivity() {

    private var barcodeView: BarcodeView? = null

    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            ScanScreen(
                onClose = {
                    setResult(RESULT_CANCELED)
                    finish()
                },
                onBarcodeViewCreated = { view ->
                    barcodeView = view
                    view.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                    view.decodeContinuous(
                        object : BarcodeCallback {
                            override fun barcodeResult(result: BarcodeResult?) {
                                if (handled) return
                                val text = result?.text ?: return
                                handled = true
                                view.pause()
                                val data = Intent().putExtra(EXTRA_SCAN_RESULT, text)
                                setResult(RESULT_OK, data)
                                finish()
                            }

                            override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) = Unit
                        },
                    )
                    view.resume()
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        barcodeView?.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView?.pause()
    }

    companion object {
        const val EXTRA_SCAN_RESULT = "nanio_scan_result"
    }
}

@Composable
private fun ScanScreen(
    onClose: () -> Unit,
    onBarcodeViewCreated: (BarcodeView) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                BarcodeView(context).also(onBarcodeViewCreated)
            },
        )
        ScanOverlay(modifier = Modifier.fillMaxSize())
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 40.dp),
        ) {
            Icon(Icons.Filled.Close, contentDescription = Graph.appContext.getString(R.string.s_close), tint = Color.White)
        }
        Text(
            text = Graph.appContext.getString(R.string.s_place_the_qr_code_within_the_frame_to_scan),
            color = Color.White,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
        )
    }
}

/** 半透明遮罩 + 正方形取景框 + 蓝色四角。 */
@Composable
private fun ScanOverlay(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val maskColor = remember { Color(0xB3000000) }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val side = minOf(with(density) { 300.dp.toPx() }, minOf(w, h) * 0.9f)
        val left = (w - side) / 2f
        val top = h * 0.42f - side / 2f
        val frame = Rect(left, top, left + side, top + side)

        // 四周半透明遮罩
        drawRect(maskColor, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, frame.top))
        drawRect(maskColor, topLeft = Offset(0f, frame.bottom), size = androidx.compose.ui.geometry.Size(w, h - frame.bottom))
        drawRect(maskColor, topLeft = Offset(0f, frame.top), size = androidx.compose.ui.geometry.Size(frame.left, frame.height))
        drawRect(maskColor, topLeft = Offset(frame.right, frame.top), size = androidx.compose.ui.geometry.Size(w - frame.right, frame.height))

        // 白色边框
        drawRect(
            color = Color.White,
            topLeft = frame.topLeft,
            size = frame.size,
            style = Stroke(width = 2.dp.toPx()),
        )

        // 蓝色四角
        val corner = Color(0xFF3482FF)
        val stroke = 4.dp.toPx()
        val len = side * 0.16f
        fun cornerLines(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(corner, Offset(x, y), Offset(x + dx, y), stroke, StrokeCap.Round)
            drawLine(corner, Offset(x, y), Offset(x, y + dy), stroke, StrokeCap.Round)
        }
        cornerLines(frame.left, frame.top, len, len)
        cornerLines(frame.right, frame.top, -len, len)
        cornerLines(frame.left, frame.bottom, len, -len)
        cornerLines(frame.right, frame.bottom, -len, -len)
    }
}
