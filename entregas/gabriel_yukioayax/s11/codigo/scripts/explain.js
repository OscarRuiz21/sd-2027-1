// Resume el explain("executionStats") de la consulta de ultimos(): las etapas del plan y cuanto examino
function etapas(p) { let s = []; while (p) { s.push(p.stage + (p.indexName ? '(' + p.indexName + ')' : '')); p = p.inputStage; } return s.join(' <- '); }
function resumen(titulo, e) {
  print(titulo);
  print('  plan                : ' + etapas(e.queryPlanner.winningPlan));
  print('  nReturned           : ' + e.executionStats.nReturned);
  print('  totalKeysExamined   : ' + e.executionStats.totalKeysExamined);
  print('  totalDocsExamined   : ' + e.executionStats.totalDocsExamined);
  print('  executionTimeMillis : ' + e.executionStats.executionTimeMillis);
}
const q = () => db.avisos.find({clabe: '002180000000000002'}).sort({fecha: -1}).limit(10);
print('documentos en avisos: ' + db.avisos.countDocuments());
resumen('CON indice:', q().explain('executionStats'));
resumen('SIN indice (.hint({$natural: 1})):', q().hint({$natural: 1}).explain('executionStats'));
