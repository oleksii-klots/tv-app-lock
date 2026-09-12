#!/usr/bin/env python3
"""Generate GitHub social preview (1280x640) for TV App Lock."""
from PIL import Image, ImageDraw, ImageFont
import math

W, H = 1280, 640
BG = (16, 20, 24)         # same #101418 as the app
PANEL = (31, 42, 56)     # #1F2A38 screen color
AMBER = (255, 193, 7)    # #FFC107 padlock/version accent
TEXT = (240, 244, 248)
SUB = (176, 196, 222)    # #B0C4DE
DIM = (90, 105, 122)

FR = "/usr/share/fonts/truetype/liberation/"
def font(name, size): return ImageFont.truetype(FR + name, size)

img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

# subtle vertical gradient stripes for depth
for y in range(H):
    f = 1 + 0.06 * math.sin(y / 90.0)
    d.line([(0, y), (W, y)], fill=(int(BG[0]*f), int(BG[1]*f), int(BG[2]*f)))

# ---------- left: TV mock ----------
tvx, tvy, tvw, tvh = 70, 120, 430, 265
# bezel
d.rounded_rectangle([tvx-14, tvy-14, tvx+tvw+14, tvy+tvh+14], radius=22, fill=(10,12,14), outline=(52,64,78), width=3)
# screen
d.rounded_rectangle([tvx, tvy, tvx+tvw, tvy+tvh], radius=10, fill=PANEL)
# stand
d.polygon([(tvx+tvw//2-60, tvy+tvh+30), (tvx+tvw//2+60, tvy+tvh+30),
           (tvx+tvw//2+28, tvy+tvh+14), (tvx+tvw//2-28, tvy+tvh+14)], fill=(40,50,62))
d.rectangle([tvx+tvw//2-8, tvy+tvh+14, tvx+tvw//2+8, tvy+tvh+34], fill=(40,50,62))

def padlock(cx, cy, s, color):
    # shackle: thick arc + its legs
    lw = max(6, int(s * 0.20))
    d.arc([cx - s*0.34, cy - s*0.82, cx + s*0.34, cy - s*0.10], 180, 360, fill=color, width=lw)
    d.rectangle([cx - s*0.34 - lw//2, cy - s*0.46, cx - s*0.34 + lw//2, cy], fill=color)
    d.rectangle([cx + s*0.34 - lw//2, cy - s*0.46, cx + s*0.34 + lw//2, cy], fill=color)
    # body
    d.rounded_rectangle([cx - s*0.55, cy - s*0.18, cx + s*0.55, cy + s*0.72],
                        radius=int(s * 0.16), fill=color)
    # keyhole
    d.ellipse([cx - s*0.09, cy + s*0.08, cx + s*0.09, cy + s*0.26], fill=PANEL)
    d.polygon([(cx - s*0.05, cy + s*0.20), (cx + s*0.05, cy + s*0.20),
               (cx + s*0.08, cy + s*0.42), (cx - s*0.08, cy + s*0.42)], fill=PANEL)

padlock(tvx+tvw//2, tvy+90, 66, AMBER)

# PIN dots on screen
pin_y = tvy+185
for i in range(4):
    cx = tvx+tvw//2 + (i-1.5)*52
    filled = i < 2
    col = AMBER if filled else (70,84,98)
    r = 11 if filled else 9
    d.ellipse([cx-r, pin_y-r, cx+r, pin_y+r], fill=col)

cap = font("LiberationSans-Bold.ttf", 26)
d.text((tvx+tvw//2, tvy+228), "Enter the PIN", font=cap, fill=TEXT, anchor="mm")
d.text((tvx+tvw//2, tvy+257), "with your remote", font=font("LiberationSans-Regular.ttf", 18), fill=DIM, anchor="mm")

# ---------- right: titles ----------
rx = 590
t1 = font("LiberationSans-Bold.ttf", 62)
t2 = font("LiberationSans-Bold.ttf", 27)
t3 = font("LiberationSans-Regular.ttf", 23)

d.text((rx, 150), "TV App Lock", font=t1, fill=TEXT)
d.rectangle([rx, 236, rx+520, 240], fill=AMBER)
d.text((rx, 262), "PIN-lock any app on your Android TV.", font=t2, fill=AMBER)
d.text((rx, 306), "Built for parents. No root, no cloud,", font=t3, fill=SUB)
d.text((rx, 340), "no ads, no tracking.", font=t3, fill=SUB)

# remote-control row (DPAD + OK)
rd = font("LiberationSans-Regular.ttf", 19)
d.text((rx, 415), "works entirely with the living-room remote:", font=rd, fill=DIM)
d.text((rx, 447), "•  4-digit PIN, auto-saves", font=rd, fill=SUB)
d.text((rx, 479), "•  per-app lock switch on TV home", font=rd, fill=SUB)
d.text((rx, 511), "•  survives reboot •  MIT licensed", font=rd, fill=SUB)

# corner brand
d.text((W-30, H-30), "oleksii-klots/tv-app-lock", font=font("LiberationSans-Regular.ttf", 18), fill=DIM, anchor="rs")

img.save("/root/tvlock/docs/social-preview.png", optimize=True)
print("saved", img.size)
