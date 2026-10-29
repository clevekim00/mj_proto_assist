#!/usr/bin/env python3
"""Account-free integration test: real loopback API and synthetic target server."""
import base64
import http.server
import json
import pathlib
import re
import subprocess
import threading
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]

class Echo(http.server.BaseHTTPRequestHandler):
    calls = 0
    def log_message(self, *args):
        pass
    def do_POST(self):
        Echo.calls += 1
        body = self.rfile.read(int(self.headers['Content-Length']))
        if self.path == '/html':
            self.send_response(500); self.send_header('Content-Type', 'text/html'); self.end_headers(); self.wfile.write(b'<h1>synthetic</h1>')
        elif self.path == '/empty':
            self.send_response(204); self.end_headers()
        else:
            self.send_response(400 if self.path == '/error' else 200)
            self.send_header('Content-Type', 'application/x-protobuf'); self.end_headers(); self.wfile.write(body)


def main():
    target = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Echo)
    threading.Thread(target=target.serve_forever, daemon=True).start()
    process = subprocess.Popen([str(ROOT/'local-api/build/install/local-api/bin/local-api')], cwd=ROOT,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    try:
        for line in process.stdout:
            match = re.search(r'Open (http://127\.0\.0\.1:\d+)/#bootstrap=([\w-]+)', line)
            if match:
                origin, bootstrap = match.groups(); break
        else:
            raise AssertionError('server did not start')
        session = ''
        def call(path, data, foreign=False):
            req = urllib.request.Request(origin+'/local/v1/'+path, json.dumps(data).encode(),
                {'Content-Type':'application/json','Origin':'https://foreign.invalid' if foreign else origin,'X-Local-Session':session})
            with urllib.request.urlopen(req, timeout=15) as response:
                return json.load(response)
        with urllib.request.urlopen(origin) as response:
            assert b'mj_proto_assist' in response.read()
            assert "frame-ancestors 'none'" in response.headers['Content-Security-Policy']
        session = call('session', {'bootstrap':bootstrap})['session']
        try:
            call('session', {'bootstrap':bootstrap}); raise AssertionError('bootstrap reused')
        except urllib.error.HTTPError as e:
            assert e.code == 400
        descriptor = base64.b64encode((ROOT/'examples/echo.pb').read_bytes()).decode()
        schema = call('schema/register', {'descriptorBase64':descriptor})['schemaId']
        params = {'schemaId':schema,'messageType':'sample.v1.Echo'}
        encoded = call('codec/encode', {**params,'protoJson':{'text':'synthetic','revision':'9007199254740993'}})
        decoded = call('codec/decode', {**params,'payloadBase64':encoded['payloadBase64']})
        assert decoded['protoJson']['revision'] == '9007199254740993'
        for route, status in [('/echo',200),('/error',400),('/html',500),('/empty',204)]:
            result = call('http/execute', {'schemaId':schema,'url':f'http://127.0.0.1:{target.server_port}{route}',
                'allowHttp':True,'payloadBase64':encoded['payloadBase64'], 'responseRules':{'2xx':'sample.v1.Echo','4xx':'sample.v1.Echo','5xx':'sample.v1.Echo'}})
            assert result['http']['status'] == status
            if route in ('/echo','/error'):
                assert result['protoJson']['revision'] == '9007199254740993'
                assert result['http']['payloadBase64'] == encoded['payloadBase64']
            else:
                assert result['protoJson'] is None
        assert Echo.calls == 4
        try:
            call('codec/encode', {**params,'protoJson':{}}, foreign=True); raise AssertionError('foreign origin accepted')
        except urllib.error.HTTPError as e:
            assert e.code == 403
        secret='synthetic-secret-marker'
        try:
            call('codec/encode', {**params,'protoJson':{'unknown':secret}}); raise AssertionError('unknown field accepted')
        except urllib.error.HTTPError as e:
            assert secret not in e.read().decode()
        print('PASS: UI assets, one-use bootstrap, schema, int64 codec, 4 HTTP response paths, origin guard, sanitized errors')
    finally:
        process.terminate()
        try: process.wait(timeout=10)
        except subprocess.TimeoutExpired: process.kill(); process.wait()
        target.shutdown(); target.server_close()

if __name__ == '__main__': main()
