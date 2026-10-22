"""Build an independent desktop asset; never modify the approved immersive sources."""
import argparse, hashlib, json
from pathlib import Path
from PIL import Image

p = argparse.ArgumentParser()
p.add_argument('--editor', type=Path, required=True)
a = p.parse_args()
root = a.editor.resolve()
textures = root / 'Sources/Textures'
outputs = []
for source, target, size in [
    ('day_ai_v51_8k.png', 'desktop_day_3k.png', (3072, 1536)),
    ('night_8k.jpg', 'desktop_night_2k.png', (2048, 1024)),
]:
    with Image.open(textures / source) as im:
        im.convert('RGB').resize(size, Image.Resampling.LANCZOS).save(textures / target)
    outputs.append(dict(file=target, size=size, sha256=hashlib.sha256((textures/target).read_bytes()).hexdigest()))
    # The editor otherwise defaults to 2048 and silently downscales the 3K input.
    meta=textures/(target+'.picometa')
    config=json.loads(meta.read_text(encoding='utf-8-sig')) if meta.exists() else {
        'authoredConfig': True,
        'textureConverter': {'generateMipmap': True, 'textureFormat': 30,
                             'sourceColorSpace': 1539, 'targetColorSpace': 1539}
    }
    config['textureConverter']['maxSize']=4096 if size[0]>2048 else 2048
    meta.write_text(json.dumps(config,indent=4), encoding='utf-8')

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
n('UV','ND_texcoord_vector2','float2',index=('int',0))
n('Day','ND_image_color3','color3f',file=('asset','@../Textures/desktop_day_3k.png@'),texcoord=link('UV','float2'))
n('Night','ND_image_color3','color3f',file=('asset','@../Textures/desktop_night_2k.png@'),texcoord=link('UV','float2'))
n('Normal','ND_normal_vector3','vector3f',space=('string','"world"'))
n('SunDot','ND_dotproduct_vector3','float',in1=link('Normal','vector3f'),in2=('vector3f','(0, 0, 1)'))
n('DayRange','ND_remap_float','float',**{'in':link('SunDot'),'inlow':f(-.08),'inhigh':f(.15),'outlow':f(0),'outhigh':f(1)})
n('DayWeight','ND_clamp_float','float',**{'in':link('DayRange'),'low':f(0),'high':f(1)})
n('ShadeRange','ND_remap_float','float',**{'in':link('SunDot'),'inlow':f(0),'inhigh':f(1),'outlow':f(.22),'outhigh':f(1)})
n('Shade','ND_clamp_float','float',**{'in':link('ShadeRange'),'low':f(.22),'high':f(1)})
n('DayShaded','ND_multiply_color3FA','color3f',in1=link('Day','color3f'),in2=link('Shade'))
n('NightLand','ND_multiply_color3FA','color3f',in1=link('Day','color3f'),in2=f(.025))
n('NightLights','ND_multiply_color3FA','color3f',in1=link('Night','color3f'),in2=f(.9))
n('NightColor','ND_add_color3','color3f',in1=link('NightLand','color3f'),in2=link('NightLights','color3f'))
n('SurfaceColor','ND_mix_color3','color3f',bg=link('NightColor','color3f'),fg=link('DayShaded','color3f'),mix=link('DayWeight'))
n('Surface','ND__unlit_surfaceshader','token',color=link('SurfaceColor','color3f'),opacity=f(1),applyPostProcessToneMap=('bool','0'),hasPremultipliedAlpha=('bool','0'))
material = '''        def Material "EarthSurface" (
            customData = {
                bool applyToneMapping = 0
                string blendingMode = "OPAQUE"
                string cullingMode = "BACK"
                bool depthTest = 1
                bool depthWrite = 1
                string materialType = "SHADER_GRAPH"
                string shaderGraphGuid = "6b5483a1-4a28-4b59-a058-202610220058"
            }
        ) {
            token outputs:mtlx:surface.connect = </EarthAsset/_materials/EarthSurface/Surface.outputs:out>
'''+''.join(nodes)+'        }\n    }\n}\n'
(root/'Sources/Models/DesktopEarthV58.usda').write_text(base[:base.index('        def Material "EarthSurface"')]+material)
scene=(root/'Sources/Scenes/EarthSwanV51.usda').read_text().replace('EarthShaderV51.usda','DesktopEarthV58.usda')
(root/'Sources/Scenes/DesktopEarthV58.usda').write_text(scene)
print(json.dumps(outputs, indent=2))
