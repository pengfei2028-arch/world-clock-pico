import sys,re,ast,math,json
from pathlib import Path
from PIL import Image
import argparse
p=argparse.ArgumentParser();p.add_argument('--editor',type=Path,default=Path(__file__).resolve().parents[1]/'WorldClockEditor');args=p.parse_args()
editor=args.editor.resolve()
base=(editor/'Sources/Models/EarthShaderV51.usda').read_text()
def arr(name):return ast.literal_eval(re.search(name+r' = (\[[^\n]+\])',base).group(1))
pts=arr(r'point3f\[\] points');faces=arr(r'int\[\] faceVertexIndices')
uv=[]
for i in range(0,len(faces),3):
 tri=[]
 for idx in faces[i:i+3]:
  x,y,z=pts[idx];lat=math.degrees(math.asin(max(-1,min(1,z/math.sqrt(x*x+y*y+z*z)))))
  lon=(math.degrees(math.atan2(y,x))-198+180)%360-180
  tri.append([(lon+180)/360,(lat+90)/180])
 if max(t[0] for t in tri)-min(t[0] for t in tri)>.5:
  for t in tri:
   if t[0]<.5:t[0]+=1
 # Pole longitude is geometrically undefined. Match adjacent vertices to avoid fans crossing map.
 for t in tri:
  if t[1]<1e-5 or t[1]>1-1e-5:t[0]=sum(a[0] for a in tri if 1e-5<a[1]<1-1e-5)/max(1,sum(1 for a in tri if 1e-5<a[1]<1-1e-5))
 uv+=tri
# Longitude wraps in shader before mapping into gutters.
uvline='                            texCoord2f[] primvars:st1 = ['+', '.join('(%0.9f, %0.9f)'%tuple(t) for t in uv)+'] (\n                                interpolation = "faceVarying"\n                            )\n'
base=base.replace('                            uniform bool doubleSided',uvline+'                            uniform bool doubleSided',1)
p='/EarthAsset/_materials/EarthSurface/'
nodes=[]
def n(name,id,out,**inputs):
 s='            def Shader "'+name+'"\n            {\n                uniform token info:id = "'+id+'"\n'
 for key,(typ,value) in inputs.items():
  if isinstance(value,str) and value.startswith('$'):s+='                '+typ+' inputs:'+key+'.connect = <'+p+value[1:]+'.outputs:out>\n'
  else:s+='                '+typ+' inputs:'+key+' = '+str(value)+'\n'
 s+='                '+out+' outputs:out\n            }\n';nodes.append(s)
def f(x):return ('float',x)
def c(x):return ('color3f',x)
def v(x):return ('vector3f',x)
def link(x,typ='float'):return (typ,'$'+x)
def srgb(h):
 a=[int(h[i:i+2],16)/255 for i in (0,2,4)];return '('+', '.join(str(x/12.92 if x<=.04045 else ((x+.055)/1.055)**2.4) for x in a)+')'
Image.new('RGB',(4,4),(128,128,128)).save(editor/'Sources/Textures/temperature_placeholder.png')
n('GeographicUV','ND_texcoord_vector2','float2',index=('int',1))
n('WrappedUV','ND_modulo_vector2','float2',in1=link('GeographicUV','float2'),in2=('float2','(1, 1000)'))
n('UVScale','ND_multiply_vector2','float2',in1=link('WrappedUV','float2'),in2=('float2','(0.99861303745, 0.9958506224)'))
n('DataUV','ND_add_vector2','float2',in1=link('UVScale','float2'),in2=('float2','(0.001040221914, 0.002074688797)'))
n('Temperature','ND_image_vector3','vector3f',file=('asset','@../Textures/temperature_placeholder.png@'),texcoord=link('DataUV','float2'))
n('Decoded','ND_dotproduct_vector3','float',in1=link('Temperature','vector3f'),in2=v('(652.8, 2.55, 0)'))
n('Celsius','ND_add_float','float',in1=link('Decoded'),in2=f(-100))
n('Validity','ND_dotproduct_vector3','float',in1=link('Temperature','vector3f'),in2=v('(0, 0, 1)'))
n('Valid','ND_remap_float','float',**{'in':link('Validity'),'inlow':f(.999),'inhigh':f(1),'outlow':f(0),'outhigh':f(1)})
n('ValidClamp','ND_clamp_float','float',**{'in':link('Valid'),'low':f(0),'high':f(1)})
colors=['173d8a','2462ae','2d97ca','76c5d3','c7ddce','f2d583','f3ab58','dd653c','a92c32']
last=None
for i,threshold in enumerate([-30,-20,-10,0,10,20,30,40]):
 n('BandRange'+str(i),'ND_remap_float','float',**{'in':link('Celsius'),'inlow':f(threshold-.45),'inhigh':f(threshold+.45),'outlow':f(0),'outhigh':f(1)})
 n('Band'+str(i),'ND_clamp_float','float',**{'in':link('BandRange'+str(i)),'low':f(0),'high':f(1)})
 name='Color'+str(i)
 n(name,'ND_mix_color3','color3f',bg=link(last,'color3f') if last else c(srgb(colors[0])),fg=c(srgb(colors[i+1])),mix=link('Band'+str(i)))
 last=name
n('Palette','ND_mix_color3','color3f',bg=c(srgb('747b83')),fg=link(last,'color3f'),mix=link('ValidClamp'))
n('SurfaceUV','ND_texcoord_vector2','float2',index=('int',0))
n('Day','ND_image_color3','color3f',file=('asset','@../Textures/temperature_placeholder.png@'),texcoord=link('SurfaceUV','float2'))
n('LumaVector','ND_convert_color3_vector3','vector3f',**{'in':link('Day','color3f')})
n('Luma','ND_dotproduct_vector3','float',in1=link('LumaVector','vector3f'),in2=v('(.2126, .7152, .0722)'))
n('NeutralValue','ND_remap_float','float',**{'in':link('Luma'),'inlow':f(0),'inhigh':f(1),'outlow':f(.03),'outhigh':f(.9)})
n('Neutral','ND_convert_float_color3','color3f',**{'in':link('NeutralValue')})
n('MutedDay','ND_mix_color3','color3f',bg=link('Day','color3f'),fg=link('Neutral','color3f'),mix=f(.8))
n('Overlay','ND_mix_color3','color3f',bg=link('MutedDay','color3f'),fg=link('Palette','color3f'),mix=f(.55))
n('ReliefValue','ND_remap_float','float',**{'in':link('Luma'),'inlow':f(0),'inhigh':f(1),'outlow':f(.65),'outhigh':f(1.45)})
n('Relief','ND_multiply_color3FA','color3f',in1=link('Overlay','color3f'),in2=link('ReliefValue'))
n('Diffuse','ND_multiply_color3FA','color3f',in1=link('Relief','color3f'),in2=f(.65))
n('Fill','ND_multiply_color3FA','color3f',in1=link('Relief','color3f'),in2=f(.55))
n('FillRGBA','ND_convert_color3_color4','color4f',**{'in':link('Fill','color3f')})
n('Normal','ND_image_vector3','vector3f',file=('asset','@../Textures/temperature_placeholder.png@'),texcoord=link('SurfaceUV','float2'))
n('NormalDouble','ND_multiply_vector3FA','vector3f',in1=link('Normal','vector3f'),in2=f(2))
n('NormalDecoded','ND_subtract_vector3','vector3f',in1=link('NormalDouble','vector3f'),in2=v('(1, 1, 1)'))
n('NormalSoft','ND_multiply_vector3','vector3f',in1=link('NormalDecoded','vector3f'),in2=v('(.55, .55, 1)'))
n('NormalMap','ND_normalize_vector3','vector3f',**{'in':link('NormalSoft','vector3f')})
n('Surface','ND_pbr_surfaceshader','token',baseColor=link('Diffuse','color3f'),emissive=link('FillRGBA','color4f'),normal=link('NormalMap','vector3f'),metallic=f(0),roughness=f(.95),reflectance=f(.05),opacity=f(1))
material='''        def Material "EarthSurface" (
            customData = {
                bool applyToneMapping = 1
                string blendingMode = "OPAQUE"
                string cullingMode = "BACK"
                bool depthTest = 1
                bool depthWrite = 1
                string materialType = "SHADER_GRAPH"
                string shaderGraphGuid = "ad1187f7-2916-4a35-9532-202610220053"
            }
        ) {
            token outputs:mtlx:surface.connect = </EarthAsset/_materials/EarthSurface/Surface.outputs:out>
'''+''.join(nodes)+'        }\n    }\n}\n'
output=base[:base.index('        def Material "EarthSurface"')]+material
(editor/'Sources/Models/TemperatureEarthV53.usda').write_text(output)
scene=(editor/'Sources/Scenes/EarthSwanV51.usda').read_text().replace('EarthShaderV51.usda','TemperatureEarthV53.usda')
(editor/'Sources/Scenes/TemperatureV53.usda').write_text(scene.rstrip()+'\n')
print(json.dumps({'uv1Corners':len(uv),'vertices':len(pts),'sameGeometry':True,'nodes':len(nodes)}))
