"""
Рендер машин LeapRemote с открытыми дверьми/капотом/багажником для режима «картинка».

blender -b --factory-startup -P render.py -- <glb> <outdir> <model> <paints.json> <masks> [engine]

masks — список через запятую (десятичные 6-битные маски: бит0 fl, 1 fr, 2 rl, 3 rr, 4 hood, 5 trunk)
Файлы: <outdir>/raw/<model>_<paint>_<mask>.png (обрезка/webp — в make.py).

Группы и петли — как FilamentCarView.buildParts() в телефоне. Оси: glTF (нос −X, верх +Y,
левый борт +Z) импортируется в Blender как (x, −z, y): нос −X, верх +Z, левый борт −Y.
"""
import bpy, sys, json, math, os
from mathutils import Vector, Matrix

argv = sys.argv[sys.argv.index("--") + 1:]
glb, outdir, model, paints_json, masks = argv[:5]
engine = argv[5] if len(argv) > 5 else "EEVEE"
paints = json.loads(open(paints_json, encoding="utf-8").read())[model]
masks = [int(m) for m in masks.split(",")]

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


groups = {}
for o in list(bpy.data.objects):
    if o.type != "MESH": continue
    g = group_of(o.name)
    if g: groups.setdefault(g, []).append(o)

pivots = {}
for g, objs in groups.items():
    lo = Vector((1e9,) * 3); hi = Vector((-1e9,) * 3)
    for o in objs:
        for c in o.bound_box:
            w = o.matrix_world @ Vector(c)
            for k in range(3): lo[k] = min(lo[k], w[k]); hi[k] = max(hi[k], w[k])
    midz = (lo.z + hi.z) / 2
    # (pivot, ось, угол°) — перевод осей glTF→Blender из buildParts()
    if g in ("fl", "rl"):   p = (Vector((lo.x, lo.y, midz)), "Z", -55)
    elif g in ("fr", "rr"): p = (Vector((lo.x, hi.y, midz)), "Z", 55)
    elif g == "hood":       p = (Vector((hi.x, 0, hi.z)), "Y", 40)
    else:                   p = (Vector((lo.x, 0, hi.z)), "Y", -65)
    e = bpy.data.objects.new("hinge_" + g, None)
    scene.collection.objects.link(e)
    e.location = p[0]
    bpy.context.view_layer.update()
    for o in objs:
        mw = o.matrix_world.copy()
        o.parent = e
        o.matrix_world = mw
    pivots[g] = (e, p[1], p[2])
print("GROUPS", {g: len(v) for g, v in groups.items()})

# --- свет, камера, фон ---
w = bpy.data.worlds.new("w"); scene.world = w; w.use_nodes = True
nt = w.node_tree; bg = nt.nodes["Background"]
env = nt.nodes.new("ShaderNodeTexEnvironment")
env.image = bpy.data.images.load(os.path.join(bpy.utils.resource_path("LOCAL"), "datafiles", "studiolights", "world", "studio.exr"))
nt.links.new(env.outputs["Color"], bg.inputs["Color"]); bg.inputs["Strength"].default_value = float(os.environ.get("WORLD", "2.0"))
scene.render.film_transparent = True

# машина: центр и размер
lo = Vector((1e9,) * 3); hi = Vector((-1e9,) * 3)
for o in bpy.data.objects:
    if o.type != "MESH": continue
    for c in o.bound_box:
        wv = o.matrix_world @ Vector(c)
        for k in range(3): lo[k] = min(lo[k], wv[k]); hi[k] = max(hi[k], wv[k])
center = (lo + hi) / 2
# ракурс как у студийных фото: спереди-слева, нос влево, чуть сверху
cam_data = bpy.data.cameras.new("cam"); cam_data.lens = 70
cam = bpy.data.objects.new("cam", cam_data); scene.collection.objects.link(cam); scene.camera = cam
d = 11.0
az = math.radians(float(os.environ.get("CAM_AZ", "-40")))   # от оси −Y (левый борт) к носу −X
el = math.radians(float(os.environ.get("CAM_EL", "10")))
cam.location = center + Vector((math.sin(az) * d * math.cos(el), -math.cos(az) * d * math.cos(el), math.sin(el) * d))
look = (center + Vector((0, 0, -0.1))) - cam.location
cam.rotation_euler = look.to_track_quat("-Z", "Y").to_euler()

# тень на полу: в Cycles — shadow catcher
if engine == "CYCLES":
    bpy.ops.mesh.primitive_plane_add(size=30, location=(center.x, center.y, lo.z))
    bpy.context.active_object.is_shadow_catcher = True
    sun = bpy.data.lights.new("sun", "SUN"); sun.energy = 2.5; sun.angle = math.radians(25)
    so = bpy.data.objects.new("sun", sun); scene.collection.objects.link(so); so.rotation_euler = (math.radians(15), 0, 0)
    scene.render.engine = "CYCLES"; scene.cycles.samples = int(os.environ.get("SAMPLES", "48"))
    scene.cycles.use_denoising = True
    prefs = bpy.context.preferences.addons["cycles"].preferences
    for dt in ("ONEAPI", "CUDA", "OPTIX", "HIP"):
        try:
            prefs.compute_device_type = dt; prefs.get_devices()
            if any(dv.type == dt for dv in prefs.devices):
                for dv in prefs.devices: dv.use = True
                scene.cycles.device = "GPU"; print("DEVICE", dt); break
        except Exception: pass
else:
    scene.render.engine = "BLENDER_EEVEE_NEXT"
    scene.eevee.taa_render_samples = 32

scene.render.resolution_x = int(os.environ.get("RES_X", "1600"))
scene.render.resolution_y = int(os.environ.get("RES_Y", "900"))
scene.render.image_settings.file_format = "PNG"; scene.render.image_settings.color_mode = "RGBA"
scene.view_settings.view_transform = "Standard"

# номерной знак в GLB с зеркальной текстурой — прячем
for o in bpy.data.objects:
    if o.type == "MESH" and o.material_slots and all(sl.material and sl.material.name.startswith("M_Lit_License") for sl in o.material_slots):
        o.hide_render = True; print("HIDE", o.name)

paint_mats = [m for m in bpy.data.materials if m.name in ("M_Paint", "M_CarPaint")]
os.makedirs(os.path.join(outdir, "raw"), exist_ok=True)
ORDER = ["fl", "fr", "rl", "rr", "hood", "trunk"]


def srgb2lin(c):
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


for code, hexc in paints.items():
    rgb = [srgb2lin(int(hexc[i:i + 2], 16) / 255) for i in (0, 2, 4)]
    for m in paint_mats:
        b = m.node_tree.nodes.get("Principled BSDF")
        if b: b.inputs["Base Color"].default_value = (*rgb, 1)
    for mask in masks:
        for i, g in enumerate(ORDER):
            if g not in pivots: continue
            e, axis, deg = pivots[g]
            e.rotation_euler = (0, 0, 0)
            if mask >> i & 1:
                setattr(e.rotation_euler, axis.lower(), math.radians(deg))
        out = os.path.abspath(os.path.join(outdir, "raw", f"{model}_{code}_{mask}.png"))
        if os.path.exists(out) and not os.environ.get("FORCE"): continue
        scene.render.filepath = out
        bpy.ops.render.render(write_still=True)
        print("WROTE", out)
