"""Read-only static forecast endpoint; put an HTTPS reverse proxy in front in production."""
import argparse,re
from functools import partial
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
from urllib.parse import urlsplit
class ForecastHandler(SimpleHTTPRequestHandler):
    def do_GET(self):
        path=urlsplit(self.path).path
        allowed=path=='/latest.json' or re.fullmatch(r'/[0-9]{8}T[0-9]{2}Z-f[0-9]{3}-[0-9]{3}-[a-f0-9]{12}/(manifest\.json|f[0-9]{3}\.(png|wctz))',path)
        if not allowed:self.send_error(404);return
        super().do_GET()
    def do_HEAD(self):
        self.send_error(405)
    def end_headers(self):
        self.send_header('Cache-Control','no-cache' if urlsplit(self.path).path=='/latest.json' else 'public, max-age=31536000, immutable')
        super().end_headers()
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--root',required=True);p.add_argument('--host',default='127.0.0.1');p.add_argument('--port',type=int,default=8765);a=p.parse_args()
    server=ThreadingHTTPServer((a.host,a.port),partial(ForecastHandler,directory=a.root));print('Serving verified forecast packages at http://'+a.host+':'+str(a.port),flush=True);server.serve_forever()
