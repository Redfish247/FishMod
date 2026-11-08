# Blender 3D title: extruded FISHMOD + subtitle, camera push-in. Run: blender -b -P blender_title.py
import bpy, math, os
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "anims", "A02_title_3d_")
bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
sc.render.engine = "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items.keys() else "BLENDER_EEVEE"
sc.render.resolution_x, sc.render.resolution_y, sc.render.fps = 1920, 1080, 30
sc.frame_start, sc.frame_end = 1, 120
sc.render.image_settings.file_format = "PNG"
sc.render.filepath = OUT
try: sc.view_settings.view_transform = "AgX"
except Exception: pass

world = bpy.data.worlds.new("w"); sc.world = world; world.use_nodes = True
world.node_tree.nodes["Background"].inputs[0].default_value = (0.004, 0.006, 0.014, 1)

def mat(name, col, emit=0.0, metal=0.9, rough=0.25):
    m = bpy.data.materials.new(name); m.use_nodes = True; b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*col, 1); b.inputs["Metallic"].default_value = metal; b.inputs["Roughness"].default_value = rough
    if emit:
        b.inputs["Emission Color"].default_value = (*col, 1); b.inputs["Emission Strength"].default_value = emit
    return m

def text(body, size, y, m, extrude, z=0):
    bpy.ops.object.text_add(location=(0, y, z)); o = bpy.context.object
    o.data.body = body; o.data.align_x = "CENTER"; o.data.align_y = "CENTER"; o.data.size = size
    o.data.extrude = extrude; o.data.bevel_depth = extrude * 0.1; o.data.bevel_resolution = 3
    try: o.data.font = bpy.data.fonts.load(r"C:\Windows\Fonts\seguibl.ttf")
    except Exception: pass
    o.rotation_euler = (math.radians(90), 0, 0); o.data.materials.append(m); return o

t1 = text("HOW I CREATED", 0.45, 0, mat("w", (0.8, 0.9, 1.0), emit=0.15), 0.04, z=2.0)
t2 = text("HYPIXEL SKYBLOCK'S", 0.75, 0, mat("c", (0.0, 0.45, 1.0), emit=0.5), 0.12, z=1.15)
t3 = text("GREATEST MOD", 1.15, 0, mat("g", (1.0, 0.6, 0.05), emit=0.6), 0.22, z=-0.05)

bpy.ops.mesh.primitive_plane_add(size=40, location=(0, 0, -1.3)); fl = bpy.context.object
fl.data.materials.append(mat("floor", (0.02, 0.025, 0.04), metal=1.0, rough=0.08))

for loc, e, col in (((-5, -6, 4), 1500, (0.4, 0.7, 1)), ((5, -5, 2), 1200, (1, 0.7, 0.4)), ((0, 4, 6), 800, (0.7, 0.5, 1))):
    bpy.ops.object.light_add(type="AREA", location=loc); L = bpy.context.object
    L.data.energy = e; L.data.color = col; L.data.size = 4
    L.rotation_euler = (0, 0, 0); c = L.constraints.new("TRACK_TO"); c.target = t2; c.track_axis = "TRACK_NEGATIVE_Z"; c.up_axis = "UP_Y"

bpy.ops.object.camera_add(location=(-2.5, -14, 1.6)); cam = bpy.context.object; sc.camera = cam
tc = cam.constraints.new("TRACK_TO"); tc.target = t2; tc.track_axis = "TRACK_NEGATIVE_Z"; tc.up_axis = "UP_Y"
cam.data.lens = 40
cam.keyframe_insert("location", frame=1)
cam.location = (0.6, -10.5, 1.0); cam.keyframe_insert("location", frame=120)

# text slides up from below the floor, staggered
for i, o in enumerate((t1, t2, t3)):
    z = o.location.z; o.location.z = z - 3; o.keyframe_insert("location", frame=1 + i * 8)
    o.location.z = z; o.keyframe_insert("location", frame=28 + i * 8)
for o in (cam, t1, t2, t3):
    if o.animation_data and o.animation_data.action:
        try:
            for fc in o.animation_data.action.fcurves:
                for k in fc.keyframe_points: k.interpolation = "BEZIER"; k.easing = "EASE_OUT"
        except Exception: pass

ee = sc.eevee
for attr, val in (("use_bloom", True), ("use_ssr", True), ("use_raytracing", True), ("taa_render_samples", 32)):
    if hasattr(ee, attr): setattr(ee, attr, val)
bpy.ops.render.render(animation=True)
