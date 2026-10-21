"""Generates the Android VectorDrawables and the docs SVG symbol from the logo SVGs.
Run from anywhere: python design/logo/make_vectors.py"""
import re, pathlib
here = pathlib.Path(__file__).parent
root = here.parents[1]
res = root / 'app/src/main/res/drawable'
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'
PATH = re.compile(r'<path d="([^"]+)" stroke="(#\w+)"(?: stroke-opacity="([\d.]+)")? stroke-width="([\d.]+)"')

def thin(d):
    pts = re.findall(r'(-?[\d.]+) (-?[\d.]+)', d)
    keep = pts[::4]
    if keep[-1] != pts[-1]: keep.append(pts[-1])
    return 'M' + ' L'.join(f'{round(float(x), 2):g} {round(float(y), 2):g}' for x, y in keep)

def paths(svg):
    out = []
    for d, c, a, w in PATH.findall((here / svg).read_text()):
        al = f'\n            android:strokeAlpha="{a}"' if a else ''
        out.append(f'        <path\n            android:pathData="{thin(d)}"\n            android:strokeColor="{c}"{al}\n'
                   f'            android:strokeWidth="{w}"\n            android:strokeLineCap="round"\n'
                   f'            android:strokeLineJoin="round"\n            android:fillColor="#00000000" />')
    return '\n'.join(out)

def vec(name, svg, size, vp, scale=None, shift=None):
    body = paths(svg)
    if scale:
        body = (f'    <group android:scaleX="{scale}" android:scaleY="{scale}" android:pivotX="54" android:pivotY="54">\n'
                + body + '\n    </group>')
    elif shift:
        body = f'    <group android:translateX="{-shift}" android:translateY="{-shift}">\n' + body + '\n    </group>'
    (res / name).write_text(f'<?xml version="1.0" encoding="utf-8"?>\n<vector {NS}\n    android:width="{size}dp"\n    android:height="{size}dp"\n'
                            f'    android:viewportWidth="{vp}"\n    android:viewportHeight="{vp}">\n{body}\n</vector>\n')

vec('ic_launcher_foreground.xml', 'logo-mark.svg', 108, 108, scale=0.8)
vec('ic_launcher_monochrome.xml', 'logo-mono.svg', 108, 108, scale=0.8)
vec('ic_plait_mark.xml', 'logo-mark.svg', 108, 108)
# braid spans y 14..94 incl. strokes; 84 viewport keeps it centred with 2 units padding
vec('ic_notification.xml', 'logo-mono.svg', 24, 84, shift=12)
(res / 'ic_launcher_background.xml').write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<vector {NS}
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0 0H108V108H0Z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="108" android:endY="108">
                <item android:offset="0" android:color="#1D2260" />
                <item android:offset="1" android:color="#0E1030" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
''')
# docs: symbol with the full-precision mark paths
inner = ''.join(re.findall(r'<path .*?/>', (here / 'logo-mark.svg').read_text()))
print(f'<symbol id="plait-mark" viewBox="0 0 108 108">{inner}</symbol>')  # paste into docs/index.html
