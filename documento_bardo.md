# Bardo: estado de implementación

Implementación de la *Adenda al Documento de Diseño v2: Bardo* (rev. 0.1, en el proyecto). **Estado (17-11-2026):
programado; compila y pasa las pruebas en GitHub Actions. Sin probar en el juego.** Las propuestas sin confirmar de la
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
| Escuela musical y 12 conjuros en Iron's | `compat/irons/music/MusicBridge.java`, `MusicSpells.java` |
| Nota Cortante y Piano de Cola | `bard/CuttingNoteEntity.java`, `GrandPianoEntity.java`, `client/bard/GrandPianoRenderer.java` |
| Baile de sacudir los hombros | `client/bard/DanceFx.java`, `mixin/client/DanceModelMixin.java` |
| Texturas provisionales | `python3 -I tools/art/make_bard.py src/main/resources` |

Red: protocolo 16 (paquetes `BardNote`, `BardSync` y `DanceSync`).

## 2. Cómo funciona lo que la adenda dejaba abierto

### Minijuego
* El **cliente juzga** las notas (la latencia no permite hacerlo en el servidor) y manda cada resultado; el servidor lleva
  el combo, lo limita (como mucho 10 aciertos por segundo) y lo devuelve. Los carriles son las teclas de vanilla
  (atacar, usar, agacharse, saltar), así que se configuran en Controles.
* **Frases:** 16 pulsos con nota en el ~60 % (el primero siempre), descanso de 4 pulsos. Tempo base 100 ppm, tope 180
  (propuesta, [MEDIR]). Ventana de acierto ±110 ms (perfecto ±50 ms), nunca más del 45 % del pulso.
* **Reparto de carriles (propuesta):** clic izquierdo 40 %, Shift 22 %, espacio 23 %, clic derecho 15 %. El clic derecho
  no se repite antes de 1,5 s (lanza conjuros) y el espacio no se pide dos pulsos seguidos.
* **Guía:** cada nota viene hacia la mira desde el lado de su carril (izquierda, clic izquierdo; derecha, clic derecho;
  arriba, espacio; abajo, Shift). Debajo de la mira, el combo, las etapas de Armonía y Crescendo. Metrónomo suave.
* **Pulsaciones de más** en el descanso entre frases no cuentan para la Armonía (propuesta).
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
* Atributo propio `rpgclasses:music_spell_power` (base 100 %). El muro de Magia del Bardo (−35 %) no lo toca; el resto
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
* Piano: sombra de 1,8 s (los últimos 0,8 s, fija) y caída de 0,3 s desde 9 bloques. El segundo piano cae 0,6 s después.
* Disonancia: el silencio bloquea conjuros de Iron's a los jugadores; a los mobs solo les afecta el aturdimiento.
* Baile Forzado: el bailarín no hace daño de ninguna forma, se queda quieto y, si es jugador, no puede moverse, atacar,
  usar objetos ni lanzar conjuros.

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
  mesa de herrería con un fragmento resonante. Los jefes van por tags de entidad (`rpgclasses:bard/*`) **[LEER: ids]**.
* **Materiales nuevos:** fragmento resonante (2 fragmentos de amatista + bloque musical + polvo de piedra luminosa → 2),
  hilo arcano (3 cuerdas + esencia arcana de Iron's o lapislázuli → 3).
* **Partituras de nivel 1:** papel + tinta + bloque musical + un ingrediente propio de cada conjuro (+ 1 o 2 fragmentos
  resonantes los de combo 20 y 30). **Subir de nivel:** partitura + una tinta de Iron's cuya rareza depende del nivel de
  destino (10 niveles: 2 común, 3-4 poco común, 5-6 rara, 7-8 épica, 9-10 legendaria).

## 3. Pendiente

1. **Probar en el juego** todo; las texturas son provisionales.
2. **[verificar]** con Epic Fight: el clic derecho del modo batalla (guardia) frente al instrumento en la mano
   secundaria; las criaturas que dibuja Epic Fight bailan quietas (sin brazos ni rebote); el moveset de espadón de la
   guitarra-hacha.
3. **[MEDIR]** tempo, ventanas, densidad de notas, poder musical total, la mitad de poder en buffs, daño del Cantor de
   Guerra con combo 30, Última Nota y la tabla de balance.
4. **[LEER]** ids de Barako/Umvuthi, el Leviatán y la Monstruosidad de Netherita en el pack.
5. Integrar la adenda en el documento v2 (regla 14 y tabla de muros).
