Write-Host 'EVIDENCIA GUARDADA: saldos antes y despues del fallo original'
$antes=Get-Content -Raw -Encoding UTF8 evidencias-reales/01-original-antes.json | ConvertFrom-Json; $antes | ForEach-Object { $_.response } | Format-Table clabe,saldo
$despues=Get-Content -Raw -Encoding UTF8 evidencias-reales/05-original-despues.json | ConvertFrom-Json; $despues | ForEach-Object { $_.response } | Format-Table clabe,saldo
Get-Content -Raw -Encoding UTF8 evidencias-reales/03-original-respuesta.json | ConvertFrom-Json | Format-List status,seconds,response