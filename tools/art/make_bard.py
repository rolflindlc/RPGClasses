# Texturas provisionales del Bardo: instrumentos, materiales, partitura y nota (16x16), iconos de conjuro (16x16),
# iconos de efecto (18x18) y retratos de la clase y sus subclases (32x32 duplicados a 64x64).
# Uso: python3 -I tools/art/make_bard.py src/main/resources
import math, os, sys
from PIL import Image

OUT = sys.argv[1]
NS = "rites_and_paths"
TEX = os.path.join(OUT, "assets", NS, "textures")
for d in ("item", "gui/spell_icons", "mob_effect", "gui/portrait/class", "gui/portrait/subclass"):
    os.makedirs(os.path.join(TEX, d), exist_ok=True)

T = (0, 0, 0, 0)


def rgb(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)


def shade(c, f):
    return tuple([max(0, min(255, int(v * f))) for v in c[:3]] + [c[3]])


class Canvas:
    def __init__(self, n):
        self.n = n
        self.im = Image.new("RGBA", (n, n), T)
        self.px = self.im.load()

    def set(self, x, y, c):
        x, y = int(round(x)), int(round(y))
        if 0 <= x < self.n and 0 <= y < self.n:
            self.px[x, y] = c

    def get(self, x, y):
        return self.px[x, y] if 0 <= x < self.n and 0 <= y < self.n else T

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def ellipse(self, cx, cy, rx, ry, c, light=None):
        for y in range(self.n):
            for x in range(self.n):
                dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
                d = dx * dx + dy * dy
                if d <= 1:
                    col = c
                    if light is not None and dx < -0.15 and dy < -0.15 and d < 0.55:
                        col = light
                    self.px[x, y] = col

    def line(self, x0, y0, x1, y1, c, w=1):
        steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
        for i in range(steps + 1):
            t = i / steps
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            for ox in range(w):
                for oy in range(w):
                    self.set(x + ox - (w - 1) / 2, y + oy - (w - 1) / 2, c)

    def outline(self, color=(28, 20, 16, 255)):
        src = [[self.px[x, y] for y in range(self.n)] for x in range(self.n)]
        for y in range(self.n):
            for x in range(self.n):
                if src[x][y][3] != 0:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < self.n and 0 <= ny < self.n and src[nx][ny][3] != 0 and src[nx][ny] != color:
                        self.px[x, y] = color
                        break

    def save(self, path, scale=None):
        im = self.im if scale is None else self.im.resize((scale, scale), Image.NEAREST)
        im.save(path)


WOOD, WOOD_D, WOOD_L = rgb(0x9C6B3C), rgb(0x6B4423), rgb(0xC48B54)
STRING = rgb(0xEDE6D0)
GOLD, GOLD_D = rgb(0xE8B931), rgb(0xA87A16)
SILVER, SILVER_D = rgb(0xCDD3DA), rgb(0x8A929C)
DARK = rgb(0x1C1410)


def item(name):
    return os.path.join(TEX, "item", name + ".png")


# ------------------------------------------------------------------ instrumentos

def lute(body=WOOD, body_l=WOOD_L, neck=WOOD_D, accent=None):
    c = Canvas(16)
    c.line(8, 8, 13, 2, neck, 2)
    c.line(13, 2, 15, 1, neck, 1)
    c.ellipse(5.5, 10.5, 4.6, 4.6, body, body_l)
    c.set(5, 10, DARK)
    c.set(6, 10, DARK)
    c.line(3, 13, 13, 3, STRING)
    if accent:
        for (x, y) in ((2, 9), (8, 13), (4, 14)):
            c.set(x, y, accent)
    c.outline()
    return c


def flute(col=WOOD_L, col_d=WOOD):
    c = Canvas(16)
    c.line(2, 14, 13, 3, col, 2)
    for t in (0.45, 0.58, 0.71, 0.84):
        c.set(2 + 11 * t, 14 - 11 * t, DARK)
    c.line(12, 4, 14, 2, col_d, 2)
    c.outline()
    return c


def harp(frame=WOOD, frame_l=WOOD_L, strings=STRING, gem=None):
    c = Canvas(16)
    for x in range(4, 14):
        top = 2 + int(6 * ((x - 4) / 9) ** 1.4)
        c.line(x, top + 1, x, 13, strings) if x % 2 == 0 else None
    c.line(3, 2, 3, 14, frame, 2)
    for x in range(3, 15):
        c.set(x, 2 + int(6 * ((x - 3) / 11) ** 1.4), frame_l)
        c.set(x, 3 + int(6 * ((x - 3) / 11) ** 1.4), frame)
    c.line(3, 14, 13, 14, frame, 1)
    if gem:
        c.set(3, 2, gem)
        c.set(13, 8, gem)
    c.outline()
    return c


def drum(body=rgb(0xB8322A), skin=rgb(0xEADCC0), rim=WOOD, cracks=None):
    c = Canvas(16)
    c.rect(2, 6, 13, 12, body)
    c.ellipse(8, 12.5, 6, 2, shade(body, 0.7))
    c.ellipse(8, 6, 6, 2.2, skin)
    for i, x in enumerate(range(3, 13, 2)):
        c.line(x, 7, x + 1, 11, rim)
    c.line(2, 6, 13, 6, rim)
    if cracks:
        for (x, y) in ((4, 9), (5, 10), (9, 8), (10, 9), (11, 11)):
            c.set(x, y, cracks)
    c.outline()
    return c


def violin(body=rgb(0xA0461E), body_l=rgb(0xD06B32), neck=DARK, alpha=255):
    c = Canvas(16)
    b, bl = body[:3] + (alpha,), body_l[:3] + (alpha,)
    c.ellipse(5, 12, 3.4, 2.8, b, bl)
    c.ellipse(7.5, 8.8, 2.8, 2.4, b, bl)
    c.line(9, 7, 13, 2, neck[:3] + (alpha,), 1)
    c.set(14, 1, neck[:3] + (alpha,))
    c.set(13, 1, neck[:3] + (alpha,))
    c.set(5, 11, DARK)
    c.set(7, 9, DARK)
    c.line(4, 13, 12, 3, STRING[:3] + (alpha,))
    c.outline()
    return c


def lyre(frame=GOLD, frame_d=GOLD_D, base=WOOD):
    c = Canvas(16)
    c.line(4, 13, 3, 3, frame, 2)
    c.line(11, 13, 12, 3, frame, 2)
    c.set(2, 2, frame_d)
    c.set(13, 2, frame_d)
    c.line(3, 5, 12, 5, frame_d)
    for x in (5, 7, 9):
        c.line(x, 5, x, 12, STRING)
    c.rect(3, 13, 12, 14, base)
    c.outline()
    return c


def ocarina():
    c = Canvas(16)
    col = rgb(0x6E8CB5)
    c.ellipse(8, 9, 6, 4, col, shade(col, 1.25))
    c.rect(12, 6, 15, 8, col)
    for (x, y) in ((5, 8), (7, 9), (9, 8), (10, 10), (6, 11)):
        c.set(x, y, DARK)
    c.outline()
    return c


def pan_flute():
    c = Canvas(16)
    reed = rgb(0xC9B36A)
    for i, x in enumerate(range(3, 13, 2)):
        c.rect(x, 3, x + 1, 13 - i * 2, reed if i % 2 == 0 else shade(reed, 0.85))
        c.set(x, 3, DARK)
    c.rect(3, 6, 12, 7, rgb(0x8A2A2A))
    c.outline()
    return c


def tambourine():
    c = Canvas(16)
    c.ellipse(8, 8, 6.5, 6.5, WOOD)
    c.ellipse(8, 8, 4.8, 4.8, rgb(0xEADCC0))
    for a in range(0, 360, 60):
        r = math.radians(a)
        c.set(8 + 6 * math.cos(r), 8 + 6 * math.sin(r), GOLD)
    c.outline()
    return c


def bagpipe():
    c = Canvas(16)
    c.ellipse(7, 10, 5, 4, rgb(0x2E7D3A), rgb(0x4CA85A))
    for y in range(7, 14, 2):
        c.line(3, y, 11, y, rgb(0xB8322A))
    c.line(9, 7, 13, 1, DARK, 1)
    c.line(7, 7, 9, 1, DARK, 1)
    c.line(5, 7, 5, 2, DARK, 1)
    c.line(4, 13, 2, 15, WOOD_D)
    c.outline()
    return c


def carillon():
    c = Canvas(16)
    c.line(1, 3, 14, 3, SILVER_D, 2)
    for x in (3, 7, 11):
        c.line(x + 1, 4, x + 1, 5, SILVER_D)
        c.rect(x, 6, x + 2, 9, GOLD)
        c.rect(x - 1, 10, x + 3, 10, GOLD_D)
        c.set(x + 1, 11, DARK)
    c.outline()
    return c


def hurdy_gurdy(body=WOOD, body_l=WOOD_L, keys=SILVER):
    c = Canvas(16)
    c.ellipse(7, 9, 6, 4.5, body, body_l)
    c.rect(4, 6, 11, 8, shade(body, 0.75))
    for x in range(5, 11, 2):
        c.set(x, 7, keys)
    c.line(13, 9, 15, 12, SILVER_D)
    c.set(15, 13, DARK)
    c.outline()
    return c


def war_horn():
    c = Canvas(16)
    horn = rgb(0xE6DCC4)
    for i in range(12):
        t = i / 11
        x = 2 + 11 * t
        y = 12 - 9 * math.sin(t * math.pi * 0.55)
        w = 1 + int(2.5 * t)
        c.ellipse(x, y, w, w, horn)
    for t in (0.35, 0.7):
        x = 2 + 11 * t
        y = 12 - 9 * math.sin(t * math.pi * 0.55)
        c.line(x - 1, y - 2, x + 1, y + 2, GOLD)
    c.outline()
    return c


def organ(pipe=rgb(0x2B6E78), pipe_l=rgb(0x58B3BE)):
    c = Canvas(16)
    heights = [8, 5, 3, 2, 3, 5, 8]
    for i, h in enumerate(heights):
        x = 1 + i * 2
        c.rect(x, h, x + 1, 12, pipe if i % 2 else pipe_l)
        c.set(x, h + 2, DARK)
    c.rect(1, 12, 14, 14, rgb(0x1C2E3A))
    c.set(4, 13, rgb(0x7FE0FF))
    c.set(11, 13, rgb(0x7FE0FF))
    c.outline()
    return c


def guitar_axe():
    c = Canvas(16)
    body = rgb(0xB8322A)
    c.ellipse(5, 11.5, 3.6, 3.4, body, rgb(0xE05A44))
    c.ellipse(6.8, 8.6, 2.6, 2.4, body, rgb(0xE05A44))
    c.set(5, 11, DARK)
    c.line(8, 7, 12, 3, DARK, 1)
    c.line(4, 13, 12, 4, STRING)
    # Hoja de hacha (netherita) en el clavijero.
    blade = rgb(0x4A4048)
    c.rect(11, 0, 15, 4, blade)
    c.line(15, 0, 15, 5, rgb(0x9A8C9A))
    c.set(11, 4, T)
    c.set(11, 0, T)
    c.outline()
    return c


def scale_lyre(col, col_d):
    c = lyre(col, col_d, WOOD_D)
    for (x, y) in ((3, 7), (12, 7), (3, 10), (12, 10)):
        c.set(x, y, shade(col, 1.35))
    return c


SPRITES = {
    "lute": lambda: lute(),
    "flute": lambda: flute(),
    "harp": lambda: harp(),
    "drum": lambda: drum(),
    "violin": lambda: violin(),
    "lyre": lambda: lyre(),
    "ocarina": ocarina,
    "pan_flute": pan_flute,
    "tambourine": tambourine,
    "bagpipe": bagpipe,
    "carillon": carillon,
    "hurdy_gurdy": lambda: hurdy_gurdy(),
    "crystal_harp": lambda: harp(rgb(0x5ED6E6), rgb(0xB8F4FA), rgb(0xD8C2FF), rgb(0xB06CF0)),
    "war_horn": war_horn,
    "spectral_violin": lambda: violin(rgb(0x6FD3E8), rgb(0xC9F6FF), rgb(0x5A4AA0), 200),
    "scale_lyre_fire": lambda: scale_lyre(rgb(0xD9502B), rgb(0x8A2A14)),
    "scale_lyre_ice": lambda: scale_lyre(rgb(0x86C5F0), rgb(0x3E7BB0)),
    "scale_lyre_lightning": lambda: scale_lyre(rgb(0x9B6BE8), rgb(0x5A3A9C)),
    "eternal_sun_lute": lambda: lute(GOLD, rgb(0xFFE88A), GOLD_D, rgb(0xFFF6C0)),
    "abyssal_organ": lambda: organ(),
    "colossus_drum": lambda: drum(rgb(0x3E3438), rgb(0x8A7A70), rgb(0x241E22), rgb(0xFF7A1A)),
    "guitar_axe": guitar_axe,
}

for name, f in SPRITES.items():
    f().save(item(name))

# ------------------------------------------------------------------ materiales, partitura y nota

def shard():
    c = Canvas(16)
    col, light = rgb(0x9B6BE8), rgb(0xD8C2FF)
    pts = [(8, 1), (12, 6), (10, 14), (6, 14), (4, 6)]
    for y in range(16):
        for x in range(16):
            inside = True
            for i in range(len(pts)):
                x0, y0 = pts[i]
                x1, y1 = pts[(i + 1) % len(pts)]
                if (x1 - x0) * (y + 0.5 - y0) - (y1 - y0) * (x + 0.5 - x0) < 0:
                    inside = False
                    break
            if inside:
                c.set(x, y, light if x < 8 and y < 8 else col)
    note(c, 7, 6, rgb(0x3A1F66))
    c.outline()
    return c


def note(c, x, y, col):
    c.rect(x, y + 4, x + 1, y + 5, col)
    c.line(x + 1, y, x + 1, y + 4, col)
    c.set(x + 2, y, col)
    c.set(x + 3, y + 1, col)


def thread():
    c = Canvas(16)
    c.rect(4, 3, 11, 4, WOOD_D)
    c.rect(4, 12, 11, 13, WOOD_D)
    c.rect(5, 5, 10, 11, rgb(0x4E7BFF))
    for y in range(5, 12, 2):
        c.line(5, y, 10, y, rgb(0x9FC0FF))
    c.line(10, 8, 14, 14, rgb(0x4E7BFF))
    c.outline()
    return c


def reed():
    c = Canvas(16)
    c.line(3, 13, 12, 2, rgb(0x2B6E78), 2)
    c.line(4, 13, 13, 2, rgb(0x58B3BE))
    c.rect(2, 12, 4, 14, rgb(0x1C2E3A))
    c.set(12, 3, rgb(0x7FE0FF))
    c.outline()
    return c


def drumskin():
    c = Canvas(16)
    c.ellipse(8, 8, 6.5, 6.5, rgb(0x3E3438))
    c.ellipse(8, 8, 4.5, 4.5, rgb(0x8A7A70))
    for (x, y) in ((6, 6), (7, 7), (9, 9), (10, 10), (8, 11)):
        c.set(x, y, rgb(0xFF7A1A))
    c.outline()
    return c


def sheet():
    c = Canvas(16)
    paper, edge = rgb(0xF2E6C4), rgb(0xC9B48A)
    c.rect(2, 2, 13, 14, paper)
    c.rect(2, 2, 13, 2, edge)
    c.rect(2, 14, 13, 14, edge)
    for y in (5, 7, 9, 11):
        c.line(3, y, 12, y, rgb(0x6B5A44))
    note(c, 5, 4, DARK)
    note(c, 9, 6, DARK)
    c.outline()
    return c


def music_note():
    c = Canvas(16)
    col = rgb(0xF2C94C)
    c.ellipse(5.5, 12, 3, 2.3, col, rgb(0xFFF0A0))
    c.rect(7, 2, 8, 12, col)
    c.line(8, 2, 13, 5, col, 2)
    c.line(13, 5, 13, 7, col)
    c.outline(rgb(0x3A2A08))
    return c


for name, f in {"resonant_shard": shard, "arcane_thread": thread, "abyssal_reed": reed, "colossus_drumskin": drumskin,
                "sheet_music": sheet, "music_note": music_note}.items():
    f().save(item(name))

# ------------------------------------------------------------------ iconos de conjuro (16x16) y de efecto (18x18)

def framed(n, bg, border):
    c = Canvas(n)
    c.rect(0, 0, n - 1, n - 1, border)
    c.rect(1, 1, n - 2, n - 2, bg)
    for y in range(1, n - 1):
        for x in range(1, n - 1):
            t = (x + y) / (2 * n)
            c.px[x, y] = shade(bg, 1.25 - 0.5 * t)
    return c


def glyph_note(c, x, y, col, big=False):
    if big:
        c.ellipse(x + 1.5, y + 7, 2.2, 1.8, col)
        c.line(x + 3, y, x + 3, y + 7, col)
        c.line(x + 3, y, x + 6, y + 2, col)
    else:
        note(c, x, y, col)


W = rgb(0xFFFFFF)
SPELL_STYLE = {
    "cutting_note": (0x4A3A8A, 0x1C1430),
    "march": (0x2E8FA8, 0x0E2C36),
    "war_hymn": (0xB8322A, 0x3A0E0A),
    "rest_ballad": (0x3E9C4A, 0x10301A),
    "elegy": (0x3B4A7A, 0x10162A),
    "rumble": (0x9A6A2A, 0x2E1E08),
    "iron_chorale": (0x7A828C, 0x22262A),
    "mana_cadence": (0x2E5ACF, 0x0C1A40),
    "dissonance": (0x6A2C8E, 0x1E0A2A),
    "grand_piano": (0x2A2A2E, 0x0A0A0C),
    "forced_dance": (0xD0559A, 0x3A0E2A),
    "final_requiem": (0xC9A227, 0x3A2A08),
}


def spell_icon(name, n=16):
    bg, border = SPELL_STYLE[name]
    c = framed(n, rgb(bg), rgb(border))
    o = (n - 16) // 2
    if name == "cutting_note":
        c.line(3 + o, 12 + o, 12 + o, 3 + o, rgb(0xD8C2FF), 2)
        glyph_note(c, 4 + o, 3 + o, W, True)
    elif name == "march":
        for dx in (0, 4):
            c.line(4 + dx + o, 4 + o, 7 + dx + o, 8 + o, W)
            c.line(7 + dx + o, 8 + o, 4 + dx + o, 12 + o, W)
    elif name == "war_hymn":
        c.line(4 + o, 12 + o, 12 + o, 4 + o, rgb(0xE6E6E6), 1)
        c.line(4 + o, 9 + o, 7 + o, 12 + o, GOLD)
        glyph_note(c, 9 + o, 6 + o, rgb(0xFFD0A0))
    elif name == "rest_ballad":
        for (x, y) in ((5, 6), (6, 5), (7, 6), (8, 6), (9, 5), (10, 6)):
            c.set(x + o, y + o, rgb(0xFF6B7A))
        c.rect(5 + o, 7 + o, 10 + o, 8 + o, rgb(0xFF6B7A))
        c.rect(6 + o, 9 + o, 9 + o, 9 + o, rgb(0xFF6B7A))
        c.rect(7 + o, 10 + o, 8 + o, 10 + o, rgb(0xFF6B7A))
        glyph_note(c, 10 + o, 9 + o, W)
    elif name == "elegy":
        c.ellipse(8 + o, 9 + o, 2.5, 3, rgb(0x8FB8FF))
        c.set(8 + o, 5 + o, rgb(0x8FB8FF))
        c.set(8 + o, 4 + o, rgb(0x8FB8FF))
    elif name == "rumble":
        for r in (3, 5, 7):
            for a in range(-40, 41, 10):
                t = math.radians(a)
                c.set(3 + o + r * math.cos(t), 8 + o + r * math.sin(t), rgb(0xFFE0A0))
    elif name == "iron_chorale":
        c.rect(5 + o, 4 + o, 10 + o, 9 + o, rgb(0xD0D6DE))
        c.rect(6 + o, 10 + o, 9 + o, 11 + o, rgb(0xD0D6DE))
        c.rect(7 + o, 12 + o, 8 + o, 12 + o, rgb(0xD0D6DE))
        c.line(7 + o, 5 + o, 7 + o, 10 + o, rgb(0x8A929C))
    elif name == "mana_cadence":
        c.ellipse(8 + o, 10 + o, 3, 3, rgb(0x7FB0FF))
        c.line(8 + o, 4 + o, 8 + o, 7 + o, rgb(0x7FB0FF))
        c.set(7 + o, 6 + o, rgb(0x7FB0FF))
        c.set(9 + o, 6 + o, rgb(0x7FB0FF))
    elif name == "dissonance":
        pts = [(3, 8), (5, 5), (7, 11), (9, 4), (11, 10), (13, 7)]
        for i in range(len(pts) - 1):
            c.line(pts[i][0] + o, pts[i][1] + o, pts[i + 1][0] + o, pts[i + 1][1] + o, rgb(0xE6A0FF))
    elif name == "grand_piano":
        c.rect(3 + o, 7 + o, 12 + o, 10 + o, rgb(0x0A0A0C))
        c.rect(3 + o, 9 + o, 12 + o, 9 + o, W)
        c.line(3 + o, 7 + o, 9 + o, 3 + o, rgb(0x0A0A0C))
        c.line(4 + o, 11 + o, 4 + o, 13 + o, WOOD_D)
        c.line(11 + o, 11 + o, 11 + o, 13 + o, WOOD_D)
        c.rect(3 + o, 6 + o, 12 + o, 6 + o, rgb(0x55555A))
    elif name == "forced_dance":
        c.ellipse(8 + o, 4 + o, 1.5, 1.5, W)
        c.line(8 + o, 6 + o, 8 + o, 10 + o, W)
        c.line(8 + o, 7 + o, 5 + o, 5 + o, W)
        c.line(8 + o, 7 + o, 11 + o, 5 + o, W)
        c.line(8 + o, 10 + o, 6 + o, 13 + o, W)
        c.line(8 + o, 10 + o, 10 + o, 13 + o, W)
    elif name == "final_requiem":
        for a in range(0, 360, 45):
            t = math.radians(a)
            c.line(8 + o, 8 + o, 8 + o + 6 * math.cos(t), 8 + o + 6 * math.sin(t), rgb(0xFFF0A0))
        glyph_note(c, 6 + o, 4 + o, rgb(0x3A2A08), True)
    return c


for s in SPELL_STYLE:
    spell_icon(s).save(os.path.join(TEX, "gui", "spell_icons", s + ".png"))

EFFECTS = {"march": "march", "war_hymn": "war_hymn", "rest_ballad": "rest_ballad", "iron_chorale": "iron_chorale",
           "mana_cadence": "mana_cadence", "requiem_fervor": "final_requiem", "forced_dance": "forced_dance",
           "silenced": "dissonance", "charmed": "forced_dance"}
for eff, base in EFFECTS.items():
    spell_icon(base, 18).save(os.path.join(TEX, "mob_effect", eff + ".png"))


def effect_icon(color, symbol):
    c = framed(18, rgb(color), shade(rgb(color), 0.35))
    if symbol == "step":
        for dx in (0, 5):
            c.line(5 + dx, 5, 8 + dx, 9, W)
            c.line(8 + dx, 9, 5 + dx, 13, W)
    else:
        c.ellipse(9, 9, 4.5, 4.5, rgb(0xFFFFFF, 90))
        for (x, y) in ((7, 6), (11, 7), (6, 10), (10, 11), (8, 8)):
            c.set(x, y, W)
    return c


effect_icon(0xF2D16B, "step").save(os.path.join(TEX, "mob_effect", "shared_step.png"))
effect_icon(0xD9502B, "scale").save(os.path.join(TEX, "mob_effect", "scale_ward_fire.png"))
effect_icon(0x86C5F0, "scale").save(os.path.join(TEX, "mob_effect", "scale_ward_ice.png"))
effect_icon(0x9B6BE8, "scale").save(os.path.join(TEX, "mob_effect", "scale_ward_lightning.png"))

# ------------------------------------------------------------------ retratos (32x32 duplicados a 64x64)

def portrait(draw, bg):
    c = Canvas(32)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5) / 22
            c.px[x, y] = shade(rgb(bg), 1.15 - 0.6 * d)
    draw(c)
    return c


def big_lute(c, body=WOOD, light=WOOD_L):
    c.line(15, 15, 26, 4, WOOD_D, 3)
    c.line(26, 4, 29, 2, WOOD_D, 2)
    c.ellipse(11, 20, 8.5, 8.5, body, light)
    c.ellipse(11, 20, 2, 2, DARK)
    c.line(6, 25, 27, 4, STRING)
    c.line(7, 26, 28, 5, STRING)


def bard_portrait(c):
    big_lute(c)
    glyph_note(c, 22, 14, GOLD, True)
    glyph_note(c, 4, 4, rgb(0xFFF0A0), True)


def war_chanter_portrait(c):
    c.line(6, 26, 24, 8, rgb(0xD8DCE2), 3)
    c.line(5, 22, 10, 27, GOLD, 2)
    c.ellipse(24, 22, 5, 4, rgb(0xE6DCC4))
    c.line(19, 22, 28, 16, rgb(0xE6DCC4), 3)
    glyph_note(c, 22, 4, rgb(0xFF8A6A), True)


def enchanter_portrait(c):
    for r, col in ((10, 0x6A2C8E), (7, 0x9B6BE8), (4, 0xD8C2FF)):
        for a in range(0, 360, 6):
            t = math.radians(a + r * 20)
            c.set(16 + r * math.cos(t) * (1 + a / 720), 16 + r * math.sin(t) * (1 + a / 720), rgb(col))
    c.ellipse(16, 16, 3, 3, rgb(0xFFE6FF))
    glyph_note(c, 23, 3, rgb(0xFF9AE0), True)


def minstrel_portrait(c):
    hat = [(6, 18), (10, 6), (16, 14), (22, 6), (26, 18)]
    for i in range(len(hat) - 1):
        c.line(hat[i][0], hat[i][1], hat[i + 1][0], hat[i + 1][1], rgb(0xD0559A), 2)
    c.rect(6, 18, 26, 21, rgb(0x2E8FA8))
    for (x, y) in ((10, 5), (22, 5), (16, 13)):
        c.ellipse(x, y, 1.6, 1.6, GOLD)
    glyph_note(c, 4, 22, rgb(0xFFF0A0), True)
    glyph_note(c, 23, 22, rgb(0xFFF0A0), True)


def save_portrait(c, path):
    c.im.resize((64, 64), Image.NEAREST).save(path)  # pixel art 32x32 duplicado a 64x64, sin suavizado


save_portrait(portrait(bard_portrait, 0x3A2A4A), os.path.join(TEX, "gui", "portrait", "class", "bard.png"))
save_portrait(portrait(war_chanter_portrait, 0x4A2222), os.path.join(TEX, "gui", "portrait", "subclass", "war_chanter.png"))
save_portrait(portrait(enchanter_portrait, 0x2A1E3E), os.path.join(TEX, "gui", "portrait", "subclass", "enchanter.png"))
save_portrait(portrait(minstrel_portrait, 0x24363E), os.path.join(TEX, "gui", "portrait", "subclass", "minstrel.png"))
print("texturas del Bardo generadas")
