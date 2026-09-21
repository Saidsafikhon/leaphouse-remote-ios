package uz.electro.remote.ui.components

import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Engine
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Трёхмерная машина на главной: GLB с сервера (`/models/<model>.glb`, из 3D-моделей
 * головы Leapmotor), Filament + ModelViewer. Крутится пальцем по горизонтали;
 * цвет кузова — материал «M_Paint», задаётся из настроек.
 *
 * Модель и окружение (IBL) качаются один раз и живут в кеше приложения; пока
 * не скачались — снаружи показывается статичный рендер.
 */
object CarModels {
    private const val BASE = "https://leapmotor.evon.uz/models/"

    fun cacheFile(ctx: Context, name: String): File = File(ctx.cacheDir, "models/$name")

    /** Скачать, если ещё нет; null — не вышло (сеть). */
    suspend fun ensure(ctx: Context, name: String): File? = withContext(Dispatchers.IO) {
        val f = cacheFile(ctx, name)
        if (f.exists() && f.length() > 1024) return@withContext f
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.path + ".part")
            URL(BASE + name).openStream().use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
            tmp.renameTo(f); f
        }.getOrNull()
    }

    /** Эмулятор (SwiftShader) не тянет Filament — там остаёмся на статичной картинке. */
    val supported: Boolean by lazy {
        val hw = android.os.Build.HARDWARE.lowercase(); val fp = android.os.Build.FINGERPRINT.lowercase()
        !(hw.contains("ranchu") || hw.contains("goldfish") || fp.contains("generic") || fp.contains("emulator"))
    }

    /** Имя файла модели по модели машины; null — 3D для неё нет. */
    fun fileFor(model: String?): String? {
        val m = (model ?: "").uppercase()
        return listOf("C16", "C10", "C11", "C01").firstOrNull { m.contains(it) }?.let { it.lowercase() + ".glb" }
    }
}

@Composable
fun CarModelView(model: String?, paint: Color, modifier: Modifier = Modifier, onReady: (Boolean) -> Unit = {}) {
    if (!CarModels.supported) return
    val name = CarModels.fileFor(model) ?: return
    var files by remember(name) { mutableStateOf<Pair<File, File>?>(null) }
    val ctx0 = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(name) {
        val glb = CarModels.ensure(ctx0, name)
        val ibl = CarModels.ensure(ctx0, "env_ibl.ktx")
        files = if (glb != null && ibl != null) glb to ibl else null
        onReady(files != null)
    }
    val ready = files ?: return
    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> FilamentCarView(ctx).also { it.load(ready.first, ready.second) } },
            update = { it.setPaint(paint) },
            onRelease = { it.destroy() },
        )
    }
}

/** SurfaceView с Filament: загрузка GLB, орбита пальцем, перекраска кузова. */
class FilamentCarView(ctx: Context) : SurfaceView(ctx) {
    private var viewer: ModelViewer? = null
    private var engine: Engine? = null
    private var uiHelper: com.google.android.filament.android.UiHelper? = null
    private var frameCallback: android.view.Choreographer.FrameCallback? = null
    private var pendingPaint: Color? = null
    private var downX = 0f
    private var yaw = 0.0f

    init {
        Utils.init()
        // прозрачный фон — машина лежит на карточке экрана, а не в своём небе
        setZOrderMediaOverlay(true); holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
    }

    fun load(glb: File, ibl: File) {
        val engine = Engine.create().also { this.engine = it }
        // Камера: трёхчетвертной ракурс чуть сверху, модель ModelViewer ставит в (0,0,-4)
        val manip = com.google.android.filament.utils.Manipulator.Builder()
            .targetPosition(0f, 0f, -4f).orbitHomePosition(-1.5f, 0.75f, -5.55f)
            .viewport(maxOf(width, 1), maxOf(height, 1))
            .build(com.google.android.filament.utils.Manipulator.Mode.ORBIT)
        val ui = com.google.android.filament.android.UiHelper(com.google.android.filament.android.UiHelper.ContextErrorPolicy.DONT_CHECK)
            .apply { isOpaque = false }   // прозрачный swapchain — иначе фон чёрный
        uiHelper = ui
        val viewer = ModelViewer(this, engine, ui, manip).also { this.viewer = it }
        // ModelViewer вешает свой orbit/zoom на касания — нам нужен только поворот, свой
        setOnTouchListener(null)
        viewer.scene.skybox = null
        viewer.view.blendMode = com.google.android.filament.View.BlendMode.TRANSLUCENT
        viewer.renderer.clearOptions = viewer.renderer.clearOptions.apply { clear = true; clearColor = floatArrayOf(0f, 0f, 0f, 0f) }
        val ktx = ByteBuffer.wrap(ibl.readBytes())
        viewer.scene.indirectLight = KTX1Loader.createIndirectLight(engine, ktx).apply { intensity = 22_000f }
        viewer.loadModelGlb(ByteBuffer.wrap(glb.readBytes()))
        viewer.transformToUnitCube()
        pendingPaint?.let { applyPaint(it) }
        val cb = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(t: Long) {
                android.view.Choreographer.getInstance().postFrameCallback(this)
                viewer.render(t)
            }
        }
        frameCallback = cb
        android.view.Choreographer.getInstance().postFrameCallback(cb)
    }

    fun setPaint(c: Color) { pendingPaint = c; applyPaint(c) }

    private fun applyPaint(c: Color) {
        val v = viewer ?: return
        val asset = v.asset ?: return
        val rm = v.engine.renderableManager
        for (e in asset.entities) {
            if (!rm.hasComponent(e)) continue
            val inst = rm.getInstance(e)
            for (i in 0 until rm.getPrimitiveCount(inst)) {
                val mi = rm.getMaterialInstanceAt(inst, i)
                if (mi.name == "M_Paint") mi.setParameter("baseColorFactor", c.red, c.green, c.blue, 1f)
            }
        }
    }

    private var downY = 0f
    private var dragging = false
    private val slop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop

    private var pitch = 0f

    /** Палец крутит машину: по горизонтали — вокруг вертикальной оси, по вертикали — наклон (ограничен,
     *  чтобы не смотреть снизу). Пока идёт вращение, страница не скроллится. */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val v = viewer ?: return false
        when (event.actionMasked) {
            // Забираем жест сразу на DOWN: иначе Compose-скролл перехватит вертикальное движение раньше нас
            MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; dragging = false; parent?.requestDisallowInterceptTouchEvent(true); return true }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) { dragging = true; downX = event.x; downY = event.y }
                    return true
                }
                val dx = event.x - downX; downX = event.x
                val dy = event.y - downY; downY = event.y
                yaw += dx * 0.01f
                pitch = (pitch + dy * 0.006f).coerceIn(-0.35f, 0.9f)
                applyYaw(v)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragging = false; parent?.requestDisallowInterceptTouchEvent(false) }
        }
        return true
    }

    private var baseCache: FloatArray? = null
    /** Поворот вокруг центра модели (0,0,-4 — туда её ставит transformToUnitCube), а не вокруг начала координат. */
    private fun applyYaw(v: ModelViewer) {
        val asset = v.asset ?: return
        val tm = v.engine.transformManager
        val root = tm.getInstance(asset.root)
        val base = baseCache ?: tm.getTransform(root, FloatArray(16)).also { baseCache = it.copyOf() }
        val ry = FloatArray(16); android.opengl.Matrix.setRotateM(ry, 0, Math.toDegrees(yaw.toDouble()).toFloat(), 0f, 1f, 0f)
        // наклон — вокруг «правой» оси камеры (камера стоит в (-1.5,0.75,-1.55) от центра), чтобы тянуть как орбиту
        val rp = FloatArray(16); android.opengl.Matrix.setRotateM(rp, 0, Math.toDegrees(pitch.toDouble()).toFloat(), -0.718f, 0f, 0.696f)
        val r = FloatArray(16); android.opengl.Matrix.multiplyMM(r, 0, rp, 0, ry, 0)
        val toC = FloatArray(16); android.opengl.Matrix.setIdentityM(toC, 0); android.opengl.Matrix.translateM(toC, 0, 0f, 0f, -4f)
        val fromC = FloatArray(16); android.opengl.Matrix.setIdentityM(fromC, 0); android.opengl.Matrix.translateM(fromC, 0, 0f, 0f, 4f)
        val t1 = FloatArray(16); val t2 = FloatArray(16); val out = FloatArray(16)
        android.opengl.Matrix.multiplyMM(t1, 0, r, 0, fromC, 0)      // R · T(-c)
        android.opengl.Matrix.multiplyMM(t2, 0, toC, 0, t1, 0)       // T(c) · R · T(-c)
        android.opengl.Matrix.multiplyMM(out, 0, t2, 0, base, 0)     // · base
        tm.setTransform(root, out)
    }

    /** Compose отпустил view. Движок, swapchain и модель ModelViewer уничтожает сам при снятии
     *  view с окна (onViewDetachedFromWindow) — трогать engine здесь нельзя, будет двойной destroy. */
    fun destroy() {
        frameCallback?.let { android.view.Choreographer.getInstance().removeFrameCallback(it) }; frameCallback = null
        viewer = null; engine = null; uiHelper = null
    }
}
