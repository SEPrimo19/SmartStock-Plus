"""Regenerates all SmartStock+ icon assets from the new logo image.

- drawable/smartstock_logo.png        (in-app logo)
- mipmap-*/ic_launcher(_round).webp   (legacy launcher, all densities)
- drawable/ic_launcher_foreground.png (adaptive foreground, replaces .xml)
- drawable/ic_launcher_background.xml  (solid colour matched to the icon)
"""
import os
from PIL import Image

SRC = r"C:\Users\Admin\.claude\image-cache\dbe41519-ee42-45d5-920c-bad86c32406d\18.png"
RES = r"C:\Users\Admin\Downloads\SmartStock+\SmartStock\app\src\main\res"

img = Image.open(SRC).convert("RGBA")

# The source has an OPAQUE BLACK background (not transparent), so the
# icon's rounded square sits on a black field. Knock out every near-black
# pixel to full transparency — this clears the background AND the area
# outside the rounded corners. The icon's darkest real colour is a deep
# blue (~6,50,138), so a low threshold never eats the artwork.
px = img.load()
w, h = img.size
for y in range(h):
    for x in range(w):
        r, g, b, a = px[x, y]
        if r < 45 and g < 45 and b < 70:
            px[x, y] = (0, 0, 0, 0)

# Now crop to the real icon (the blue rounded square).
bbox = img.getbbox()
icon = img.crop(bbox)

# Pad to a perfect square (centered, transparent) so nothing is distorted.
side = max(icon.size)
square = Image.new("RGBA", (side, side), (0, 0, 0, 0))
square.paste(icon, ((side - icon.width) // 2, (side - icon.height) // 2), icon)

# The icon's background blue = the most common fully-opaque colour
# (it covers the largest area). Used for the adaptive background so the
# masked corners blend seamlessly with the icon's own rounded square.
counts: dict = {}
small = square.resize((128, 128), Image.LANCZOS)
for px in small.getdata():
    rr, gg, bb, aa = px
    if aa > 230 and bb >= rr and bb >= gg and (bb - rr) > 20:
        counts[(rr, gg, bb)] = counts.get((rr, gg, bb), 0) + 1
if counts:
    r, g, b = max(counts, key=counts.get)
else:
    r, g, b = (0x1D, 0x4E, 0xD8)  # fallback: cobalt blue
blue_hex = f"#{r:02X}{g:02X}{b:02X}"


def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)
    print("wrote", os.path.relpath(path, RES), im.size)


def resized(px):
    return square.resize((px, px), Image.LANCZOS)


# 1. In-app logo
save(resized(512), os.path.join(RES, "drawable", "smartstock_logo.png"))

# 2. Legacy launcher icons (overwrite the existing .webp, all densities)
densities = {
    "mipmap-mdpi": 48, "mipmap-hdpi": 72, "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144, "mipmap-xxxhdpi": 192,
}
for folder, px in densities.items():
    im = resized(px)
    for name in ("ic_launcher.webp", "ic_launcher_round.webp"):
        p = os.path.join(RES, folder, name)
        im.save(p, "WEBP", lossless=True, quality=100)
        print("wrote", os.path.join(folder, name), im.size)

# 3. Adaptive foreground — 432px canvas (4x 108dp), icon at ~92% so the
#    launcher mask rounds it nicely; transparent elsewhere.
fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
scaled = square.resize((int(432 * 0.92), int(432 * 0.92)), Image.LANCZOS)
fg.paste(scaled, ((432 - scaled.width) // 2, (432 - scaled.height) // 2), scaled)
# Replace the old vector foreground with this bitmap (same resource name).
old_fg_xml = os.path.join(RES, "drawable", "ic_launcher_foreground.xml")
if os.path.exists(old_fg_xml):
    os.remove(old_fg_xml)
    print("removed drawable/ic_launcher_foreground.xml")
save(fg, os.path.join(RES, "drawable", "ic_launcher_foreground.png"))

# 4. Adaptive background — solid colour matched to the icon.
bg_xml = os.path.join(RES, "drawable", "ic_launcher_background.xml")
with open(bg_xml, "w", encoding="utf-8") as f:
    f.write(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<shape xmlns:android="http://schemas.android.com/apk/res/android" '
        'android:shape="rectangle">\n'
        f'    <solid android:color="{blue_hex}" />\n'
        '</shape>\n'
    )
print("background colour set to", blue_hex)
print("DONE")
