# Iron Rituals

Addon de magia ritual para Iron's Spells 'n Spellbooks (Forge 1.20.1). Diseño completo en el
*Plan de Desarrollo v2* del proyecto.

> Rama temporal alojada en `rpgclasses` hasta que el mod tenga repositorio propio.

## Estado: Fase 1 (bloques base)

- **Velas Arcanas** (`arcane_candle` 3 usos, `arcane_candle_worn` 2, `arcane_candle_stub` 1): se encienden con
  mechero, carga ígnea o proyectiles en llamas; clic derecho con la mano vacía las apaga.
- **Núcleo del Ritual**: ranura de grimorio (clic con el libro / Shift + clic con la mano vacía para retirarlo),
  propietario y aliados, protección contra rotura. Al activarlo con el libro puesto hace un **diagnóstico**:
  nivel del círculo, velas que faltan (partículas rojas) o están apagadas (amarillas) y componentes del área central.
- **Daga Ritual**: captura el alma al matar de un golpe directo; no captura si ya tiene una ni de la
  `soul_blacklist`; Shift + clic derecho 3 s la vacía; modelo con capa roja emisiva cuando lleva alma.
- **Grimorios**: un ítem `ritual_tome` con el id del ritual en NBT (los 29 en la pestaña creativa).

## Círculo

Velas en el mismo plano Y que el núcleo, en las casillas del perímetro del cuadrado de semilado r con ambas
coordenadas relativas pares: nivel 1 = r 2 (8 velas), nivel 2 = + r 4 (24), nivel 3 = + r 6 (48).

## Compilar

`./gradlew build` (Java 17). La CI de GitHub compila, pasa las pruebas y ejecuta `issProbe`, que lista ids
de ítems, entidades y tablas de botín del jar de ISS para verificar los datos del mod.

`python3 tools/gen_assets.py` regenera modelos, recetas, tags, lang y texturas provisionales.
