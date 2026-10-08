#!/bin/sh
# Слои для режима «картинка»: все модели × краски (render2.py), потом make2.py
cd "$(dirname "$0")"
B=/d/PROJECT/Tools/blender/blender-4.2.3-windows-x64/blender.exe
for m in c16 c10 c11 c01; do
  SAMPLES=64 "$B" -b --factory-startup -P render2.py -- glb/$m.glb out2 $m paints_photo.json 2>&1 | grep -E --line-buffered "GROUPS|WROTE|rror"
done
