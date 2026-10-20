"""Crea una vista legible de archivos originales para capturar en el navegador.
No dibuja terminales ni inventa respuestas. Conserva vínculos a los registros.
"""
import html
import sys
from pathlib import Path

folder=Path(sys.argv[1]).resolve()
name=sys.argv[2]
files=sys.argv[3:]
parts=['<!doctype html><html lang="es"><meta charset="utf-8">',
       '<style>body{font:16px system-ui;margin:30px;max-width:1100px;color:#153047;background:#f4f7fa}',
       'h1{font-size:26px}article{background:white;border:1px solid #cbd5df;padding:18px;margin:18px 0}',
       'pre{font:13px/1.45 monospace;white-space:pre-wrap;overflow-wrap:anywhere}small{color:#50667b}</style>',
       '<h1>'+html.escape(name)+'</h1>',
       '<small>Vista de registros originales de la ejecución asistida del 19 y 20 de octubre de 2026. No es una captura de Terminal.</small>']
for rel in files:
    path=folder/rel
    content=path.read_text(errors='replace')
    # Quita únicamente códigos ANSI de color; conserva el archivo original intacto.
    import re
    content=re.sub(r'\x1b\[[0-?]*[ -/]*[@-~]','',content)
    parts.append('<article><h2><a href="'+html.escape(rel)+'">'+html.escape(rel)+'</a></h2><pre>'+html.escape(content)+'</pre></article>')
parts.append('</html>')
(folder/(name.split(' · ')[0].lower().replace(' ','-')+'.html')).write_text(''.join(parts))
