# RPG Classes (Forge 1.20.1)

Mod de clases, ramas y subclases definido por datapack. Diseño funcional en
`documento_de_diseno_de_clases_v2.md` (rev. 2.6) y arquitectura en el documento
"Arquitectura técnica del mod de clases".

## Estado: fase 2 (estadísticas y daño)

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

Aún sin efecto en el juego: habilidades activas, mecánicas de subclase, guardia de Epic Fight, zoom,
velocidad de carga y de proyectil, resistencia a debuffs, kits iniciales y la pantalla de selección.

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
| `/clases debug item` | Disciplina del objeto en la mano y por qué |
| `/clases debug stats` | Modificadores activos, muros, crítico y desglose del último golpe |

Para tener XP de prueba: `/xp add @s 9000 points`.

## Añadir una clase

1. `data/<namespace>/rpgclasses/classes/<id>.json` con 3 ramas de 10 puntos y 3 subclases.
2. Tres archivos en `data/<namespace>/rpgclasses/subclasses/`.
3. Nombres en `assets/<namespace>/lang/`.
4. Código Java solo si necesita una mecánica nueva.

`/clases validar` y la prueba `DefinitionFilesTest` comprueban la estructura.
