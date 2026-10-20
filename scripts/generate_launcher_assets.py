#!/usr/bin/env python3
"""Generate the independent client launcher icon and its PNG derivatives.

Requires Pillow only when regenerating bitmap assets. Shapes are original
project artwork; NextDNS colors are used only as color references.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
BRAND = ROOT / 'docs/brand'
WHITE = '#FFFFFF'
BLUE = '#0055FF'
GREEN = '#008000'
BLACK = '#000000'

SIGNALS = [
    (GREEN, 2, ((34, 36), (20, 43), (20, 65), (34, 72))),
    (GREEN, 2, ((74, 36), (88, 43), (88, 65), (74, 72))),
]
SHIELD = 'M54,30 L69,37 L69,51 C69,63 63,72 54,78 C45,72 39,63 39,51 L39,37 Z'


def path(color, data, *, fill=None, stroke_width=None):
    stroke = f' android:strokeColor="{color}" android:strokeWidth="{stroke_width}" android:strokeLineCap="round" android:strokeLineJoin="round"' if stroke_width else ''
    fill_color = fill if fill is not None else '@android:color/transparent'
    return f'    <path android:fillColor="{fill_color}"{stroke} android:pathData="{data}" />'


def vector(monochrome=False):
    paths = []
    for color, width, points in SIGNALS:
        signal_color = BLACK if monochrome else color
        start, control_one, control_two, end = points
        path_data = (
            f'M{start[0]},{start[1]} '
            f'C{control_one[0]},{control_one[1]} '
            f'{control_two[0]},{control_two[1]} '
            f'{end[0]},{end[1]}'
        )
        paths.append(path(signal_color, path_data, stroke_width=width))
    paths.append(path(BLACK if monochrome else BLUE, SHIELD, fill=BLACK if monochrome else BLUE))
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

    for color, width, points in SIGNALS:
        draw.line(
            [point(x, y) for x, y in cubic(points)],
            fill=color,
            width=round(width * scale),
            joint='curve'
        )
    shield = [
        (54, 30), (69, 37), (69, 51),
        *cubic(((69, 51), (69, 63), (63, 72), (54, 78)))[1:],
        *cubic(((54, 78), (45, 72), (39, 63), (39, 51)))[1:],
        (39, 37),
    ]
    draw.polygon([point(x, y) for x, y in shield], fill=BLUE)

    if round_mask:
        mask = Image.new('L', image.size)
        ImageDraw.Draw(mask).ellipse((0, 0, image.width - 1, image.height - 1), fill=255)
        image.putalpha(mask)
    return image.resize((size, size), Image.Resampling.LANCZOS)


if __name__ == '__main__':
    RES.joinpath('drawable/ic_launcher_foreground.xml').write_text(vector(), encoding='utf8', newline='\n')
    RES.joinpath('drawable/ic_launcher_monochrome.xml').write_text(vector(monochrome=True), encoding='utf8', newline='\n')
    for density, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
        for name, rounded in [('ic_launcher', False), ('ic_launcher_round', True)]:
            render(size, rounded).save(RES / f'mipmap-{density}' / f'{name}.png')
    BRAND.mkdir(parents=True, exist_ok=True)
    render(512).save(BRAND / 'store-icon-512.png')
    print('Generated adaptive and monochrome shield vectors, ten legacy PNGs, and the 512px store icon.')
