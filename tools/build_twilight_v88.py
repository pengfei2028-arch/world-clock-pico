"""V88: compile only a compact solar-angle twilight lift; preserve shipped textures/mesh.
The preview project uses 1 px compile placeholders; NONE enter the runtime bundle.
Run from the repository with --editor and --work, then pack work/probe/Sources/Scenes.
"""
from pathlib import Path
from PIL import Image
import argparse,re,math
p=argparse.ArgumentParser();p.add_argument('--editor',type=Path,required=True);p.add_argument('--work',type=Path,required=True);a=p.parse_args()
src=(a.editor/'Sources/Models/EarthShaderV51.usda').read_text()
root='/EarthAsset/_materials/EarthSurface'
nodes=[]
def node(name,kind,out='float',**inputs):
 s=f'            def Shader "{name}"\n            {{\n                uniform token info:id = "{kind}"\n'
 for key,(typ,value) in inputs.items():
  if isinstance(value,str) and value.startswith('$'):
   s+=f'                {typ} inputs:{key}.connect = <{root}/{value[1:]}.outputs:out>\n'
  else:s+=f'                {typ} inputs:{key} = {value}\n'
 s+=f'                {out} outputs:out\n            }}\n'
 nodes.append(s)
def f(v):return ('float',v)
def link(v):return f('$'+v)
# Compact smooth Lambert extension: max(0,w-|N.L|)^2/(4*w).
# Default w = sin(24deg). No lift outside +-24deg; peak 0.1017 at the horizon.
w=math.sin(math.radians(24))
node('TwilightAbs','ND_absval_float',**{'in':link('SunDot')})
node('TwilightWidth','ND_subtract_float',in1=f(w),in2=link('TwilightAbs'))
node('TwilightClamp','ND_clamp_float',**{'in':link('TwilightWidth'),'low':f(0),'high':f(1)})
node('TwilightSquare','ND_multiply_float',in1=link('TwilightClamp'),in2=link('TwilightClamp'))
node('TwilightGain','ND_multiply_float',in1=link('TwilightSquare'),in2=f(1/(4*w)))
node('TwilightColor','ND_multiply_color3FA','color3f',in1=('color3f','$Day'),in2=link('TwilightGain'))
node('SurfaceEmission','ND_add_color3','color3f',in1=('color3f','$NightColor'),in2=('color3f','$TwilightColor'))
# Keep the existing color4 conversion / alpha (opacity is still 1).
old=f'color3f inputs:in.connect = <{root}/NightColor.outputs:out>'
assert src.count(old)==1
src=src.replace(old,f'color3f inputs:in.connect = <{root}/SurfaceEmission.outputs:out>')
needle='            def Shader "NightRGBA"'
assert src.count(needle)==1
src=src.replace(needle,''.join(nodes)+'\n'+needle)
src=src.replace('202610220049','202610250088')
dest=a.editor/'Sources/Models/EarthShaderV88.usda'
dest.write_text(src,encoding='utf-8')
# Source version refers to original assets; only probe resolves small placeholder images.
probe=a.work/'probe';models=probe/'Sources/Models';scenes=probe/'Sources/Scenes';textures=probe/'Sources/Textures'
for d in [models,scenes,textures]:d.mkdir(parents=True,exist_ok=True)
paths=re.findall(r'asset inputs:file = @([^@]+)@',src)
assert len(paths)==4,paths
for path in paths:
 name=path.replace('\\','/').split('/')[-1]
 assert name.endswith(('.jpg','.png')),name
 Image.new('RGB',(1,1),(128,128,128)).save(textures/name)
 src=src.replace('@'+path+'@','@../Textures/'+name+'@')
(models/'EarthShaderV88.usda').write_text(src,encoding='utf-8')
scene=(a.editor/'Sources/Scenes/EarthSwanV51.usda').read_text()
scene=scene.replace('../Models/EarthShaderV51.usdz','../Models/EarthShaderV88.usda').replace('../Models/EarthShaderV51.usda','../Models/EarthShaderV88.usda')
assert 'EarthShaderV88.usda' in scene,scene
(a.editor/'Sources/Scenes/EarthSwanV88.usda').write_text(scene,encoding='utf-8')
(scenes/'EarthSwanV51.usda').write_text(scene,encoding='utf-8')
print('new scalar nodes',5,'new textures',0,'widthDot',w,'gain',1/(4*w),'probe',probe)
