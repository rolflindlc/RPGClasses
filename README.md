# Rolflin's Rites & Paths (Forge 1.20.1) — modId `rites_and_paths`

Mod de clases, ramas, subclases y razas definido por datapack. Diseño funcional en
`documento_de_diseno_de_clases_v2.md`, razas y su estado en
`documento_de_razas_implementacion.md`, y arquitectura en el documento
"Arquitectura técnica del mod de clases".

## Estado actual (2026-11-17)

Todo lo siguiente está en el código y compila con sus pruebas, pero **no se ha probado en el juego** salvo la fase 1.

- **Clases:** 7 clases con 5 ramas (3 usables) y perks, 21 subclases, habilidades, muros de disciplina, críticos
  propios y topes (diseño v2, rev. 2.21). Integración con Iron's Spells y Epic Fight (opcionales).
- **Equipo de Clase (rev. 2.20):** armaduras de Bárbaro y Monje de todos los tiers (hasta dragonsteel de Ice and Fire),
  armas marciales del Monje, efectos de set y nudilleras en la ranura de manos de Curios (opcional); los 32 curios están **desactivados** en la versión de salida (`Curio.ENABLED` y `CURIOS_ENABLED` en `tools/gear.py`). Texturas provisionales
  (`python3 -I tools/art/make_gear.py src/main/resources`). Detalle en `documento_equipo_de_clase.md`.
- **Razas:** 12 razas, **39 subrazas** y 117 ritos, con Altar de Raza y jefes de rito; incluye las 4 razas de la
  adenda 0.7 (Slime, Coloso, Dracónido y Centauro). Estado y pruebas en `documento_de_razas_implementacion.md`.
- **Aliados** (tecla J), **Libro de Vínculos** del Maestro de Bestias, retratos e interfaz propia
  (`python3 -I tools/art/make_ui.py src/main/resources`).
- **HUD:** habilidades de clase y raciales con su tecla y enfriamiento (abajo a la derecha), conjuros gratuitos del
  Mago, barras de sigilo, salto cargado, adherencia y fusión.
- **Configuración:** de servidor (`rites_and_paths-server.toml`: coste de XP, respec, aliados, depuración) y de cliente
  (`rites_and_paths-client.toml`: intensidad del temblor del Titán).
- **Cronomante** (sustituye al Evocador Caótico): Retroceso Temporal y Parada Temporal (esfera gris translúcida,
  compatible con shaders). Pruebas en el juego: ver "Probar el Cronomante" más abajo.
- **Bardo** (adenda del Bardo): minijuego de ritmo con los cuatro carriles (clic izquierdo, clic derecho, Shift y
  espacio), combo, Armonía, Contrapunto y Crescendo; escuela musical registrada en Iron's (`rites_and_paths:music`) con 12
  conjuros, 21 instrumentos más la guitarra-hacha, partituras con nivel y atril, 3 subclases. Detalle y diferencias con
  el diseño en `documento_bardo.md`. Pruebas: ver "Probar el Bardo" más abajo.
- Red: protocolo **16** (servidor y cliente con el mismo jar). 18 mixins (3 solo de cliente).
- Depuración: `/clases debug volcar` escribe un informe en `logs/`; `logs/rites_and_paths-debug.log` lleva el registro.

### Pendiente

- Probar en el juego y calibrar los valores marcados **[MEDIR]** en los documentos de diseño.
- Ids del pack: todos fijados (Mojado `traveloptics:wet`, conjuros del Moldeador, dragones y criaturas de Ice and Fire).
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
| `/clase elegir <id>` | Elige clase, p. ej. `rites_and_paths:barbarian` |
| `/clase punto <rama>` | Gasta un punto en una rama (cobra XP) |
| `/clase subclase <id>` | Elige subclase (desde 15 puntos de base) |
| `/clase subpunto` | Sube un nivel de subclase |
| `/clase reiniciar clase` / `subclase` | Respec ilimitado; cada uno pierde el 33% de la XP (configurable) |
| `/clase info` | Estado actual y coste del siguiente nivel |
| `/clases info <jugador>`, `/clases reset <jugador>`, `/clases validar` | Administración (nivel 2) |
| `/clases debug item` | Disciplina del objeto en la mano y por qué |
| `/clases debug stats` | Modificadores activos, muros, crítico y desglose del último golpe |

Para tener XP de prueba: `/xp add @s 9000 points`.

**Amuleto del Creativo** (pestaña Herramientas y utilidades del creativo, o `/give @s rites_and_paths:creative_amulet`):
mientras esté en cualquier hueco del inventario, todos los enfriamientos de clase y de raza son 0 (habilidades,
tiempos mínimos entre usos de pasivas y perks, recargas de cargas, Salvaguarda de Bestias y robo del Goblin Ladrón).
No tiene receta; funciona en cualquier modo de juego para poder probar en supervivencia.

## Probar el Cronomante

`/clase elegir rites_and_paths:mage`, sube la base a 15 puntos y `/clase subclase rites_and_paths:chronomancer`; ambas
habilidades se tienen desde el nivel 1 de subclase (teclas Z y X). Para probar los niveles altos, sube la subclase con
`/clase subpunto` (cada nivel exige más puntos de base).

1. **Retroceso:** camina unos segundos, recibe daño y gasta maná; al pulsarlo vuelves al sitio de hace 3 s (5 s desde
   el nivel 4 y 8 s desde el 8) con parte de la vida y el maná. Unas chispas moradas marcan dónde volverías. Probar:
   caída desde altura (sin daño), efecto de Veneno (se quita desde el nivel 4), recarga de un conjuro (baja a la mitad
   desde el nivel 8), volver a un sitio que ya tiene un bloque (busca hueco o falla sin gastar recarga) y cambiar de
   dimensión (borra el historial).
2. **Parada:** junto a zombis y esqueletos: quedan quietos (también en el aire) y sus flechas se paran en el aire y
   siguen al terminar. Con otro jugador no aliado: pantalla gris, tercera persona, tictac y sin poder moverse ni atacar;
   al acabar vuelve su cámara. Un aliado (tecla J) no se para. Un jefe (Mowzie's, Cataclysm) se para la mitad del tiempo.
3. **Shaders:** con Iris/Oculus y un shaderpack, la esfera debe verse igual (es una cáscara translúcida, sin
   postprocesado).
4. **Seguridad:** desconectarse, morir o salir del chunk durante la parada no debe dejar nada congelado.

## Probar el Bardo

`/clase elegir rites_and_paths:bard` (con Iron's Spells instalado). El kit trae un laúd en la mano secundaria. Ritmo llega
con el primer punto (`/clase punto compas`); Armonía con Nivel Total 5, Contrapunto con 15 y Crescendo (tecla Z) con 30.
Las partituras de nivel 1 y al máximo están en la pestaña de creativo del mod.

1. **Minijuego:** con un instrumento en la mano aparecen notas que vienen hacia la mira: desde la izquierda, clic
   izquierdo; desde la derecha, clic derecho; desde arriba, espacio; desde abajo, Shift. Acierta y mira el combo (x12)
   debajo de la mira. Deja pasar una nota: el combo vuelve a 0. Pulsa otros carriles: el combo no baja.
2. **Atril:** pasa el instrumento a la mano principal (F) y Mayús + clic derecho fuera de combate. Mete partituras; una de
   nivel demasiado alto para la rareza no entra. Vuelve a pasarlo a la mano secundaria.
3. **Conjuros:** elige uno del instrumento en la rueda de Iron's y lánzalo con el clic derecho. Sin el combo mínimo sale
   un aviso; con la tecla de lanzar de Iron's (V) también (los musicales solo se tocan). Probar Marcha y Himno de Guerra
   con un aliado (tecla J), el Piano de Cola sobre un zombi (la sombra lo sigue y se para antes de caer) y el Baile
   Forzado sobre un zombi y sobre otro jugador (no puede moverse ni atacar).
4. **Armonía:** desde Nivel Total 5, toca una frase entera sin fallos ni pulsaciones de más: suben las etapas (puntos bajo
   el combo) y el tempo.
5. **Subir partituras:** partitura + tinta de Iron's en la mesa de trabajo (la rareza de la tinta depende del nivel).
6. **Otras clases:** un Mago no puede lanzar conjuros musicales ni tocar instrumentos.

## Añadir una clase

1. `data/<namespace>/rites_and_paths/classes/<id>.json` con 3 ramas de 10 puntos y 3 subclases.
2. Tres archivos en `data/<namespace>/rites_and_paths/subclasses/`.
3. Nombres en `assets/<namespace>/lang/`.
4. Código Java solo si necesita una mecánica nueva.

`/clases validar` y la prueba `DefinitionFilesTest` comprueban la estructura.
