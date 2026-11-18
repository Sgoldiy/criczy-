#!/usr/bin/env python3
"""Draws the Criczy mark once and writes it everywhere it is used:

  app/src/main/res/drawable/ic_launcher_background.xml   adaptive icon background
  app/src/main/res/drawable/ic_launcher_foreground.xml   adaptive icon foreground (also the splash icon)
  app/src/main/res/drawable/ic_launcher_monochrome.xml   themed icon (Android 13+)
  app/src/main/res/drawable/ic_stat_criczy.xml           notification icon
  docs/store/icon.svg                                    the full icon, for the store images and icon-512.png

The mark is a bowled ball: the amber C of Criczy around three stumps, the ball striking the off stump
with its trail coming in through the mouth of the C, the stump knocked back and both bails flying.

Run from the repository root:  python3 docs/store/make_icon.py
"""
import math
import os

# ---------------------------------------------------------------- geometry helpers (108 x 108 canvas)


def f(v):
    s = ('%.2f' % v).rstrip('0').rstrip('.')
    return '0' if s in ('-0', '') else s


def circle(cx, cy, r):
    return f'M{f(cx - r)},{f(cy)}a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0z'


def ellipse(cx, cy, rx, ry):
    return f'M{f(cx - rx)},{f(cy)}a{f(rx)},{f(ry)} 0 1,0 {f(2 * rx)},0a{f(rx)},{f(ry)} 0 1,0 {f(-2 * rx)},0z'


def capsule(cx, cy, w, h):
    """A rectangle with fully rounded ends."""
    r = min(w, h) / 2
    x, y = cx - w / 2, cy - h / 2
    return (f'M{f(x + r)},{f(y)}h{f(w - 2 * r)}a{f(r)},{f(r)} 0 0 1 {f(r)},{f(r)}v{f(h - 2 * r)}'
            f'a{f(r)},{f(r)} 0 0 1 {f(-r)},{f(r)}h{f(-(w - 2 * r))}a{f(r)},{f(r)} 0 0 1 {f(-r)},{f(-r)}'
            f'v{f(-(h - 2 * r))}a{f(r)},{f(r)} 0 0 1 {f(r)},{f(-r)}z')


def line(x1, y1, x2, y2):
    return f'M{f(x1)},{f(y1)}L{f(x2)},{f(y2)}'


class Lin:
    def __init__(self, x1, y1, x2, y2, *stops):
        self.kind, self.p, self.stops = 'linear', (x1, y1, x2, y2), stops


class Rad:
    def __init__(self, cx, cy, r, *stops):
        self.kind, self.p, self.stops = 'radial', (cx, cy, r), stops


def path(d, fill=None, stroke=None, width=None, even_odd=False):
    return dict(d=d, fill=fill, stroke=stroke, width=width, even_odd=even_odd)


def group(children, rotate=0.0, px=0.0, py=0.0, tx=0.0, ty=0.0):
    return dict(group=children, rotate=rotate, px=px, py=py, tx=tx, ty=ty)


# ---------------------------------------------------------------- the mark

CX, CY = 54.0, 54.0          # centre of the C (and of the canvas)
RING_R, RING_W = 26.0, 10.0  # the C
RING_GAP = 44.0              # half the opening of the C, in degrees, facing right
STUMP_W, STUMP_TOP, STUMP_BOTTOM = 3.2, 44.0, 70.0
STUMPS_X = (44.5, 52.0, 59.5)
HIT_LEAN = -7.0              # the off stump, knocked back by the ball
BALL = (67.6, 58.0)
BALL_R = 7.6
TRAIL_ANGLE = -14.0          # the ball comes in from the right, slightly from above
BAIL_W, BAIL_H = 7.6, 2.6
BAILS = ((46.0, 37.8, -22.0), (55.2, 37.0, 38.0))  # centre x, centre y, rotation: both popped off the stumps

AMBER = Lin(CX, CY - RING_R - 5, CX, CY + RING_R + 5, (0, '#FFFFD166'), (1, '#FFFF9F1C'))
NAVY = '#FF0B1018'
WHITE = '#FFFFFFFF'


def ring_path():
    a = math.radians(RING_GAP)
    x = CX + RING_R * math.cos(a)
    return f'M{f(x)},{f(CY - RING_R * math.sin(a))}A{f(RING_R)},{f(RING_R)} 0 1,0 {f(x)},{f(CY + RING_R * math.sin(a))}'


def stumps(fill):
    h = STUMP_BOTTOM - STUMP_TOP
    cy = (STUMP_TOP + STUMP_BOTTOM) / 2
    left, middle, off = (capsule(x, cy, STUMP_W, h) for x in STUMPS_X)
    return [
        path(left + middle, fill=fill),
        group([path(off, fill=fill)], rotate=HIT_LEAN, px=STUMPS_X[2], py=STUMP_BOTTOM),
    ]


def bails(fill, halo):
    out = []
    for x, y, a in BAILS:
        shapes = []
        if halo:  # a navy edge keeps a white bail readable where it flies over the amber C
            shapes.append(path(capsule(0, 0, BAIL_W + 2.2, BAIL_H + 2.2), fill=NAVY))
        shapes.append(path(capsule(0, 0, BAIL_W, BAIL_H), fill=fill))
        out.append(group(shapes, rotate=a, tx=x, ty=y))
    return out


def trail_vectors():
    a = math.radians(TRAIL_ANGLE)
    d = (math.cos(a), math.sin(a))  # from the ball back along where it came from
    n = (-d[1], d[0])
    return d, n


def trail(color):
    bx, by = BALL
    d, n = trail_vectors()
    tip = (bx + 26 * d[0], by + 26 * d[1])
    p1 = (bx - BALL_R * n[0], by - BALL_R * n[1])
    p2 = (bx + BALL_R * n[0], by + BALL_R * n[1])
    wedge = f'M{f(p1[0])},{f(p1[1])}L{f(tip[0])},{f(tip[1])}L{f(p2[0])},{f(p2[1])}z'
    fade = Lin(bx, by, tip[0], tip[1], (0, '#B3FF7A45'), (0.45, '#40FF9F1C'), (1, '#00FF9F1C'))
    shapes = [path(wedge, fill=fade if color else None)] if color else []
    # Two speed lines either side of the trail.
    lines = []
    for side, start, length in ((-1, 2.0, 12.0), (1, 7.0, 7.0)):
        off = BALL_R + (2.4 if side < 0 else 1.8)
        sx = bx + side * off * n[0] + start * d[0]
        sy = by + side * off * n[1] + start * d[1]
        ex, ey = sx + length * d[0], sy + length * d[1]
        stroke = Lin(sx, sy, ex, ey, (0, '#E6FFFFFF'), (1, '#00FFFFFF')) if color else WHITE
        lines.append(path(line(sx, sy, ex, ey), stroke=stroke, width=1.4))
    return shapes + lines


def seam_curves():
    # In ball coordinates, before the spin rotation: two stitched rows across the ball.
    return ('M-1.6,-7.4C1.6,-3 1.6,3 -1.6,7.4', 'M1.1,-7.3C4.3,-3 4.3,3 1.1,7.3')


def seam_band(dx):
    # The seam row as a thin closed band, for the one-colour icons where it is cut out of the ball.
    w = 0.65
    return (f'M{f(-1.6 + dx - w)},-7.4C{f(1.6 + dx - w)},-3 {f(1.6 + dx - w)},3 {f(-1.6 + dx - w)},7.4'
            f'L{f(-1.6 + dx + w)},7.4C{f(1.6 + dx + w)},3 {f(1.6 + dx + w)},-3 {f(-1.6 + dx + w)},-7.4z')


def ball(color):
    bx, by = BALL
    if not color:
        d = circle(0, 0, BALL_R) + seam_band(0) + seam_band(2.7)
        return [group([path(d, fill=WHITE, even_odd=True)], rotate=28, tx=bx, ty=by)]
    body = path(circle(bx, by, BALL_R), fill=Rad(bx - 2.6, by - 3.0, BALL_R + 4.5, (0, '#FFFF6B6F'), (0.55, '#FFE5383B'), (1, '#FFA80F1A')))
    s1, s2 = seam_curves()
    seam = group([
        path(s1, stroke='#F2FFFFFF', width=1.25),
        path(s2, stroke='#8CFFFFFF', width=1.25),
    ], rotate=28, tx=bx, ty=by)
    shine = group([path(ellipse(0, 0, 2.3, 1.3), fill='#59FFFFFF')], rotate=-35, tx=bx - 3.4, ty=by - 3.6)
    return [body, seam, shine]


def burst(color=True):
    """Three short strokes where the bails left the off stump."""
    lean = math.radians(HIT_LEAN)
    h = STUMP_BOTTOM - STUMP_TOP
    x0 = STUMPS_X[2] + h * math.sin(lean)
    y0 = STUMP_BOTTOM - h * math.cos(lean)
    out = []
    for deg, start, length in ((-52, 3.4, 2.6), (-16, 3.6, 3.0), (20, 3.4, 2.4)):
        a = math.radians(deg)
        sx, sy = x0 + start * math.cos(a), y0 + start * math.sin(a)
        ex, ey = x0 + (start + length) * math.cos(a), y0 + (start + length) * math.sin(a)
        out.append(path(line(sx, sy, ex, ey), stroke='#FFFFD166' if color else WHITE, width=1.3))
    return out


def flash():
    x = STUMPS_X[2] + 1.5
    return path(circle(x, BALL[1] - 1, 13), fill=Rad(x, BALL[1] - 1, 13, (0, '#73FFD166'), (1, '#00FFD166')))


def foreground():
    stump_fill = Lin(0, STUMP_TOP, 0, STUMP_BOTTOM, (0, '#FFFFFFFF'), (1, '#FFDCE3EE'))
    return ([path(ring_path(), stroke=AMBER, width=RING_W), flash()] + trail(True)
            + stumps(stump_fill) + bails(WHITE, halo=False) + burst() + ball(True))


def background():
    return [
        path('M0,0h108v108h-108z', fill=Lin(0, 0, 108, 108, (0, '#FF1B2C52'), (1, '#FF070B12'))),
        path('M0,0h108v108h-108z', fill=Rad(66, 50, 58, (0, '#33FFB627'), (1, '#00FFB627'))),
    ]


def monochrome():
    return ([path(ring_path(), stroke=WHITE, width=RING_W)] + trail(False)
            + stumps(WHITE) + bails(WHITE, halo=False) + burst(False) + ball(False))


# ---------------------------------------------------------------- writers

def vd_color(c):
    return c if len(c) == 9 else '#FF' + c[1:]


def vd_gradient(g, attr, ind):
    if g.kind == 'linear':
        x1, y1, x2, y2 = g.p
        head = f'<gradient android:type="linear" android:startX="{f(x1)}" android:startY="{f(y1)}" android:endX="{f(x2)}" android:endY="{f(y2)}">'
    else:
        cx, cy, r = g.p
        head = f'<gradient android:type="radial" android:centerX="{f(cx)}" android:centerY="{f(cy)}" android:gradientRadius="{f(r)}">'
    items = ''.join(f'\n{ind}        <item android:offset="{f(o)}" android:color="{vd_color(c)}" />' for o, c in g.stops)
    return f'\n{ind}    <aapt:attr name="android:{attr}">\n{ind}      {head}{items}\n{ind}      </gradient>\n{ind}    </aapt:attr>'


def vd_element(e, ind='    '):
    if 'group' in e:
        attrs = []
        if e['rotate']:
            attrs.append(f'android:rotation="{f(e["rotate"])}"')
        if e['px'] or e['py']:
            attrs.append(f'android:pivotX="{f(e["px"])}" android:pivotY="{f(e["py"])}"')
        if e['tx'] or e['ty']:
            attrs.append(f'android:translateX="{f(e["tx"])}" android:translateY="{f(e["ty"])}"')
        inner = '\n'.join(vd_element(c, ind + '    ') for c in e['group'])
        return f'{ind}<group {" ".join(attrs)}>\n{inner}\n{ind}</group>'
    attrs = [f'android:pathData="{e["d"]}"']
    kids = ''
    if isinstance(e['fill'], str):
        attrs.append(f'android:fillColor="{vd_color(e["fill"])}"')
    elif e['fill'] is not None:
        kids += vd_gradient(e['fill'], 'fillColor', ind)
    if e['even_odd']:
        attrs.append('android:fillType="evenOdd"')
    if e['stroke'] is not None:
        attrs.append(f'android:strokeWidth="{f(e["width"])}" android:strokeLineCap="round"')
        if isinstance(e['stroke'], str):
            attrs.append(f'android:strokeColor="{vd_color(e["stroke"])}"')
        else:
            kids += vd_gradient(e['stroke'], 'strokeColor', ind)
    if kids:
        return f'{ind}<path {" ".join(attrs)}>{kids}\n{ind}</path>'
    return f'{ind}<path {" ".join(attrs)} />'


def vector(elements, comment, size=108, viewport=(0, 0, 108, 108)):
    x, y, w, h = viewport
    body = '\n'.join(vd_element(e) for e in elements)
    if x or y:
        body = f'    <group android:translateX="{f(-x)}" android:translateY="{f(-y)}">\n' + \
               '\n'.join(vd_element(e, '        ') for e in elements) + '\n    </group>'
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!-- {comment} Generated by docs/store/make_icon.py; edit that, not this. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"\n'
            f'    android:width="{size}dp" android:height="{size}dp" android:viewportWidth="{f(w)}" android:viewportHeight="{f(h)}">\n'
            f'{body}\n</vector>\n')


class Svg:
    def __init__(self):
        self.defs, self.n = [], 0

    def paint(self, p):
        if isinstance(p, str):
            c = vd_color(p)
            return f'#{c[3:]}', int(c[1:3], 16) / 255
        self.n += 1
        gid = f'g{self.n}'
        stops = ''.join(f'<stop offset="{f(o)}" stop-color="#{vd_color(c)[3:]}" stop-opacity="{f(int(vd_color(c)[1:3], 16) / 255)}"/>' for o, c in p.stops)
        if p.kind == 'linear':
            x1, y1, x2, y2 = p.p
            self.defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{f(x1)}" y1="{f(y1)}" x2="{f(x2)}" y2="{f(y2)}">{stops}</linearGradient>')
        else:
            cx, cy, r = p.p
            self.defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}">{stops}</radialGradient>')
        return f'url(#{gid})', 1.0

    def element(self, e):
        if 'group' in e:
            t = []
            if e['tx'] or e['ty']:
                t.append(f'translate({f(e["tx"])} {f(e["ty"])})')
            if e['rotate']:
                t.append(f'rotate({f(e["rotate"])} {f(e["px"])} {f(e["py"])})')
            return f'<g transform="{" ".join(t)}">' + ''.join(self.element(c) for c in e['group']) + '</g>'
        a = [f'd="{e["d"]}"']
        if e['fill'] is None:
            a.append('fill="none"')
        else:
            p, o = self.paint(e['fill'])
            a.append(f'fill="{p}"' + (f' fill-opacity="{f(o)}"' if o < 1 else ''))
        if e['even_odd']:
            a.append('fill-rule="evenodd"')
        if e['stroke'] is not None:
            p, o = self.paint(e['stroke'])
            a.append(f'stroke="{p}" stroke-width="{f(e["width"])}" stroke-linecap="round"' + (f' stroke-opacity="{f(o)}"' if o < 1 else ''))
        return f'<path {" ".join(a)}/>'

    def document(self, elements, viewbox='0 0 108 108'):
        body = ''.join(self.element(e) for e in elements)
        return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{viewbox}"><defs>{"".join(self.defs)}</defs>{body}</svg>\n'


def write(rel, text):
    p = os.path.join(os.getcwd(), rel)
    with open(p, 'w') as fh:
        fh.write(text)
    print('wrote', rel)


if __name__ == '__main__':
    res = 'app/src/main/res/drawable/'
    write(res + 'ic_launcher_background.xml', vector(background(), 'Navy night sky with a floodlight glow behind the stumps.'))
    write(res + 'ic_launcher_foreground.xml', vector(foreground(), 'Bowled: the C of Criczy, stumps knocked back, bails flying. Everything that matters sits inside the 66dp safe zone.'))
    write(res + 'ic_launcher_monochrome.xml', vector(monochrome(), 'One-colour mark for themed icons.'))
    write(res + 'ic_stat_criczy.xml', vector(monochrome(), 'Notification icon: the one-colour mark, cropped to the mark.', size=24, viewport=(21, 21, 66, 66)))
    write('docs/store/icon.svg', Svg().document(background() + foreground()))
    import sys
    if '--previews' in sys.argv:  # one-colour SVGs, for checking the themed and notification icons
        out = sys.argv[sys.argv.index('--previews') + 1]
        write(os.path.join(out, 'mono.svg'), Svg().document(monochrome()))
        write(os.path.join(out, 'stat.svg'), Svg().document(monochrome(), viewbox='21 21 66 66'))
