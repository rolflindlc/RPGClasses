# Textura del Amuleto del Creativo (16x16): cordón dorado y gema magenta con un reloj.
# Uso: python3 -I tools/art/make_creative_amulet.py src/main/resources/assets/rites_and_paths/textures/item/creative_amulet.png
import sys
from PIL import Image
im = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); px = im.load()
gold, goldd = (232, 190, 80, 255), (160, 116, 40, 255)
# cordón
for x, y in [(3,1),(4,2),(5,3),(6,4),(12,1),(11,2),(10,3),(9,4),(4,1),(11,1),(7,5),(8,5)]:
    px[x, y] = goldd if y % 2 else gold
# marco
ring = [(6,6),(7,6),(8,6),(9,6),(5,7),(10,7),(4,8),(11,8),(4,9),(11,9),(4,10),(11,10),(4,11),(11,11),(5,12),(10,12),(6,13),(7,13),(8,13),(9,13)]
for x, y in ring: px[x, y] = gold if y < 10 else goldd
# gema
for y in range(7, 13):
    for x in range(5, 11):
        if (x, y) in ring: continue
        if (x - 7.5) ** 2 + (y - 9.5) ** 2 <= 9:
            px[x, y] = (200, 70, 220, 255) if y < 10 else (140, 40, 170, 255)
# agujas del reloj y brillo
px[7, 9] = px[7, 8] = (255, 240, 255, 255); px[8, 9] = (255, 240, 255, 255); px[6, 8] = (255, 200, 255, 255)
im.save(sys.argv[1])
