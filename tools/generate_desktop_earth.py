"""Build an independent desktop asset; never modify the approved immersive sources."""
import argparse, hashlib, json, math
from pathlib import Path
from PIL import Image

p = argparse.ArgumentParser()
p.add_argument('--editor', type=Path, required=True)
p.add_argument('--shader-only', action='store_true', help='Reuse the existing derived textures unchanged')
a = p.parse_args()
root = a.editor.resolve()
textures = root / 'Sources/Textures'
outputs = []
for source, target, size in [
    ('day_ai_v51_8k.png', 'desktop_day_3k.png', (3072, 1536)),
    ('night_8k.jpg', 'desktop_night_2k.png', (2048, 1024)),
]:
    if not a.shader_only or not (textures / target).exists():
        with Image.open(textures / source) as im:
            im.convert('RGB').resize(size, Image.Resampling.LANCZOS).save(textures / target)
    outputs.append(dict(file=target, size=size, sha256=hashlib.sha256((textures/target).read_bytes()).hexdigest()))
    if a.shader_only:
        continue
    # The editor otherwise defaults to 2048 and silently downscales the 3K input.
    meta=textures/(target+'.picometa')
    config=json.loads(meta.read_text(encoding='utf-8-sig')) if meta.exists() else {
        'authoredConfig': True,
        'textureConverter': {'generateMipmap': True, 'textureFormat': 30,
                             'sourceColorSpace': 1539, 'targetColorSpace': 1539}
    }
    config['textureConverter']['maxSize']=4096 if size[0]>2048 else 2048
    meta.write_text(json.dumps(config,indent=4), encoding='utf-8')

# Two linear masks share one 2K RGB texture: R=ocean specular, G=source clouds.
# Packing avoids a fourth texture sample and an additional transparent sphere.
packed = textures / 'desktop_surface_2k.png'
if not a.shader_only or not packed.exists():
    with Image.open(textures/'specular_ai_v52_8k.png') as im:
        ocean = im.convert('L').resize((2048,1024), Image.Resampling.LANCZOS)
    with Image.open(textures/'clouds_8k.jpg') as im:
        clouds = im.convert('L').resize((2048,1024), Image.Resampling.LANCZOS)
    Image.merge('RGB', (ocean,clouds,Image.new('L',(2048,1024)))).save(packed)
    (textures/(packed.name+'.picometa')).write_text(json.dumps({
        'authoredConfig':True, 'textureConverter':{'generateMipmap':True,
        'maxSize':2048,'textureFormat':30,'sourceColorSpace':1539,'targetColorSpace':1539}
    },indent=4),encoding='utf-8')
outputs.append(dict(file=packed.name,size=(2048,1024),sha256=hashlib.sha256(packed.read_bytes()).hexdigest()))

base = (root / 'Sources/Models/EarthShaderV51.usda').read_text()
prefix = '/EarthAsset/_materials/EarthSurface/'
nodes = []
def n(name, kind, out, **inputs):
    text = '            def Shader "'+name+'"\n            {\n                uniform token info:id = "'+kind+'"\n'
    for key, (typ, value) in inputs.items():
        if isinstance(value, str) and value.startswith('$'):
            text += '                '+typ+' inputs:'+key+'.connect = <'+prefix+value[1:]+'.outputs:out>\n'
        else:
            text += '                '+typ+' inputs:'+key+' = '+str(value)+'\n'
    nodes.append(text+'                '+out+' outputs:out\n            }\n')
def link(name, typ='float'): return (typ, '$'+name)
def f(value): return ('float', value)
def quintic(name, source, low, high):
    # C2-continuous feather: zero first/second derivatives at both boundaries.
    n(name+'_Remap','ND_remap_float','float',**{'in':link(source),'inlow':f(low),'inhigh':f(high),'outlow':f(0),'outhigh':f(1)})
    n(name+'_T','ND_clamp_float','float',**{'in':link(name+'_Remap'),'low':f(0),'high':f(1)})
    n(name+'_T2','ND_multiply_float','float',in1=link(name+'_T'),in2=link(name+'_T'))
    n(name+'_T3','ND_multiply_float','float',in1=link(name+'_T2'),in2=link(name+'_T'))
    n(name+'_Six','ND_multiply_float','float',in1=link(name+'_T'),in2=f(6))
    n(name+'_Minus','ND_subtract_float','float',in1=link(name+'_Six'),in2=f(15))
    n(name+'_Inner','ND_multiply_float','float',in1=link(name+'_T'),in2=link(name+'_Minus'))
    n(name+'_Plus','ND_add_float','float',in1=link(name+'_Inner'),in2=f(10))
    n(name,'ND_multiply_float','float',in1=link(name+'_T3'),in2=link(name+'_Plus'))
n('UV','ND_texcoord_vector2','float2',index=('int',0))
# These packed RGBA8 textures contain encoded sRGB bytes. Decode explicitly:
# the 6.1.9 Unlit graph did not apply the image's colorSpace annotation alone.
# RAW prevents an implicit second decode if the importer changes in future.
n('Day','ND_image_color3','color3f',file=('asset','@../Textures/desktop_day_3k.png@ (colorSpace = "raw")'),texcoord=link('UV','float2'))
n('Night','ND_image_color3','color3f',file=('asset','@../Textures/desktop_night_2k.png@ (colorSpace = "raw")'),texcoord=link('UV','float2'))
n('DayLinear','ND_srgb_texture_to_lin_rec709_color3','color3f',**{'in':link('Day','color3f')})
n('NightLinear','ND_srgb_texture_to_lin_rec709_color3','color3f',**{'in':link('Night','color3f')})
n('Normal','ND_normal_vector3','vector3f',space=('string','"world"'))
n('SunDot','ND_dotproduct_vector3','float',in1=link('Normal','vector3f'),in2=('vector3f','(0, 0, 1)'))
# Lights keep Full's 0 to -12 degree fade. Surface illumination gets its own
# wider +/-18 degree feather, centered on the same true UTC solar horizon.
quintic('NightMask','SunDot',0,math.sin(math.radians(-12)))
quintic('DayWeight','SunDot',math.sin(math.radians(-18)),math.sin(math.radians(18)))
# No sqrt(max(N.L, 0)): its unbounded slope at zero made a hard-looking crease.
# This wrapped linear shading is smooth through the horizon, and the feather
# removes it on the night side. Project art tuning, no room lights or exposure.
n('Diffuse','ND_clamp_float','float',**{'in':link('SunDot'),'low':f(0),'high':f(1)})
n('Shade','ND_remap_float','float',**{'in':link('SunDot'),'inlow':f(-1),'inhigh':f(1),'outlow':f(.10),'outhigh':f(1)})
n('DayShaded','ND_multiply_color3FA','color3f',in1=link('DayLinear','color3f'),in2=link('Shade'))
n('NightLand','ND_multiply_color3FA','color3f',in1=link('DayLinear','color3f'),in2=f(.005))
n('NightLights','ND_multiply_color3FA','color3f',in1=link('NightLinear','color3f'),in2=f(.9))
# Same local, smooth night shimmer as Full, without another texture. The time
# coordinate is the shared motion effect clock, never UTC or the globe angle.
n('NightUVScale','ND_multiply_vector2','float2',in1=link('UV','float2'),in2=('float2','(100, 50)'))
n('NightUV3','ND_convert_vector2_vector3','vector3f',**{'in':link('NightUVScale','float2')})
n('NoiseTimePosition','ND_add_vector3','vector3f',in1=link('NightUV3','vector3f'),in2=('vector3f','(0, 0, 0.37)'))
n('NightNoise','ND_noise3d_float','float',amplitude=f(2),pivot=f(1),position=link('NoiseTimePosition','vector3f'))
n('NightNoiseClamp','ND_clamp_float','float',**{'in':link('NightNoise'),'low':f(.5),'high':f(1.5)})
n('NightLocal','ND_multiply_color3FA','color3f',in1=link('NightLights','color3f'),in2=link('NightNoiseClamp'))
n('NightVisible','ND_multiply_color3FA','color3f',in1=link('NightLocal','color3f'),in2=link('NightMask'))
n('DaySurface','ND_mix_color3','color3f',bg=link('NightLand','color3f'),fg=link('DayShaded','color3f'),mix=link('DayWeight'))
n('SurfaceColor','ND_add_color3','color3f',in1=link('DaySurface','color3f'),in2=link('NightVisible','color3f'))
n('Detail','ND_image_color3','color3f',file=('asset','@../Textures/desktop_surface_2k.png@ (colorSpace = "raw")'),texcoord=link('UV','float2'))
n('Ocean','ND_extract_color3','float',**{'in':link('Detail','color3f'),'index':('int',0)})
n('CloudMask','ND_extract_color3','float',**{'in':link('Detail','color3f'),'index':('int',1)})
# Analytic sunlight reflection, independent of all system lights/IBL.
# Official shader view direction works in Shared; HMDTrackingProvider does not
# expose poses there. Keep the per-fragment/per-eye view vector inside rendering.
n('View','ND__viewdirection_vector3','vector3f',space=('string','"world"'))
n('HalfSum','ND_add_vector3','vector3f',in1=link('View','vector3f'),in2=('vector3f','(0, 0, 1)'))
n('Half','ND_normalize_vector3','vector3f',**{'in':link('HalfSum','vector3f')})
n('HalfDot','ND_dotproduct_vector3','float',in1=link('Normal','vector3f'),in2=link('Half','vector3f'))
n('HalfClamp','ND_clamp_float','float',**{'in':link('HalfDot'),'low':f(0),'high':f(1)})
n('GlossExponent','ND_remap_float','float',**{'in':link('Ocean'),'inlow':f(0),'inhigh':f(1),'outlow':f(12),'outhigh':f(96)})
n('Lobe','ND_power_float','float',in1=link('HalfClamp'),in2=link('GlossExponent'))
n('GlossStrength','ND_remap_float','float',**{'in':link('Ocean'),'inlow':f(0),'inhigh':f(1),'outlow':f(.012),'outhigh':f(.70)})
n('GlossMasked','ND_multiply_float','float',in1=link('Lobe'),in2=link('GlossStrength'))
n('GlossSunlit','ND_multiply_float','float',in1=link('GlossMasked'),in2=link('Diffuse'))
n('GlossColor','ND_multiply_color3FA','color3f',in1=('color3f','(1, 0.95, 0.85)'),in2=link('GlossSunlit'))
n('ReflectiveSurface','ND_add_color3','color3f',in1=link('SurfaceColor','color3f'),in2=link('GlossColor','color3f'))
# Cloud density is the existing Full visibility envelope, composited in the
# opaque surface. This changes RGB only: it can never reveal the room behind.
n('CloudDensity','ND_multiply_float','float',in1=link('CloudMask'),in2=f(0))
n('CloudDay','ND_multiply_float','float',in1=link('Shade'),in2=link('DayWeight'))
n('CloudShade','ND_add_float','float',in1=link('CloudDay'),in2=f(.002))
n('CloudColor','ND_multiply_color3FA','color3f',in1=('color3f','(0.92, 0.96, 1)'),in2=link('CloudShade'))
n('CloudComposite','ND_mix_color3','color3f',bg=link('ReflectiveSurface','color3f'),fg=link('CloudColor','color3f'),mix=link('CloudDensity'))
n('Surface','ND__unlit_surfaceshader','token',color=link('CloudComposite','color3f'),opacity=f(1),applyPostProcessToneMap=('bool','0'),hasPremultipliedAlpha=('bool','0'))
material = '''        def Material "EarthSurface" (
            customData = {
                bool applyToneMapping = 0
                string blendingMode = "OPAQUE"
                string cullingMode = "BACK"
                bool depthTest = 1
                bool depthWrite = 1
                string materialType = "SHADER_GRAPH"
                string shaderGraphGuid = "6b5483a1-4a28-4b62-a062-202610230062"
            }
        ) {
            token outputs:mtlx:surface.connect = </EarthAsset/_materials/EarthSurface/Surface.outputs:out>
'''+''.join(nodes)+'        }\n    }\n}\n'
(root/'Sources/Models/DesktopEarthV62.usda').write_text(base[:base.index('        def Material "EarthSurface"')]+material)
scene=(root/'Sources/Scenes/EarthSwanV51.usda').read_text().replace('EarthShaderV51.usda','DesktopEarthV62.usda')
(root/'Sources/Scenes/DesktopEarthV62.usda').write_text(scene.rstrip()+'\n')
print(json.dumps(outputs, indent=2))
