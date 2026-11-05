// Servidor local de evidências para o build padrão; nenhum arquivo de build é alterado.
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
const root=fileURLToPath(new URL('../frontend/dist/frontend/browser/',import.meta.url));
const api=new URL(process.env.AC3_API_URL ?? 'http://127.0.0.1:18239/api');
if(!['127.0.0.1','localhost'].includes(api.hostname)) throw new Error('Use API local descartável.');
const types={'.html':'text/html','.js':'text/javascript','.css':'text/css','.ico':'image/x-icon'};
http.createServer((req,res)=>{
 if(req.url.startsWith('/api/')) {
  const proxy=http.request({host:api.hostname,port:api.port,path:req.url,method:req.method,headers:req.headers},up=>{res.writeHead(up.statusCode,up.headers);up.pipe(res)});
  proxy.on('error',()=>{res.writeHead(502);res.end()});req.pipe(proxy);return;
 }
 let pathname;
 try {pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname)} catch {res.writeHead(400);res.end();return}
 const file=path.resolve(root,'.'+pathname);
 if(file!==path.resolve(root)&&!file.startsWith(root)){res.writeHead(403);res.end();return}
 const target=fs.existsSync(file)&&fs.statSync(file).isFile()?file:path.join(root,'index.html');
 if(!fs.existsSync(target)){res.writeHead(503);res.end('Execute npm run build.');return}
 res.writeHead(200,{'Content-Type':types[path.extname(target)]??'application/octet-stream'});fs.createReadStream(target).pipe(res);
}).listen(Number(process.env.AC3_WEB_PORT ?? 4239),'127.0.0.1');
