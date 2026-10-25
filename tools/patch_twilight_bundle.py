"""Splice the SDK-compiled V88 shader into the accepted bundle without reimporting assets."""
from pathlib import Path
import argparse,zipfile,json,xml.etree.ElementTree as ET,math
from pack_scalar_textures import fields,read_var,prefix,replace_bundle
p=argparse.ArgumentParser();p.add_argument('--baseline',type=Path,required=True);p.add_argument('--candidate',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--report',type=Path,required=True);a=p.parse_args()
old=zipfile.ZipFile(a.baseline);new=zipfile.ZipFile(a.candidate)
ot=ET.fromstring(old.read('8.shader_origin_mtlx.data'));nt=ET.fromstring(new.read('8.shader_origin_mtlx.data'))
def nodes(t):return {e.get('name'):e for e in t.iter() if e.get('nodedef')}
on,nn=nodes(ot),nodes(nt)
# Both compile source and exported graph refer to original textures in the delivered result.
for name in ('Day','Night','NormalTex','SpecularTex'):
 before=on[name].find("input[@name='file']")
 after=nn[name].find("input[@name='file']")
 after.set('value',before.get('value'))
changed=[k for k in on if ET.tostring(on[k])!=ET.tostring(nn[k])]
added=set(nn)-set(on)
assert changed==['NightRGBA'],changed
assert added=={'TwilightAbs','TwilightWidth','TwilightClamp','TwilightSquare','TwilightGain','TwilightColor','SurfaceEmission'},added
assert nn['NightRGBA'].find("input[@name='in']").get('nodename')=='SurfaceEmission'
op=json.loads(old.read('7.shader_origin_parm_info.data'));np=json.loads(new.read('7.shader_origin_parm_info.data'))
np['samplerParameters']=op['samplerParameters']
# Retain accepted sampler/default metadata, add only new uniforms.
for k,v in op['uniformValues'].items():np['uniformValues'][k]=v
newkeys=set(np['uniformValues'])-set(op['uniformValues'])
assert newkeys=={'TwilightWidth_in1','TwilightClamp_high','TwilightClamp_low','TwilightGain_in2'},newkeys
def instance_fields(data):
 head,pos=prefix(data);items=[]
 while pos<len(data):
  start=pos;tag,pos=read_var(data,pos);wire=tag&7;header=False
  if wire==0:value,pos=read_var(data,pos)
  elif wire==2:
   # PICO repeated-object arrays carry an unframed type header before their records.
   if data[pos]==2 and data[pos+2:pos+11]==b'spatial::':
    value,size=prefix(data[pos:]);pos+=size;header=True
   else:
    size,pos=read_var(data,pos);value=data[pos:pos+size];pos+=size
  elif wire in (1,5):
   size=8 if wire==1 else 4;value=data[pos:pos+size];pos+=size
  else:raise AssertionError((pos,tag,wire))
  items.append((tag,value,data[start:pos],header))
 assert pos==len(data)
 return head,items
head,oi=instance_fields(old.read('9.materialinstance.data'));_,ni=instance_fields(new.read('9.materialinstance.data'))
uh=next(v for t,v,r,h in ni if t==114 and h)
newuniform=[]
for t,v,raw,header in ni:
 if t!=114 or header:continue
 key=dict(fields(uh+v)[1])[10].decode()
 if key in newkeys:newuniform.append(raw)
assert len(newuniform)==4
newnames=[raw for t,v,raw,h in ni if t==146 and v.decode() in newkeys]
assert len(newnames)==4
lastuniform=max(i for i,(t,v,r,h) in enumerate(oi) if t==114)
lastnames=max(i for i,(t,v,r,h) in enumerate(oi) if t==146)
instance=head
for i,(t,v,raw,h) in enumerate(oi):
 instance+=raw
 if i==lastuniform:instance+=b''.join(newuniform)
 if i==lastnames:instance+=b''.join(newnames)
# Mathematical check of the actual compiled scalar graph, not a duplicated Kotlin curve.
def scalar(name,dot):
 e=nn[name]
 if name=='SunDot':return dot
 v={i.get('name'):scalar(i.get('nodename'),dot) if i.get('nodename') else float(i.get('value')) for i in e.findall('input')}
 if e.tag=='absval':return abs(v['in'])
 if e.tag=='subtract':return v['in1']-v['in2']
 if e.tag=='multiply':return v['in1']*v['in2']
 if e.tag=='clamp':return min(v['high'],max(v['low'],v['in']))
 raise AssertionError(e.tag)
w=np['uniformValues']['TwilightWidth_in1']['mxValue']['Value'];g=np['uniformValues']['TwilightGain_in2']['mxValue']['Value']
assert abs(w-math.sin(math.radians(24)))<1e-6
def diffuse(x):return max(x,0)+scalar('TwilightGain',x)
previous=0
for i in range(20001):
 d=-1+2*i/20000;v=diffuse(d)
 assert math.isfinite(v) and v>=previous-1e-8
 assert scalar('TwilightGain',d)>=0
 if abs(d)>=w:assert scalar('TwilightGain',d)==0
 previous=v
for x in (-w,0,w):
 h=1e-5
 left=(diffuse(x)-diffuse(x-h))/h;right=(diffuse(x+h)-diffuse(x))/h
 assert abs(left-right)<.0001,(x,left,right)
replacements={'6.shader.data':new.read('6.shader.data'),'7.shader_origin_parm_info.data':json.dumps(np,separators=(',',':')).encode(),'8.shader_origin_mtlx.data':ET.tostring(nt,encoding='utf-8',xml_declaration=True),'9.materialinstance.data':instance}
a.output.write_bytes(a.baseline.read_bytes());replace_bundle(a.output,replacements)
with zipfile.ZipFile(a.output) as z:
 for n in old.namelist():
  if n not in replacements:assert z.read(n)==old.read(n),n
 checkhead,checkitems=instance_fields(z.read('9.materialinstance.data'))
 assert len(checkitems)==len(oi)+8
 assert all(raw in z.read('9.materialinstance.data') for t,v,raw,h in oi)
 assert len(z.namelist())==len(old.namelist())
report=dict(changedOldNodes=changed,newNodes=sorted(added),newUniforms=sorted(newkeys),halfWidthDegrees=24,horizonLift=scalar('TwilightGain',0),linearReferenceContinuousSlope=True,originalUniformsUnchanged=True,allFourTexturePayloadsUnchanged=True,meshAndSceneAndAssetMapUnchanged=True,newTextures=0)
a.report.write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
