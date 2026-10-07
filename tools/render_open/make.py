"""Обрезка рендеров out/raw → webp для приложения.
Кадр — общий на модель (объединение рамок всех масок), чтобы машина не прыгала
при смене состояния. Android: android/app/src/main/assets/open/<model>_<paint>_<mask>.webp,
iOS: ElectroRemote/Resources/OpenArt/open_<model>_<paint>_<mask>.webp.
python -I make.py [ширина=800] [качество=78]"""
import sys, os, glob, collections
from PIL import Image
here = os.path.dirname(os.path.abspath(__file__)); root = os.path.join(here, "..", "..")
W = int(sys.argv[1]) if len(sys.argv) > 1 else 800
Q = int(sys.argv[2]) if len(sys.argv) > 2 else 78
files = sorted(glob.glob(os.path.join(here, "out", "raw", "*.png")))
box = {}
for f in files:
    m = os.path.basename(f).split("_")[0]
    b = Image.open(f).getbbox()
    if not b: continue
    o = box.get(m)
    box[m] = b if not o else (min(o[0], b[0]), min(o[1], b[1]), max(o[2], b[2]), max(o[3], b[3]))
adir = os.path.join(root, "android", "app", "src", "main", "assets", "open")
idir = os.path.join(root, "ElectroRemote", "Resources", "OpenArt")
os.makedirs(adir, exist_ok=True); os.makedirs(idir, exist_ok=True)
total = 0
for f in files:
    name = os.path.basename(f)[:-4]; m = name.split("_")[0]
    im = Image.open(f).crop(box[m])
    im = im.resize((W, round(im.height * W / im.width)), Image.LANCZOS)
    a = os.path.join(adir, name + ".webp")
    im.save(a, "WEBP", quality=Q, method=6)
    im.save(os.path.join(idir, "open_" + name + ".webp"), "WEBP", quality=Q, method=6)
    total += os.path.getsize(a)
print(len(files), "files,", round(total / 1e6, 2), "MB per platform", box)
