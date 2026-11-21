Invoke-RestMethod http://localhost:18080/api/transferencias/3 | Format-List
Invoke-RestMethod http://localhost:18080/api/cuentas/002187000000003081 | Format-Table clabe,saldo
Invoke-RestMethod http://localhost:18080/api/cuentas/002187000000003082 | Format-Table clabe,saldo