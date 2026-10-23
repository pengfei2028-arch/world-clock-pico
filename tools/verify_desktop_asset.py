"""Verify the exported Shared asset contract before installing an APK.

Checks the compiled bundle, not just the editable graph. Optionally compares all
original APK assets byte for byte, to protect the approved Full presentation.
"""
import argparse
import hashlib
import json
import math
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--bundle', type=Path, required=True)
p.add_argument('--apk', type=Path)
p.add_argument('--baseline-apk', type=Path)
a = p.parse_args()
blob = a.bundle.read_bytes()
with zipfile.ZipFile(a.bundle) as z:
    names = z.namelist()
    parameters = json.loads(z.read(next(n for n in names if 'shader_origin_parm_info' in n)))
    assert set(parameters['samplerParameters']) == {'Day_file', 'Night_file', 'Detail_file'}
    uniforms = parameters['uniformValues']
    assert {'HalfSum_in2', 'CloudDensity_in2', 'SunDot_in2', 'NoiseTimePosition_in2',
            'DayWeight_Remap_inlow', 'DayWeight_Remap_inhigh'} <= set(uniforms)
    graph_bytes = z.read(next(n for n in names if 'shader_origin_mtlx' in n))
    assert b'ND__viewdirection_vector3' in graph_bytes
    graph = ET.fromstring(graph_bytes)
    nodes = {e.get('name'): e for e in graph.iter() if e.get('nodedef')}
    assert not any(e.tag == 'sqrt' for e in nodes.values()), 'Old horizon cusp remains'
    # Evaluate the exported scalar graph, so this checks the delivered feather
    # and night gating rather than a separate hand-written copy of the formula.
    def scalar(name, sun_dot):
        if name == 'SunDot': return sun_dot
        e = nodes[name]
        values = {i.get('name'): scalar(i.get('nodename'), sun_dot) if i.get('nodename')
                  else float(i.get('value')) for i in e.findall('input')}
        if e.tag == 'multiply': return values['in1'] * values['in2']
        if e.tag == 'subtract': return values['in1'] - values['in2']
        if e.tag == 'add': return values['in1'] + values['in2']
        if e.tag == 'clamp': return max(values['low'], min(values['high'], values['in']))
        if e.tag == 'remap':
            return values['outlow'] + (values['in']-values['inlow'])/(values['inhigh']-values['inlow'])*(values['outhigh']-values['outlow'])
        raise AssertionError('Unexpected scalar node '+e.tag)
    def surface_coefficient(dot):
        weight = scalar('DayWeight',dot)
        return .005*(1-weight) + scalar('Shade',dot)*weight
    samples = [surface_coefficient(-1+i/5000) for i in range(10001)]
    assert all(b >= a-1e-6 for a,b in zip(samples,samples[1:])), 'Surface shade must be monotonic'
    for dot in [math.sin(math.radians(-18)), 0, math.sin(math.radians(18))]:
        h=1e-5
        left=(surface_coefficient(dot)-surface_coefficient(dot-h))/h
        right=(surface_coefficient(dot+h)-surface_coefficient(dot))/h
        assert abs(left-right)<.001, 'Discontinuous twilight slope'
    assert all(scalar('NightMask',i/100)==0 for i in range(101)), 'Lights must stay off above horizon'
    assert nodes['NightNoise'].find("input[@name='position']").get('nodename') == 'NoiseTimePosition'
    assert nodes['NightVisible'].find("input[@name='in2']").get('nodename') == 'NightMask'
    assert uniforms['opacity']['mxValue']['Value'] == 1
    assert uniforms['applyPostProcessToneMap']['mxValue']['Value'] is False
    assert any(b'DesktopEarthV62' in z.read(n) for n in names if n.endswith('.scene.data'))
    textures = [z.getinfo(n).file_size for n in names if n.endswith('.texture.data')]
    # RGBA8 with mips and small format headers: one 3K + two 2K textures.
    assert len(textures) == 3
    assert sorted(size // 1_000_000 for size in textures) == [11, 11, 25]
    result = dict(bundleSha256=hashlib.sha256(blob).hexdigest(),
                  textureCount=3, compiledTextureBytes=sum(textures),
                  twilightContinuous=True, nightNoiseConnected=True,
                  note='Compiled payload size, not a measured GPU allocation or power reading')
if a.apk:
    with zipfile.ZipFile(a.apk) as apk:
        assert apk.read('assets/DesktopEarth.bundle') == blob, 'APK contains a stale desktop bundle'
        if a.baseline_apk:
            with zipfile.ZipFile(a.baseline_apk) as baseline:
                originals = [n for n in baseline.namelist() if n.startswith('assets/')]
                changed = [n for n in originals if n not in apk.namelist() or baseline.read(n) != apk.read(n)]
                assert not changed, f'Approved Full assets changed: {changed}'
                result['originalAssetsPreserved'] = len(originals)
print(json.dumps(result, indent=2))
