#!/usr/bin/env python3
"""Generate the independent client launcher icon and its PNG derivatives.

Requires Pillow only when regenerating bitmap assets. The three-node mark and
its slate/steel palette are original project artwork and do not reuse NextDNS
logo geometry or brand colors.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
BRAND = ROOT / 'docs/brand'
WHITE = '#FFFFFF'
BLACK = '#000000'
INK = '#263238'
NODE = '#607D8B'
CONNECTOR = '#455A64'
CONNECTOR_PATH = 'M38,39 C45,39 47,50 54,54 C61,58 63,69 70,69'
CONNECTOR_CURVES = (
    ((38, 39), (45, 39), (47, 50), (54, 54)),
    ((54, 54), (61, 58), (63, 69), (70, 69)),
)
NODES = ((38, 39, 5, NODE), (54, 54, 8, INK), (70, 69, 5, NODE))

def path(color, data, *, fill=None, stroke_width=None):
    stroke = f' android:strokeColor="{color}" android:strokeWidth="{stroke_width}" android:strokeLineCap="round" android:strokeLineJoin="round"' if stroke_width else ''
    fill_color = fill if fill is not None else '@android:color/transparent'
    return f'    <path android:fillColor="{fill_color}"{stroke} android:pathData="{data}" />'


def circle_path(cx, cy, radius):
    control = radius * 0.552285
    left = cx - radius
    right = cx + radius
    top = cy - radius
    bottom = cy + radius
    return (
        f'M{cx},{top} '
        f'C{cx + control:.3f},{top} {right},{cy - control:.3f} {right},{cy} '
        f'C{right},{cy + control:.3f} {cx + control:.3f},{bottom} {cx},{bottom} '
        f'C{cx - control:.3f},{bottom} {left},{cy + control:.3f} {left},{cy} '
        f'C{left},{cy - control:.3f} {cx - control:.3f},{top} {cx},{top} Z'
    )


def vector(monochrome=False):
    connector_color = BLACK if monochrome else CONNECTOR
    paths = [path(connector_color, CONNECTOR_PATH, stroke_width=2.5)]
    for cx, cy, radius, color in NODES:
        node_color = BLACK if monochrome else color
        paths.append(path(node_color, circle_path(cx, cy, radius), fill=node_color))
    return '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n' + '\n'.join(paths) + '\n</vector>\n'


def cubic(points, steps=32):
    p0, p1, p2, p3 = points
    result = []
    for index in range(steps + 1):
        t = index / steps
        u = 1 - t
        result.append((
            u**3 * p0[0] + 3 * u**2 * t * p1[0] + 3 * u * t**2 * p2[0] + t**3 * p3[0],
            u**3 * p0[1] + 3 * u**2 * t * p1[1] + 3 * u * t**2 * p2[1] + t**3 * p3[1],
        ))
    return result


def render(size, round_mask=False):
    # Legacy icons use the 72dp center viewport of the adaptive 108dp layer.
    scale = size * 4 / 72
    image = Image.new('RGBA', (size * 4, size * 4), WHITE)
    draw = ImageDraw.Draw(image)

    def point(x, y):
        return ((x - 18) * scale, (y - 18) * scale)

    connector_points = []
    for curve in CONNECTOR_CURVES:
        connector_points.extend(cubic(curve)[1:] if connector_points else cubic(curve))
    draw.line(
        [point(x, y) for x, y in connector_points],
        fill=CONNECTOR,
        width=round(2.5 * scale),
        joint='curve'
    )
    for cx, cy, radius, color in NODES:
        x, y = point(cx, cy)
        r = radius * scale
        draw.ellipse((x - r, y - r, x + r, y + r), fill=color)

    if round_mask:
        mask = Image.new('L', image.size)
        ImageDraw.Draw(mask).ellipse((0, 0, image.width - 1, image.height - 1), fill=255)
        image.putalpha(mask)
    return image.resize((size, size), Image.Resampling.LANCZOS)


def svg():
    nodes = '\n'.join(
        f'  <circle cx="{cx}" cy="{cy}" r="{radius}" fill="{color}" />'
        for cx, cy, radius, color in NODES
    )
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">\n'
        f'  <rect width="108" height="108" fill="{WHITE}" />\n'
        f'  <path d="{CONNECTOR_PATH}" fill="none" stroke="{CONNECTOR}" '
        'stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />\n'
        f'{nodes}\n'
        '</svg>\n'
    )


if __name__ == '__main__':
    RES.joinpath('drawable/ic_launcher_foreground.xml').write_text(vector(), encoding='utf8', newline='\n')
    RES.joinpath('drawable/ic_launcher_monochrome.xml').write_text(vector(monochrome=True), encoding='utf8', newline='\n')
    for density, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
        for name, rounded in [('ic_launcher', False), ('ic_launcher_round', True)]:
            render(size, rounded).save(RES / f'mipmap-{density}' / f'{name}.png')
    BRAND.mkdir(parents=True, exist_ok=True)
    (BRAND / 'launcher-icon.svg').write_text(svg(), encoding='utf8', newline='\n')
    render(512).save(BRAND / 'store-icon-512.png')
    print('Generated the original three-node launcher SVG, adaptive and monochrome vectors, ten legacy PNGs, and the 512px store icon.')
