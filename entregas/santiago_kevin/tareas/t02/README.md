# HotStuff

El protocolo tiene un mecanismo de consenso de tres fases: _prepare_, _pre-commit_ y _commit_. Esto permite simplificar el reemplazo del líder, manteniendo una comunicación lineal y una respuesta optimista. Un punto importante es que no requiere autenticadores adicionales para reemplazar al líder.

En este protocolo, las transiciones entre líderes son fluidas, ya que cuenta con el predicado `safeNode`, que permite a las réplicas votar por nuevas propuestas si estas extienden el estado bloqueado actual o provienen de un número de vista superior.

## Fase _prepare_

El líder recopila mensajes, identifica el certificado de quórum de la vista más alta y propone nuevos comandos. Las réplicas validan la propuesta mediante el predicado y envían sus votos.

## Fase _pre-commit_

El líder agrega estos votos en un `prepareQC` y lo difunde. Las réplicas responden con votos _pre-commit_.

## Fase _commit_

Las réplicas actualizan su `lockedQC` y envían votos _commit_, que el líder agrega en un `commitQC` para enviar el mensaje final _decide_.

Con esta estructura se pueden procesar múltiples propuestas a la vez, ya que las fases _pre-commit_ y _commit_ también corresponden a propuestas anteriores.

En cuanto a la seguridad, un comando solo se confirma cuando se presentan tres certificados de quórum (QC) consecutivos, donde cada uno sigue directamente al anterior. El predicado impide que las réplicas voten por ramas conflictivas.

La implementación es más sencilla, ya que el código puede reducirse a unas 200 líneas de C++, lo que disminuye la posibilidad de errores. Su uso en proyectos de blockchain, como Diem de Facebook, demuestra su capacidad de escalamiento.

## Tendermint

Tendermint es un protocolo de consenso y ayuda a que los participantes de una red acuerden qué bloques se añaden y en qué orden. Una vez que un bloque queda confirmado, alcanza la finalidad: los validadores no pueden confirmar otro bloque distinto en esa misma posición de la cadena.

Para decidir el siguiente bloque, Tendermint trabaja por rondas. En cada una, un validador actúa como **proponente** y presenta un bloque candidato. Los demás validadores lo revisan y emiten un primer voto, llamado _prevote_. Si los _prevotes_ a favor de ese bloque reúnen más de dos tercios del poder de voto, los validadores pueden pasar a votar por él en la fase _precommit_.

El bloque queda confirmado cuando recibe votos _precommit_ que representan más de dos tercios del poder de voto en la misma ronda. Así, la decisión depende del **poder de voto** de los validadores y no solo de cuántos validadores votaron.

Tendermint también utiliza la interfaz **ABCI** para comunicarse con la aplicación. El motor de consenso se encarga de acordar el orden de los bloques, mientras que la aplicación procesa las transacciones y actualiza su estado.

# Bibliografía

- Yin, M., Malkhi, D., Reiter, M. K., Gueta, G. G., & Abraham, I. (2019). HotStuff: BFT Consensus in the Lens of Blockchain. alphaXiv. [https://www.alphaxiv.org/es/abs/1803.05069](https://www.alphaxiv.org/es/abs/1803.05069)

- Gate. (s. f.). ¿Qué es Tendermint? Motor de consenso BFT explicado. [https://www.gate.com/es/learn/glossary/tendermint](https://www.gate.com/es/learn/glossary/tendermint)
