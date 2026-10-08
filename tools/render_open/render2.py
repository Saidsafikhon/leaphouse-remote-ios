"""
Фотореалистичные слои машины для режима «картинка» (Cycles).

blender -b --factory-startup -P render2.py -- <glb> <outdir> <model> <paints.json> [paint,...]

На каждую краску 13 PNG одного кадра (прозрачный фон, premultiplied при склейке «over»):
  <model>_<paint>_base.png          кузов без 6 подвижных деталей (детали в сцене есть,
                                    но камере не видны — свет/тени/отражения как у закрытой машины)
  <model>_<paint>_<part>_c.png      деталь закрыта, остальная машина — holdout
  <model>_<paint>_<part>_o.png      деталь открыта, остальная машина — holdout
part: fl fr rl rr hood trunk. Склейка: base, затем детали в порядке LAYER_ORDER (дальние → ближние).

Группы и петли — как FilamentCarView.buildParts() в телефоне и render.py.
"""
import bpy, sys, json, math, os, time
from mathutils import Vector

argv = sys.argv[sys.argv.index("--") + 1:]
glb, outdir, model, paints_json = argv[:4]
only = argv[4].split(",") if len(argv) > 4 and argv[4] else None
paints = json.loads(open(paints_json, encoding="utf-8").read())[model]
if only: paints = {k: v for k, v in paints.items() if k in only}
ENV = os.environ.get
PARTS = ["fl", "fr", "rl", "rr", "hood", "trunk"]

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=glb)
scene = bpy.context.scene


def group_of(name):
    n = name.lower()
    if n.startswith(("door_lf", "interactive_lf", "l_frontdoor")): return "fl"
    if n.startswith(("door_rf", "interactive_rf", "r_frontdoor", "door_front_r")): return "fr"
    if n.startswith(("door_lr", "interactive_lr", "l_reardoor")): return "rl"
    if n.startswith(("door_rr", "interactive_rr", "r_reardoor")): return "rr"
    if "bonnet" in n: return "hood"
    if n.startswith(("body_m_trunk", "trunk")): return "trunk"
    return None


meshes = [o for o in bpy.data.objects if o.type == "MESH"]
# номерной знак в GLB с зеркальной текстурой — прячем
for o in meshes:
    if o.material_slots and all(sl.material and sl.material.name.startswith("M_Lit_License") for sl in o.material_slots):
        o.hide_render = True
meshes = [o for o in meshes if not o.hide_render]

groups = {}
for o in meshes:
    g = group_of(o.name)
    if g: groups.setdefault(g, []).append(o)
body = [o for o in meshes if group_of(o.name) is None]

pivots = {}
for g, objs in groups.items():
    lo = Vector((1e9,) * 3); hi = Vector((-1e9,) * 3)
    for o in objs:
        for c in o.bound_box:
            w = o.matrix_world @ Vector(c)
            for k in range(3): lo[k] = min(lo[k], w[k]); hi[k] = max(hi[k], w[k])
    midz = (lo.z + hi.z) / 2
    if g in ("fl", "rl"):   p = (Vector((lo.x, lo.y, midz)), "z", -55)
    elif g in ("fr", "rr"): p = (Vector((lo.x, hi.y, midz)), "z", 55)
    elif g == "hood":       p = (Vector((hi.x, 0, hi.z)), "y", 40)
    else:                   p = (Vector((lo.x, 0, hi.z)), "y", -65)
    e = bpy.data.objects.new("hinge_" + g, None)
    scene.collection.objects.link(e)
    e.location = p[0]
    bpy.context.view_layer.update()
    for o in objs:
        mw = o.matrix_world.copy(); o.parent = e; o.matrix_world = mw
    pivots[g] = (e, p[1], p[2])
print("GROUPS", {g: len(v) for g, v in groups.items()})


def pose(open_parts):
    for g, (e, axis, deg) in pivots.items():
        e.rotation_euler = (0, 0, 0)
        if g in open_parts: setattr(e.rotation_euler, axis, math.radians(deg))
    bpy.context.view_layer.update()


# --- материалы: из «игровых» GLB-материалов делаем студийные ---
def principled(m):
    return m.node_tree.nodes.get("Principled BSDF") if m and m.use_nodes else None


def srgb2lin(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


for m in bpy.data.materials:
    b = principled(m)
    if not b: continue
    n = m.name
    m.blend_method = "OPAQUE" if not n.startswith(("M_Glass", "M_GlassLight")) else m.blend_method
    if n in ("M_Paint", "M_CarPaint"):
        b.inputs["Metallic"].default_value = 0.45
        b.inputs["Roughness"].default_value = 0.32
        b.inputs["Coat Weight"].default_value = 1.0
        b.inputs["Coat Roughness"].default_value = 0.02
        b.inputs["Coat IOR"].default_value = 1.5
    elif n.startswith("M_Glass_Transpanent") or n == "M_Glass":
        # тонированное стекло: прозрачность + отражения
        for l in list(b.inputs["Alpha"].links): m.node_tree.links.remove(l)
        b.inputs["Alpha"].default_value = 1.0
        b.inputs["Base Color"].default_value = (0.06, 0.07, 0.075, 1)
        b.inputs["Transmission Weight"].default_value = 1.0
        b.inputs["Roughness"].default_value = 0.0
        b.inputs["IOR"].default_value = 1.5
        b.inputs["Metallic"].default_value = 0.0
    elif n == "M_GlassLight":
        for l in list(b.inputs["Alpha"].links): m.node_tree.links.remove(l)
        b.inputs["Alpha"].default_value = 1.0
        b.inputs["Base Color"].default_value = (0.8, 0.8, 0.82, 1)
        b.inputs["Transmission Weight"].default_value = 1.0
        b.inputs["Roughness"].default_value = 0.02
    elif n == "M_Lit":
        # пластик, салон, решётки: тёмный полуматовый
        b.inputs["Base Color"].default_value = (0.025, 0.026, 0.028, 1)
        b.inputs["Roughness"].default_value = 0.55
        b.inputs["Specular IOR Level"].default_value = 0.4
    elif n == "M_Lit_Seat":
        b.inputs["Base Color"].default_value = (0.12, 0.11, 0.10, 1)
        b.inputs["Roughness"].default_value = 0.6
    elif n == "M_Mirror_Front":
        # чёрный глянец (рояльный лак) вокруг окон и решётки
        b.inputs["Base Color"].default_value = (0.01, 0.01, 0.012, 1)
        b.inputs["Metallic"].default_value = 0.0
        b.inputs["Roughness"].default_value = 0.08
        b.inputs["Coat Weight"].default_value = 1.0
    elif n == "M_Mirror_Logo":
        b.inputs["Roughness"].default_value = 0.08
    elif n == "M_Lit_Tire":
        # текстура шин/дисков слишком светлая — резина почти чёрная, диски тёмно-серые
        b.inputs["Roughness"].default_value = 0.55
        src = b.inputs["Base Color"].links[0].from_socket if b.inputs["Base Color"].links else None
        if src:
            mul = m.node_tree.nodes.new("ShaderNodeMix"); mul.data_type = "RGBA"; mul.blend_type = "MULTIPLY"
            mul.inputs["Factor"].default_value = 1.0
            mul.inputs[7].default_value = (0.22, 0.22, 0.23, 1)
            m.node_tree.links.new(src, mul.inputs[6]); m.node_tree.links.new(mul.outputs[2], b.inputs["Base Color"])
    if m.blend_method == "BLEND": m.blend_method = "HASHED"

paint_mats = [m for m in bpy.data.materials if m.name in ("M_Paint", "M_CarPaint")]

# --- свет: студия (мягкий бокс сверху + заполнение), фон прозрачный ---
w = bpy.data.worlds.new("w"); scene.world = w; w.use_nodes = True
nt = w.node_tree; bg = nt.nodes["Background"]
env = nt.nodes.new("ShaderNodeTexEnvironment")
env.image = bpy.data.images.load(os.path.join(bpy.utils.resource_path("LOCAL"), "datafiles", "studiolights", "world", "studio.exr"))
nt.links.new(env.outputs["Color"], bg.inputs["Color"])
bg.inputs["Strength"].default_value = float(ENV("WORLD", "0.7"))
scene.render.film_transparent = True

lo = Vector((1e9,) * 3); hi = Vector((-1e9,) * 3)
for o in meshes:
    for c in o.bound_box:
        wv = o.matrix_world @ Vector(c)
        for k in range(3): lo[k] = min(lo[k], wv[k]); hi[k] = max(hi[k], wv[k])
center = (lo + hi) / 2
size = hi - lo


def area(name, loc, rot, sx, sy, energy):
    L = bpy.data.lights.new(name, "AREA"); L.shape = "RECTANGLE"; L.size = sx; L.size_y = sy; L.energy = energy
    o = bpy.data.objects.new(name, L); scene.collection.objects.link(o); o.location = loc; o.rotation_euler = rot
    return o


# софтбокс над машиной (длинная полоса блика по плечу кузова)
area("top", center + Vector((0, 0, 4.0)), (0, 0, 0), size.x * 1.4, size.y * 1.6, float(ENV("TOP", "500")))
# мягкое заполнение со стороны камеры
area("fill", center + Vector((-3.5, -6.5, 1.2)), (math.radians(80), 0, math.radians(-30)), 6, 3, float(ENV("FILL", "150")))
# контровой слева сзади — отделяет крышу от фона
area("rim", center + Vector((6, 3, 3)), (math.radians(60), 0, math.radians(120)), 4, 2, float(ENV("RIM", "150")))

# пол — ловец тени
bpy.ops.mesh.primitive_plane_add(size=40, location=(center.x, center.y, lo.z))
floor = bpy.context.active_object; floor.is_shadow_catcher = True

# камера как у студийных фото: спереди-слева, нос влево, низко
cam_data = bpy.data.cameras.new("cam"); cam_data.lens = float(ENV("LENS", "85"))
cam = bpy.data.objects.new("cam", cam_data); scene.collection.objects.link(cam); scene.camera = cam
d = float(ENV("DIST", "14"))
az = math.radians(float(ENV("CAM_AZ", "-35")))
el = math.radians(float(ENV("CAM_EL", "7")))
cam.location = center + Vector((math.sin(az) * d * math.cos(el), -math.cos(az) * d * math.cos(el), math.sin(el) * d))
look = (center + Vector((0, 0, -0.15))) - cam.location
cam.rotation_euler = look.to_track_quat("-Z", "Y").to_euler()

scene.render.engine = "CYCLES"
scene.cycles.samples = int(ENV("SAMPLES", "96"))
scene.cycles.use_adaptive_sampling = True
scene.cycles.adaptive_threshold = 0.02
scene.cycles.use_denoising = True
scene.cycles.denoiser = "OPENIMAGEDENOISE"
scene.cycles.max_bounces = 8; scene.cycles.transmission_bounces = 8; scene.cycles.glossy_bounces = 4
scene.cycles.caustics_reflective = False; scene.cycles.caustics_refractive = False
scene.cycles.film_transparent_glass = False
prefs = bpy.context.preferences.addons["cycles"].preferences
dev = ENV("DEVICE", "ONEAPI")
if dev != "CPU":
    try:
        prefs.compute_device_type = dev; prefs.get_devices()
        if any(dv.type == dev for dv in prefs.devices):
            for dv in prefs.devices: dv.use = dv.type == dev
            scene.cycles.device = "GPU"; print("DEVICE", dev)
    except Exception as ex: print("NO GPU", ex)
scene.render.resolution_x = int(ENV("RES_X", "1600"))
scene.render.resolution_y = int(ENV("RES_Y", "900"))
scene.render.image_settings.file_format = "PNG"; scene.render.image_settings.color_mode = "RGBA"
scene.render.image_settings.color_depth = "8"
scene.view_settings.view_transform = "AgX"
scene.view_settings.look = ENV("LOOK", "AgX - Medium High Contrast")
scene.view_settings.exposure = float(ENV("EXPOSURE", "0"))

os.makedirs(os.path.join(outdir, "layers"), exist_ok=True)


def setup(mode, part=None):
    """mode: "shadow" — только тень на полу (машина целиком holdout, детали закрыты и отбрасывают тень);
    "car" — кузов без подвижных деталей (их нет совсем: салон освещён как при открытых дверях,
    закрытые детали всё равно перекроют его своими слоями); "part" — деталь, остальное holdout."""
    for o in body:
        o.is_holdout = mode != "car"
    floor.visible_camera = mode == "shadow"
    for g, objs in groups.items():
        for o in objs:
            o.hide_render = mode == "car"
            o.is_holdout = mode == "shadow"
            o.visible_camera = mode == "shadow" or g == part


def shoot(path):
    if os.path.exists(path) and not ENV("FORCE"): return
    scene.render.filepath = path
    t = time.time()
    bpy.ops.render.render(write_still=True)
    print("WROTE", path, round(time.time() - t, 1), "s", flush=True)


for code, hexc in paints.items():
    rgb = [srgb2lin(int(hexc[i:i + 2], 16) / 255) for i in (0, 2, 4)]
    for m in paint_mats:
        b = principled(m)
        if b: b.inputs["Base Color"].default_value = (*rgb, 1)
    pre = os.path.abspath(os.path.join(outdir, "layers", f"{model}_{code}"))
    pose(set())
    setup("shadow"); shoot(pre + "_shadow.png")
    setup("car"); shoot(pre + "_car.png")
    if ENV("FULL"):   # проверочные цельные рендеры для сравнения со склейкой (без пола)
        for o in body: o.is_holdout = False
        for g in groups:
            for o in groups[g]: o.hide_render = False; o.is_holdout = False; o.visible_camera = True
        for name, op in (("all_c", set()), ("m35", {"fl", "fr", "trunk"})):
            pose(op); shoot(pre + f"_full_{name}.png")
        pose(set())
    for g in PARTS:
        if g not in groups: continue
        setup("part", g); shoot(pre + f"_{g}_c.png")
    pose(set(PARTS))
    for g in PARTS:
        if g not in groups: continue
        setup("part", g); shoot(pre + f"_{g}_o.png")
