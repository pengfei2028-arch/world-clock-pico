"""PICO MaterialX: one texture-free transparent material shared by spherical decal batches."""
from pathlib import Path
import argparse,re
p=argparse.ArgumentParser();p.add_argument('--editor',type=Path,required=True);a=p.parse_args();root=a.editor
# One source for Compose selection and the material. Editor compiler serializes
# these linear inputs to display sRGB; the bundle verifier checks the result.
style=root.parent/'WorldClockSpatial/app/src/main/java/com/wupengfei/worldclock/content/AppSelectionStyle.kt'
accent=int(re.search(r'COLOR_ARGB = 0x([0-9A-Fa-f]+)',style.read_text(encoding='utf-8'))[1],16)
def linear(v):
 c=v/255
 return c/12.92 if c<=.04045 else ((c+.055)/1.055)**2.4
selection_color='('+','.join(f'{linear((accent>>shift)&255):.9f}' for shift in [16,8,0])+')'
prefix='/QuakeAsset/QuakeMaterial/';nodes=[]
def n(name,kind,out='float',**inputs):
 s='        def Shader "'+name+'" {\n            uniform token info:id = "'+kind+'"\n'
 for k,(t,v) in inputs.items():
  s+='            '+t+' inputs:'+k+('.connect = <'+prefix+v[1:]+'.outputs:out>' if isinstance(v,str) and v.startswith('$') else ' = '+str(v))+'\n'
 nodes.append(s+'            '+out+' outputs:out\n        }\n')
def f(x):return ('float',x)
def l(x,t='float'):return (t,'$'+x)
def op(name,kind,x,y):n(name,'ND_'+kind+'_float',in1=x,in2=y)
def smooth(name,x,lo,hi):n(name,'ND_smoothstep_float',**{'in':l(x),'low':f(lo),'high':f(hi)})
def mul(name,x,y):op(name,'multiply',l(x),l(y))
n('UV','ND_texcoord_vector2','float2',index=('int',0))
n('Meta','ND_texcoord_vector2','float2',index=('int',1))
n('Vertex','ND_geomcolor_color4','color4',index=('int',0))
for name,index in [('Wave',0),('Slot',1),('Seed',2),('Magnitude',3)]:n(name,'ND_extract_color4',**{'in':l('Vertex','color4'),'index':('int',index)})
for name,index in [('Birth',0),('Event',1)]:n(name,'ND_extract_vector2',**{'in':l('Meta','float2'),'index':('int',index)})
n('Radius','ND_magnitude_vector2',**{'in':l('UV','float2')})
op('Animation','add',f(0),f(0)) # Animation_in2, seconds, only one animation clock
op('SelectionDelta','subtract',l('Event'),f(-1)) # SelectionDelta_in2 = selected token
n('SelectionAbs','ND_absval_float',**{'in':l('SelectionDelta')})
op('SelectionWeight','subtract',f(1),l('SelectionAbs'))
n('Selected','ND_clamp_float',**{'in':l('SelectionWeight'),'low':f(0),'high':f(1)})
op('Age','subtract',l('Animation'),l('Birth'));smooth('Arrival','Age',0,.18)
smooth('DotEdge','Radius',.59,.72);op('Dot','subtract',f(1),l('DotEdge'));mul('DotVisible','Dot','Arrival')
op('Cycles','divide',l('Animation'),f(2.4));op('SeedTime','add',l('Cycles'),l('Seed'))
last=None
for i in range(3):
 k='Ring'+str(i)
 op(k+'Offset','add',l('SeedTime'),f(1-i/3))
 op(k+'Phase','modulo',l(k+'Offset'),f(1))
 n(k+'Range','ND_mix_float',bg=f(3.2/6.5),fg=f(5.7/6.5),mix=l('Selected'))
 mul(k+'Travel',k+'Phase',k+'Range');op(k+'Radius','add',l(k+'Travel'),f(.3/6.5))
 op(k+'Delta','subtract',l('Radius'),l(k+'Radius'));n(k+'Distance','ND_absval_float',**{'in':l(k+'Delta')})
 smooth(k+'Edge',k+'Distance',.008,.023);op(k+'Line','subtract',f(1),l(k+'Edge'))
 smooth(k+'Rise',k+'Phase',0,.12);op(k+'Fall','subtract',f(1),l(k+'Phase'));mul(k+'Fall2',k+'Fall',k+'Fall')
 mul(k+'Envelope',k+'Rise',k+'Fall2');mul(k+'Alpha',k+'Line',k+'Envelope')
 if last:op(k+'Sum','add',l(last),l(k+'Alpha'));last=k+'Sum'
 else:last=k+'Alpha'
n('WaveStrength','ND_mix_float',bg=f(.22),fg=f(.72),mix=l('Selected'))
mul('WaveAlpha',last,'WaveStrength')
for i in range(6):
 k='Gain'+str(i)
 op(k+'Delta','subtract',l('Slot'),f(i/5));n(k+'Abs','ND_absval_float',**{'in':l(k+'Delta')})
 n(k+'Match','ND_ifgreatereq_float',value1=f(.08),value2=l(k+'Abs'),in1=f(1),in2=f(0))
 op(k,'multiply',l(k+'Match'),f(0)) # GainN_in2, smooth CPU slot envelope
last='Gain0'
for i in range(1,6):
 name='Gain' if i==5 else 'GainSum'+str(i)
 op(name,'add',l(last),l('Gain'+str(i)));last=name
mul('WavesVisible','WaveAlpha','Gain')
n('Opacity','ND_mix_float',bg=l('DotVisible'),fg=l('WavesVisible'),mix=l('Wave'))
n('YellowBand','ND_ifgreatereq_float',value1=l('Magnitude'),value2=f(.25),in1=f(1),in2=f(0))
n('RedBand','ND_ifgreatereq_float',value1=l('Magnitude'),value2=f(.75),in1=f(1),in2=f(0))
op('PointOnly','subtract',f(1),l('Wave'));mul('YellowPoint','YellowBand','PointOnly')
# Linear input values compile to displayed sRGB #FFD54F and #FF5145.
n('PointColor','ND_mix_color3','color3f',bg=('color3f','(1,1,1)'),fg=('color3f','(1,.6653873,.0781874)'),mix=l('YellowPoint'))
n('MagnitudeColor','ND_mix_color3','color3f',bg=l('PointColor','color3f'),fg=('color3f','(1,.0822827,.0595112)'),mix=l('RedBand'))
n('Color','ND_mix_color3','color3f',bg=l('MagnitudeColor','color3f'),fg=('color3f',selection_color),mix=l('Selected'))
n('Surface','ND__unlit_surfaceshader','token',color=l('Color','color3f'),opacity=l('Opacity'),applyPostProcessToneMap=('bool',0),hasPremultipliedAlpha=('bool',0))
head="""#usda 1.0
(defaultPrim = "QuakeAsset"
 metersPerUnit = 1
 upAxis = "Y")
def Xform "QuakeAsset" {
    def Mesh "Template" {
        point3f[] points = [(-1,-1,0),(1,-1,0),(0,1,0)]
        int[] faceVertexCounts = [3]
        int[] faceVertexIndices = [0,1,2]
        normal3f[] normals = [(0,0,1),(0,0,1),(0,0,1)] (interpolation = "vertex")
        texCoord2f[] primvars:st = [(-1,-1),(1,-1),(0,1)] (interpolation = "vertex")
        texCoord2f[] primvars:st1 = [(0,1),(0,1),(0,1)] (interpolation = "vertex")
        color3f[] primvars:displayColor = [(0,0,0),(0,0,0),(0,0,0)] (interpolation = "vertex")
        float[] primvars:displayOpacity = [1,1,1] (interpolation = "vertex")
        uniform token subdivisionScheme = "none"
        rel material:binding = </QuakeAsset/QuakeMaterial>
    }
    def Material "QuakeMaterial" (customData = {
        bool applyToneMapping = 0
        string blendingMode = "TRANSPARENT"
        string cullingMode = "BACK"
        bool depthTest = 1
        bool depthWrite = 0
        string materialType = "SHADER_GRAPH"
        string shaderGraphGuid = "fd128f24-8040-4b13-938a-202610250078"
    }) {
        token outputs:mtlx:surface.connect = </QuakeAsset/QuakeMaterial/Surface.outputs:out>
"""
(root/'Sources/Models/QuakeDecalsV78.usda').write_text(head+''.join(nodes)+'    }\n}\n',encoding='utf-8')
(root/'Sources/Scenes/QuakeDecalsV78.usda').write_text('#usda 1.0\n(defaultPrim = "Root"\n metersPerUnit = 1\n upAxis = "Y")\ndef Xform "Root"\n{\n    def "QuakeDecals" (prepend references = @../Models/QuakeDecalsV78.usda@)\n    {\n    }\n}\n',encoding='utf-8')
print('nodes',len(nodes),'textures',0)
