"""Generate Better Red logo assets from mark.json (output of trace.py).

Usage: python3 -I gen.py mark.json <android-project-root>

Writes:
  res/drawable/ic_launcher_background.xml   white
  res/drawable/ic_launcher_foreground.xml   mark, placed like in the round design
  res/drawable/ic_launcher_monochrome.xml   mark, single color (Android 13+ themed icons)
  res/drawable/splash_logo_animated.xml     animated splash icon (b fades up, red square pops in)
  java/.../ui/brand/BrandPaths.kt           the same paths for Compose
"""
import json
import os
import sys

src, root = sys.argv[1], sys.argv[2]
d = json.load(open(src))
x0, y0, x1, y1 = d["bbox"]
U = 0.1  # mark units: 1 unit = 10 source px
W, H = round((x1 - x0) * U, 2), round((y1 - y0) * U, 2)


def path(loops):
    parts = []
    for loop in loops:
        if len(loop) < 4:
            continue  # tracing noise
        pts = [f"{(x - x0) * U:.2f},{(y - y0) * U:.2f}" for x, y in loop]
        parts.append("M" + pts[0] + "L" + " ".join(pts[1:]) + "Z")
    return "".join(parts)


B = path(d["grey"])
DOT = path(d["red"])
red_pts = [p for l in d["red"] for p in l]
dot_cx = round((sum(p[0] for p in red_pts) / len(red_pts) - x0) * U, 2)
dot_cy = round((sum(p[1] for p in red_pts) / len(red_pts) - y0) * U, 2)

# Round design: white circle Ø1194 px centred at (637.5, 640.5); a launcher icon shows a ~72dp circle of the 108dp canvas.
CIRCLE_D, CX, CY = 1194.0, 637.5, 640.5


def placement(canvas, visible_d):
    s = visible_d / CIRCLE_D  # dp per source px
    tx = canvas / 2 + (x0 - CX) * s
    ty = canvas / 2 + (y0 - CY) * s
    return round(s / U, 4), round(tx, 3), round(ty, 3)


res = os.path.join(root, "app/src/main/res")
HEADER = '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated from the Better Red logo (trace.py + gen.py). -->\n'


def vector(canvas, group_body):
    return (
        f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{canvas}dp" android:height="{canvas}dp"\n'
        f'    android:viewportWidth="{canvas}" android:viewportHeight="{canvas}">\n{group_body}</vector>\n'
    )


def mark_group(scale, tx, ty, grey, red, names=False):
    n = (lambda s: f' android:name="{s}"') if names else (lambda s: "")
    return (
        f'    <group android:scaleX="{scale}" android:scaleY="{scale}" android:translateX="{tx}" android:translateY="{ty}">\n'
        f'        <group{n("b_group")}>\n'
        f'            <path{n("b_path")} android:fillColor="{grey}" android:fillType="evenOdd"\n'
        f'                android:pathData="{B}" />\n'
        f'        </group>\n'
        f'        <group{n("dot_group")} android:pivotX="{dot_cx}" android:pivotY="{dot_cy}">\n'
        f'            <path android:fillColor="{red}" android:pathData="{DOT}" />\n'
        f'        </group>\n'
        f'    </group>\n'
    )


def write(rel, text):
    p = os.path.join(res, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, "w").write(text)
    print("wrote", os.path.relpath(p, root))


# --- launcher icon (108dp canvas, ~72dp visible circle) ---
s, tx, ty = placement(108, 72)
write("drawable/ic_launcher_background.xml", HEADER + vector(108, '    <path android:fillColor="#FFFFFF" android:pathData="M0,0h108v108h-108z" />\n'))
write("drawable/ic_launcher_foreground.xml", HEADER + vector(108, mark_group(s, tx, ty, "#767676", "#FF0000")))
write("drawable/ic_launcher_monochrome.xml", HEADER + vector(108, mark_group(s, tx, ty, "#FFFFFF", "#FFFFFF")))

# --- splash icon (Android 12+: 288dp canvas, content must fit a 192dp circle) ---
# Same proportions as the round design, scaled so the white circle would be 192dp.
s2, tx2, ty2 = placement(288, 192)
splash_vector = vector(288, mark_group(s2, tx2, ty2, "@color/logo_grey", "@color/logo_red", names=True))
splash_vector = splash_vector.replace("<vector ", "<vector ", 1)
anim = f"""<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt">
    <!-- The vector's default state is the finished logo, so Android 10/11 (no splash animation) show it complete. -->
    <aapt:attr name="android:drawable">
{''.join('        ' + line + chr(10) for line in splash_vector.strip().splitlines())}    </aapt:attr>

    <!-- 1. the "b" fades in and rises a little -->
    <target android:name="b_path">
        <aapt:attr name="android:animation">
            <objectAnimator android:propertyName="fillAlpha" android:valueFrom="0" android:valueTo="1"
                android:duration="350" android:interpolator="@android:interpolator/fast_out_slow_in" />
        </aapt:attr>
    </target>
    <target android:name="b_group">
        <aapt:attr name="android:animation">
            <objectAnimator android:propertyName="translateY" android:valueFrom="4" android:valueTo="0"
                android:duration="450" android:interpolator="@android:interpolator/fast_out_slow_in" />
        </aapt:attr>
    </target>

    <!-- 2. then the red square pops in -->
    <target android:name="dot_group">
        <aapt:attr name="android:animation">
            <set>
                <objectAnimator android:propertyName="scaleX" android:valueFrom="0" android:valueTo="1"
                    android:startOffset="300" android:duration="450" android:interpolator="@android:interpolator/overshoot" />
                <objectAnimator android:propertyName="scaleY" android:valueFrom="0" android:valueTo="1"
                    android:startOffset="300" android:duration="450" android:interpolator="@android:interpolator/overshoot" />
            </set>
        </aapt:attr>
    </target>
</animated-vector>
"""
write("drawable/splash_logo_animated.xml", HEADER + anim)

# --- Compose paths ---
kt = os.path.join(root, "app/src/main/java/site/brutt/betterred/ui/brand/BrandPaths.kt")
os.makedirs(os.path.dirname(kt), exist_ok=True)
open(kt, "w").write(f"""package site.brutt.betterred.ui.brand

// Generated from the Better Red logo (trace.py + gen.py). Do not edit by hand.
// Coordinates are in "mark units": the mark is {W} × {H}.
internal object BrandPaths {{
    const val WIDTH = {W}f
    const val HEIGHT = {H}f

    /** Centre of the red square, used as the pivot for its pop-in animation. */
    const val DOT_CENTER_X = {dot_cx}f
    const val DOT_CENTER_Y = {dot_cy}f

    /** The grey "b" (outer outline + counter; draw with even-odd fill). */
    const val B = "{B}"

    /** The red rounded square. */
    const val DOT = "{DOT}"
}}
""")
print("wrote", os.path.relpath(kt, root))
print("mark units", W, H, "dot centre", dot_cx, dot_cy, "launcher", (s, tx, ty), "splash", (s2, tx2, ty2))
