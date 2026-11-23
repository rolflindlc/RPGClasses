package io.github.rpgclasses.client.druid;

/**
 * Mobs cuya animación de ataque se arranca con el evento de entidad 4 y se descuenta en su propio {@code aiStep}
 * (golem de hierro, devastador, hoglin, zoglin). Los cuerpos de bestia del Druida no se actualizan como entidades, así
 * que {@link DruidClient} descuenta la animación a mano cada tic con esto (lo implementan los mixins de
 * {@code mixin.client.beast}).
 */
public interface BeastAttackTicker {
    void rites_and_paths$tickAttackAnim();
}
