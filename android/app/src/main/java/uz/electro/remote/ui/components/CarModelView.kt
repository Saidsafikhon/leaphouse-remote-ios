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

/** Что открыто на машине — 3D-модель открывает эти детали на петлях. */
data class BodyPose(
    val doorFL: Boolean = false, val doorFR: Boolean = false,
    val doorRL: Boolean = false, val doorRR: Boolean = false,
    val trunk: Boolean = false, val hood: Boolean = false,
) {
    val anyOpen: Boolean get() = doorFL || doorFR || doorRL || doorRR || trunk || hood
}

@Composable
fun CarModelView(
    model: String?, paint: Color, modifier: Modifier = Modifier,
    body: BodyPose = BodyPose(), onReady: (Boolean) -> Unit = {},
) {
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
            update = { it.setPaint(paint); it.setBody(body) },
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

    /** Стартовый ракурс (выбран владельцем 21.09): чистый вид сбоку, нос влево, чуть сверху. Одинаковый для всех моделей.
     *  03.10: камера ближе (2,0 → 1,6) — машина крупнее, вокруг меньше пустого поля. */
    private companion object {
        const val EYE_X = 0f; const val EYE_Y = 0.25f; const val EYE_Z = -2.4f   // цель в (0,0,-4)
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
        parts = null
        applyBody(snap = true)
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
                stepParts(v, t)
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

    // ---- двери, капот, багажник на петлях ----

    /**
     * Группа деталей, которая открывается как одно целое. Петли в GLB нет (иерархия
     * плоская, геометрия в координатах модели), поэтому точку и ось петли считаем по
     * габаритам группы. Ось у всех наших моделей одна: нос в −X, верх +Y, левый борт +Z.
     */
    private class Part(val entities: IntArray, val pivot: FloatArray, val axis: FloatArray, val maxDeg: Float) {
        var t = 0f        // 0 — закрыто, 1 — открыто
        var target = 0f
    }

    private var parts: Map<String, Part>? = null
    private var pose = BodyPose()
    private var animLast = 0L

    /** Имя узла → группа. C16/C11/C01: Door_LF_…, Body_M_Bonnet, Body_M_Trunk…; C10: l_frontdoor…, bonnet, trunk. */
    private fun groupOf(name: String): String? {
        val n = name.lowercase()
        return when {
            n.startsWith("door_lf") || n.startsWith("interactive_lf") || n.startsWith("l_frontdoor") -> "fl"
            n.startsWith("door_rf") || n.startsWith("interactive_rf") || n.startsWith("r_frontdoor") ||
                n.startsWith("door_front_r") -> "fr"
            n.startsWith("door_lr") || n.startsWith("interactive_lr") || n.startsWith("l_reardoor") -> "rl"
            n.startsWith("door_rr") || n.startsWith("interactive_rr") || n.startsWith("r_reardoor") -> "rr"
            n.contains("bonnet") -> "hood"
            n.startsWith("body_m_trunk") || n.startsWith("trunk") -> "trunk"
            else -> null
        }
    }

    private fun buildParts(v: ModelViewer): Map<String, Part> {
        val asset = v.asset ?: return emptyMap()
        val rm = v.engine.renderableManager
        val ents = HashMap<String, MutableList<Int>>()
        val mn = HashMap<String, FloatArray>(); val mx = HashMap<String, FloatArray>()
        val box = com.google.android.filament.Box()
        for (e in asset.entities) {
            val g = groupOf(asset.getName(e) ?: continue) ?: continue
            ents.getOrPut(g) { mutableListOf() }.add(e)
            if (!rm.hasComponent(e)) continue
            rm.getAxisAlignedBoundingBox(rm.getInstance(e), box)
            val c = box.center; val h = box.halfExtent
            val lo = mn.getOrPut(g) { floatArrayOf(1e9f, 1e9f, 1e9f) }
            val hi = mx.getOrPut(g) { floatArrayOf(-1e9f, -1e9f, -1e9f) }
            for (k in 0..2) { lo[k] = minOf(lo[k], c[k] - h[k]); hi[k] = maxOf(hi[k], c[k] + h[k]) }
        }
        val out = HashMap<String, Part>()
        for ((g, list) in ents) {
            val lo = mn[g] ?: continue; val hi = mx[g] ?: continue
            val midY = (lo[1] + hi[1]) / 2f
            val part = when (g) {
                // двери: петля на переднем крае, у наружной поверхности; поворот вокруг вертикали наружу
                "fl", "rl" -> Part(list.toIntArray(), floatArrayOf(lo[0], midY, hi[2]), floatArrayOf(0f, 1f, 0f), -55f)
                "fr", "rr" -> Part(list.toIntArray(), floatArrayOf(lo[0], midY, lo[2]), floatArrayOf(0f, 1f, 0f), 55f)
                // капот: петля у лобового стекла (задний верхний край), передний край вверх
                "hood" -> Part(list.toIntArray(), floatArrayOf(hi[0], hi[1], 0f), floatArrayOf(0f, 0f, 1f), -40f)
                // багажник: петля сверху у крыши (передний верхний край), низ уходит вверх-назад
                "trunk" -> Part(list.toIntArray(), floatArrayOf(lo[0], hi[1], 0f), floatArrayOf(0f, 0f, 1f), 65f)
                else -> null
            } ?: continue
            out[g] = part
        }
        return out
    }

    fun setBody(b: BodyPose) { if (b == pose) return; pose = b; applyBody(snap = false) }

    private fun applyBody(snap: Boolean) {
        val v = viewer ?: return
        val ps = parts ?: buildParts(v).also { parts = it }
        ps["fl"]?.target = if (pose.doorFL) 1f else 0f
        ps["fr"]?.target = if (pose.doorFR) 1f else 0f
        ps["rl"]?.target = if (pose.doorRL) 1f else 0f
        ps["rr"]?.target = if (pose.doorRR) 1f else 0f
        ps["trunk"]?.target = if (pose.trunk) 1f else 0f
        ps["hood"]?.target = if (pose.hood) 1f else 0f
        if (snap) { ps.values.forEach { it.t = it.target }; placeParts(v, ps) }
        animLast = 0L
        wake(900)
    }

    /** Шаг анимации: ~0,6 с на полное открытие; зовётся из цикла кадров. */
    private fun stepParts(v: ModelViewer, frameNanos: Long) {
        val ps = parts ?: return
        val dt = if (animLast == 0L) 0f else ((frameNanos - animLast) / 1e9f).coerceIn(0f, 0.1f)
        animLast = frameNanos
        var moving = false
        for (p in ps.values) {
            if (p.t == p.target) continue
            val step = dt / 0.6f
            p.t = if (p.target > p.t) minOf(p.target, p.t + step) else maxOf(p.target, p.t - step)
            moving = true
        }
        if (moving) { placeParts(v, ps); wake(100) }
    }

    private fun placeParts(v: ModelViewer, ps: Map<String, Part>) {
        val tm = v.engine.transformManager
        val m = FloatArray(16); val r = FloatArray(16); val t1 = FloatArray(16); val tmp = FloatArray(16)
        for (p in ps.values) {
            // плавный разгон и торможение
            val e = p.t * p.t * (3f - 2f * p.t)
            android.opengl.Matrix.setIdentityM(t1, 0)
            android.opengl.Matrix.translateM(t1, 0, p.pivot[0], p.pivot[1], p.pivot[2])
            android.opengl.Matrix.setRotateM(r, 0, p.maxDeg * e, p.axis[0], p.axis[1], p.axis[2])
            android.opengl.Matrix.multiplyMM(tmp, 0, t1, 0, r, 0)                     // T(p) · R
            android.opengl.Matrix.setIdentityM(t1, 0)
            android.opengl.Matrix.translateM(t1, 0, -p.pivot[0], -p.pivot[1], -p.pivot[2])
            android.opengl.Matrix.multiplyMM(m, 0, tmp, 0, t1, 0)                      // · T(−p)
            for (ent in p.entities) {
                if (!tm.hasComponent(ent)) continue
                tm.setTransform(tm.getInstance(ent), m)
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
