# Aria app icon: design spec

<p align="center"><img src="icon.png" width="160" alt="Aria icon" /></p>

Same system as the Minutia icon, so the two apps read as a set.

## Concept
**"a."**: the first letter of *Aria* followed by a round coral period. One letter, one dot,
said plainly: a short, finished phrase, like the song it's named after.

## Typeface
- **Inter Display ExtraBold** (800), lowercase `a` and `.` (Inter's round dot). SIL OFL.
- Real glyph outlines as-is: no custom letterforms, no rounded-stroke shapes.

## Colour
| Role | Hex |
|---|---|
| Background | `#121614` (ink; `@color/ic_launcher_background`, `drawable/ic_launcher_background.xml`) |
| Letter "a" | `#F0ECDE` (warm cream) |
| Period | `#FF8A65` (coral, the only colour) |

Monochrome layer = the same two shapes in one colour (themed icons).

## Geometry (108 dp canvas)
- Letter ink height **21.8 dp**, the same as Minutia's "m", so both icons sit at the same scale.
- Gap letter → period **0.07 em**. Mark centred on its ink (x ≈ 38.8 → 69.2, y ≈ 43.1 → 64.9).

## Files
Path data is **generated, never hand-edited**:
```bash
pip install fonttools cairosvg pillow
python3 docs/icon/make.py --font /usr/share/fonts/opentype/inter/InterDisplay-ExtraBold.otf --write
```
Writes `drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `docs/icon/icon.png`,
`docs/icon.png`, and `icon_preview.png` (circle, squircle, themed, 48 px).

## Rules
- Two shapes, two colours. No gradients, shadows, outlines or extra marks.
- No soft/bubbly rounded-stroke letterforms. Round dot only.

## History
| Design | Outcome |
|---|---|
| White equaliser bars on black | Generic |
| Cream "a" with play button in the bowl, purple gradient | Disliked |
| Rounded-stroke "a" with coral dot in the bowl | Disliked |
| **Inter Display ExtraBold "a.", cream + coral on ink** | **Current** |
