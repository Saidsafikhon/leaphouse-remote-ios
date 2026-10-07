import bpy, sys
argv = sys.argv[sys.argv.index("--")+1:]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=argv[0])
import collections
mats=collections.Counter()
for o in bpy.data.objects:
    if o.type=='MESH':
        for s in o.material_slots:
            if s.material: mats[s.material.name]+=1
names=[o.name for o in bpy.data.objects]
print("N objects", len(names))
keys=("door","bonnet","trunk","interactive","frontdoor","reardoor")
print([n for n in names if any(k in n.lower() for k in keys)][:80])
print(mats.most_common(40))
for m in bpy.data.materials:
    if m.name in ("M_Paint","M_CarPaint"):
        for n in m.node_tree.nodes: print(" node", n.type, n.name, [ (i.name, i.is_linked) for i in n.inputs][:3])
import mathutils
lo=[1e9]*3; hi=[-1e9]*3
for o in bpy.data.objects:
    if o.type=='MESH':
        for c in o.bound_box:
            w=o.matrix_world@mathutils.Vector(c)
            for k in range(3): lo[k]=min(lo[k],w[k]); hi[k]=max(hi[k],w[k])
print("bounds", lo, hi)
