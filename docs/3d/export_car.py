"""Сборка GLB машины из Unity-бандла головы Leapmotor (car_c16 / car_c10 / …).

Идём по иерархии префаба от корня (<MODEL>_Car), берём активные MeshRenderer'ы,
геометрию — в мировых координатах, Unity (левая, Y вверх) → glTF (правая):
x → -x, обход треугольников наоборот, UV v → 1-v. Материалы упрощаем до PBR:
кузов — материал «M_Paint» (цвет задаёт приложение), стекло — полупрозрачное,
фонари — эмиссия, шины/номер — текстуры из бандла.
"""
import sys, io, json
import numpy as np
import UnityPy
import trimesh
from PIL import Image

src, root_name, out = sys.argv[1], sys.argv[2], sys.argv[3]
env = UnityPy.load(src)

def rd(p):
    try: return p.read()
    except Exception: return None

textures = {}
for o in env.objects:
    if o.type.name == 'Texture2D':
        t = o.read()
        try:
            im = t.image.convert('RGBA')
            # для телефона 512 px за глаза; вес GLB важнее
            if max(im.size) > 512: im = im.resize((min(512, im.size[0]), min(512, im.size[1])), Image.LANCZOS)
            textures[t.m_Name] = im
        except Exception as e: print('tex fail', t.m_Name, e)

def comps(go):
    out = {}
    for c in go.m_Components:
        comp = rd(c.component) if hasattr(c, 'component') else rd(c)
        if comp is not None: out.setdefault(comp.__class__.__name__, comp)
    return out

def quat_to_mat(q):
    x, y, z, w = q.x, q.y, q.z, q.w
    return np.array([
        [1-2*(y*y+z*z), 2*(x*y-z*w), 2*(x*z+y*w)],
        [2*(x*y+z*w), 1-2*(x*x+z*z), 2*(y*z-x*w)],
        [2*(x*z-y*w), 2*(y*z+x*w), 1-2*(x*x+y*y)],
    ])

def local_matrix(t):
    m = np.eye(4)
    r = quat_to_mat(t.m_LocalRotation)
    s = np.diag([t.m_LocalScale.x, t.m_LocalScale.y, t.m_LocalScale.z])
    m[:3, :3] = r @ s
    m[:3, 3] = [t.m_LocalPosition.x, t.m_LocalPosition.y, t.m_LocalPosition.z]
    return m

# --- материалы -> PBR ---
def pbr_for(mat):
    n = mat.m_Name
    sp = mat.m_SavedProperties
    cols = {k: (v.r, v.g, v.b, v.a) for k, v in sp.m_Colors}
    texs = {}
    for k, v in sp.m_TexEnvs:
        t = rd(v.m_Texture)
        if t is not None: texs[k] = t.m_Name
    kw = dict(name=n, metallicFactor=0.0, roughnessFactor=0.6)
    if n in ('M_Paint', 'M_CarPaint'):
        kw.update(baseColorFactor=[0.91, 0.91, 0.91, 1.0], metallicFactor=0.55, roughnessFactor=0.28)
    elif n.startswith('M_Mirror_Logo'):
        kw.update(baseColorFactor=[0.9, 0.9, 0.9, 1.0], metallicFactor=1.0, roughnessFactor=0.15)
    elif n.startswith('M_Mirror'):
        c = cols.get('_BaseCol', (0.04, 0.04, 0.04, 1))
        kw.update(baseColorFactor=[c[0], c[1], c[2], 1.0], metallicFactor=0.85, roughnessFactor=0.35)
    elif 'Glass' in n or 'Transpanent' in n:
        kw.update(baseColorFactor=[0.05, 0.06, 0.07, 0.55], metallicFactor=0.0, roughnessFactor=0.08, alphaMode='BLEND')
    elif n.startswith('M_Glow'):
        c = cols.get('_Col', (0.11, 0.11, 0.11, 1))
        kw.update(baseColorFactor=[c[0], c[1], c[2], 1.0], roughnessFactor=0.3, emissiveFactor=[c[0]*0.6, c[1]*0.6, c[2]*0.6])
    elif n == 'M_Lit_Reflector':
        c = cols.get('_Color', (0.37, 0.02, 0.02, 1))
        kw.update(baseColorFactor=[c[0], c[1], c[2], 1.0], roughnessFactor=0.4)
    elif n == 'M_Shadow':
        kw.update(baseColorFactor=[0.0, 0.0, 0.0, 1.0], alphaMode='BLEND', roughnessFactor=1.0)
    elif n == 'M_TrunkTranslucent':
        kw.update(baseColorFactor=[1, 1, 1, 0.6], alphaMode='BLEND')
    else:  # M_Lit, M_Lit_Tire, M_Lit_License
        c = cols.get('_Color', (0.35, 0.35, 0.35, 1))
        kw.update(baseColorFactor=[c[0], c[1], c[2], 1.0], roughnessFactor=0.7)
    img = None
    for key in ('_Albedo', '_MainTex'):
        if key in texs and texs[key] in textures:
            img = textures[texs[key]]
            if n == 'M_Shadow':
                # тень: яркость картинки → альфа чёрного
                g = img.convert('L'); img = Image.merge('RGBA', (Image.new('L', g.size, 0),) * 3 + (g,))
            break
    m = trimesh.visual.material.PBRMaterial(baseColorTexture=img, **kw)
    return m

mat_cache = {}
def material(mat):
    if mat.m_Name not in mat_cache: mat_cache[mat.m_Name] = pbr_for(mat)
    return mat_cache[mat.m_Name]

# --- геометрия ---
def mesh_arrays(mesh):
    from UnityPy.helpers.MeshHelper import MeshHandler
    h = MeshHandler(mesh); h.process()
    v = np.asarray(h.m_Vertices, dtype=np.float32)[:, :3]
    n = np.asarray(h.m_Normals, dtype=np.float32)[:, :3] if h.m_Normals is not None and len(h.m_Normals) else None
    uv = np.asarray(h.m_UV0, dtype=np.float32)[:, :2] if h.m_UV0 is not None and len(h.m_UV0) else None
    if uv is not None and uv.shape[0] != v.shape[0]: uv = None
    subs = [np.asarray([t for t in tris if len(t) == 3], dtype=np.int64).reshape(-1, 3) for tris in h.get_triangles()]
    return v, n, uv, subs

scene = trimesh.Scene()
FLIP = np.diag([-1.0, 1.0, 1.0, 1.0])
count = 0

def walk(go, parent_m):
    global count
    if not go.m_IsActive: return
    cs = comps(go); t = cs.get('Transform')
    world = parent_m @ local_matrix(t) if t else parent_m
    mf, mr = cs.get('MeshFilter'), cs.get('MeshRenderer')
    if mf and mr and (mesh := rd(mf.m_Mesh)) is not None:
        mesh_obj = mesh
        v, n, uv, subs = mesh_arrays(mesh_obj)
        mats = [rd(m) for m in mr.m_Materials]
        vw = (world[:3, :3] @ v.T).T + world[:3, 3]
        vw = (FLIP[:3, :3] @ vw.T).T
        nw = None
        if n is not None:
            nm = np.linalg.inv(world[:3, :3]).T
            nw = (nm @ n.T).T; nw = (FLIP[:3, :3] @ nw.T).T
            nw /= np.maximum(np.linalg.norm(nw, axis=1, keepdims=True), 1e-8)
        for i, faces in enumerate(subs):
            mat = mats[min(i, len(mats) - 1)] if mats else None
            if mat is None or len(faces) == 0: continue
            faces = faces[:, ::-1]  # зеркалирование → обратный обход
            tm = trimesh.Trimesh(vertices=vw, faces=faces, vertex_normals=nw, process=False)
            if uv is not None:
                uvg = uv.copy(); uvg[:, 1] = 1.0 - uvg[:, 1]
                tm.visual = trimesh.visual.TextureVisuals(uv=uvg, material=material(mat))
            else:
                tm.visual = trimesh.visual.TextureVisuals(material=material(mat))
            scene.add_geometry(tm, node_name=f"{go.m_Name}#{i}", geom_name=f"{go.m_Name}#{i}")
            count += 1
    if t:
        for ch in t.m_Children:
            ct = rd(ch)
            if ct: walk(rd(ct.m_GameObject), world)

root = next(o.read() for o in env.objects if o.type.name == 'GameObject' and o.read().m_Name == root_name)
walk(root, np.eye(4))
b = scene.bounds
print('nodes', count, 'bounds', np.round(b, 3).tolist(), 'size', np.round(b[1]-b[0], 3).tolist())
scene.export(out)
import os; print(out, os.path.getsize(out) // 1024, 'KB')
