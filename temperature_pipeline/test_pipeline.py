import argparse,gzip,json,struct,tempfile,unittest
from pathlib import Path
from unittest.mock import patch
import numpy as np
import build_gfs as g
from PIL import Image
import io
class PipelineTest(unittest.TestCase):
    def test_numeric_encoding_missing_and_seams(self):
        q=np.zeros((721,1440),dtype='<i2');q[0,0]=-5500;q[-1,-1]=4500;q[12,33]=-32768
        binary,png=g.encode(q);raw=gzip.decompress(binary)
        self.assertEqual(raw[:4],b'WCT1');self.assertEqual(struct.unpack('<II',raw[4:12]),(1440,721))
        np.testing.assert_array_equal(np.frombuffer(raw[12:],dtype='<i2').reshape(q.shape),q)
        image=np.array(Image.open(io.BytesIO(png)));self.assertEqual(image[13,34,2],0)
        np.testing.assert_array_equal(image[:,0],image[:,-2]);np.testing.assert_array_equal(image[:,-1],image[:,1])
        np.testing.assert_array_equal(image[0],image[1]);np.testing.assert_array_equal(image[-1],image[-2])
    def test_failure_does_not_publish(self):
        with tempfile.TemporaryDirectory() as tmp:
            out=Path(tmp);old=b'{"manifest":"previous/manifest.json"}';(out/'latest.json').write_bytes(old)
            args=argparse.Namespace(output=tmp,wgrib2='unused',cycle='2026102200',start=8,hours=0,keep_hours=48)
            with g.output_lock(out),patch.object(g,'fetch',side_effect=OSError('offline')):
                with self.assertRaises(OSError):g.build(args)
            self.assertEqual((out/'latest.json').read_bytes(),old)
    def test_cycle_is_selected_from_published_inventory(self):
        now=g.dt.datetime(2026,10,22,8,tzinfo=g.UTC)
        class Response:
            def __enter__(self):return self
            def __exit__(self,*a):pass
            def read(self,*a):return b'1:0:d=2026102200:TMP:2 m above ground:33 hour fcst:'
        # Latest 06Z is incomplete, previous 00Z is actually published.
        with patch.object(g.urllib.request,'urlopen',side_effect=[OSError('404'),Response()]) as request:
            self.assertEqual(g.choose_cycle(now,25).hour,0);self.assertEqual(request.call_count,2)
if __name__=='__main__':unittest.main()
