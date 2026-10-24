"""Export only the approved Earth mesh positions/UV for offline coordinate lookup.

No SDK code, textures, transforms or material data are copied or modified.
The runtime converts hits into the mesh entity's own frame before lookup.
"""
import argparse, ast, hashlib, json, re, struct
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--model', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
source = a.model.read_text(encoding='utf-8-sig')
def array(name):
    match = re.search(r'^\s*[^\n]*?\b' + re.escape(name) + r' = (\[[^\n]*\])', source, re.M)
    assert match, name
    return ast.literal_eval(match.group(1))
points, uv = array('points'), array('primvars:st')
indices, uv_indices, counts = array('faceVertexIndices'), array('primvars:st:indices'), array('faceVertexCounts')
assert all(n == 3 for n in counts)
assert len(indices) == len(uv_indices) == len(counts)*3
payload = bytearray(struct.pack('>ii', 0x47454F31, len(counts)))
for start in range(0,len(indices),3):
    values = [c for i in indices[start:start+3] for c in points[i]]
    values += [c for i in uv_indices[start:start+3] for c in uv[i]]
    payload.extend(struct.pack('>15f', *values))
a.output.parent.mkdir(parents=True,exist_ok=True)
a.output.write_bytes(payload)
print(json.dumps(dict(triangles=len(counts),bytes=len(payload),sha256=hashlib.sha256(payload).hexdigest()),indent=2))
