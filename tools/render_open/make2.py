"""Слои out2/layers → webp для приложения (склейка «over» в CarArt.openBitmap / openImage).
base = тень на полу (ослабленная, как в студийных фото) + кузов без подвижных деталей.
Кадр общий на модель (объединение рамок всех слоёв), все слои одного размера.
Android: android/app/src/main/assets/open/<model>_<paint>_{base|<part>_<c|o>}.webp
iOS:     ElectroRemote/Resources/OpenArt/open_<то же>.webp
python -I make2.py [ширина=800] [качество=85]"""
import sys, os, glob
from PIL import Image
here = os.path.dirname(os.path.abspath(__file__)); root = os.path.join(here, "..", "..")
W = int(sys.argv[1]) if len(sys.argv) > 1 else 1000
Q = int(sys.argv[2]) if len(sys.argv) > 2 else 88
SH = 0.45
L = os.path.join(here, "out2", "layers")
PARTS = [f"{g}_{s}" for g in ("fl", "fr", "rl", "rr", "hood", "trunk") for s in ("c", "o")]

sets = sorted({os.path.basename(f)[:-len("_car.png")] for f in glob.glob(os.path.join(L, "*_car.png"))})
adir = os.path.join(root, "android", "app", "src", "main", "assets", "open")
idir = os.path.join(root, "ElectroRemote", "Resources", "OpenArt")
os.makedirs(adir, exist_ok=True); os.makedirs(idir, exist_ok=True)
for d, pat in ((adir, "*.webp"), (idir, "open_*.webp")):
    for f in glob.glob(os.path.join(d, pat)): os.remove(f)


def layers(pre):
    sh = Image.open(os.path.join(L, pre + "_shadow.png")).convert("RGBA")
    sh.putalpha(sh.getchannel("A").point(lambda a: int(a * SH)))
    out = {"base": Image.alpha_composite(sh, Image.open(os.path.join(L, pre + "_car.png")).convert("RGBA"))}
    for p in PARTS:
        f = os.path.join(L, f"{pre}_{p}.png")
        if os.path.exists(f): out[p] = Image.open(f).convert("RGBA")
    return out


box = {}
for pre in sets:
    m = pre.split("_")[0]
    for name, im in layers(pre).items():
        if name == "base":   # рамка — по машине, а не по растёкшейся тени
            im = Image.open(os.path.join(L, pre + "_car.png")).convert("RGBA")
        # по краю кадра шумодав оставляет полупрозрачную рамку в 1–2 px — её не считаем
        b = im.getchannel("A").crop((4, 4, im.width - 4, im.height - 4)).point(lambda a: 255 if a > 6 else 0).getbbox()
        if not b: continue
        b = (b[0] + 4, b[1] + 4, b[2] + 4, b[3] + 4)
        o = box.get(m)
        box[m] = b if not o else (min(o[0], b[0]), min(o[1], b[1]), max(o[2], b[2]), max(o[3], b[3]))
for m, (x0, y0, x1, y1) in box.items():   # поле вокруг машины под тень
    px, py = int((x1 - x0) * 0.03), int((y1 - y0) * 0.08)
    box[m] = (max(4, x0 - px), max(4, y0 - py), min(1596, x1 + px), min(896, y1 + py))
total = 0; n = 0
for pre in sets:
    m = pre.split("_")[0]
    for name, im in layers(pre).items():
        im = im.crop(box[m])
        if name == "base":   # тень гасим к краям кадра, чтобы не было обреза
            car = Image.open(os.path.join(L, pre + "_car.png")).convert("RGBA").crop(box[m])
            ex, ey = max(1, im.width // 12), max(1, im.height // 8)
            fx = Image.new("L", (im.width, 1)); fx.putdata([min(255, int(255 * min(x, im.width - 1 - x) / ex)) for x in range(im.width)])
            fy = Image.new("L", (1, im.height)); fy.putdata([min(255, int(255 * min(y, im.height - 1 - y) / ey)) for y in range(im.height)])
            fx = fx.resize(im.size, Image.NEAREST); fy = fy.resize(im.size, Image.NEAREST)
            from PIL import ImageChops
            sh = Image.open(os.path.join(L, pre + "_shadow.png")).convert("RGBA").crop(box[m])
            a = sh.getchannel("A").point(lambda v: int(v * SH))
            sh.putalpha(ImageChops.multiply(a, ImageChops.multiply(fx, fy)))
            im = Image.alpha_composite(sh, car)
        im = im.resize((W, round(im.height * W / im.width)), Image.LANCZOS)
        a = os.path.join(adir, f"{pre}_{name}.webp")
        im.save(a, "WEBP", quality=Q, alpha_quality=100, method=4)
        im.save(os.path.join(idir, f"open_{pre}_{name}.webp"), "WEBP", quality=Q, alpha_quality=100, method=4)
        total += os.path.getsize(a); n += 1
print(len(sets), "sets,", n, "files,", round(total / 1e6, 2), "MB per platform", box)
