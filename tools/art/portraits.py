"""Retratos de 36x36 (el doble que un icono de efecto) para clases, subclases, razas y subrazas.

Marco: medallón cuadrado con esquinas cortadas en el color de la clase o raza. Dentro, un glifo encogido.
Uso: python3 -I tools/art/portraits.py src/main/resources   (desde la raíz del repo)
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pix import Canvas, hexrgb, light, dark  # noqa: E402
from glyphs import (shield, shield_shape, skull, flame, droplet, star, blade, heart_shape, speed_lines,  # noqa: E402
                    GOLD, GOLD_D, STEEL, STEEL_D, WOOD, BONE, RED, BLOOD, GREEN, TOXIC, PURPLE, BLUE, FIRE1, FIRE2, FIRE3)
import glyphs  # noqa: E402

SIZE = 36

# ---------------------------------------------------------------- clases

def mage(cv):
    cv.sparkle(31, 6); cv.sparkle(4, 13, r=1.5)
    cv.layer("#5b4bc4", lambda d: d.poly((19, 2), (24, 6), (21, 7), (27, 25), (9, 25), (16, 8)))
    cv.layer("#3e3290", lambda d: d.ellipse(3, 22, 33, 31))
    cv.layer(FIRE1, lambda d: (star(d, 17, 15, 4, 1.4), star(d, 22, 20, 2.6, 1)), shade=False)


def paladin(cv):
    shield(cv, "#eef1f5", GOLD_D, lambda cv: cv.layer(GOLD, lambda d: (d.rect(16, 8, 20, 28), d.rect(10, 13, 26, 17))))


def barbarian(cv):
    cv.layer(WOOD, lambda d: d.rect(16.5, 3, 19.5, 34))
    def blades(d):
        d.poly((16, 7), (8, 3), (3, 12), (8, 21), (16, 17))
        d.poly((20, 7), (28, 3), (33, 12), (28, 21), (20, 17))
    cv.layer(STEEL, blades)
    cv.layer(STEEL_D, lambda d: d.rect(15, 8, 21, 16))


def rogue(cv):
    cv.layer(STEEL, lambda d: blade(d, 13, 23, 32, 4, 6))
    cv.layer(GOLD_D, lambda d: d.poly((8, 20), (11, 17), (19, 25), (16, 28)))
    cv.layer("#4a2f2a", lambda d: d.poly((4, 30), (6, 32), (14, 24), (12, 22)))


def ranger(cv):
    cv.layer(WOOD, lambda d: d.arc(-2, 2, 24, 34, -78, 78, w=3))
    cv.layer("#e8e8e8", lambda d: d.line(13, 4, 13, 32, w=1), shade=False)
    cv.layer(STEEL_D, lambda d: d.line(4, 18, 30, 18, w=2))
    cv.layer(STEEL, lambda d: d.poly((26, 13), (34, 18), (26, 23)))
    cv.layer("#e85a4a", lambda d: (d.poly((2, 14), (8, 17), (8, 19), (2, 22))), shade=False)


def monk(cv):
    R = 14
    cv.layer("#f4eee0", lambda d: d.circle(18, 18, R))
    def half(d):
        pts = [(18 + R * math.cos(math.radians(a)), 18 + R * math.sin(math.radians(a))) for a in range(-90, 91, 6)]
        d.poly(*pts)
    cv.layer("#2a2a32", half)
    cv.layer("#2a2a32", lambda d: d.circle(18, 25, R / 2))
    cv.layer("#f4eee0", lambda d: d.circle(18, 11, R / 2))
    cv.layer("#f4eee0", lambda d: d.circle(18, 25, 2.2), shade=False)
    cv.layer("#2a2a32", lambda d: d.circle(18, 11, 2.2), shade=False)


# ---------------------------------------------------------------- subclases

def battle_mage(cv):
    cv.sparkle(31, 26, "#d9c4ff")
    cv.layer(STEEL, lambda d: d.poly((9, 5), (14, 5), (18, 9), (22, 5), (27, 5), (32, 11), (28, 16), (27, 32), (9, 32), (8, 16), (4, 11)))
    cv.layer(STEEL_D, lambda d: d.rect(17, 11, 19, 31), shade=False)
    cv.layer(PURPLE, lambda d: star(d, 18, 19, 6, 2.2))


def chaotic_evoker(cv):
    cv.sparkle(5, 6, "#ff9ad5"); cv.sparkle(31, 9, "#d9a4ff", r=1.5)
    flame(cv, colors=("#6a1fa0", "#d0389a", "#ff9ad5"))


def elementalist(cv):
    cv.sparkle(31, 4); cv.sparkle(5, 31, r=1.5)
    cv.layer("#e0452b", lambda d: d.poly((18, 3), (18, 18), (3, 18)))
    cv.layer("#4fa8e8", lambda d: d.poly((18, 3), (33, 18), (18, 18)))
    cv.layer("#5aa02d", lambda d: d.poly((18, 18), (33, 18), (18, 33)))
    cv.layer("#a07a4a", lambda d: d.poly((3, 18), (18, 18), (18, 33)))
    cv.layer("#ffffff", lambda d: d.circle(18, 18, 2.4), shade=False)


def blessed_guardian(cv):
    cv.sparkle(4, 8); cv.sparkle(32, 12, r=1.5)
    cv.layer(GOLD, lambda d: d.arc(8, 1, 28, 9, 0, 360, w=2))
    shield(cv, "#eef1f5", GOLD_D, lambda cv: cv.layer(GOLD, lambda d: (d.rect(16.5, 14, 19.5, 30), d.rect(12, 18, 24, 21))), y=4, s=0.82)


def charging_juggernaut(cv):
    shield(cv, STEEL, STEEL_D, lambda cv: cv.layer(GOLD, lambda d: d.poly((18, 11), (24, 18), (18, 25), (12, 18)), shade=False), x=5, s=0.88)
    speed_lines(cv, "#ffe2a8", ys=(9, 17, 25), x0=1, x1=9)


def unyielding_bastion(cv):
    cv.layer("#8a8f99", lambda d: (d.rect(7, 11, 29, 33), d.rect(7, 5, 11, 11), d.rect(14, 5, 18, 11) if False else d.rect(16, 5, 20, 11),
                                   d.rect(25, 5, 29, 11)))
    cv.layer("#2a2420", lambda d: (d.rect(14, 23, 22, 33), d.circle(18, 23, 4)), shade=False)
    cv.layer("#5c616b", lambda d: (d.rect(9, 15, 15, 16), d.rect(21, 15, 27, 16), d.rect(12, 19, 24, 20)), shade=False)


def indestructible(cv):
    cv.sparkle(31, 6); cv.sparkle(5, 27, r=1.5)
    def ankh(d):
        d.arc(11, 2, 25, 18, 0, 360, w=4)
        d.rect(16, 15, 20, 34)
        d.rect(7, 17, 29, 21)
    cv.layer(GOLD, ankh)


def plague_corruptor(cv):
    skull(cv, bone="#c9d49a", eye_color="#3f6b10", s=0.9, y=-2)
    cv.layer(TOXIC, lambda d: (d.rect(10, 29, 12, 34), d.rect(23, 30, 25, 34), d.circle(17, 33, 1.5)))


def suicidal_berserker(cv):
    cv.layer("#3b2f2f", lambda d: d.line(9, 33, 27, 5, w=3))
    cv.layer("#4a4a55", lambda d: d.poly((22, 2), (34, 6), (33, 18), (27, 15), (24, 9)))
    cv.layer(RED, lambda d: (d.circle(9, 12, 3), d.poly((9, 5), (12, 11), (6, 11))))
    cv.layer(RED, lambda d: (d.circle(26, 26, 2.5), d.poly((26, 20), (28.5, 25), (23.5, 25))))


def arcane_blade(cv):
    cv.sparkle(6, 7, "#e6d6ff"); cv.sparkle(30, 26, "#d9c4ff", r=1.5)
    cv.layer("#b38cf2", lambda d: blade(d, 13, 23, 32, 4, 6))
    cv.layer("#e6d6ff", lambda d: d.line(15, 21, 28, 8, w=1), shade=False)
    cv.layer(GOLD_D, lambda d: d.poly((8, 20), (11, 17), (19, 25), (16, 28)))
    cv.layer("#3a2a52", lambda d: d.poly((4, 30), (6, 32), (14, 24), (12, 22)))


def shadow_illusionist(cv):
    def mask(d, ox):
        d.poly((5 + ox, 6), (27 + ox, 6), (27 + ox, 16), (16 + ox, 31), (5 + ox, 16))
    cv.layer("#7a5ab0", lambda d: mask(d, 4))
    cv.layer("#eeeaf2", lambda d: mask(d, 0))
    cv.layer("#1d1d22", lambda d: (d.poly((8, 12), (15, 12), (14, 16), (9, 16)), d.poly((17, 12), (24, 12), (23, 16), (18, 16)),
                                     d.poly((12, 21), (20, 21), (16, 25))), shade=False)


def shadow_walker(cv):
    cv.layer("#1f4a46", lambda d: d.circle(18, 18, 14))
    cv.layer("#2f8c7e", lambda d: d.circle(18, 18, 9), shade=False)
    cv.layer("#9be8c8", lambda d: d.ellipse(10, 14, 26, 22), shade=False)
    cv.layer("#0d1f1d", lambda d: d.ellipse(15, 14, 21, 22), shade=False)


def agile_scout(cv):
    cv.layer("#f2f2f2", lambda d: d.poly((30, 3), (33, 6), (17, 25), (8, 29), (12, 20)))
    cv.layer("#b0b8c0", lambda d: d.line(31, 5, 5, 31, w=1), shade=False)
    cv.layer("#7cc8a0", lambda d: d.poly((23, 10), (27, 13), (21, 20), (15, 19)), shade=False)


def beast_master(cv):
    c = "#5a3a22"
    cv.layer(c, lambda d: (d.ellipse(9, 17, 27, 32), d.ellipse(4, 12, 11, 20), d.ellipse(10, 4, 17, 13),
                           d.ellipse(19, 4, 26, 13), d.ellipse(25, 12, 32, 20)))


def sniper(cv):
    cv.layer(STEEL, lambda d: d.arc(4, 4, 32, 32, 0, 360, w=3))
    cv.layer("#9fd8ff", lambda d: d.circle(18, 18, 11), shade=False)
    cv.layer("#1d2a3a", lambda d: (d.rect(17, 7, 19, 15), d.rect(17, 21, 19, 29), d.rect(7, 17, 15, 19), d.rect(21, 17, 29, 19)), shade=False)
    cv.layer(RED, lambda d: d.rect(17, 17, 19, 19), shade=False)


def breeze_master(cv):
    c = "#d6f4ff"
    cv.layer(c, lambda d: (d.arc(4, 4, 30, 30, 200, 520, w=3), d.arc(10, 10, 24, 24, 20, 300, w=3),
                           d.line(2, 26, 14, 26, w=3), d.line(22, 31, 34, 31, w=2)))


def heavy_palm(cv):
    cv.layer(FIRE2, lambda d: star(d, 18, 18, 16, 7, 8, rot=-90))
    cv.layer(FIRE1, lambda d: star(d, 18, 18, 9, 4, 8, rot=-67.5))
    cv.layer("#fff3c4", lambda d: d.circle(18, 18, 3), shade=False)


def triworld_guardian(cv):
    cv.sparkle(31, 6, r=1.5); cv.sparkle(4, 8)
    cv.layer(GOLD_D, lambda d: (d.line(18, 9, 9, 25, w=2), d.line(18, 9, 27, 25, w=2), d.line(9, 25, 27, 25, w=2)))
    cv.layer("#5aa02d", lambda d: d.circle(18, 9, 6.5))
    cv.layer("#d9361c", lambda d: d.circle(9, 26, 6.5))
    cv.layer("#9a4ad9", lambda d: d.circle(27, 26, 6.5))


# ---------------------------------------------------------------- razas (caras)

def face(cv, skin, hair=None, eyes="#1d1d22", ears=None, extra_back=None, extra_front=None, mouth="#5a2a22"):
    if extra_back:
        extra_back(cv)
    if ears:
        cv.layer(skin, ears)
    cv.layer(skin, lambda d: (d.ellipse(9, 7, 27, 31)))
    if hair:
        cv.layer(hair[0], hair[1])
    cv.layer(eyes, lambda d: (d.rect(13, 17, 16, 20), d.rect(20, 17, 23, 20)), shade=False)
    cv.layer(mouth, lambda d: d.rect(15, 25, 21, 26), shade=False)
    if extra_front:
        extra_front(cv)


def human(cv):
    face(cv, "#e0ac7e", hair=("#6b4226", lambda d: (d.ellipse(8, 5, 28, 17), d.rect(8, 10, 11, 20), d.rect(25, 10, 28, 20))),
         ears=lambda d: (d.ellipse(6, 16, 11, 23), d.ellipse(25, 16, 30, 23)))


def orc(cv):
    face(cv, "#6f9a3c", hair=("#1d1d22", lambda d: (d.ellipse(13, 3, 23, 11))),
         ears=lambda d: (d.poly((9, 15), (3, 12), (9, 22)), d.poly((27, 15), (33, 12), (27, 22))),
         mouth="#2a3a14",
         extra_front=lambda cv: (cv.layer("#2a3a14", lambda d: (d.rect(12, 15, 17, 16), d.rect(19, 15, 24, 16)), shade=False),
                                 cv.layer("#f4eee0", lambda d: (d.poly((13, 27), (15, 27), (14, 21)), d.poly((21, 27), (23, 27), (22, 21))))))


def dwarf(cv):
    face(cv, "#e0ac7e", ears=lambda d: (d.ellipse(6, 15, 11, 22), d.ellipse(25, 15, 30, 22)),
         hair=(STEEL, lambda d: (d.ellipse(7, 3, 29, 17), d.rect(7, 11, 29, 14))),
         extra_front=lambda cv: (cv.layer("#c06a2a", lambda d: d.poly((9, 21), (27, 21), (26, 30), (18, 35), (10, 30))),
                                 cv.layer("#5a2a22", lambda d: d.rect(15, 23, 21, 24), shade=False),
                                 cv.layer(STEEL_D, lambda d: d.rect(17, 3, 19, 14), shade=False)))


def elf(cv):
    face(cv, "#f0d0b0", hair=("#f2d16b", lambda d: (d.ellipse(8, 4, 28, 15), d.rect(8, 9, 11, 30), d.rect(25, 9, 28, 30))),
         ears=lambda d: (d.poly((10, 17), (1, 9), (10, 22)), d.poly((26, 17), (35, 9), (26, 22))), eyes="#2f8a6b")


def goblin(cv):
    face(cv, "#7bb33a", ears=lambda d: (d.poly((10, 14), (0, 6), (2, 14), (10, 21)), d.poly((26, 14), (36, 6), (34, 14), (26, 21))),
         eyes="#ffe14a", mouth="#2a3a14",
         extra_front=lambda cv: (cv.layer("#1d1d22", lambda d: (d.rect(14, 18, 15, 20), d.rect(21, 18, 22, 20)), shade=False),
                                 cv.layer("#6a9a30", lambda d: d.poly((18, 18), (21, 24), (15, 24)))))


def triton(cv):
    fin = "#2f8fae"
    face(cv, "#5fb3c9", eyes="#0d2a3a", mouth="#1f4a5a",
         extra_back=lambda cv: cv.layer(fin, lambda d: (d.poly((12, 9), (18, 0), (24, 9)), d.poly((10, 14), (1, 10), (4, 20), (10, 24)),
                                                         d.poly((26, 14), (35, 10), (32, 20), (26, 24)))),
         extra_front=lambda cv: cv.layer("#3f93ab", lambda d: (d.rect(11, 23, 13, 24), d.rect(23, 23, 25, 24), d.rect(11, 26, 13, 27),
                                                                 d.rect(23, 26, 25, 27)), shade=False))


def infernal(cv):
    face(cv, "#c9452e", eyes="#ffd34a", mouth="#4a1010",
         extra_back=lambda cv: cv.layer("#2a2024", lambda d: (d.poly((10, 12), (3, 2), (8, 2), (15, 9)), d.poly((26, 12), (33, 2), (28, 2), (21, 9)))),
         ears=lambda d: (d.poly((10, 16), (4, 14), (10, 21)), d.poly((26, 16), (32, 14), (26, 21))))


def undead(cv):
    skull(cv, eye_color="#5ad97a", s=0.95)


# ---------------------------------------------------------------- subrazas

def polyvalent(cv):
    cv.sparkle(31, 31, r=1.5)
    cv.layer(GOLD, lambda d: star(d, 18, 18, 16, 4, 4))
    cv.layer(STEEL, lambda d: star(d, 18, 18, 10, 3, 4, rot=-45))
    cv.layer(RED, lambda d: d.circle(18, 18, 3))


def specialist(cv):
    cv.layer("#f4eee0", lambda d: d.circle(18, 18, 15))
    cv.layer(RED, lambda d: d.circle(18, 18, 11))
    cv.layer("#f4eee0", lambda d: d.circle(18, 18, 7))
    cv.layer(RED, lambda d: d.circle(18, 18, 3.5))


def autodidact(cv):
    cv.layer("#6b3a1f", lambda d: d.poly((2, 11), (18, 13), (34, 11), (34, 30), (18, 32), (2, 30)))
    cv.layer("#f4eee0", lambda d: (d.poly((4, 8), (17, 10), (17, 29), (4, 27)), d.poly((19, 10), (32, 8), (32, 27), (19, 29))))
    cv.layer("#8a8478", lambda d: [d.rect(7, y, 15, y + 1) for y in (13, 17, 21)] + [d.rect(21, y, 29, y + 1) for y in (13, 17, 21)], shade=False)


def stoneskin(cv):
    cv.layer("#8a8f99", lambda d: d.poly((5, 8), (16, 3), (31, 7), (33, 26), (20, 33), (4, 27)))
    cv.layer("#4a4f58", lambda d: (d.line(13, 6, 18, 17, w=1), d.line(18, 17, 12, 25, w=1), d.line(18, 17, 28, 20, w=1)), shade=False)
    cv.layer("#b4b9c2", lambda d: (d.rect(22, 9, 25, 12), d.rect(8, 15, 10, 17)), shade=False)


def devourer(cv):
    cv.layer(BONE, lambda d: (d.line(8, 28, 17, 19, w=4), d.circle(6, 28, 3), d.circle(9, 31, 3)))
    cv.layer("#a8502a", lambda d: d.ellipse(12, 3, 33, 25))
    cv.layer("#d9783e", lambda d: d.ellipse(17, 7, 28, 16), shade=False)


def warlord(cv):
    cv.layer("#e4d6b0", lambda d: d.poly((3, 32), (4, 26), (8, 19), (14, 13), (21, 9), (28, 6), (33, 8), (34, 17), (29, 18), (22, 19),
                                         (16, 22), (11, 26), (7, 32)))
    cv.layer("#6b3a1f", lambda d: (d.poly((8, 19), (11, 17), (14, 24), (11, 26)), d.poly((19, 10), (23, 9), (24, 18), (20, 19))))
    cv.layer("#3a2a20", lambda d: d.ellipse(29, 8, 35, 18), shade=False)


def deep(cv):
    cv.sparkle(31, 5, "#d6fbff"); cv.sparkle(4, 6, "#d6fbff", r=1.5)
    cv.layer("#2fd4e0", lambda d: d.poly((18, 2), (26, 12), (18, 34), (10, 12)))
    cv.layer("#1d8fa8", lambda d: d.poly((8, 14), (13, 20), (9, 32), (4, 20)))
    cv.layer("#1d8fa8", lambda d: d.poly((28, 14), (32, 20), (27, 32), (23, 20)))
    cv.layer("#d6fbff", lambda d: d.poly((18, 6), (21, 12), (18, 16), (15, 12)), shade=False)


def smith(cv):
    cv.layer("#3a3a42", lambda d: (d.poly((4, 20), (32, 20), (30, 25), (6, 25)), d.rect(13, 25, 23, 29), d.rect(9, 29, 27, 33)))
    cv.layer(WOOD, lambda d: d.line(9, 17, 24, 2, w=3))
    cv.layer(STEEL, lambda d: d.poly((19, 1), (29, 11), (32, 8), (22, -2)))


def demolisher(cv):
    cv.layer("#2a2a32", lambda d: d.circle(16, 21, 12))
    cv.layer("#4a4a55", lambda d: d.rect(21, 7, 28, 13))
    cv.layer(WOOD, lambda d: d.line(26, 8, 31, 3, w=2), shade=False)
    cv.layer(FIRE1, lambda d: star(d, 32, 3, 4, 1.5, 4), shade=False)
    cv.layer("#ffffff", lambda d: d.rect(10, 15, 13, 18), shade=False)


def dark_elf(cv):
    def moon(d):
        d.circle(16, 18, 14)
        d.erase_circle(24, 14, 12)
    cv.layer("#b49ae8", moon)
    cv.layer("#ffffff", lambda d: (star(d, 27, 22, 5, 1.6), star(d, 22, 30, 3, 1)), shade=False)


def arcane_elf(cv):
    cv.sparkle(5, 30, "#e6d6ff", r=1.5); cv.sparkle(31, 31, "#e6d6ff")
    cv.layer(PURPLE, lambda d: star(d, 18, 18, 16, 5, 4))
    cv.layer("#c9b0ff", lambda d: star(d, 18, 18, 11, 4, 4, rot=-45))
    cv.layer("#ffffff", lambda d: d.circle(18, 18, 3), shade=False)


def sylvan(cv):
    cv.layer("#4caf3a", lambda d: d.poly((5, 31), (6, 18), (14, 8), (31, 4), (28, 20), (19, 29)))
    cv.layer("#2a6b1f", lambda d: (d.line(5, 31, 27, 8, w=2), d.line(13, 22, 11, 14, w=1), d.line(18, 17, 24, 19, w=1)), shade=False)


def wolf_rider(cv):
    g = "#9a9aa4"
    cv.layer(g, lambda d: (d.poly((6, 14), (8, 2), (15, 10)), d.poly((30, 14), (28, 2), (21, 10)), d.ellipse(6, 7, 30, 29)))
    cv.layer("#d8d8de", lambda d: d.poly((12, 20), (24, 20), (22, 33), (14, 33)))
    cv.layer("#1d1d22", lambda d: (d.rect(16, 29, 20, 33), d.rect(12, 15, 15, 18), d.rect(21, 15, 24, 18)), shade=False)


def thief(cv):
    cv.layer("#8a5a32", lambda d: (d.ellipse(5, 11, 31, 34), d.poly((12, 4), (24, 4), (21, 12), (15, 12))))
    cv.layer("#d8c8a0", lambda d: d.rect(13, 11, 23, 14))
    cv.layer("#5a3a22", lambda d: d.rect(15, 20, 21, 23), shade=False)
    cv.layer(GOLD, lambda d: d.circle(28, 28, 5))
    cv.layer(GOLD_D, lambda d: d.rect(27, 25, 29, 31), shade=False)


def trapper(cv):
    cv.layer(STEEL, lambda d: d.arc(3, 3, 33, 33, 0, 360, w=4))
    def teeth(d):
        for i in range(10):
            a = math.radians(i * 36)
            ca, sa = math.cos(a), math.sin(a)
            base = 13
            d.poly((18 + base * ca - 2.4 * sa, 18 + base * sa + 2.4 * ca), (18 + base * ca + 2.4 * sa, 18 + base * sa - 2.4 * ca),
                   (18 + 7.5 * ca, 18 + 7.5 * sa))
    cv.layer(STEEL_D, teeth)
    cv.layer("#5c3a1f", lambda d: d.circle(18, 18, 4))
    cv.layer("#8a6a3a", lambda d: d.circle(18, 18, 2), shade=False)


def fisher(cv):
    cv.layer("#f08a3a", lambda d: (d.ellipse(4, 10, 26, 26), d.poly((24, 18), (33, 10), (33, 26))))
    cv.layer("#ffd0a0", lambda d: d.ellipse(8, 18, 20, 24), shade=False)
    cv.layer("#1d1d22", lambda d: d.rect(9, 15, 11, 17), shade=False)


def shaper(cv):
    cv.layer("#3a8fd9", lambda d: (d.poly((2, 34), (2, 24), (8, 14), (17, 8), (27, 8), (33, 14), (24, 13), (19, 18), (22, 24), (34, 24), (34, 34))))
    cv.layer("#bfe6ff", lambda d: (d.poly((17, 8), (27, 8), (33, 14), (24, 12))), shade=False)


def abyssal(cv):
    cv.layer("#4f9a8a", lambda d: d.rect(5, 5, 31, 31))
    cv.layer("#2f6f62", lambda d: [d.rect(x, 1, x + 3, 5) for x in (8, 16.5, 25)] + [d.rect(1, y, 5, y + 3) for y in (12, 22)] +
             [d.rect(31, y, 35, y + 3) for y in (12, 22)])
    cv.layer("#f4eee0", lambda d: d.ellipse(9, 12, 27, 24), shade=False)
    cv.layer("#d97a2a", lambda d: d.circle(18, 18, 4.5), shade=False)
    cv.layer("#1d1d22", lambda d: d.rect(17, 16, 19, 20), shade=False)


def native(cv):
    cv.sparkle(31, 6); cv.sparkle(5, 4, r=1.5)
    def ingot(d, y):
        d.poly((6, y + 8), (10, y), (26, y), (30, y + 8))
    cv.layer(GOLD, lambda d: ingot(d, 20))
    cv.layer(GOLD, lambda d: ingot(d, 10))
    cv.layer("#fff3c4", lambda d: (d.rect(11, 11, 22, 12), d.rect(11, 21, 22, 22)), shade=False)


def smelter(cv):
    cv.layer("#7a7a82", lambda d: d.rect(4, 4, 32, 32))
    cv.layer("#3a3a42", lambda d: (d.rect(9, 9, 27, 12), d.rect(10, 18, 26, 29)), shade=False)
    flame(cv, x=0, y=6, s=0.45)


def pyromaniac(cv):
    flame(cv)


def vampire(cv):
    w = "#3a2a3a"
    cv.layer(w, lambda d: (d.poly((14, 14), (2, 6), (4, 14), (1, 20), (7, 19), (9, 25), (14, 21)),
                           d.poly((22, 14), (34, 6), (32, 14), (35, 20), (29, 19), (27, 25), (22, 21))))
    cv.layer("#5a3a52", lambda d: (d.ellipse(12, 11, 24, 29), d.poly((13, 13), (13, 7), (16, 11)), d.poly((23, 13), (23, 7), (20, 11))))
    cv.layer("#ff2a2a", lambda d: (d.rect(14, 16, 16, 18), d.rect(20, 16, 22, 18)), shade=False)
    cv.layer("#ffffff", lambda d: (d.poly((15, 23), (17, 23), (16, 26)), d.poly((19, 23), (21, 23), (20, 26))), shade=False)


def skeleton(cv):
    def bone(d, x0, y0, x1, y1):
        d.line(x0, y0, x1, y1, w=4)
        for x, y in ((x0, y0), (x1, y1)):
            dx, dy = (x1 - x0), (y1 - y0)
            L = math.hypot(dx, dy)
            px, py = -dy / L * 2.6, dx / L * 2.6
            d.circle(x + px, y + py, 2.8)
            d.circle(x - px, y - py, 2.8)
    cv.layer(BONE, lambda d: bone(d, 7, 7, 29, 29))
    cv.layer(BONE, lambda d: bone(d, 29, 7, 7, 29))


def specter(cv):
    cv.sparkle(3, 10, "#d6e6f2", r=1.5); cv.sparkle(33, 18, "#d6e6f2", r=1)
    cv.layer("#d6e6f2", lambda d: (d.ellipse(7, 3, 29, 25), d.rect(7, 14, 29, 28), d.poly((7, 27), (7, 34), (12, 29), (16, 34), (20, 29),
                                                                                         (24, 34), (29, 29), (29, 27))))
    cv.layer("#1d2a3a", lambda d: (d.ellipse(11, 11, 16, 18), d.ellipse(20, 11, 25, 18), d.ellipse(15, 20, 21, 25)), shade=False)


# ---------------------------------------------------------------- catálogo

CLASS_COLOR = {"mage": "#4b3f9e", "paladin": "#b8922a", "barbarian": "#8e2b25", "rogue": "#3a3a4a", "ranger": "#3d7a35", "monk": "#b36a24"}
SUBCLASSES = {
    "mage": {"battle_mage": battle_mage, "chaotic_evoker": chaotic_evoker, "elementalist": elementalist},
    "paladin": {"blessed_guardian": blessed_guardian, "charging_juggernaut": charging_juggernaut, "unyielding_bastion": unyielding_bastion},
    "barbarian": {"indestructible": indestructible, "plague_corruptor": plague_corruptor, "suicidal_berserker": suicidal_berserker},
    "rogue": {"arcane_blade": arcane_blade, "shadow_illusionist": shadow_illusionist, "shadow_walker": shadow_walker},
    "ranger": {"agile_scout": agile_scout, "beast_master": beast_master, "sniper": sniper},
    "monk": {"breeze_master": breeze_master, "heavy_palm": heavy_palm, "triworld_guardian": triworld_guardian},
}
CLASSES = {"mage": mage, "paladin": paladin, "barbarian": barbarian, "rogue": rogue, "ranger": ranger, "monk": monk}
RACE_COLOR = {"human": "#4f7397", "orc": "#4a6326", "dwarf": "#7a4e2c", "elf": "#2a7a5f", "goblin": "#5f6c1c",
              "triton": "#1c6680", "infernal": "#7d1c1c", "undead": "#43434f"}
RACES = {"human": human, "orc": orc, "dwarf": dwarf, "elf": elf, "goblin": goblin, "triton": triton, "infernal": infernal, "undead": undead}
SUBRACES = {
    "human": {"polyvalent": polyvalent, "specialist": specialist, "autodidact": autodidact},
    "orc": {"stoneskin": stoneskin, "devourer": devourer, "warlord": warlord},
    "dwarf": {"deep": deep, "smith": smith, "demolisher": demolisher},
    "elf": {"dark": dark_elf, "arcane": arcane_elf, "sylvan": sylvan},
    "goblin": {"wolf_rider": wolf_rider, "thief": thief, "trapper": trapper},
    "triton": {"fisher": fisher, "shaper": shaper, "abyssal": abyssal},
    "infernal": {"native": native, "smelter": smelter, "pyromaniac": pyromaniac},
    "undead": {"vampire": vampire, "skeleton": skeleton, "specter": specter},
}


# ---------------------------------------------------------------- marco y composición

def frame(color, accent=None):
    """Medallón de 36x36: esquinas cortadas, borde oscuro, filo claro y degradado vertical."""
    from PIL import Image
    base = hexrgb(color)
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    cut = 3
    for y in range(SIZE):
        for x in range(SIZE):
            # distancia a la esquina para cortarla en diagonal
            dx = min(x, SIZE - 1 - x)
            dy = min(y, SIZE - 1 - y)
            if dx + dy < cut:
                continue
            edge = min(dx, dy) == 0 or dx + dy == cut
            rim = (min(dx, dy) == 1 or dx + dy == cut + 1) and not edge
            inner = (min(dx, dy) == 2 or dx + dy == cut + 2) and not edge and not rim
            if edge:
                c = (20, 18, 22)
            elif rim:
                c = light(hexrgb(accent) if accent else base, 0.45) if (x + y) < SIZE else dark(base, 0.25)
            elif inner:
                c = dark(base, 0.35)
            else:
                t = y / (SIZE - 1)
                c = light(base, 0.18 * (1 - t)) if t < 0.5 else dark(base, 0.3 * (t - 0.5) * 2)
            px[x, y] = c + (255,)
    return img


def portrait(color, glyph, accent=None):
    """Sin marco (estilo de iconos sueltos): el glifo ocupa casi todo el lienzo, con contorno teñido y destellos."""
    cv = Canvas(SIZE, k=0.9, off=1.8)
    glyph(cv)
    cv.finish()
    return cv.img


def all_portraits():
    """(ruta relativa dentro de textures/gui/portrait, imagen)."""
    out = []
    for cid, fn in CLASSES.items():
        out.append((f"class/{cid}.png", portrait(CLASS_COLOR[cid], fn, GOLD)))
        for sid, sfn in SUBCLASSES[cid].items():
            out.append((f"subclass/{sid}.png", portrait(CLASS_COLOR[cid], sfn)))
    for rid, fn in RACES.items():
        out.append((f"race/{rid}.png", portrait(RACE_COLOR[rid], fn, GOLD)))
        for sid, sfn in SUBRACES[rid].items():
            out.append((f"subrace/{rid}/{sid}.png", portrait(RACE_COLOR[rid], sfn)))
    return out


def all_effects():
    out = []
    for name, fn in glyphs.EFFECTS.items():
        cv = Canvas(18, k=0.47, off=0.55)
        fn(cv)
        cv.finish()
        out.append((name + ".png", cv.img))
    return out


if __name__ == "__main__":
    res = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources"
    tex = os.path.join(res, "assets", "rpgclasses", "textures")
    for rel, img in all_portraits():
        path = os.path.join(tex, "gui", "portrait", rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
    for rel, img in all_effects():
        img.save(os.path.join(tex, "mob_effect", rel))
    print("ok")
