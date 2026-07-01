import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const htmlPath = join(__dirname, 'jiten-sasayaki-dom-harness.html');
const port = Number(process.env.PORT || 8787);

function send(res, status, headers, body) {
  res.writeHead(status, headers);
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', chunk => chunks.push(chunk));
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

createServer(async (req, res) => {
  try {
    const url = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
    if (req.method === 'GET' && (url.pathname === '/' || url.pathname === '/harness')) {
      const html = await readFile(htmlPath, 'utf8');
      send(res, 200, { 'Content-Type': 'text/html; charset=utf-8' }, html);
      return;
    }

    if (req.method === 'POST' && url.pathname === '/jiten/parse') {
      const apiKey = req.headers['x-jiten-api-key'];
      const endpoint = String(req.headers['x-jiten-endpoint'] || 'https://api.jiten.moe/api').replace(/\/$/, '');
      if (!apiKey) {
        send(res, 400, { 'Content-Type': 'application/json' }, JSON.stringify({ error: 'Missing x-jiten-api-key header' }));
        return;
      }
      const body = await readBody(req);
      const upstream = await fetch(`${endpoint}/reader/parse`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
          'Authorization': `ApiKey ${apiKey}`,
        },
        body,
      });
      const responseBody = Buffer.from(await upstream.arrayBuffer());
      send(res, upstream.status, {
        'Content-Type': upstream.headers.get('content-type') || 'application/json',
        'Access-Control-Allow-Origin': '*',
      }, responseBody);
      return;
    }

    send(res, 404, { 'Content-Type': 'text/plain; charset=utf-8' }, 'Not found');
  } catch (error) {
    send(res, 500, { 'Content-Type': 'text/plain; charset=utf-8' }, error?.stack || String(error));
  }
}).listen(port, '127.0.0.1', () => {
  console.log(`Jiten/Sasayaki harness: http://127.0.0.1:${port}/`);
});
