"""Склейка слоёв как в приложении (для проверки): python -I compose.py <layers_dir> <model_paint> <mask> <out.png>"""
import sys
from PIL import Image
d, pre, mask, out = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
ORDER = ["fr", "rr", "trunk", "rl", "hood", "fl"]; BIT = {"fl": 1, "fr": 2, "rl": 4, "rr": 8, "hood": 16, "trunk": 32}
SH = 0.45   # сила тени на полу (в студийных фото она мягкая)
sh = Image.open(f"{d}/{pre}_shadow.png").convert("RGBA")
sh.putalpha(sh.getchannel("A").point(lambda a: int(a * SH)))
im = Image.alpha_composite(sh, Image.open(f"{d}/{pre}_car.png").convert("RGBA"))
for g in ORDER:
    try: im = Image.alpha_composite(im, Image.open(f"{d}/{pre}_{g}_{'o' if mask & BIT[g] else 'c'}.png").convert("RGBA"))
    except FileNotFoundError: pass
im.save(out)
bg = Image.new("RGBA", im.size, (255, 255, 255, 255)); Image.alpha_composite(bg, im).convert("RGB").save(out.replace(".png", "_w.jpg"), quality=90)
