import sys
from PIL import Image
sys.path.insert(0, '.')
from pix import Canvas
import glyphs

def sheet(items, size, scale, out, bg=(70, 70, 70, 255)):
    pad = 4
    cols = min(len(items), 13)
    rows = (len(items) + cols - 1) // cols
    W = Image.new("RGBA", (cols * (size * scale + pad), rows * (size * scale + pad)), bg)
    for i, im in enumerate(items):
        big = im.resize((size * scale, size * scale), Image.NEAREST)
        W.paste(big, ((i % cols) * (size * scale + pad), (i // cols) * (size * scale + pad)), big)
    W.save(out)

if __name__ == "__main__":
    ims = []
    for name, fn in glyphs.EFFECTS.items():
        cv = Canvas(18)
        fn(cv)
        cv.outline()
        ims.append(cv.img)
    sheet(ims, 18, 6, "/tmp/prev/fx.png")
    print(len(ims))
