# Blender loop background: floating voxel blocks in fog, slow dolly, DOF. Neutral colours so anims.py can tint it.
# Run: blender -b -P blender_bg.py
import bpy, math, os, random
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "anims", "bg", "bg_")
random.seed(4)
bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene
sc.render.engine = "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items.keys() else "BLENDER_EEVEE"
sc.render.resolution_x, sc.render.resolution_y, sc.render.fps = 1280, 720, 30
sc.frame_start, sc.frame_end = 1, 180
sc.render.image_settings.file_format = "PNG"; sc.render.filepath = OUT
try: sc.view_settings.view_transform = "AgX"; sc.view_settings.look = "AgX - Punchy"
except Exception: pass

world = bpy.data.worlds.new("w"); sc.world = world; world.use_nodes = True
nt = world.node_tree; nt.nodes["Background"].inputs[0].default_value = (0.003, 0.004, 0.008, 1)
vol = nt.nodes.new("ShaderNodeVolumePrincipled"); vol.inputs["Density"].default_value = 0.006
nt.links.new(vol.outputs[0], nt.nodes["World Output"].inputs["Volume"])

def mat(name, col, rough, metal=0.0, emit=0.0):
    m = bpy.data.materials.new(name); m.use_nodes = True; b = m.node_tree.nodes["Principled BSDF"]
    b.inputs["Base Color"].default_value = (*col, 1); b.inputs["Roughness"].default_value = rough; b.inputs["Metallic"].default_value = metal
    if emit: b.inputs["Emission Color"].default_value = (*col, 1); b.inputs["Emission Strength"].default_value = emit
    return m
dark = mat("dark", (0.05, 0.055, 0.065), 0.35, 0.6)
mid = mat("mid", (0.18, 0.2, 0.24), 0.5)
glow = mat("glow", (0.85, 0.9, 1.0), 0.3, emit=3.0)

for i in range(260):
    x, y, z = random.uniform(-16, 16), random.uniform(2, 60), random.uniform(-9, 9)
    if abs(x) < 3.2 and abs(z) < 2.2 and y < 18: continue  # keep the centre clear for text
    s = random.uniform(0.25, 1.1)
    bpy.ops.mesh.primitive_cube_add(size=s, location=(x, y, z)); o = bpy.context.object
    o.data.materials.append(glow if random.random() < 0.07 else (mid if random.random() < 0.3 else dark))
    o.rotation_euler = [random.uniform(0, 6.28) for _ in range(3)]
    o.keyframe_insert("rotation_euler", frame=1)
    o.rotation_euler = [a + random.uniform(-0.6, 0.6) for a in o.rotation_euler]; o.location.z += random.uniform(-0.6, 0.6)
    o.keyframe_insert("rotation_euler", frame=180); o.keyframe_insert("location", frame=180)
    o.location.z = z; o.keyframe_insert("location", frame=1)

for loc, e in (((-8, 4, 6), 400), ((9, 10, -3), 300), ((0, 30, 8), 900)):
    bpy.ops.object.light_add(type="AREA", location=loc); L = bpy.context.object; L.data.energy = e; L.data.size = 6
    c = L.constraints.new("TRACK_TO"); bpy.ops.object.empty_add(location=(0, 15, 0)); c.target = bpy.context.object
    c.track_axis = "TRACK_NEGATIVE_Z"; c.up_axis = "UP_Y"

bpy.ops.object.camera_add(location=(0, -6, 0), rotation=(math.radians(90), 0, 0)); cam = bpy.context.object; sc.camera = cam
cam.data.lens = 32; cam.data.dof.use_dof = True; cam.data.dof.focus_distance = 14; cam.data.dof.aperture_fstop = 1.8
cam.keyframe_insert("location", frame=1); cam.location = (0.6, -1.5, 0.4); cam.keyframe_insert("location", frame=180)
cam.rotation_euler = (math.radians(90), 0, math.radians(-3)); cam.keyframe_insert("rotation_euler", frame=180)
cam.rotation_euler = (math.radians(90), 0, 0); cam.keyframe_insert("rotation_euler", frame=1)

ee = sc.eevee
for attr, val in (("use_bloom", True), ("use_volumetric_shadows", True), ("taa_render_samples", 24), ("use_raytracing", True)):
    if hasattr(ee, attr): setattr(ee, attr, val)
bpy.ops.render.render(animation=True)
