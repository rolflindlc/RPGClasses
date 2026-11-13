# RPG Classes (Forge 1.20.1)

Mod de clases, ramas, subclases y razas definido por datapack. Diseño funcional en
`documento_de_diseno_de_clases_v2.md` (rev. 2.11), razas y su estado en
`documento_de_razas_implementacion.md`, y arquitectura en el documento
"Arquitectura técnica del mod de clases".

## Estado actual (2026-11-13)

Además de lo descrito abajo, ya están en el código, sin probar en el juego:

- **Razas:** 8 razas × 3 subrazas con ritos, Altar de Raza y jefes de rito (`documento_de_razas_implementacion.md`).
- **Aliados:** sistema de aliados y enemigos (tecla J).
- **Libro de Vínculos** del Maestro de Bestias.
- **Interfaz:** retratos de clases, subclases, razas y subrazas, iconos de estados, y texturas propias de
  panel, cabecera y tarjetas (`python3 -I tools/art/make_ui.py src/main/resources`; el origen de los
  retratos está en `tools/art/`).
- Red: protocolo **10**; 14 mixins.

El resto de este apartado es el historial de las fases iniciales.

## Fase 4 en curso (historial)

Hecho:

- **Fase 1, núcleo:** progresión con XP, ramas, subclase, respec ilimitado (pierde el 33% de la XP),
  datos del jugador que sobreviven a la muerte, carga del datapack con `/reload`, red y comandos.
- **Fase 2, estadísticas y daño:**
  - Pasivas de atributo de ramas y subclases aplicadas en el juego (armadura, velocidad, vida, salto...).
  - Pasivas "sin armadura" de Bárbaro y Monje; Coraza del Paladín; topes globales de velocidad y salto.
  - Muros de Armadura, armas pesadas, ligeras, arco y Escudos (desgaste y cooldown al ser desactivado
    por un hacha); muro de Magia si Iron's Spells está instalado.
  - Críticos propios (30% base, Bárbaro 35%, Pícaro 25%) y Furia de Heridas.
  - `/clases debug item` y `/clases debug stats`.

- **Fase 3 (en curso), interfaz:**
  - Pantalla de selección al entrar sin clase, con cuatro pestañas por clase: resumen, ramas y
    habilidades, subclases y datos exactos. No se cierra hasta elegir (Esc abre el menú de pausa).
  - La tecla K abre la misma pantalla; en tu clase, la pestaña Progreso permite gastar puntos,
    elegir subclase y reiniciar con botones.
  - Los "datos exactos" se generan de los JSON del datapack, incluidas las habilidades
    (`abilities/*.json`), así que siguen cualquier cambio de balance tras `/reload`.

- **Fase 4 (en curso), habilidades:**
  - Framework: habilidades desbloqueadas calculadas del progreso, cooldowns guardados con el jugador,
    teclas Z / X / C (configurables) y lista con cooldown en la esquina inferior derecha.
  - Equipo inicial irrompible al elegir clase; se retira del inventario al reiniciarla.
  - Paladín: Provocación, Guardia Firme y Baluarte. Bárbaro: Sed de Sangre, Embestida y Furia Berserker.
  - Ranger: Conservación de Munición, Doble Salto, Disparo Pesado, zoom, velocidad de carga y de proyectil.
  - Monje: Posturas Elementales, Desvío de Proyectiles y Paso Ligero (sin daño por caída).

Aún sin efecto en el juego: habilidades de Mago y Pícaro, mecánicas de subclase, caminar sobre
agua y lava del Monje, ballestas en Conservación de Munición, integración con Iron's Spells y
Epic Fight y resistencia a debuffs.

Problema conocido: el muro de Escudos aumenta el desgaste, pero el cooldown alargado no se aplica.

Los textos y los JSON se generan con `python3 tools/gen_data.py src/main/resources`
(números de habilidades y textos en `tools/texts.py`).

## Compilar y probar

Requiere JDK 17 y conexión a internet la primera vez.

```
./gradlew test        # pruebas unitarias de la lógica pura
./gradlew runClient   # cliente de desarrollo
./gradlew runServer   # servidor dedicado de desarrollo
./gradlew build       # jar en build/libs/
```

## Probar la fase 1 en el juego

Al entrar sin clase se abre la pantalla de selección.

| Comando | Efecto |
| --- | --- |
| `/clase elegir <id>` | Elige clase, p. ej. `rpgclasses:barbarian` |
| `/clase punto <rama>` | Gasta un punto en una rama (cobra XP) |
| `/clase subclase <id>` | Elige subclase (desde 15 puntos de base) |
| `/clase subpunto` | Sube un nivel de subclase |
| `/clase reiniciar clase` / `subclase` | Respec ilimitado; cada uno pierde el 33% de la XP (configurable) |
| `/clase info` | Estado actual y coste del siguiente nivel |
| `/clases info <jugador>`, `/clases reset <jugador>`, `/clases validar` | Administración (nivel 2) |
| `/clases debug item` | Disciplina del objeto en la mano y por qué |
| `/clases debug stats` | Modificadores activos, muros, crítico y desglose del último golpe |

Para tener XP de prueba: `/xp add @s 9000 points`.

## Añadir una clase

1. `data/<namespace>/rpgclasses/classes/<id>.json` con 3 ramas de 10 puntos y 3 subclases.
2. Tres archivos en `data/<namespace>/rpgclasses/subclasses/`.
3. Nombres en `assets/<namespace>/lang/`.
4. Código Java solo si necesita una mecánica nueva.

`/clases validar` y la prueba `DefinitionFilesTest` comprueban la estructura.
