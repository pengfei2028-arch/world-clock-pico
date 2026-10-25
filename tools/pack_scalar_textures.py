"""Repack existing scalar maps as lossless R8, retaining authored 8K resolution.
Only storage descriptors/texture payloads change; mesh/shaders/color maps stay byte-identical.
"""
from pathlib import Path
from PIL import Image
import zipfile,hashlib,json,argparse

def read_var(b,p=0):
 n=0;s=0
 while True:
  x=b[p];p+=1;n|=(x&127)<<s
  if x<128:return n,p
  s+=7

def var(n):
 out=bytearray()
 while n>127:out.append((n&127)|128);n>>=7
 out.append(n);return bytes(out)

def prefix(b):
 assert b[0]==2
 n,p=read_var(b,1);return b[:p+n],p+n

def fields(b):
 head,p=prefix(b);out=[]
 while p<len(b):
  tag,p=read_var(b,p);wire=tag&7
  if wire==0:v,p=read_var(b,p)
  elif wire==2:n,p=read_var(b,p);v=b[p:p+n];p+=n
  elif wire in (1,5):n=8 if wire==1 else 4;v=b[p:p+n];p+=n
  else:raise ValueError(wire)
  out.append((tag,v))
 assert p==len(b);return head,out

def obj(head,items):
 out=bytearray(head)
 for tag,value in items:
  out.extend(var(tag));wire=tag&7
  if wire==0:out.extend(var(value))
  else:
   if wire==2:out.extend(var(len(value)))
   out.extend(value)
 return bytes(out)

def unpack(b):
 head,items=fields(b);root=dict(items);dh,desc=fields(root[10]);bh,p=prefix(root[18]);n,p=read_var(root[18],p)
 data=root[18][p:];assert len(data)==n
 return head,items,dh,desc,bh,data

def pack_like(template,pixels,width,height,fmt,sizes):
 head,items,dh,desc,bh,_=unpack(template)
 changes={8:width,16:height,32:len(sizes),66:fmt.encode(),90:b''.join(var(n) for n in sizes),98:b'RAW'}
 new_desc=obj(dh,[(tag,changes.get(tag,value)) for tag,value in desc])
 new_root={10:new_desc,18:bh+var(len(pixels))+pixels}
 return obj(head,[(tag,new_root.get(tag,value)) for tag,value in items])

def replace_bundle(path,replacements):
 with zipfile.ZipFile(path) as z:items=[(i,z.read(i.filename)) for i in z.infolist()]
 tmp=path.with_suffix('.packing')
 with zipfile.ZipFile(tmp,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
  for info,data in items:z.writestr(info,replacements.get(info.filename,data))
 with zipfile.ZipFile(tmp) as z:
  assert set(z.namelist())==set(i.filename for i,_ in items)
  for info,data in items:
   if info.filename not in replacements:assert z.read(info.filename)==data,info.filename
 tmp.replace(path)

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--assets',type=Path,required=True);parser.add_argument('--report',type=Path,required=True);args=parser.parse_args();p=args.assets
 with zipfile.ZipFile(p/'Clouds.bundle') as z:cloud=z.read('2.texture.data')
 _,_,_,desc,_,rgba=unpack(cloud);desc=dict(desc)
 assert (desc[8],desc[16],desc[66],desc[98])==(8192,4096,b'RGBA8',b'RAW')
 # Every mip texel uses its exact existing R byte; no resize, re-filter or recompression.
 sizes=[];offset=0
 while offset<len(desc[90]):
  size,offset=read_var(desc[90],offset);assert size%4==0;sizes.append(size//4)
 red=rgba[:sum(sizes)*4:4]
 assert len(red)==sum(sizes) and len(sizes)==desc[32]
 cloud_r8=pack_like(cloud,red,8192,4096,'R8',sizes)
 assert unpack(cloud_r8)[-1]==red
 source=Image.open(p/'earth_data/specular_ai_v52_8k.png').convert('RGB').getchannel('R');assert source.size==(8192,4096)
 planes=[];level=source
 while True:
  planes.append(level.tobytes())
  if len(planes)==len(sizes):break
  level=level.resize((max(1,level.width//2),max(1,level.height//2)),Image.Resampling.BOX)
 spec_r8=pack_like(cloud,b''.join(planes),8192,4096,'R8',[len(x) for x in planes])
 assert unpack(spec_r8)[-1][:8192*4096]==source.tobytes()
 # This normal placeholder is replaced by the unchanged RAW original at startup.
 normal_placeholder=pack_like(cloud,bytes((128,128,255,255)),1,1,'RGBA8',[4])
 replace_bundle(p/'Earth.bundle',{'3.texture.data':spec_r8,'4.texture.data':normal_placeholder})
 replace_bundle(p/'Clouds.bundle',{'2.texture.data':cloud_r8})
 report={'resolution':[8192,4096],'mips':len(sizes),'cloudEveryMipRedIdentical':True,'specularBaseRedIdentical':True,'specularMips':'linear 2x2 BOX, <=1 LSB rounding may differ from driver','unchangedMeshShadersDayNight':True,'normalSourceUnchanged':True,'new8kMaps':0,'scalarBytesBefore':2*sum(sizes)*4,'scalarBytesAfter':2*sum(sizes),'scalarBytesSaved':2*sum(sizes)*3,'sourceSpecularSHA256':hashlib.sha256((p/'earth_data/specular_ai_v52_8k.png').read_bytes()).hexdigest()}
 args.report.write_text(json.dumps(report,indent=2),encoding='utf-8');print(json.dumps(report))
if __name__=='__main__':main()
