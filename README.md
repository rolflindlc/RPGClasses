# RPG Classes (Forge 1.20.1)

Mod de clases, ramas y subclases definido por datapack. Diseño funcional en
`documento_de_diseno_de_clases_v2.md` (rev. 2.6) y arquitectura en el documento
"Arquitectura técnica del mod de clases".

## Estado: fase 1 (núcleo)

Hecho:

- `core/`: lógica pura sin dependencias de Minecraft (costes de XP, reglas de progresión,
  tope de subclase, respec, muros, críticos, topes, `LevelValue`, lectura y validación de JSON).
- Datos: las 6 clases y las 18 subclases en `src/main/resources/data/rpgclasses/rpgclasses/`.
- Forge: capability del jugador con copia al morir, carga del datapack con `/reload`,
  sincronización al cliente, configuración de servidor y comandos.
- Pruebas unitarias de `core/` y de los JSON reales (`src/test`).

Pendiente (siguientes fases): StatEngine y muros en juego, DamagePipeline, pantalla de selección
y ficha de datos exactos, HUD, habilidades, kits iniciales e integración con Iron's Spells y Epic Fight.
Hasta entonces las pasivas están definidas en los JSON pero **no tienen efecto en el juego**.

## Compilar y probar

Requiere JDK 17 y conexión a internet la primera vez.

```
./gradlew test        # pruebas unitarias de la lógica pura
./gradlew runClient   # cliente de desarrollo
./gradlew runServer   # servidor dedicado de desarrollo
./gradlew build       # jar en build/libs/
```

## Probar la fase 1 en el juego

Al entrar sin clase aparece en el chat la lista de clases (provisional hasta la fase 3); haz clic en una.

| Comando | Efecto |
| --- | --- |
| `/clase elegir <id>` | Elige clase, p. ej. `rpgclasses:barbarian` |
| `/clase punto <rama>` | Gasta un punto en una rama (cobra XP) |
| `/clase subclase <id>` | Elige subclase (desde 15 puntos de base) |
| `/clase subpunto` | Sube un nivel de subclase |
| `/clase reiniciar clase` / `subclase` | Respec ilimitado; cada uno pierde el 33% de la XP (configurable) |
| `/clase info` | Estado actual y coste del siguiente nivel |
| `/clases info <jugador>`, `/clases reset <jugador>`, `/clases validar` | Administración (nivel 2) |

Para tener XP de prueba: `/xp add @s 9000 points`.

## Añadir una clase

1. `data/<namespace>/rpgclasses/classes/<id>.json` con 3 ramas de 10 puntos y 3 subclases.
2. Tres archivos en `data/<namespace>/rpgclasses/subclasses/`.
3. Nombres en `assets/<namespace>/lang/`.
4. Código Java solo si necesita una mecánica nueva.

`/clases validar` y la prueba `DefinitionFilesTest` comprueban la estructura.
