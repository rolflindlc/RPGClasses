package io.github.rpgclasses.gear;

/**
 * Las cuatro armas marciales. Para cualquier clase tienen el daño de la espada del tier por {@code damageFactor} y la
 * velocidad de la espada (1,6) por {@code speedFactor}. Para el Monje cuentan como desarmado: su daño sale del daño
 * desarmado y se ajusta a la velocidad del arma para que el DPS sea {@code monkDps} veces el de los puños.
 */
public enum WeaponKind {
    /** Rápido, corto, combo largo (favorece Fuego). */
    NUNCHAKU("nunchaku", 0.7, 1.4, -0.5, 0.98),
    /** Medio, con parry al mantener el clic derecho (favorece Piedra). */
    SAI("sai", 1.0, 0.95, 0, 0.95),
    /** Lento, con alcance y barrido en arco (favorece Agua). */
    BO("bo", 1.35, 0.67, 1.5, 0.90),
    /** Nudilleras: van en la ranura de manos de Curios (o en la mano secundaria) y solo cuentan con la mano vacía. */
    KNUCKLES("knuckles", 1.0, 1.0, 0, 1.0);

    public static final double SWORD_SPEED = 1.6;
    /** Velocidad de ataque base del jugador (puños). */
    public static final double FIST_SPEED = 4.0;

    public final String id;
    public final double damageFactor;
    public final double speedFactor;
    public final double reach;
    public final double monkDps;

    WeaponKind(String id, double damageFactor, double speedFactor, double reach, double monkDps) {
        this.id = id;
        this.damageFactor = damageFactor;
        this.speedFactor = speedFactor;
        this.reach = reach;
        this.monkDps = monkDps;
    }

    public double attackSpeed() {
        return SWORD_SPEED * speedFactor;
    }
}
