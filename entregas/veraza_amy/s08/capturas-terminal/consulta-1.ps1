Invoke-RestMethod http://localhost:18080/api/transferencias/2 | Format-List
Invoke-RestMethod http://localhost:18080/api/cuentas/002187000000004081 | Format-Table clabe,saldo
Invoke-RestMethod http://localhost:18080/api/cuentas/002187000000004082 | Format-Table clabe,saldo