package uz.electro.remote.ui.components

import android.content.Context
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.filament.Engine
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.Manipulator
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Трёхмерная машина на главной: GLB с сервера (`/models/<model>.glb`, из 3D-моделей
 * головы Leapmotor), Filament + ModelViewer. Крутится пальцем (поворот + наклон);
 * цвет кузова — материал «M_Paint» (у C01 — «M_CarPaint»), задаётся из настроек.
 *
 * Модель и окружение (IBL) качаются один раз и живут в кеше приложения; пока не
 * отрисован первый кадр — снаружи показывается статичный рендер. Сам Filament-view
 * живёт в [CarViewCache] и переиспользуется между экранами, чтобы возврат на главную
 * не перезагружал модель.
 */
object CarModels {
    private const val BASE = "https://leapmotor.evon.uz/models/"
    const val IBL = "env_ibl.ktx"
    /** Поднимать при перевыпуске GLB на сервере — старый кеш на телефонах сотрётся. */
    private const val VERSION = 2

    fun cacheFile(ctx: Context, name: String): File {
        val root = File(ctx.cacheDir, "models")
        root.listFiles()?.filter { it.name != "v$VERSION" }?.forEach { it.deleteRecursively() }
        return File(root, "v$VERSION/$name")
    }

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

    /** Заранее подтянуть модель + окружение (зовётся с экрана подключения, чтобы на главной всё уже было). */
    suspend fun prefetch(ctx: Context, model: String?) {
        if (!supported) return
        val name = fileFor(model) ?: return
        ensure(ctx, name); ensure(ctx, IBL)
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

/** Один живой Filament-view на процесс: движок и модель не пересоздаются при смене экранов. */
private object CarViewCache {
    var view: FilamentCarView? = null
    var name: String? = null

    fun obtain(ctx: Context, name: String, glb: File, ibl: File): FilamentCarView {
        view?.let { v ->
            if (this.name == name) { (v.parent as? ViewGroup)?.removeView(v); return v }
            v.dispose(); view = null
        }
        return FilamentCarView(ctx.applicationContext).also { it.load(glb, ibl); view = it; this.name = name }
    }
}

@Composable
fun CarModelView(model: String?, paint: Color, modifier: Modifier = Modifier, onReady: (Boolean) -> Unit = {}) {
    if (!CarModels.supported) return
    val name = CarModels.fileFor(model) ?: return
    var files by remember(name) { mutableStateOf<Pair<File, File>?>(null) }
    val ctx0 = LocalContext.current
    LaunchedEffect(name) {
        val glb = CarModels.ensure(ctx0, name)
        val ibl = CarModels.ensure(ctx0, CarModels.IBL)
        files = if (glb != null && ibl != null) glb to ibl else null
        if (files == null) onReady(false)
    }
    val ready = files ?: return
    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                CarViewCache.obtain(ctx, name, ready.first, ready.second).also { v ->
                    v.onFirstFrame = { onReady(true) }
                    if (v.hasRendered) onReady(true)
                }
            },
            update = { it.setPaint(paint) },
            onRelease = { it.onFirstFrame = null },
        )
    }
}

/** SurfaceView с Filament: загрузка GLB, орбита пальцем, перекраска кузова. */
class FilamentCarView(ctx: Context) : SurfaceView(ctx) {
    private var viewer: ModelViewer? = null
    private var engine: Engine? = null
    private var uiHelper: UiHelper? = null
    private var pendingPaint: Color? = null

    /** Стартовый ракурс — как на статичном рендере экрана подключения: три четверти спереди-слева, чуть сверху, нос влево. Одинаковый для всех моделей. */
    private companion object {
        const val EYE_X = -1.45f; const val EYE_Y = 0.45f; const val EYE_Z = -2.2f   // цель в (0,0,-4)
    }

    // ось наклона = «правая» ось камеры, чтобы вертикальный свайп работал как орбита
    private val rightAxis: FloatArray = run {
        val fx = -EYE_X; val fz = -4f - EYE_Z                          // forward = target - eye (без y)
        val rx = -fz; val rz = fx                                       // forward × up(0,1,0)
        val n = sqrt(rx * rx + rz * rz); floatArrayOf(rx / n, 0f, rz / n)
    }

    /** Первый настоящий кадр отрисован (модель и текстуры на месте) — можно убирать статичную картинку. */
    var hasRendered = false; private set
    var onFirstFrame: (() -> Unit)? = null

    // ModelViewer вешает на view слушатель detach и по нему убивает движок — перехватываем его,
    // чтобы движок жил, пока view лежит в кеше, а не пересоздавался при каждом уходе с экрана.
    private var viewerDetachListener: View.OnAttachStateChangeListener? = null
    private var capturing = false

    override fun addOnAttachStateChangeListener(listener: View.OnAttachStateChangeListener?) {
        if (capturing && listener != null) { viewerDetachListener = listener; return }
        super.addOnAttachStateChangeListener(listener)
    }

    init {
        Utils.init()
        // прозрачный фон — машина лежит на карточке экрана, а не в своём небе
        setZOrderMediaOverlay(true); holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        super.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { renderUntil = System.nanoTime() + 2_000_000_000L; ensureLoop() }
            override fun onViewDetachedFromWindow(v: View) { stopLoop() }
        })
    }

    fun load(glb: File, ibl: File) {
        val engine = Engine.create().also { this.engine = it }
        val manip = Manipulator.Builder()
            .targetPosition(0f, 0f, -4f).orbitHomePosition(EYE_X, EYE_Y, EYE_Z)
            .viewport(maxOf(width, 1), maxOf(height, 1))
            .build(Manipulator.Mode.ORBIT)
        val ui = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque = false }   // прозрачный swapchain
        uiHelper = ui
        capturing = true
        val viewer = ModelViewer(this, engine, ui, manip).also { this.viewer = it }
        capturing = false
        // ModelViewer вешает свой orbit/zoom на касания — нам нужен только свой поворот
        setOnTouchListener(null)
        viewer.scene.skybox = null
        viewer.view.blendMode = com.google.android.filament.View.BlendMode.TRANSLUCENT
        viewer.renderer.clearOptions = viewer.renderer.clearOptions.apply { clear = true; clearColor = floatArrayOf(0f, 0f, 0f, 0f) }
        viewer.scene.indirectLight = KTX1Loader.createIndirectLight(engine, ByteBuffer.wrap(ibl.readBytes())).apply { intensity = 22_000f }
        viewer.loadModelGlb(ByteBuffer.wrap(glb.readBytes()))
        viewer.transformToUnitCube()
        pendingPaint?.let { applyPaint(it) }
        renderUntil = System.nanoTime() + 3_000_000_000L
        ensureLoop()
    }

    // ---- цикл кадров: рисуем только пока что-то меняется (загрузка, жест), иначе спим ----
    private var frameCallback: Choreographer.FrameCallback? = null
    private var renderUntil = 0L
    private var framesDone = 0

    private fun ensureLoop() {
        if (frameCallback != null || viewer == null) return
        val cb = object : Choreographer.FrameCallback {
            override fun doFrame(t: Long) {
                val v = viewer ?: return
                if (!isAttachedToWindow) { frameCallback = null; return }
                v.render(t)
                framesDone++
                if (!hasRendered && v.progress >= 1f && framesDone > 2) { hasRendered = true; onFirstFrame?.invoke() }
                if (t < renderUntil || v.progress < 1f) Choreographer.getInstance().postFrameCallback(this) else frameCallback = null
            }
        }
        frameCallback = cb
        Choreographer.getInstance().postFrameCallback(cb)
    }

    private fun stopLoop() { frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }; frameCallback = null }

    private fun wake(ms: Long = 250) { renderUntil = maxOf(renderUntil, System.nanoTime() + ms * 1_000_000L); ensureLoop() }

    fun setPaint(c: Color) { if (pendingPaint == c) return; pendingPaint = c; applyPaint(c); wake() }

    private fun applyPaint(c: Color) {
        val v = viewer ?: return
        val asset = v.asset ?: return
        val rm = v.engine.renderableManager
        for (e in asset.entities) {
            if (!rm.hasComponent(e)) continue
            val inst = rm.getInstance(e)
            for (i in 0 until rm.getPrimitiveCount(inst)) {
                val mi = rm.getMaterialInstanceAt(inst, i)
                if (mi.name == "M_Paint" || mi.name == "M_CarPaint") mi.setParameter("baseColorFactor", c.red, c.green, c.blue, 1f)
            }
        }
    }

    // ---- жест ----
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private val slop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop
    private var yaw = 0f
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
                applyPose(v); wake()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { dragging = false; parent?.requestDisallowInterceptTouchEvent(false) }
        }
        return true
    }

    private var baseCache: FloatArray? = null
    /** Поворот вокруг центра модели (0,0,-4 — туда её ставит transformToUnitCube), а не вокруг начала координат. */
    private fun applyPose(v: ModelViewer) {
        val asset = v.asset ?: return
        val tm = v.engine.transformManager
        val root = tm.getInstance(asset.root)
        val base = baseCache ?: tm.getTransform(root, FloatArray(16)).also { baseCache = it.copyOf() }
        val ry = FloatArray(16); android.opengl.Matrix.setRotateM(ry, 0, Math.toDegrees(yaw.toDouble()).toFloat(), 0f, 1f, 0f)
        val rp = FloatArray(16); android.opengl.Matrix.setRotateM(rp, 0, Math.toDegrees(pitch.toDouble()).toFloat(), rightAxis[0], rightAxis[1], rightAxis[2])
        val r = FloatArray(16); android.opengl.Matrix.multiplyMM(r, 0, rp, 0, ry, 0)
        val toC = FloatArray(16); android.opengl.Matrix.setIdentityM(toC, 0); android.opengl.Matrix.translateM(toC, 0, 0f, 0f, -4f)
        val fromC = FloatArray(16); android.opengl.Matrix.setIdentityM(fromC, 0); android.opengl.Matrix.translateM(fromC, 0, 0f, 0f, 4f)
        val t1 = FloatArray(16); val t2 = FloatArray(16); val out = FloatArray(16)
        android.opengl.Matrix.multiplyMM(t1, 0, r, 0, fromC, 0)      // R · T(-c)
        android.opengl.Matrix.multiplyMM(t2, 0, toC, 0, t1, 0)       // T(c) · R · T(-c)
        android.opengl.Matrix.multiplyMM(out, 0, t2, 0, base, 0)     // · base
        tm.setTransform(root, out)
    }

    /** Полное уничтожение (смена модели): отдаём ModelViewer его же detach-обработчик. */
    fun dispose() {
        stopLoop()
        (parent as? ViewGroup)?.removeView(this)
        viewerDetachListener?.onViewDetachedFromWindow(this); viewerDetachListener = null
        viewer = null; engine = null; uiHelper = null
    }
}
