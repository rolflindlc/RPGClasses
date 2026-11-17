# Anexo rev. 2.20: Rebalance del Monje y Equipo de Clase

Anexo del *Documento de Diseño v2* (clases) y del anexo de *Razas*. Sustituye al «Plan de la update Equipo de Clase». **Estado: implementado en el código (rama `equipo-de-clase`, fusionada en `main`), sin probar en el juego.**

**Convenciones:** sin marca = decidido por el usuario. **(propuesta)** = decisión de Claude, pendiente de confirmar. **[MEDIR]** = calibrar en el juego. **[LEER]** = leer el valor real en la config del pack.

## 1. Rebalance del Monje (rev. 2.20)

**Decisión del usuario:** en el aspecto de cada postura (daño en Fuego, armadura en Piedra, movilidad en Agua), el Monje con **0 cargas** queda por debajo de la media de las clases, con **5 cargas** en la media y con **10 cargas** por encima. Se **quita el techo** de armadura de Piedra (solo queda el máximo vanilla de 30 de armadura y 20 de dureza).

| Parámetro | Antes (rev. 2.9 en el código) | Rev. 2.20 |
| ----- | ----- | ----- |
| Suelo de *Cuerpo Marcial* | 10 / 4 | **13 / 4** (rev. 2.17) |
| Fuego: daño por carga | +3 % | **+8 %** (≈ 72 % del DPS objetivo con 0 cargas, 100 % con 5 y 128 % con 10) |
| Piedra: armadura / dureza por carga | +0,75 / +0,25, tope 20 / 8 | **+1,4 / +0,8**, sin tope propio (13/4 → 20/8 con 5 → 27/12 con 10) |
| Agua: velocidad y salto por carga | +2,5 % | **+3 %** |
| Ramas Puño, Viento y Piel | +33 % a su postura | **+25 %** |
| Decaimiento de Fuego | 1,5 s y luego 1 cada 0,5 s | **2,5 s** y luego **1 cada 0,75 s** |
| Decaimiento de Piedra | 3 s y luego 1 cada 2 s | **1 cada 5 s sin recibir daño** (rev. 2.17) |
| Decaimiento de Agua | 1 por segundo quieto | **1 cada 1,5 s** quieto |
| Absorción de la descarga de Piedra | 0,5 HP por carga | **2 HP por carga** (rev. 2.17) |

* **Rev. 2.21 (decisión del usuario):** *Maestría de Postura* ya no sube las cargas máximas (siempre 10): da **+20 % (5 puntos) / +40 % (10 puntos) de eficacia** a la descarga al cambiar de postura y al efecto propio. Para no romper el umbral de 10, *Postura Dominante* pasa a **+30 % de eficacia en la favorita y −30 % en las otras** (antes ±3 cargas máximas). El buff por carga no cambia.
* **Rev. 2.21, armas marciales del Monje:** sin compensar la velocidad de ataque (con Epic Fight el ritmo lo marcan las animaciones y el nunchaku de madera llegaba a ×3). El golpe vale el desarmado por el factor del arma (nunchaku 0,7; sai 1; bo 1,35) y da 1 carga. El nunchaku usa el moveset de daga **[MEDIR]**.
* Con Piel llena y 10 cargas de Piedra: 30,5 / 14 (la armadura se recorta a 30). Las cargas que no caben dan resistencia al empuje, como antes.
* Fuego con Puño llena y 10 cargas: ≈ 144 % del DPS objetivo. Con *Postura Dominante* o *Maestría* (14–17 cargas) puede pasar de ×1,9: **vigilar [MEDIR]**.
* Los números están en `tools/texts.py` (habilidad `stances`) y se cambian por datapack.

## 2. Equipo de Clase: reglas

1. **Cualquier clase puede llevar y usar** las armaduras y armas nuevas (decisión del usuario). Reciben su armadura, su daño y sus stats ligeros.
2. **Los efectos de set completo** (4 piezas del mismo tier y clase de armadura) **solo funcionan para su clase**.
3. **Exención de la regla sin armadura:** las piezas del tag `rites_and_paths:class_armor/barbarian` no apagan las pasivas desarmadas del Bárbaro, y las de `rites_and_paths:class_armor/monk` no apagan *Cuerpo Marcial* ni borran las cargas del Monje. Cualquier otra pieza (también la del Monje en un Bárbaro y al revés) sigue contando como armadura.
4. **Paridad, no ventaja:** desde la netherita, el set más la defensa propia de la clase iguala al set normal del tier. En los tiers bajos la armadura es 0 o casi 0 y solo hay stats ligeros.
5. **Armas marciales** (tag `rites_and_paths:martial_weapons`): para cualquier clase son armas ligeras (muro de Armas ligeras); para el Monje cuentan como desarmado (cargas, Shift + golpe, perks, sin muro).
6. **Ice and Fire es opcional:** los objetos de dragón se registran siempre; sus recetas llevan la condición `forge:mod_loaded`. Los materiales van por tags con entradas opcionales (`rites_and_paths:iceandfire/*`) **[LEER: nombres exactos en la versión del pack]**.
7. **Curios es opcional:** sin Curios, las nudilleras funcionan en la mano secundaria y los demás curios no se pueden equipar.
8. Los bonus de velocidad de las piezas (cuero, +3 % con 4 piezas) son modificadores del objeto y **no cuentan** para el tope global de la regla 7 **(propuesta; es un 3 %)**.

## 3. Tiers y valores

| Tier | Bárbaro: armadura / dureza del set | Total con *Piel de Hierro* (16/6) | Monje: armadura / dureza del set | Stat ligero por pieza (×4) |
| ----- | ----- | ----- | ----- | ----- |
| Cuero | 0 / 0 | 16 / 6 | 0 / 0 | +0,75 % de velocidad |
| Cota (anillas) | 0 / 0 | 16 / 6 | 0 / 0 | +1,25 % de resistencia al empuje |
| Hierro | 0 / 0 | 16 / 6 | 1 / 0 (peto) | Bárbaro: +1,25 % de resistencia al empuje |
| Oro | 0 / 0 | 16 / 6 | 0 / 0 | +0,25 de Suerte; piglins neutrales |
| Diamante | 1 / 0 | 17 / 6 | 1 / 0 | +0,75 (Bárbaro) o +0,5 (Monje) puntos de crítico |
| Netherita | 4 / 6 | 20 / 12 | 0 / 4 | +10 % (Monje +5 %) de resistencia al empuje por pieza |
| Escamas de dragón (fuego, hielo, rayo) | 9 / 12 | 25 / 18 | 5 / 10 | −5 % de daño de su elemento por pieza |
| Dragonsteel (fuego, hielo, rayo) | 14 / 14 | 30 / 20 | 10 / 12 | −12,5 % de daño de su elemento por pieza (tope 50 %) |

* **Monje:** el set sube el suelo para que con **5 cargas de Piedra** (+7 / +4) iguale al set normal del tier: netherita 13/8 → 20/12; escamas 18/14 → 25/18; dragonsteel 23/16 → 30/20.
* Las cifras de escamas y dragonsteel son objetivos **[LEER]** hasta leer la config de Ice and Fire del pack.
* Los tiers de dragón y dragonsteel son resistentes al fuego (como la netherita) y raros/épicos.

## 4. Efectos de set completo (solo su clase)

| Tier | Bárbaro | Monje |
| ----- | ----- | ----- |
| Cuero | **Olfato:** los mobs hostiles a 12 bloques con menos del 30 % de vida brillan | **Primer Paso:** al entrar en una postura sin cargas recibes 1, y las cargas decaen un 10 % más despacio |
| Cota | **Tintineo de Guerra:** el primer golpe tras 2 s corriendo empuja más | **Anillas Sonoras:** cada carga suena; al llegar al umbral, acorde |
| Hierro | **Pecho de Hierro:** −5 % de daño de proyectiles | **Postura Firme:** Piedra gana carga con golpes desde 1,5 HP |
| Oro | **Botín Dorado:** 10 % de que un mob hostil suelte una pepita que salta hacia ti | **Peregrinaje:** la Meditación cura un 20 % más |
| Diamante | **Crujido:** los críticos ralentizan 1 s (ICD 3 s por objetivo) | **Reflejo de Cristal:** el Desvío da +1 carga con destello |
| Netherita | **Corazón de Brasas:** los golpes recibidos acumulan brasas (máx. 5, 8 s); Embestida o Furia Berserker las sueltan en un anillo de fuego de 4 bloques (2 + 1,5 por brasa) | **Sello de Postura:** Piedra decae la mitad de rápido; cambiar de postura con ≥5 cargas deja un sello que repite la descarga al 30 % a los 3 s |
| Escamas | **Rugido de Dragón:** Furia Berserker hace huir a los mobs no jefes a 8 bloques y les aplica el elemento | **Danza del Dragón:** al disparar un efecto propio, un dragón espectral te rodea 4 s y muerde al más cercano cada segundo (25 % de un golpe y el efecto de la postura) |
| Dragonsteel | **Ira del Wyrm:** con <40 % de vida o en Furia Berserker, alas espectrales; cada crítico suelta un cono elemental de 4 bloques al 35 % (ICD 1 s). Fuego quema; hielo ralentiza y el 3.er cono congela; rayo salta a 2 enemigos más | **Puño del Wyrm:** cada efecto propio lanza una cabeza de dragón de energía 8 bloques en línea (40 % de un golpe y el elemento; rayo con relámpago visual). Pasivas: fuego, velocidad sobre lava en Fuego; hielo, congela el agua en Agua; rayo, chispa al atacante en Piedra |

## 5. Armas marciales

| Arma | Daño (cualquier clase) | Velocidad | Alcance | DPS del Monje frente a sus puños | Rasgo |
| ----- | ----- | ----- | ----- | ----- | ----- |
| Nudilleras (curio de manos o mano secundaria) | Resto de clases: +(espada del tier − 1) × 0,6 con la mano vacía | — | — | 100 % + bonus de tier | Mejoran los golpes con la mano vacía |
| Nunchaku | Espada del tier × 0,7 | 2,24 | −0,5 | 98 % | Molinete a los 8 golpes encadenados (netherita+) |
| Sai | Espada del tier × 1,0 | 1,52 | — | 95 % | Guardia con clic derecho: parry en los primeros 0,25 s (anula, aturde 0,5 s, +2 cargas al Monje); después −30 % de daño |
| Bo | Espada del tier × 1,35 | 1,07 | +1,5 | 90 % (a un objetivo) | Barrido en arco de 150° al 60 % |

* **Monje:** el golpe vale (1 + daño desarmado + bonus de tier) × DPS del arma × (4 / velocidad del arma), para que el DPS no dependa de la velocidad. Las armas lentas dan más cargas de Fuego por golpe (4 / velocidad, tope 3) **[MEDIR con Epic Fight]**.
* **Bonus de tier del Monje:** 0 hasta diamante, +1 netherita, +2 hueso de dragón, +5 dragonsteel **[LEER]**.
* **Espada de referencia:** madera 4, piedra 5, hierro 6, oro 4, diamante 7, netherita 8, hueso de dragón 9, dragonsteel 12 **[LEER]**.
* **Epic Fight:** nunchaku con moveset de puños, sai de daga y bo de lanza (`data/rites_and_paths/capabilities/weapons`). Animaciones propias: fase posterior.

**Efectos por tier (cualquier clase):**

| Arma | Netherita | Hueso de dragón | Dragonsteel |
| ----- | ----- | ----- | ----- |
| Nudilleras | Grietas: a los 5 golpes al mismo objetivo, estallido de 2 bloques (30 %) | Herida: 25 % de −30 % de curación 4 s | Elemento en cada golpe; efectos propios del Monje +20 % |
| Nunchaku | Molinete: giro al 50 % en 2,5 bloques | Nunchaku fantasma: 3 golpes al 30 % | Torbellino de 3 s que atrae y aplica el elemento |
| Sai | Devuelve el 50 % del golpe parado | Debilidad II 3 s al atacante | Estocada elemental: el siguiente golpe en 1,5 s hace ×1,5 y perfora en línea 4 bloques |
| Bo | El barrido ciega 1 s a los mobs | Salto de pértiga (agachado + saltar, 8 s) con onda al aterrizar | Esperar 1,5 s entre golpes lanza una onda elemental en arco de 6 bloques |

Las armas de dragonsteel aplican su elemento en cada golpe (fuego quema, hielo ralentiza, rayo salta). Stats ligeros de los tiers bajos: nunchaku de madera más rápido; sai con ventana de parry o reducción algo mayores (madera, piedra, hierro, diamante); bo con más alcance (madera, diamante) o barrido de 160° (hierro).

## 6. Recetas

* **Intermedios:** Correa curtida (2 de cuero + hilo → 2), Tela marcial (3 de lana + hilo → 2), Plantilla de forja dracónica (netherita + huesos y escamas de dragón; se duplica con diamantes y un lingote de dragonsteel).
* **Armaduras (cuero a diamante):** patrón de arnés con correas (Bárbaro) o de gi con tela (Monje), con menos material que la pieza vanilla.
* **Netherita:** mesa de herrería con la plantilla vanilla. **Escamas:** sin forma, pieza de netherita + escamas del elemento (Bárbaro 4/6/5/3, Monje 3/5/4/2). **Dragonsteel:** mesa de herrería con la plantilla dracónica + pieza de escamas de cualquier elemento + lingote de dragonsteel (el lingote decide la variante).
* **Armas:** nudilleras `MMM / S S`, nunchaku `M M / _C_`, sai `M M / _M_ / _P_`, bo en diagonal con palo. Netherita en mesa de herrería; hueso de dragón con huesos de dragón; dragonsteel con la plantilla dracónica.

## 7. Curios (32)

> **Desactivados en la versión de salida.** Con `Curio.ENABLED = false` (Java) y `CURIOS_ENABLED = False` (`tools/gear.py`) no se registran los objetos, no hay recetas ni ranuras salvo la de manos (nudilleras) y sus efectos no se escuchan. El código y los textos se conservan: para recuperarlos basta poner los dos interruptores a verdadero y regenerar los datos.

Rarezas: Común, Raro, Épico y Reliquia (**máx. 1 Reliquia equipada**). Los curios de clase o raza solo se equipan con esa clase o raza, y sus efectos se apagan si dejas de cumplirla. Todos se fabrican; los de jefe usan su material (Ice and Fire, Cataclysm, Iron's) con una alternativa vanilla cara en el tag `rites_and_paths:curio/*`.

| Grupo | Curios |
| ----- | ----- |
| Generales | Farol de Fuegos Fatuos, Brújula del Último Aliento, Medallón del Ermitaño, Cinturón del Viajero, Anillo de Escarcha Errante, Campanilla de Pixie, Colgante de Serpiente Marina, Cuerno de Cíclope, Ojo de Gorgona, Talismán de la Tormenta, Pluma de Fénix (Reliquia) |
| Mago | Prisma del Archimago, Reloj de Arena Arcano |
| Paladín | Relicario del Juramento, Estandarte de Bolsillo |
| Bárbaro | Collar de Colmillos, Tambor de Guerra, Cráneo Trofeo (Reliquia) |
| Pícaro | Capa de Cuervos, Dados Trucados |
| Ranger | Pluma de Hipogrifo, Carcaj del Matadragones (Reliquia) |
| Monje | Rosario de las Tres Posturas, Campana del Templo |
| Razas | Insignia de Mil Oficios (Humano), Pendiente de Hueso de Trol (Orco), Farolillo de Mina (Enano), Hoja del Árbol Eterno (Elfo), Bolsa de Chucherías (Goblin), Caracola de las Mareas (Tritón), Corazón de Magma (Infernal), Velo de Medianoche (No-muerto) |

**Cambios respecto al plan (propuesta, por no haber tecla de activación de curios):** el Cuerno de Cíclope suena solo al entrar en combate (primer golpe tras 10 s); el Reloj de Arena Arcano se activa al bajar del 30 % de vida (no de maná); la Pluma de Hipogrifo hace planear al tensar el arco en el aire; el Cinturón del Viajero come de tu inventario; la Caracola de las Mareas es pasiva; el Estandarte de Bolsillo es solo visual; los Dados Trucados con un 1 dejan el crítico en golpe normal. Las descripciones exactas están en los textos del juego (`curio.rites_and_paths.<id>.desc`).

**Orden de supervivencia:** clase → raza → **Pluma de Fénix** (tras la armadura) → Tótem.

## 8. Implementación

* Código: paquete `io.github.rpgclasses.gear` (`ArmorTier`, `WeaponTier`, `WeaponKind`, `ClassArmorItem`, `MartialWeaponItem`, `KnucklesItem`, `ModGear`, `GearRules`, `GearEvents`, `SetEffects`, `Curio`, `CurioItem`, `CurioEffects`, `GearFx`) y `compat/curios/CuriosCompat`.
* Datos: `tools/gear.py` (recetas, tags, tipos de daño, capacidades de Epic Fight, ranuras de Curios y textos) desde `tools/gen_data.py`; texturas provisionales con `python3 -I tools/art/make_gear.py src/main/resources`.
* Ganchos en el código existente: `StatEngine.isWearingArmor` (exención), `WeaponClassifier` (armas marciales), `DamagePipeline` (daño marcial del Monje, nudilleras, críticos), `MonkCharges` (decaimiento, umbrales, descarga, efecto propio, cargas por golpe), `MonkPerks` (Meditación), `CombatAbilities` (Embestida y Furia Berserker), `RaceTraits` (Última Resistencia).
* Pestaña de creativo propia: «RPG Classes: Equipo de Clase».

## 9. Pendiente

1. **Probar en el juego** todo el anexo; las texturas son provisionales (generadas por script).
2. **[LEER]** valores de Ice and Fire del pack (escamas, dragonsteel, hueso de dragón) y nombres exactos de sus objetos y tipos de daño.
3. **[MEDIR]** DPS del Monje con armas marciales en Epic Fight (velocidades reales de los movesets), ventana del parry con latencia, y si la guardia de Epic Fight intercepta el clic derecho del sai.
4. **[MEDIR]** Fuego con 14–17 cargas (Dominante, Maestría) y la absorción de Piedra sin tope (2 HP por carga).
5. Animaciones propias de nunchaku y bo; variantes elementales del hueso de dragón empapado en sangre (no implementadas).
6. Curios por subclase (18) y por subraza (24): fase posterior.
7. Integrar este anexo en el documento v2 (secciones 1, 5, 8 y 10).
