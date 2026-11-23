"""Trace the Better Red mark (transparent PNG) into vector paths.

Usage: python3 -I trace.py <mark.png> <out.json>
Output: {"bbox": [x0,y0,x1,y1], "colors": {...}, "grey": [[(x,y),...], ...], "red": [...]}
Coordinates are in source pixels. Marching squares at alpha 0.5, then Ramer–Douglas–Peucker.
"""
import json
import sys

import numpy as np
from PIL import Image


def masks(path):
    im = np.asarray(Image.open(path).convert("RGBA")).astype(np.float32) / 255.0
    r, g, b, a = im[..., 0], im[..., 1], im[..., 2], im[..., 3]
    is_red = (r - g) > 0.35
    red = np.where(is_red, a, 0.0)
    grey = np.where(~is_red, a, 0.0)
    return grey, red, im


def marching_squares(field, level=0.5):
    """Return closed loops of (x, y) points where field crosses level."""
    f = np.pad(field, 1)  # make sure every shape is closed
    h, w = f.shape
    segs = []

    def interp(p1, p2, v1, v2):
        t = (level - v1) / (v2 - v1) if v2 != v1 else 0.5
        return (p1[0] + t * (p2[0] - p1[0]), p1[1] + t * (p2[1] - p1[1]))

    above = f >= level
    ys, xs = np.nonzero(above[:-1, :-1] ^ above[1:, :-1] | above[:-1, :-1] ^ above[:-1, 1:] | above[1:, 1:] ^ above[1:, :-1])
    for y, x in zip(ys, xs):
        tl, tr, br, bl = f[y, x], f[y, x + 1], f[y + 1, x + 1], f[y + 1, x]
        c = (tl >= level) * 8 + (tr >= level) * 4 + (br >= level) * 2 + (bl >= level) * 1
        if c in (0, 15):
            continue
        T = interp((x, y), (x + 1, y), tl, tr)
        R = interp((x + 1, y), (x + 1, y + 1), tr, br)
        B = interp((x, y + 1), (x + 1, y + 1), bl, br)
        L = interp((x, y), (x, y + 1), tl, bl)
        table = {
            1: [(L, B)], 2: [(B, R)], 3: [(L, R)], 4: [(T, R)], 5: [(L, T), (B, R)], 6: [(T, B)], 7: [(L, T)],
            8: [(T, L)], 9: [(T, B)], 10: [(T, R), (L, B)], 11: [(T, R)], 12: [(L, R)], 13: [(B, R)], 14: [(L, B)],
        }
        segs.extend(table[c])

    # link segments into loops
    key = lambda p: (round(p[0], 4), round(p[1], 4))
    nbrs = {}
    for a, b in segs:
        nbrs.setdefault(key(a), []).append(key(b))
        nbrs.setdefault(key(b), []).append(key(a))
    seen, loops = set(), []
    for start in list(nbrs):
        if start in seen:
            continue
        loop, prev, cur = [start], None, start
        seen.add(start)
        while True:
            nxt = [n for n in nbrs[cur] if n != prev and (n not in seen or (n == start and len(loop) > 2))]
            if not nxt:
                break
            prev, cur = cur, nxt[0]
            if cur == start:
                break
            loop.append(cur)
            seen.add(cur)
        if len(loop) > 8:
            loops.append([(x - 1, y - 1) for x, y in loop])  # undo padding
    return loops


def rdp(points, eps):
    pts = np.asarray(points)
    if len(pts) < 3:
        return pts.tolist()
    start, end = pts[0], pts[-1]
    d = end - start
    n = np.hypot(*d)
    dist = np.abs((d[0] * (pts[:, 1] - start[1]) - d[1] * (pts[:, 0] - start[0]))) / n if n else np.hypot(*(pts - start).T)
    i = int(np.argmax(dist))
    if dist[i] > eps:
        return rdp(pts[: i + 1], eps)[:-1] + rdp(pts[i:], eps)
    return [start.tolist(), end.tolist()]


def simplify_loop(loop, eps=0.6):
    # split the closed loop at its farthest point pair so RDP works on two open halves
    pts = np.asarray(loop)
    far = int(np.argmax(np.hypot(*(pts - pts[0]).T)))
    a = rdp(np.vstack([pts[: far + 1]]), eps)
    b = rdp(np.vstack([pts[far:], pts[:1]]), eps)
    return a[:-1] + b[:-1]


def main(src, out):
    grey, red, im = masks(src)
    alpha = im[..., 3]
    ys, xs = np.nonzero(alpha > 0.5)
    bbox = [int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1]

    def avg_color(mask):
        sel = mask > 0.95
        rgb = (im[..., :3][sel].mean(axis=0) * 255).round().astype(int)
        return "#%02X%02X%02X" % tuple(rgb)

    result = {"bbox": bbox, "colors": {"grey": avg_color(grey), "red": avg_color(red)}}
    for name, m in (("grey", grey), ("red", red)):
        loops = [simplify_loop(l) for l in marching_squares(m)]
        result[name] = loops
        print(name, "loops:", [len(l) for l in loops])
    json.dump(result, open(out, "w"))
    print("bbox", bbox, result["colors"])


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
