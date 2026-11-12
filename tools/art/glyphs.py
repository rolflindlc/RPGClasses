"""Dibujos (glifos) en un espacio de 36x36. Cada función recibe un Canvas y pinta capas.

Los mismos dibujos sirven para los iconos de efectos (18 px, k=0.5) y para los retratos (36 px, encogidos dentro
del marco). Paleta: cada glifo trae sus colores; el sombreado y el contorno los pone pix.Canvas.
"""
import math

GOLD, GOLD_D = "#f2c94c", "#b8862b"
STEEL, STEEL_D = "#c9d1d9", "#7d8790"
WOOD = "#8b5a2b"
BONE = "#efe6cf"
RED, BLOOD = "#d93a2e", "#9b1b1b"
GREEN, TOXIC = "#5aa02d", "#8bd13a"
PURPLE = "#9f6fe0"
BLUE = "#4f8fe8"
FIRE1, FIRE2, FIRE3 = "#ffd34a", "#ff8a1f", "#d9361c"
DARK = "#2b2b36"


# ---------------------------------------------------------------- piezas reutilizables

def shield_shape(d, x=0, y=0, s=1.0):
    pts = [(8, 5), (18, 3), (28, 5), (29, 9), (28, 20), (18, 33), (8, 20), (7, 9)]
    d.poly(*[(x + (px - 18) * s + 18, y + (py - 18) * s + 18) for px, py in pts])


def shield(cv, body, rim, emblem=None, x=0, y=0, s=1.0):
    cv.layer(rim, lambda d: shield_shape(d, x, y, s))
    cv.layer(body, lambda d: shield_shape(d, x, y, s * 0.78))
    if emblem:
        emblem(cv)


def skull(cv, bone=BONE, eyes="#1d1d22", x=0, y=0, s=1.0, eye_color=None):
    t = lambda px, py: (x + (px - 18) * s + 18, y + (py - 18) * s + 18)
    def head(d):
        a, b = t(7, 5)
        c, e = t(29, 25)
        d.ellipse(a, b, c, e)
        p, q = t(11, 20)
        r, u = t(25, 31)
        d.rect(p, q, r, u)
    cv.layer(bone, head)
    def holes(d):
        for ex in (13, 23):
            a, b = t(ex - 3.2, 13)
            c, e = t(ex + 3.2, 20)
            d.ellipse(a, b, c, e)
        d.poly(t(18, 20), t(16, 24), t(20, 24))
    cv.layer(eye_color or eyes, holes, shade=False)
    def teeth(d):
        for tx in (14, 18, 22):
            a, b = t(tx - 0.6, 26)
            c, e = t(tx + 0.6, 31)
            d.rect(a, b, c, e)
    cv.layer(dark(bone), teeth, shade=False)


def dark(c):
    from pix import hexrgb, dark as dk
    return dk(hexrgb(c), 0.35)


def flame(cv, x=0, y=0, s=1.0, colors=(FIRE3, FIRE2, FIRE1)):
    t = lambda px, py: (x + (px - 18) * s + 18, y + (py - 18) * s + 18)
    shapes = [
        [(18, 2), (24, 10), (29, 16), (29, 24), (24, 32), (12, 32), (7, 24), (7, 17), (11, 13), (13, 18), (15, 9)],
        [(18, 11), (23, 17), (25, 23), (22, 30), (14, 30), (11, 24), (13, 19), (15, 21)],
        [(18, 19), (21, 23), (21, 28), (15, 28), (15, 23)],
    ]
    for col, pts in zip(colors, shapes):
        cv.layer(col, lambda d, pts=pts: d.poly(*[t(*p) for p in pts]))


def droplet(cv, color, x=0, y=0, s=1.0):
    t = lambda px, py: (x + (px - 18) * s + 18, y + (py - 18) * s + 18)
    def g(d):
        d.poly(t(18, 3), t(25, 16), t(11, 16))
        a, b = t(9.5, 12)
        c, e = t(26.5, 33)
        d.ellipse(a, b, c, e)
    cv.layer(color, g)
    cv.layer("#ffffff", lambda d: d.rect(*t(13, 19), *t(15, 24)), shade=False)


def star(d, cx, cy, r1, r2, n=4, rot=-90):
    pts = []
    for i in range(n * 2):
        r = r1 if i % 2 == 0 else r2
        a = math.radians(rot + i * 180 / n)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    d.poly(*pts)


def blade(d, x0, y0, x1, y1, w):
    """Hoja recta de (x0,y0) (empuñadura) a (x1,y1) (punta)."""
    dx, dy = x1 - x0, y1 - y0
    L = math.hypot(dx, dy)
    nx, ny = -dy / L * w / 2, dx / L * w / 2
    tip_back = 0.82
    bx, by = x0 + dx * tip_back, y0 + dy * tip_back
    d.poly((x0 + nx, y0 + ny), (bx + nx, by + ny), (x1, y1), (bx - nx, by - ny), (x0 - nx, y0 - ny))


def heart_shape(d, cx=18, cy=19, s=1.0):
    d.circle(cx - 6 * s, cy - 4 * s, 7 * s)
    d.circle(cx + 6 * s, cy - 4 * s, 7 * s)
    d.poly((cx - 12.6 * s, cy - 2 * s), (cx + 12.6 * s, cy - 2 * s), (cx, cy + 13 * s))


def speed_lines(cv, color, ys=(10, 18, 26), x0=2, x1=9):
    cv.layer(color, lambda d: [d.rect(x0 + (i % 2) * 2, y, x1, y + 2) for i, y in enumerate(ys)], shade=False)


# ---------------------------------------------------------------- efectos

def fx_bulwark(cv):
    shield(cv, GOLD, GOLD_D, lambda cv: cv.layer("#fff3c4", lambda d: (d.rect(16.5, 9, 19.5, 26), d.rect(11, 14, 25, 17)), shade=False))


def fx_charging_bulwark(cv):
    shield(cv, "#d9824a", "#8a4422", x=4, s=0.85)
    speed_lines(cv, "#ffd9b0")


def fx_immortal_fury(cv):
    cv.layer(RED, lambda d: d.poly((4, 22), (6, 6), (11, 12), (14, 1), (18, 9), (22, 1), (25, 12), (30, 6), (32, 22)))
    cv.layer(FIRE2, lambda d: d.poly((8, 20), (9, 11), (13, 15), (18, 7), (23, 15), (27, 11), (28, 20)), shade=False)
    skull(cv, x=0, y=4, s=0.82, eye_color="#ff2a2a")


def fx_plague(cv):
    def cloud(d):
        d.circle(12, 15, 7)
        d.circle(22, 12, 8)
        d.circle(26, 19, 6)
        d.rect(8, 15, 28, 23)
    cv.layer("#6b7a2a", cloud)
    cv.layer(TOXIC, lambda d: (d.circle(11, 28, 2.6), d.circle(19, 30, 2.6), d.circle(27, 27, 2.6)))


def fx_taunt_guard(cv):
    shield(cv, "#78aaf0", "#355d9c", lambda cv: cv.layer("#ffffff", lambda d: (d.rect(16.5, 8, 19.5, 20), d.rect(16.5, 22, 19.5, 25)), shade=False))


def fx_berserker_rage(cv):
    def face(d):
        d.circle(18, 18, 14)
    cv.layer(RED, face)
    cv.layer("#3a0a0a", lambda d: (d.poly((8, 11), (16, 15), (15, 18), (9, 15)), d.poly((28, 11), (20, 15), (21, 18), (27, 15)),
                                     d.poly((11, 26), (25, 26), (23, 23), (13, 23))), shade=False)
    cv.layer("#ffe14a", lambda d: (d.rect(12, 15, 14, 17), d.rect(22, 15, 24, 17)), shade=False)


def fx_toxin(cv):
    droplet(cv, GREEN)


def fx_endurance(cv):
    cv.layer("#b04a3a", lambda d: heart_shape(d))
    cv.layer("#8c6e50", lambda d: (d.rect(6, 15, 30, 21)))
    cv.layer("#d9c6a5", lambda d: (d.rect(10, 17, 12, 19), d.rect(17, 17, 19, 19), d.rect(24, 17, 26, 19)), shade=False)


def fx_condemned(cv):
    def sword(d):
        blade(d, 18, 4, 18, 33, 5)
    cv.layer("#fff0a0", sword)
    cv.layer(GOLD_D, lambda d: d.rect(10, 9, 26, 12))
    cv.layer("#7a5a2a", lambda d: d.rect(16.5, 2, 19.5, 9))


def fx_banner(cv):
    cv.layer(WOOD, lambda d: d.rect(7, 2, 10, 34))
    cv.layer("#e6c35a", lambda d: d.poly((10, 4), (30, 4), (30, 26), (20, 21), (10, 26)))
    cv.layer("#b02a2a", lambda d: star(d, 20, 13, 6, 2.6, 4), shade=False)


def fx_hunters_mark(cv):
    cv.layer("#c83c28", lambda d: (d.arc(5, 5, 31, 31, 0, 360, w=3), d.rect(16.5, 2, 19.5, 12), d.rect(16.5, 24, 19.5, 34),
                                    d.rect(2, 16.5, 12, 19.5), d.rect(24, 16.5, 34, 19.5)))
    cv.layer("#ff7a5a", lambda d: d.circle(18, 18, 2.5), shade=False)


def fx_retreat(cv):
    def arrows(d):
        for ox in (0, 11):
            d.poly((4 + ox, 18), (15 + ox, 6), (19 + ox, 6), (10 + ox, 18), (19 + ox, 30), (15 + ox, 30))
    cv.layer("#7cc8a0", arrows)


def fx_arcane_step(cv):
    cv.layer(PURPLE, lambda d: star(d, 21, 16, 13, 4, 4))
    cv.layer("#e6d6ff", lambda d: star(d, 21, 16, 5, 1.8, 4), shade=False)
    speed_lines(cv, "#c9b0ff", ys=(22, 28), x0=3, x1=11)


EFFECTS = {
    "bulwark": fx_bulwark, "charging_bulwark": fx_charging_bulwark, "immortal_fury": fx_immortal_fury,
    "plague": fx_plague, "taunt_guard": fx_taunt_guard, "berserker_rage": fx_berserker_rage, "toxin": fx_toxin,
    "endurance": fx_endurance, "condemned": fx_condemned, "banner": fx_banner, "hunters_mark": fx_hunters_mark,
    "retreat": fx_retreat, "arcane_step": fx_arcane_step,
}
