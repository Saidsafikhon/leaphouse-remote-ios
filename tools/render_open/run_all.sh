#!/bin/sh
# Полный прогон: все модели × краски × маски (одиночные, пары, все двери, капот+багажник, всё)
cd "$(dirname "$0")"
B=/d/PROJECT/Tools/blender/blender-4.2.3-windows-x64/blender.exe
MASKS=1,2,4,8,16,32,3,5,15,48,63
for m in c16 c10 c11 c01; do
  "$B" -b --factory-startup -P render.py -- glb/$m.glb out $m paints.json $MASKS 2>&1 | grep -E "Saved|rror"
done
