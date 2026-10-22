"""NOAA GFS 0.25 degree TMP at 2 m. Atomic, immutable forecast packages.
Requires Python 3.10+, numpy, Pillow, and NOAA wgrib2 on PATH or --wgrib2.
A texture encodes numeric Celsius, NOT pre-coloured or AI-generated weather.
"""
import contextlib, re
import argparse, datetime as dt, hashlib, json, os, subprocess, time, urllib.request, gzip, struct, shutil
from pathlib import Path
import numpy as np
from PIL import Image
UTC=dt.timezone.utc
BASE='https://nomads.ncep.noaa.gov/cgi-bin/filter_gfs_0p25.pl'
def iso(t): return t.astimezone(UTC).isoformat(timespec='seconds').replace('+00:00','Z')
def sha(b): return hashlib.sha256(b).hexdigest()
def fetch(url):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'WorldClockTemperature/1.0 (GFS 2m only)'}),timeout=90) as r:
                data=r.read(8_000_001)
            if len(data)>8_000_000: raise ValueError('Response exceeds limit')
            return data
        except Exception:
            if attempt==2: raise
            time.sleep(2+attempt*3)
def decode(raw, exe, temporary):
    # Separate metadata request: -grid requires default we:sn order.
    p=temporary/'input.grib2';p.write_bytes(raw)
    result=subprocess.run([exe,str(p),'-s','-grid','-stats','-no_header','-bin',str(temporary/'values.bin')],capture_output=True,text=True,check=True)
    info=result.stdout
    if 'TMP:2 m above ground:' not in info or '(1440 x 721)' not in info or 'undef=0' not in info:
        # Partial data are permitted, but grid/variable identity is not negotiable.
        if 'TMP:2 m above ground:' not in info or '(1440 x 721)' not in info: raise ValueError(info)
    vals=np.fromfile(temporary/'values.bin',dtype='<f4')
    if vals.size!=1440*721:raise ValueError('Unexpected GRIB count')
    kelvin=np.roll(vals.reshape(721,1440)[::-1],720,axis=1).copy()
    good=np.isfinite(kelvin)&(kelvin>=173.15)&(kelvin<360)
    c=kelvin-273.15
    q=np.where(good,np.rint(c*100),-32768).astype('<i2')
    return q,info

def encode(q):
    # Query grid: node-centered north-to-south, longitude -180..179.75, Celsius x100.
    raw=b'WCT1'+struct.pack('<II',1440,721)+q.tobytes()
    # RGB data: R*652.8 + G*2.55 -100 = Celsius; B is validity.
    # One wrapped longitude gutter / duplicated pole row per side.
    good=q!=-32768
    if np.any(q[good]<-10000): raise ValueError("Temperature below numeric texture encoding range")
    n=np.where(good,q.astype(np.int32)+10000,0)
    rgb=np.stack([n//256,n%256,np.where(good,255,0)],axis=-1).astype('uint8')
    rgb=np.concatenate([rgb[:,-1:],rgb,rgb[:,:1]],axis=1)
    rgb=np.concatenate([rgb[:1],rgb,rgb[-1:]],axis=0)
    import io
    stream=io.BytesIO();Image.fromarray(rgb).save(stream,format='PNG')
    return gzip.compress(raw,mtime=0),stream.getvalue()

def choose_cycle(now, hours, start_override=None):
    # Check actual published forecast inventory, not a presumed availability delay.
    latest=now.replace(hour=(now.hour//6)*6,minute=0,second=0,microsecond=0)
    for age in range(5):
        cycle=latest-dt.timedelta(hours=age*6)
        start=start_override if start_override is not None else max(0,int((now-cycle).total_seconds()//3600))
        lead=start+hours
        if lead>120: continue
        url='https://nomads.ncep.noaa.gov/pub/data/nccf/com/gfs/prod/gfs.'+cycle.strftime('%Y%m%d')+'/'+cycle.strftime('%H')+'/atmos/gfs.t'+cycle.strftime('%H')+'z.pgrb2.0p25.f'+str(lead).zfill(3)+'.idx'
        try:
            with urllib.request.urlopen(url,timeout=15) as response: index=response.read(250000).decode('ascii')
            if 'd='+cycle.strftime('%Y%m%d%H')+':TMP:2 m above ground:' in index: return cycle
        except (OSError,ValueError): pass
    raise RuntimeError('No complete published GFS run found; current package remains unchanged')

@contextlib.contextmanager
def output_lock(out):
    out.mkdir(parents=True,exist_ok=True)
    lock=(out/'.producer.lock').open('a+b')
    lock.seek(0);lock.write(b'0');lock.flush();lock.seek(0)
    try:
        if os.name=='nt':
            import msvcrt
            msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
        else:
            import fcntl
            fcntl.flock(lock.fileno(),fcntl.LOCK_EX|fcntl.LOCK_NB)
        yield
    finally: lock.close()  # OS releases the lock even after process termination.

def prune(out, current, keep_hours):
    cutoff=time.time()-keep_hours*3600
    for child in out.iterdir():
        if child.name!=current and re.fullmatch(r'\d{8}T\d{2}Z-f\d{3}-\d{3}-[a-f0-9]{12}',child.name) and child.is_dir() and child.stat().st_mtime<cutoff:
            assert child.resolve().parent==out.resolve()
            shutil.rmtree(child)
    for raw in (out/'raw-cache').glob('*.grib2'):
        if raw.stat().st_mtime<cutoff: raw.unlink()

def build(args):
    now=dt.datetime.now(UTC)
    cycle=dt.datetime.strptime(args.cycle,'%Y%m%d%H').replace(tzinfo=UTC) if args.cycle else choose_cycle(now,args.hours,args.start)
    if cycle.hour not in (0,6,12,18): raise ValueError('GFS cycle must be 00/06/12/18 UTC')
    start=args.start if args.start is not None else max(0,int((now-cycle).total_seconds()//3600))
    end=start+args.hours
    if not 0<=start<=end<=120:raise ValueError('Hourly range must lie in f000..f120')
    runid=cycle.strftime('%Y%m%dT%HZ')
    out=Path(args.output).resolve();out.mkdir(parents=True,exist_ok=True)
    stage=out/('.staging-'+runid)
    if stage.exists(): shutil.rmtree(stage)
    stage.mkdir()
    cache=out/'raw-cache';cache.mkdir(exist_ok=True)
    temporary=stage/'decode';temporary.mkdir(exist_ok=True)
    frames=[]
    for lead in range(start,end+1):
        url=BASE+'?file=gfs.t'+cycle.strftime('%H')+'z.pgrb2.0p25.f'+str(lead).zfill(3)+'&lev_2_m_above_ground=on&var_TMP=on&dir=%2Fgfs.'+cycle.strftime('%Y%m%d')+'%2F'+cycle.strftime('%H')+'%2Fatmos'
        rawfile=cache/(runid+'-f'+str(lead).zfill(3)+'.grib2')
        if rawfile.exists():raw=rawfile.read_bytes();obtained=dt.datetime.fromtimestamp(rawfile.stat().st_mtime,UTC)
        else:
            raw=fetch(url)
            if not raw.startswith(b'GRIB') or not raw.endswith(b'7777'):raise ValueError('Not a complete GRIB message: '+url)
            rawtmp=rawfile.with_suffix('.tmp');rawtmp.write_bytes(raw);os.replace(rawtmp,rawfile);obtained=dt.datetime.now(UTC)
        grid,info=decode(raw,args.wgrib2,temporary)
        expected='d='+cycle.strftime('%Y%m%d%H')+':TMP:2 m above ground:'
        if expected not in info or (str(lead)+' hour fcst' not in info if lead else ':anl:' not in info):raise ValueError('GRIB timestamp differs from URL: '+info)
        numeric,png=encode(grid)
        stem='f'+str(lead).zfill(3)
        (stage/(stem+'.wctz')).write_bytes(numeric);(stage/(stem+'.png')).write_bytes(png)
        valid=cycle+dt.timedelta(hours=lead)
        values=grid[grid!=-32768]/100.0
        frame={'validTime':iso(valid),'forecastHour':lead,'grid':stem+'.wctz','texture':stem+'.png','gridBytes':len(numeric),'textureBytes':len(png),'gridSha256':sha(numeric),'textureSha256':sha(png),'sourceUrl':url,'sourceSha256':sha(raw),'fetchedAt':iso(obtained),'minC':float(values.min()) if values.size else None,'maxC':float(values.max()) if values.size else None,'missingCount':int((grid==-32768).sum())}
        frames.append(frame);print(json.dumps({'frame':stem,'valid':iso(valid),'min':frame['minC'],'max':frame['maxC']}),flush=True)
    # Validation complete before publishing. Never overwrite an immutable run's bytes.
    package=runid+'-f'+str(start).zfill(3)+'-'+str(end).zfill(3)+'-'+sha(json.dumps(frames,sort_keys=True).encode())[:12]
    manifest={'schema':1,'id':package,'source':'NOAA GFS','variable':'TMP','level':'2 m above ground','units':'C','kind':('analysis' if end==0 else 'analysis+forecast' if start==0 else 'forecast'),'runTime':iso(cycle),'fetchedAt':max(x['fetchedAt'] for x in frames),'generatedAt':iso(dt.datetime.now(UTC)),'width':1440,'height':721,'longitudeStart':-180,'latitudeStart':90,'stepDegrees':0.25,'missingValue':-32768,'textureWidth':1442,'textureHeight':723,'encoding':'RGB8: C=R*652.8+G*2.55-100; B=validity; linear; gutter=1','legendBreaksC':[-30,-20,-10,0,10,20,30,40],'frames':frames}
    (stage/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
    shutil.rmtree(temporary)
    target=out/package
    if target.exists(): shutil.rmtree(stage)
    else:stage.rename(target)
    # Only latest pointer changes; clients resolve immutable run once per transaction.
    pointer={'schema':1,'manifest':package+'/manifest.json','sha256':sha((target/'manifest.json').read_bytes())}
    (out/'latest.json.tmp').write_text(json.dumps(pointer),encoding='utf8');os.replace(out/'latest.json.tmp',out/'latest.json')
    prune(out,package,args.keep_hours)
    print('PUBLISHED '+str(target),flush=True)
    return target
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',required=True);p.add_argument('--wgrib2',default='wgrib2');p.add_argument('--cycle');p.add_argument('--start',type=int);p.add_argument('--hours',type=int,default=25);p.add_argument('--keep-hours',type=int,default=48);a=p.parse_args()
    if a.keep_hours<48:p.error('--keep-hours must be at least 48 to protect active clients')
    with output_lock(Path(a.output).resolve()):build(a)
