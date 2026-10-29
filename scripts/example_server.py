#!/usr/bin/env python3
"""Synthetic loopback echo target. Never use service credentials with this server."""
import http.server

class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self,*args):
        pass
    def do_POST(self):
        length=int(self.headers.get('Content-Length','0'))
        if length>10*1024*1024:
            self.send_error(413);return
        body=self.rfile.read(length)
        self.send_response(200)
        self.send_header('Content-Type','application/x-protobuf')
        self.send_header('Content-Length',str(len(body)))
        self.end_headers();self.wfile.write(body)

if __name__=='__main__':
    server=http.server.ThreadingHTTPServer(('127.0.0.1',8787),Handler)
    print('Synthetic POST echo: http://127.0.0.1:8787/echo',flush=True)
    try:server.serve_forever()
    except KeyboardInterrupt:pass
    finally:server.server_close()
