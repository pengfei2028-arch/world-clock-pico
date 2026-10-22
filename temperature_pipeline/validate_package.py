"""Validate actual numeric packages, with optional independent wgrib2 point checks."""
import argparse,gzip,hashlib,json,struct,subprocess
from pathlib import Path
import numpy as np
from PIL import Image

def validate(folder,wgrib2=None,raw_cache=None):
    folder=Path(folder);m=json.loads((folder/'manifest.json').read_text(encoding='utf8'));points=[]
    for index,f in enumerate(m['frames']):
        for kind in ('grid','texture'):
            b=(folder/f[kind]).read_bytes()
            assert len(b)==f[kind+'Bytes'] and hashlib.sha256(b).hexdigest()==f[kind+'Sha256']
        raw=gzip.decompress((folder/f['grid']).read_bytes())
        assert raw[:4]==b'WCT1' and struct.unpack('<II',raw[4:12])==(1440,721)
        q=np.frombuffer(raw[12:],dtype='<i2').reshape(721,1440)
        rgb=np.array(Image.open(folder/f['texture']),dtype=np.float64)
        assert rgb.shape==(723,1442,3)
        inner=rgb[1:-1,1:-1];valid=q!=-32768
        decoded=inner[:,:,0]*2.56+inner[:,:,1]*.01-100
        assert np.max(np.abs(decoded[valid]-q[valid]/100),initial=0)<1e-5
        assert np.array_equal(inner[:,:,2]==255,valid)
        assert np.array_equal(rgb[:,0],rgb[:,-2]) and np.array_equal(rgb[:,-1],rgb[:,1])
        assert np.array_equal(rgb[0],rgb[1]) and np.array_equal(rgb[-1],rgb[-2])
        if wgrib2 and index in (0,len(m['frames'])//2,len(m['frames'])-1):
            cycle=m['runTime'].replace('-','').replace(':','')[:11]+'Z'
            grib=Path(raw_cache)/(cycle+'-f'+str(f['forecastHour']).zfill(3)+'.grib2')
            assert hashlib.sha256(grib.read_bytes()).hexdigest()==f['sourceSha256']
            for name,lat,lon in [('Beijing',39.75,116.5),('NewYork',40.75,-74),('Sydney',-33.75,151.25),('Greenwich',0,0),('DateLine',0,-180),('NorthPole',90,0),('SouthPole',-90,0)]:
                text=subprocess.check_output([wgrib2,str(grib),'-lon',str(lon%360),str(lat)],text=True)
                independent=float(text.strip().split('val=')[-1])-273.15
                actual=float(q[round((90-lat)*4),round((lon+180)*4)%1440])/100
                error=abs(independent-actual);assert error<.02
                points.append(dict(place=name,validTime=f['validTime'],latitude=lat,longitude=lon,independentC=independent,gridC=actual,absErrorC=error))
    return dict(id=m['id'],source=m['source'],runTime=m['runTime'],first=m['frames'][0]['validTime'],last=m['frames'][-1]['validTime'],frames=len(m['frames']),nodesPerFrame=1440*721,hashesAndSizes=True,numericTextureRoundtrip=True,longitudeGutters=True,poleGutters=True,independentPoints=points)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('package');p.add_argument('--wgrib2');p.add_argument('--raw-cache');p.add_argument('--report');a=p.parse_args()
    result=validate(a.package,a.wgrib2,a.raw_cache);text=json.dumps(result,indent=2)
    if a.report:Path(a.report).write_text(text,encoding='utf8')
    print(text)
