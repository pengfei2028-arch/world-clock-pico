"""Evaluate the shipped MaterialX graph, including loop seams and slot masks."""
from pathlib import Path
import argparse,json,math,zipfile,xml.etree.ElementTree as ET
from functools import lru_cache
p=argparse.ArgumentParser();p.add_argument('--bundle',type=Path,required=True);a=p.parse_args()
with zipfile.ZipFile(a.bundle) as z:
 names=z.namelist();params=json.loads(z.read(next(n for n in names if 'shader_origin_parm_info' in n)))
 assert params['samplerParameters']=={}
 assert not any(n.endswith('.texture.data') for n in names)
 graph=ET.fromstring(z.read(next(n for n in names if 'shader_origin_mtlx' in n)))
 nodes={e.get('name'):e for e in graph.iter() if e.get('nodedef')}
 assert {'Animation_in2','SelectionDelta_in2','Gain0_in2','Gain1_in2','Gain2_in2','Gain3_in2','Gain4_in2','Gain5_in2'}<=params['uniformValues'].keys()
 def evaluate(output='Opacity',radius=0,time=3,birth=-1,token=7,selected=-1,wave=1,slot=0,seed=.37,gains=(1,1,1,1,1,1),band=0):
  overrides={'Animation_in2':time,'SelectionDelta_in2':selected,**{f'Gain{i}_in2':g for i,g in enumerate(gains)}}
  @lru_cache(None)
  def scalar(name):
   e=nodes[name]
   if e.tag=='texcoord':return (radius,0) if name=='UV' else (birth,token)
   if e.tag=='geomcolor':return (wave,slot/5,seed,band/2)
   values={}
   for i in e.findall('input'):
    key=name+'_'+i.get('name')
    if key in overrides:v=overrides[key]
    elif i.get('nodename'):v=scalar(i.get('nodename'))
    else:
     s=i.get('value');v=tuple(float(x) for x in s.split(',')) if ',' in s else float(s)
    values[i.get('name')]=v
   v=values
   if e.tag=='extract':return v['in'][int(v['index'])]
   if e.tag=='magnitude':return math.sqrt(sum(x*x for x in v['in']))
   if e.tag=='add':return v['in1']+v['in2']
   if e.tag=='subtract':return v['in1']-v['in2']
   if e.tag=='multiply':return v['in1']*v['in2']
   if e.tag=='divide':return v['in1']/v['in2']
   if e.tag=='modulo':return v['in1']%v['in2']
   if e.tag=='absval':return abs(v['in'])
   if e.tag=='clamp':return max(v['low'],min(v['high'],v['in']))
   if e.tag=='smoothstep':
    t=max(0,min(1,(v['in']-v['low'])/(v['high']-v['low'])));return t*t*(3-2*t)
   if e.tag=='mix':
    if isinstance(v['bg'],tuple):return tuple(x+(y-x)*v['mix'] for x,y in zip(v['bg'],v['fg']))
    return v['bg']+(v['fg']-v['bg'])*v['mix']
   if e.tag=='ifgreatereq':return v['in1'] if v['value1']>=v['value2'] else v['in2']
   raise AssertionError(e.tag)
  return scalar(output)
 for slot in range(6):
  for i in range(101):
   r=i/100
   assert evaluate(radius=r,slot=slot,gains=(0,0,0,0,0,0))==0
   assert abs(evaluate(radius=r,time=5,slot=slot)-evaluate(radius=r,time=7.4,slot=slot))<1e-10
   # Loop edge, with other staggered rings also evaluated.
   seam=(1-.37)*2.4
   assert abs(evaluate(radius=r,time=seam-1e-7,slot=slot)-evaluate(radius=r,time=seam+1e-7,slot=slot))<1e-4
 assert evaluate(wave=0,time=3,birth=3)==0
 assert evaluate(wave=0,time=3.18,birth=3)==1
 assert evaluate(wave=0,radius=.9)==0
 assert evaluate('Selected',token=7,selected=7)==1
 assert evaluate('Selected',token=7,selected=8)==0
 white=evaluate('Color',wave=0,band=0);yellow=evaluate('Color',wave=0,band=1);red=evaluate('Color',wave=0,band=2)
 assert white==(1.,1.,1.)
 assert yellow[0]>yellow[1]>.8 and yellow[2]<.4
 assert red[0]>.9 and red[1]<.4 and red[2]<.4
 assert evaluate('Color',wave=1,band=1)==white
 assert all(abs(a-b)<1e-8 for a,b in zip(evaluate('Color',wave=1,band=2),red))
 # Selection overrides magnitude for BOTH the epicenter and waves. A different
 # selected token or clearing selection must immediately restore magnitude colors.
 accent=(116/255,91/255,1.)
 for band in range(3):
  for wave in range(2):
   for selected in (7,):
    assert all(abs(a-b)<.0001 for a,b in zip(evaluate('Color',wave=wave,band=band,selected=selected),accent))
   assert evaluate('Color',wave=wave,band=band,selected=8)==evaluate('Color',wave=wave,band=band,selected=-1)
 for slot in range(6):
  gains=tuple(1 if i==slot else 0 for i in range(6))
  for tested in range(6):
   assert evaluate('Gain',slot=tested,gains=gains)==(1 if slot==tested else 0)
 # A selected event reaches the wider ring radius; ordinary events remain restrained.
 t=(.8-.37)*2.4;r=(.3+5.7*.8)/6.5
 assert evaluate(radius=r,time=t,selected=7)>0
 assert evaluate(radius=r,time=t,selected=-1)==0
 print(json.dumps(dict(bundleBytes=a.bundle.stat().st_size,textureSamplers=0,materialGraphs=1,loopContinuity=True,sixSlotsIndependent=True,magnitudeColors=True,selectedColor='#745BFF',selectionRestoresMagnitude=True,selectionAndArrivalMasks=True)))
