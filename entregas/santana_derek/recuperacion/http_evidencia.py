import json
import time
from datetime import datetime
from zoneinfo import ZoneInfo
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

def ahora(): return datetime.now(ZoneInfo('America/Mexico_City')).isoformat()
def pedir(url,method='GET',data=None,timeout=45,headers=None):
    head={'Content-Type':'application/json','Accept':'application/json',**(headers or {})}
    request=Request(url,method=method,headers=head,data=None if data is None else json.dumps(data).encode())
    started=time.monotonic()
    try:
        response=urlopen(request,timeout=timeout)
    except HTTPError as e: response=e
    except (URLError,TimeoutError,OSError) as e:
        return {'hora':ahora(),'codigo':0,'error':str(e),'segundos':round(time.monotonic()-started,3)}
    with response:
        raw=response.read().decode()
        try:body=json.loads(raw)
        except ValueError:body=raw
        return {'hora':ahora(),'codigo':response.status,'cabeceras':dict(response.headers),
                'cuerpo':body,'segundos':round(time.monotonic()-started,3)}

def guardar(path,data):
    path.write_text(json.dumps(data,indent=2,ensure_ascii=False))
    print(path.name,flush=True)
