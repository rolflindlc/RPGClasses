# Retrato provisional del Cronomante (reloj de arena sobre una esfera de reloj).
# Uso: python3 -I tools/art/make_chronomancer.py src/main/resources/assets/rites_and_paths/textures/gui/portrait/subclass/chronomancer.png
from PIL import Image, ImageDraw
import math, sys
N=32
bg=(32,35,52,255)
im=Image.new('RGBA',(N,N),bg); px=im.load()
# reloj de fondo (anillo azul oscuro con marcas)
cx=cy=15.5
for y in range(N):
    for x in range(N):
        d=math.hypot(x-cx,y-cy)
        if 12.2<=d<=13.6: px[x,y]=(70,86,140,255)
        elif 11.2<=d<12.2: px[x,y]=(44,52,86,255)
        elif d<11.2: px[x,y]=(38,42,66,255)
for k in range(12):
    a=k*math.pi/6
    x=int(round(cx+11.6*math.cos(a))); y=int(round(cy+11.6*math.sin(a)))
    px[x,y]=(150,170,220,255)
# reloj de arena
gold=(214,170,74,255); goldd=(150,110,40,255); glass=(150,190,230,255); glassd=(100,140,190,255)
sand=(240,214,140,255); sandd=(205,170,90,255)
for x in range(8,24):
    px[x,5]=gold; px[x,6]=goldd; px[x,25]=goldd; px[x,26]=gold
for y in range(7,25):
    t=abs(y-15.5)/9.5   # 0 en el centro, 1 en los extremos
    half=int(round(1+6.5*t))
    l=16-half; r=15+half
    for x in range(l,r+1):
        if x in (l,r): px[x,y]=glassd
        else: px[x,y]=(60,74,110,255)
    # arena: arriba queda poca, abajo montón
    if 9<=y<=13:
        for x in range(l+1,r):
            if y>=11: px[x,y]=sand if (x+y)%3 else sandd
    if 19<=y<=24:
        top=24-(y-19)
        for x in range(l+1,r):
            if abs(x-15.5) < (y-17)*1.2: px[x,y]=sand if (x*y)%4 else sandd
# chorro central
for y in range(14,20): px[15,y]=sand; px[16,y]=sandd if y%2 else sand
# brillo
for y in range(8,12): px[18 if y<10 else 19,y]=(220,235,255,255)
# columnas
for y in range(7,25): px[7,y]=goldd; px[24,y]=goldd
for y in (7,24): px[7,y]=gold; px[24,y]=gold
# chispas moradas
for (x,y) in [(4,6),(27,9),(5,24),(26,25)]: px[x,y]=(186,130,240,255)
im.resize((64,64),Image.NEAREST).save(sys.argv[1])  # 32x32 duplicado a 64x64, sin suavizado
