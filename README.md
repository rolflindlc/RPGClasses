# RPG Classes (Forge 1.20.1)

Mod de clases, ramas, subclases y razas definido por datapack. Diseño funcional en
`documento_de_diseno_de_clases_v2.md`, razas y su estado en
`documento_de_razas_implementacion.md`, y arquitectura en el documento
"Arquitectura técnica del mod de clases".

## Estado actual (2026-11-16)

Todo lo siguiente está en el código y compila con sus pruebas, pero **no se ha probado en el juego** salvo la fase 1.

- **Clases:** 6 clases con 5 ramas (3 usables) y perks, 18 subclases, habilidades, muros de disciplina, críticos
  propios y topes (diseño v2, rev. 2.21). Integración con Iron's Spells y Epic Fight (opcionales).
- **Equipo de Clase (rev. 2.20):** armaduras de Bárbaro y Monje de todos los tiers (hasta dragonsteel de Ice and Fire),
  armas marciales del Monje, efectos de set y 32 curios (Curios opcional). Texturas provisionales
  (`python3 -I tools/art/make_gear.py src/main/resources`). Detalle en `documento_equipo_de_clase.md`.
- **Razas:** 12 razas, **39 subrazas** y 117 ritos, con Altar de Raza y jefes de rito; incluye las 4 razas de la
  adenda 0.7 (Slime, Coloso, Dracónido y Centauro). Estado y pruebas en `documento_de_razas_implementacion.md`.
- **Aliados** (tecla J), **Libro de Vínculos** del Maestro de Bestias, retratos e interfaz propia
  (`python3 -I tools/art/make_ui.py src/main/resources`).
- **HUD:** habilidades de clase y raciales con su tecla y enfriamiento (abajo a la derecha), conjuros gratuitos del
  Mago, barras de sigilo, salto cargado, adherencia y fusión.
- **Configuración:** de servidor (`rpgclasses-server.toml`: coste de XP, respec, aliados, depuración) y de cliente
  (`rpgclasses-client.toml`: intensidad del temblor del Titán).
- Red: protocolo **14** (servidor y cliente con el mismo jar). 17 mixins (2 solo de cliente).
- Depuración: `/clases debug volcar` escribe un informe en `logs/`; `logs/rpgclasses-debug.log` lleva el registro.

### Pendiente

- Probar en el juego y calibrar los valores marcados **[MEDIR]** en los documentos de diseño.
- Ids que hay que leer en el pack: conjuros de agua de T.O Magic 'n Extras (Moldeador), efecto Mojado y dragones de
  Ice and Fire.
- Enfriamiento del escudo por el muro de Escudos (aparcado): hoy solo aumenta el desgaste.
- Animaciones propias de nunchaku y bo; curios por subclase y por subraza (sin diseñar).
- Integrar los anexos (razas, adenda 0.7, equipo) en el documento de diseño v2. Publicación en CurseForge.

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
