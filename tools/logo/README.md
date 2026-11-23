# Logo tools

`logo-mark.png` (transparent) is the source. To regenerate every logo file after changing it:

```bash
cd tools/logo
python3 trace.py logo-mark.png mark.json   # PNG → vector outlines
python3 gen.py mark.json ../..             # writes launcher icon, splash animation, Compose paths
```

Needs Python 3 with `numpy` and `Pillow`. The script assumes the same layout as `logo-round.png`
(white circle, mark in the middle), a grey "b" and a red square.
