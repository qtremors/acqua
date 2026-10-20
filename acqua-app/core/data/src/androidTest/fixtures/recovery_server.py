from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import base64
import time

body = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aXioAAAAASUVORK5CYII=') + bytes(8 * 1024 * 1024)

class Fixture(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != '/recovery.png':
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header('Content-Type', 'image/png')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        try:
            for offset in range(0, len(body), 32768):
                self.wfile.write(body[offset:offset + 32768])
                self.wfile.flush()
                time.sleep(0.08)
        except ConnectionError:
            pass

ThreadingHTTPServer(('127.0.0.1', 18734), Fixture).serve_forever()
