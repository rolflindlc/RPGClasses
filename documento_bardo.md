# Bardo: estado de implementación

Implementación de la *Adenda al Documento de Diseño v2: Bardo* (rev. 0.1, en el proyecto). **Estado (18-11-2026):
programado; sin probar en el juego.** Las propuestas sin confirmar de la
adenda se han programado tal cual y están en datos (`tools/bard.py`), así que se cambian sin tocar código.

**Convenciones:** **(propuesta)** = decisión de Claude al programar, pendiente de confirmar. **[MEDIR]** = calibrar en el
juego. **[verificar]** = comprobar con el pack.

## 1. Qué hay

| Parte | Dónde |
| ----- | ----- |
| Clase, ramas, perks, subclases y habilidades (números) | `tools/bard.py` → `classes/bard.json`, `subclasses/{war_chanter,enchanter,minstrel}.json`, `abilities/*.json` |
| Minijuego (cliente): frases, juicio de notas, HUD junto a la mira, sonidos | `client/bard/RhythmGame.java` |
| Combo, Armonía, Crescendo, Contrapunto, poder musical, bonus del instrumento, velocidad | `bard/BardState.java` |
| Auras (canciones) y su persistencia | `bard/BardAuras.java`, `bard/BardEffects.java` |
| Golpe a compás, Cantor de Guerra, guitarra-hacha, control (baile, silencio, hechizo), botín de jefes | `bard/BardCombat.java` |
| Instrumentos, guitarra-hacha, partituras, atril, receta de subir partituras | `bard/Instrument.java`, `InstrumentItem`, `GuitarAxeItem`, `SheetMusicItem`, `Sheets`, `MusicStandMenu`, `SheetMusicUpgradeRecipe` |
| Escuela musical y 21 conjuros en Iron's (12 de la adenda + 9 del 18-11) | `compat/irons/music/MusicBridge.java`, `MusicSpells.java` |
| Nota Cortante y Piano de Cola | `bard/CuttingNoteEntity.java`, `GrandPianoEntity.java`, `client/bard/GrandPianoRenderer.java` |
| Baile de sacudir los hombros | `client/bard/DanceFx.java`, `mixin/client/DanceModelMixin.java` |
| Texturas provisionales | `python3 -I tools/art/make_bard.py src/main/resources` |

Red: protocolo 16 (paquetes `BardNote`, `BardSync` y `DanceSync`).

## 2. Cómo funciona lo que la adenda dejaba abierto

### Minijuego
* El **cliente juzga** las notas (la latencia no permite hacerlo en el servidor) y manda cada resultado; el servidor lleva
  el combo, lo limita (como mucho 10 aciertos por segundo) y lo devuelve. Los carriles son las teclas de vanilla
  (atacar, usar, agacharse, saltar), así que se configuran en Controles.
* **Ráfagas (18-11):** las notas salen una por pulso en ráfagas de 3-4 notas, descanso de 1 s, 3-6 notas, descanso de
  1 s, 6-10 notas y descanso de 1,5 a 4 s, y vuelta a empezar. Esas tres ráfagas son un ciclo y cuentan como una *frase*
  para la Armonía. El patrón es la constante `BURST_NOTES` / `BURST_REST_MS` de `RhythmGame.java` ([MEDIR]).
* **Tempo (18-11):** base 100 ppm, **+1,5 % por punto de combo** (`tempo_per_combo`), tope 180 ppm. La Armonía ya no
  acelera: solo sube la potencia de las auras. Tempo Largo recorta la subida por combo. Ventana de acierto ±110 ms
  (perfecto ±50 ms), nunca más del 45 % del pulso.
* **Fallos (18-11):** un fallo quita la **mitad** del combo (`miss_keep` 0,5 de Ritmo). Oído Absoluto sube lo que
  conservas de 52,5 % (1 punto) a **75 %** (10 puntos): pierdes un cuarto. El Violín Espectral reduce a la mitad lo que pierdes (un 50 % menos de pérdida).
* **Reparto de carriles (propuesta):** clic izquierdo 40 %, Shift 22 %, espacio 23 %, clic derecho 15 %. El clic derecho
  no se repite antes de 1,5 s (lanza conjuros) y el espacio no se pide dos pulsos seguidos.
* **Guía:** cada nota viene hacia la mira desde el lado de su carril (izquierda, clic izquierdo; derecha, clic derecho;
  arriba, espacio; abajo, Shift). Debajo de la mira, el combo, las etapas de Armonía y Crescendo. Metrónomo suave.
* **Pulsaciones de más** en los descansos entre ráfagas no cuentan para la Armonía (propuesta).
* **Guardar el instrumento** más de 3 s pone el combo y la Armonía a 0 (propuesta). Morir también.
* Con un menú abierto, el juego en pausa o bailando, el minijuego se detiene sin contar fallos.

### Instrumento y lanzamiento
* El clic derecho con el instrumento lanza el conjuro elegido en la **rueda de Iron's** si es del instrumento; si no, el
  primero del instrumento. **La tecla de lanzar de Iron's no lanza conjuros musicales** (ni libros ni pergaminos): solo
  se tocan. Mantener pulsado el clic derecho solo lanza una vez.
* Se lanza como desde un libro (gasta maná y recarga completa).
* **Atril (propuesta):** Mayús + clic derecho con el instrumento en la mano principal y fuera de combate. 12 huecos;
  solo se usan los de tus ranuras (las del instrumento + Juglar), sin repetir conjuro y con el nivel que admite la
  rareza. Las partituras se guardan enteras: se pueden sacar.
* Los **bonus del instrumento solo cuentan para un Bardo** y no son del objeto: los aplica el servidor mientras lo toca.
* Si se imbuye un conjuro en un instrumento con el yunque arcano, desaparece en un segundo (las partituras mandan).

### Poder musical
* Atributo propio `rites_and_paths:music_spell_power` (base 100 %). El muro de Magia del Bardo (−35 %) no lo toca; el resto
  de clases tiene −100 % fijo.
* **Tope +150 %** sobre combo + Compás + instrumento + Solista/Coro + Tempo Largo.
* **Los buffs y debuffs reciben la mitad del poder musical (propuesta, parámetro `buff_power_scaling`)**: con +150 %,
  el Himno de Guerra pasa de +12 % a +21 %, no a +30 %. El daño recibe el poder entero.

### Auras
* Se refrescan cada medio segundo a los aliados en el radio (a ti también); el efecto dura 1,25 s más la persistencia de
  Reverberación. Estela: la persistencia va con menos potencia (efecto débil oculto bajo el fuerte).
* Potencia en el amplificador del efecto: dos Bardos con la misma canción no se suman, cuenta la más alta.
* Volver a lanzar la misma canción la renueva. Danza acelera mientras suene alguna canción tuya.
* **Tocar ralentiza −10 % (propuesta)** salvo con Danza (la adenda dice que Danza quita la ralentización al tocar).
* Coral de Hierro: tope de −60 % de daño recibido (propuesta). Elegía: a los jefes solo Debilidad.

### Conjuros
* Piano de Cola y Baile Forzado necesitan un enemigo a la vista (24 bloques, con ayuda de puntería de 6°); sin él no se
  lanzan ni gastan maná. Baile Forzado no apunta a jefes.
* Piano: sombra de 1,8 s (los últimos 0,8 s, fija) y caída de 0,3 s desde 9 bloques. Cae un solo piano (18-11: se quitó el segundo del nivel máximo) con un solo ruido, el del golpe.
* Disonancia: el silencio bloquea conjuros de Iron's a los jugadores; a los mobs solo les afecta el aturdimiento.
* Baile Forzado: el bailarín no hace daño de ninguna forma, se queda quieto y, si es jugador, no puede moverse, atacar,
  usar objetos ni lanzar conjuros. **(18-11)** Las criaturas bailan en el servidor (`BardCombat.danceStep`): sin objetivo,
  saltito a cada pulso (11 ticks), torso girando ±35° y una nota musical por pulso; así se ve también con Epic Fight. En
  los jugadores, rebote y brazos siguen siendo del cliente (`DanceFx`).

### Instrumentos (propuestas)
* Bonus secundarios de Épicos y Legendarios: los del instrumento del que salen, multiplicados por su rareza
  (Arpa de Cristal +20 % de regeneración, Violín Espectral +12 % de poder, Lira de Escamas +100 de maná, Laúd del Sol
  como un Laúd ×3, Órgano como la Zanfona ×3, Tambor del Coloso −9 % de recarga; Cuerno de Guerra +8 % de lanzamiento
  y −4 % de recarga).
* Cuerno de Guerra y Órgano: +25 % de radio de las auras. Lira de Escamas: −25 % del daño de su elemento a los aliados.
* Arpa de Cristal: +5 % del maná máximo cada 10 aciertos seguidos. Laúd del Sol: los aciertos perfectos curan 0,5 a los
  aliados a 8 bloques (a ti 0,25; como mucho cada 0,5 s). Tambor del Coloso: cada 10 aciertos seguidos (también con el combo ya en 30), aturdimiento de 1 s a 4 bloques.

### Obtención (propuestas)
* **Comunes:** madera y cuerda (ocarina de arcilla, pandereta de hierro y cuero). **Raros:** hierro, oro, cuero y lana.
* **Épicos:** mesa de herrería: fragmento resonante (plantilla) + instrumento base (arpa, violín o un cuerno de cabra)
  + lingote arcano de Iron's. Liras de Escamas: lira rodeada de 8 escamas (Ice and Fire).
* **Guitarra-hacha:** la receta de la adenda (fragmento resonante, lingote arcano e hilo arcano).
* **Legendarios:** el Laúd del Sol Eterno lo suelta Barako/Umvuthi (Mowzie's); el Leviatán suelta la Lengüeta abisal
  (Zanfona → Órgano del Abismo) y la Monstruosidad de Netherita el Parche del Coloso (Tambor → Tambor del Coloso), en la
  mesa de herrería con un fragmento resonante. Los jefes van por tags de entidad (`rites_and_paths:bard/*`) **[LEER: ids]**.
* **Materiales nuevos:** fragmento resonante (2 fragmentos de amatista + bloque musical + polvo de piedra luminosa → 2),
  hilo arcano (3 cuerdas + esencia arcana de Iron's o lapislázuli → 3).
* **Partituras de nivel 1:** papel + tinta + bloque musical + un ingrediente propio de cada conjuro (+ 1 o 2 fragmentos
  resonantes los de combo 20 y 30). **Subir de nivel:** partitura + una tinta de Iron's cuya rareza depende del nivel de
  destino (10 niveles: 2 común, 3-4 poco común, 5-6 rara, 7-8 épica, 9-10 legendaria).

### Cambios del 18-11 que sustituyen a la adenda (rev. 0.1)
* Ritmo: un fallo ya no pone el combo a 0, quita la mitad. Oído Absoluto: de «a la mitad» pasa a «un cuarto».
* Armonía: ya no da +10 % de tempo por etapa (era hasta +50 %); el tempo sube con el combo. Sigue dando +6 % a los buffs.
* Frases: ya no son 16 pulsos con densidad del 60 %; son ciclos de tres ráfagas (ver Minijuego).
* Piano de Cola: el nivel máximo ya no deja caer un segundo piano con la mitad de daño.
* Baile Forzado: las criaturas bailan de verdad en el servidor (antes solo se quedaban quietas con Epic Fight).

## 3. Pendiente

1. **Probar en el juego** todo; las texturas son provisionales.
2. **[verificar]** con Epic Fight: el clic derecho del modo batalla (guardia) frente al instrumento en la mano
   secundaria; el baile servidor (salto y giro) de las criaturas que dibuja Epic Fight; el moveset de espadón de la
   guitarra-hacha.
3. **[MEDIR]** tempo (1,5 % por combo), patrón de ráfagas, ventanas, poder musical total, la mitad de poder en buffs, daño del Cantor de
   Guerra con combo 30, Última Nota y la tabla de balance.
4. **[LEER]** ids de Barako/Umvuthi, el Leviatán y la Monstruosidad de Netherita en el pack.
5. Integrar la adenda en el documento v2 (regla 14 y tabla de muros).

## 18-11 (2): mesa de inscripción, pestaña y clase
- `isBard` se decide por la clase del jugador (no por nivel): ya no dice «no eres Bardo» a nivel 1.
- Mesa de inscripción de Iron's: el instrumento va en la ranura del libro y una partitura en la del pergamino; el botón
  la guarda en el primer hueco libre (misma regla que el atril: sin repetir conjuro, nivel limitado por el instrumento,
  capacidad del jugador). Mixins: `mixin/irons/Inscription*Mixin` (servidor) y `mixin/client/InscriptionScreenMixin`
  (cliente), con `remap=false` y `require=0` (nombres SRG de Iron's 3.16.3). Lógica en `bard/InscriptionTable.java`.
- Las partituras (nivel 1 y máximo) salen también en la pestaña «Iron's Spellbooks Scrolls» (`irons_spellbooks:spellbook_scrolls`).
- Sin compilar ni probar en juego: lo compilará GitHub Actions.

## 18-11 (3): 9 conjuros nuevos y rotación de conjuros
Los 12 originales siguen igual. Los nuevos están en `MusicSpellInfo` (valores a nivel 1), `MusicSpells` (lógica) y
`bard/BardZones.java` (efectos que duran: coro, barrera, sirena, sueño y protección). Texto, recetas de partitura e
iconos provisionales en `tools/bard.py`, `tools/i18n` y `tools/art/make_bard.py`. **[MEDIR]** todos los números.

| Conjuro | Combo | Niv. | Maná | Recarga | Qué hace (nivel 1) |
| ------- | ----- | ---- | ---- | ------- | ------------------ |
| Fuga | 10 | 10 | 40 | 14 s | Salto instantáneo de 7 bloques (menos si hay un muro) en la dirección de la mirada; lo que atraviesas queda empujado y aturdido 0,5 s (jefes, la mitad). |
| Nana | 10 | 10 | 55 | 30 s | Cono de 8 bloques: los enemigos normales se duermen 4 s (jugadores, la mitad); el daño los despierta; jefes inmunes. |
| Resolución | 10 | 10 | 60 | 40 s | Radio 8: aliados (y tú) pierden todos los efectos negativos y ganan absorción (4 de vida con la mitad del poder musical en buffs) 10 s. |
| Eco | 20 | 5 | 30 | 45 s | Repite tu último conjuro musical (hasta 30 s antes) al 50 % de poder, sin gastar su maná ni su recarga. Sin conjuro reciente no se lanza. No repite Eco ni Solo. |
| Coro Fantasma | 20 | 5 | 100 | 60 s | Tres coristas (partículas, no entidades) a 3 bloques de ti durante 8 s: los mobs a 6 bloques dejan de atacar a tu grupo y van hacia ellos; 3 de daño por segundo a menos de 3,5 bloques. Los jefes solo reciben el daño. |
| Barrera de Armonía | 20 | 5 | 90 | 50 s | Cúpula fija de radio 5 durante 6 s: elimina los proyectiles enemigos y −30 % de daño recibido para ti y tus aliados dentro (tope 60 %). |
| Canto de Sirena | 20 | 5 | 85 | 45 s | Durante 2,5 s los enemigos a 12 bloques son arrastrados hacia ti (jefes al 40 % de fuerza). |
| Solo Magistral | 30 | 5 | 150 | 120 s | Combo al máximo y sin perderlo (como Crescendo) 8 s; el siguiente conjuro (que no sea el Solo) no tiene recarga. |
| Canción de Cuna Eterna | 30 | 5 | 180 | 150 s | Aliados a 10 bloques: durante 15 s el primer golpe mortal de cada uno no lo mata; sobrevive con el 30 % de vida y sin efectos negativos. **No resucita:** revivir a un jugador ya muerto no es fiable en Forge. |

Los ingredientes de sus partituras están en `SPELL_INGREDIENT` de `tools/bard.py` (pluma, lana blanca, botella de miel,
bloque de amatista, arena de almas, fragmento de prismarina, caparazón de nautilo, diamante, tótem de la inmortalidad).

### Rotación de conjuros (mecánica nueva)
* Con un instrumento en la mano principal o secundaria, la rueda de Iron's **solo muestra los conjuros del instrumento**:
  no salen libros, armaduras, curios ni conjuros de raza (`mixin/irons/SpellSelectionMixin` y `RacialSpells`).
* Tras cada lanzamiento, el conjuro elegido pasa al **siguiente del instrumento que se pueda tocar**: sin recarga y con
  el combo mínimo cubierto. Si el elegido deja de poder tocarse (entra en recarga o el combo baja de su mínimo), pasa
  solo al siguiente que pueda; se comprueba cada 5 ticks (`BardState.onServerTick` → `BardMagic.rotateSpell`).
* La selección se mueve en el servidor (datos sincronizados de Iron's, por reflexión: `MusicRotation`). La rueda sigue
  pudiendo usarse a mano, pero si se elige uno que no se puede tocar, se corrige solo.
* Si ninguno se puede tocar, la selección se queda donde está.

## 18-11 (4): arreglos de la rotación y de la mesa de inscripción
- **Rotación de conjuros:** el servidor ya movía la selección, pero el cliente de Iron's guarda la rueda
  (`ClientMagicData.spellSelectionManager`) y solo la rehace al cambiar de equipo, así que seguía mostrando el conjuro
  anterior. `client/bard/SpellWheelSync` compara cada tick (con un instrumento en la mano) la selección sincronizada con
  la de la rueda y, si no coinciden, la rehace. Por reflexión; si falla, se desactiva con un aviso en el log.
- **Sacar partituras en la mesa de inscripción:** como con un libro, se elige un hueco del instrumento, su partitura
  (con su nivel guardado) sale en la ranura de resultado y al cogerla se quita del instrumento. Iron's solo lo hacía con
  libros. `InscriptionMenuMixin` (resultado y botones) e `InscriptionResultSlotMixin` (clase anónima n.º 5, `onTake`).
- **Inscribir usa ahora el botón -2** (`InscriptionTable.INSCRIBE_BUTTON`): Iron's usa -1 también para «sin selección»
  cada vez que cambia el objeto de la ranura del libro, y con -1 una partitura ya puesta se inscribía sola.
- Sin probar en el juego.
