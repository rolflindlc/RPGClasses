# Iron Rituals

Addon de magia ritual para Iron's Spells 'n Spellbooks (Forge 1.20.1). Diseño completo en el
*Plan de Desarrollo v2* del proyecto.

> Rama temporal alojada en `rpgclasses` hasta que el mod tenga repositorio propio.

## Estado: Fases 1–3 completas

### Fase 1 (bloques base)

- **Velas Arcanas** (`arcane_candle` 3 usos, `arcane_candle_worn` 2, `arcane_candle_stub` 1): se encienden con
  mechero, carga ígnea o proyectiles en llamas; clic derecho con la mano vacía las apaga.
- **Núcleo del Ritual**: ranura de grimorio (clic con el libro / Shift + clic con la mano vacía para retirarlo),
  propietario y aliados, protección contra rotura. Al activarlo con el libro puesto hace un **diagnóstico**:
  nivel del círculo, velas que faltan (partículas rojas) o están apagadas (amarillas) y componentes del área central.
- **Daga Ritual**: captura el alma al matar de un golpe directo; no captura si ya tiene una ni de la
  `soul_blacklist`; Shift + clic derecho 3 s la vacía; modelo con capa roja emisiva cuando lleva alma.
- **Grimorios**: un ítem `ritual_tome` con el id del ritual en NBT (los 29 en la pestaña creativa).

### Fase 2 (motor de rituales)

- Rituales definidos por datapack: `data/<ns>/recipes/rituals/<id>.json` (tipo `ironrituals:ritual`) con escuela,
  círculo mínimo, rareza, niveles (componentes, maná, duración, `params`) y catalizadores (con grupos excluyentes).
  Componentes: `item`/`tag`/`ingredient` con `count`, o `soul` (daga con alma: `any`, `player`, `tamable`, `entities`).
- El núcleo elige el mayor nivel que permiten el círculo y los componentes; luego los catalizadores con lo sobrante.
- Canalización: 10 s + 5 s por nivel (configurable), barra de progreso, partículas, maná drenado poco a poco.
  Se interrumpe si el lanzador se aleja (> 8 bloques), muere o se desconecta, se apaga o rompe una vela, se queda
  sin maná, se rompe el núcleo o hay Supresión. Shift + clic derecho del lanzador la cancela. Al interrumpirse, los
  componentes vuelven al suelo y las velas no se gastan.
- Al completarse: velas −1 uso, componentes consumidos, la daga vuelve vacía, se aplica el efecto.
- `RitualSavedData` + `RitualTicker`: rituales persistentes con temporizador (solo avanza con el propietario
  conectado), pausa por Supresión, fin al destruir el núcleo y prórroga sin límite para los `extendable`.
- Escala por poder de hechizo de la escuela (ISS): duración × (1 + 0.5 × (poder − 1)), tope ×1.5.
- Efecto ya implementado para probar el motor: **Ritual del Diluvio**. El resto se canaliza a partir de la fase 4.

### Fase 3 (grimorios, datos y compatibilidad)

- GUI del grimorio (clic derecho): lore, pista, diagrama del círculo y componentes por nivel; los catalizadores solo
  aparecen como "notas al margen".
- Grimorios en cofres de estructuras de ISS (`GlobalLootModifier`), por rareza (común, poco común, rara).
- JEI (y EMI vía JEI): componentes base por nivel; los catalizadores se ocultan.
- Las 29 definiciones, lang es/en (lore y pistas) y recetas se generan con `tools/gen_assets.py`.

## Círculo

Velas en el mismo plano Y que el núcleo, en las casillas del perímetro del cuadrado de semilado r con ambas
coordenadas relativas pares: nivel 1 = r 2 (8 velas), nivel 2 = + r 4 (24), nivel 3 = + r 6 (48).

## Compilar

`./gradlew build` (Java 17). La CI de GitHub compila, pasa las pruebas y ejecuta `issProbe`, que lista ids
de ítems, entidades y tablas de botín del jar de ISS para verificar los datos del mod.

`python3 tools/gen_assets.py` regenera modelos, recetas, tags, lang y texturas provisionales.
