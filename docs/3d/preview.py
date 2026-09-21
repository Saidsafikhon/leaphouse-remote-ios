"""Грубый софт-рендер GLB (painter's algorithm) для проверки геометрии/цветов без GPU."""
import sys, numpy as np, trimesh
from PIL import Image, ImageDraw
src, out = sys.argv[1], sys.argv[2]
yaw = float(sys.argv[3]) if len(sys.argv) > 3 else 35.0
scene = trimesh.load(src, force='scene')
W, H = 900, 520
img = Image.new('RGB', (W, H), (236, 240, 244)); d = ImageDraw.Draw(img)
tris = []
for name, geom in scene.geometry.items():
    T = scene.graph.get(name)[0]
    v = trimesh.transform_points(geom.vertices, T)
    mat = geom.visual.material
    base = np.array(mat.baseColorFactor[:4] if mat.baseColorFactor is not None else [0.7, 0.7, 0.7, 1.0], dtype=float)
    if base.max() > 1.5: base = base / 255.0
    if mat.baseColorTexture is not None and 'Shadow' not in (mat.name or ''): base[:3] = [0.35, 0.35, 0.35]
    if 'Tire' in (mat.name or ''): base[:3] = [0.12, 0.12, 0.12]
    alpha = base[3] if len(base) > 3 else 1.0
    f = geom.faces
    p = v[f]  # (n,3,3)
    n = np.cross(p[:, 1] - p[:, 0], p[:, 2] - p[:, 0]); n /= np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-9)
    for i in range(len(f)):
        tris.append((p[i], n[i], base[:3], alpha))
# камера: поворот вокруг Y, лёгкий наклон вниз, ортографика
a = np.radians(yaw); b = np.radians(18)
Ry = np.array([[np.cos(a), 0, np.sin(a)], [0, 1, 0], [-np.sin(a), 0, np.cos(a)]])
Rx = np.array([[1, 0, 0], [0, np.cos(b), -np.sin(b)], [0, np.sin(b), np.cos(b)]])
R = Rx @ Ry
light = np.array([0.4, 0.8, 0.45]); light /= np.linalg.norm(light)
proj = []
for p, n, c, al in tris:
    q = (R @ p.T).T; nn = R @ n
    if nn[2] < 0 and al >= 0.99: continue  # back-face (камера смотрит вдоль -z... берём грани к камере)
    shade = 0.35 + 0.65 * max(0.0, float(np.dot(n, light)))
    col = tuple(int(min(255, 255 * c[k] * shade)) for k in range(3))
    proj.append((q[:, 2].mean(), q, col, al))
proj.sort(key=lambda t: t[0])
s = 150; cx, cy = W / 2, H / 2 + 80
for _, q, col, al in proj:
    pts = [(cx + q[k, 0] * s, cy - q[k, 1] * s) for k in range(3)]
    if al < 0.99:
        ov = Image.new('RGBA', (W, H), (0, 0, 0, 0)); ImageDraw.Draw(ov).polygon(pts, fill=col + (int(255 * al),))
        img.paste(ov, (0, 0), ov)
    else:
        d.polygon(pts, fill=col)
img.save(out); print(out, len(proj), 'tris')
