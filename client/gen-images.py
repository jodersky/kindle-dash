#!/usr/bin/env python3
"""Generate the status screens shown by dist/run.sh.

Images are 758x1024 8-bit grayscale, the native resolution of a Kindle
Paperwhite 2. The top of the screen is kept empty because run.sh draws its
status line there.

It also generates the scriptlet icon and inlines it into dist/start.sh.

Usage: python3 gen-images.py   (writes into dist/)
"""

import base64
import io
import os
import re

from PIL import Image, ImageDraw, ImageFont

W, H = 758, 1024
BLACK, GRAY, WHITE = 0, 136, 255
FONT_DIR = "/usr/share/fonts/truetype/noto"
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "dist")

title_font = ImageFont.truetype(os.path.join(FONT_DIR, "NotoSans-Bold.ttf"), 56)
body_font = ImageFont.truetype(os.path.join(FONT_DIR, "NotoSans-Regular.ttf"), 30)

# icon is centered on this point
CX, CY = W // 2, 430


def moon(d):
    r = 120
    d.ellipse((CX - r, CY - r, CX + r, CY + r), fill=BLACK)
    # cut out a crescent
    ox, oy = 60, -45
    d.ellipse((CX - r + ox, CY - r + oy, CX + r + ox, CY + r + oy), fill=WHITE)
    # a few stars
    for sx, sy, s in ((CX + 115, CY - 120, 14), (CX + 160, CY - 30, 9), (CX + 80, CY + 40, 7)):
        d.polygon(
            [(sx, sy - s * 2), (sx + s // 2, sy - s // 2), (sx + s * 2, sy),
             (sx + s // 2, sy + s // 2), (sx, sy + s * 2), (sx - s // 2, sy + s // 2),
             (sx - s * 2, sy), (sx - s // 2, sy - s // 2)],
            fill=BLACK,
        )


def wifi_off(d):
    base_y = CY + 100
    for r in (60, 120, 180):
        d.arc((CX - r, base_y - r, CX + r, base_y + r), 225, 315, fill=GRAY, width=22)
    d.ellipse((CX - 20, base_y - 20, CX + 20, base_y + 20), fill=GRAY)
    # strike-through
    d.line((CX - 150, CY - 130, CX + 150, CY + 130), fill=WHITE, width=44)
    d.line((CX - 150, CY - 130, CX + 150, CY + 130), fill=BLACK, width=20)


def warning(d):
    s = 150
    tri = [(CX, CY - s), (CX + s * 1.15, CY + s * 0.85), (CX - s * 1.15, CY + s * 0.85)]
    d.polygon(tri, outline=BLACK, width=18)
    d.rounded_rectangle((CX - 13, CY - 60, CX + 13, CY + 40), radius=12, fill=BLACK)
    d.ellipse((CX - 16, CY + 65, CX + 16, CY + 97), fill=BLACK)


def screen(name, icon, title, lines):
    img = Image.new("L", (W, H), WHITE)
    d = ImageDraw.Draw(img)
    icon(d)
    y = 680
    d.text((W // 2, y), title, font=title_font, fill=BLACK, anchor="mt")
    y += 100
    for line in lines:
        d.text((W // 2, y), line, font=body_font, fill=GRAY, anchor="mt")
        y += 46
    img.save(os.path.join(OUT_DIR, name), optimize=True)


def icon():
    """Library icon for the start.sh scriptlet, inlined into its "# Icon:" header."""
    s = 128
    img = Image.new("L", (s, s), WHITE)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((4, 4, s - 5, s - 5), radius=14, outline=BLACK, width=6)
    # dashboard tiles: a wide header, two small tiles and a bar chart
    d.rounded_rectangle((18, 18, s - 19, 42), radius=4, fill=BLACK)
    d.rounded_rectangle((18, 52, 58, 76), radius=4, fill=GRAY)
    d.rounded_rectangle((18, 84, 58, 109), radius=4, fill=GRAY)
    for i, h in enumerate((20, 38, 28, 52)):
        x = 68 + i * 11
        d.rectangle((x, 109 - h, x + 7, 109), fill=BLACK)

    buf = io.BytesIO()
    img.save(buf, format="PNG", optimize=True)
    data = base64.b64encode(buf.getvalue()).decode()

    start_sh = os.path.join(OUT_DIR, "start.sh")
    with open(start_sh) as f:
        src = f.read()
    src = re.sub(r"^# Icon:.*$", f"# Icon: data:image/png;base64,{data}", src, count=1, flags=re.M)
    with open(start_sh, "w") as f:
        f.write(src)


icon()
screen("sleeping.png", moon, "Sleeping", ["The dashboard will wake up", "at the next scheduled time."])
screen("wifi-error.png", wifi_off, "No Wi-Fi", ["Could not connect to the network.", "Retrying at the next refresh."])
screen("fetch-error.png", warning, "Fetch error", ["Could not download the dashboard.", "Retrying at the next refresh."])
