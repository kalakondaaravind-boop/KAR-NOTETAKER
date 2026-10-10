# KAR Notes design system: Sky & Pastel Blue, 3D glossy

## Palette (`ui/theme/Theme.kt`)

| Token | Light | Dark |
|---|---|---|
| primary / onPrimary | `#2A76AD` / white (4.89:1) | `#9DD0F7` / `#00344F` (7.98:1) |
| primaryContainer / on | `#CDE7FA` / `#0A2A42` (11.53:1) | `#1D4E72` / `#CDE7FA` (6.88:1) |
| background / on | `#F4F9FE` / `#13212E` (15.43:1) | `#0E1822` / `#DCE6F0` (14.18:1) |
| surfaceVariant / on | `#DEE8F2` / `#3F4D5A` (6.99:1) | `#3C4855` / `#BCC8D4` (5.49:1) |
| tertiary (pins, stars) | `#5C6BB0` | `#BFC6F5` |

## Glossy layer (`ui/theme/Gloss.kt`)

A glossy surface has four parts:

- a soft coloured drop shadow, for depth;
- a top-to-bottom gradient, for curvature;
- a white sheen over the top 45%, drawn **under** the content;
- a light-to-dark hairline edge, for the bevel.

| Tone | Used for | Light gradient | Dark gradient | Worst-case text contrast* |
|---|---|---|---|---|
| PRIMARY | Main buttons, quick-action badges | `#2C74A9` → `#195685`, sheen 18% | `#AEDAFA` → `#85C2EF` | white 4.91:1 · dark 7.36:1 |
| PASTEL | FAB, quick-action tiles, backup banner | `#DDF0FD` → `#B9DCF5`, sheen 45% | `#285D84` → `#174264` | 10.9:1 · 5.4:1 |
| CARD | Home panels | `#FFFFFF` → `#EAF3FB`, sheen 60% | `#22303D` → `#17232E` | 7.73:1 · 6.6:1 |

\*Contrast is computed for text anywhere between 28% and 72% of the surface's height, with the sheen composited in. All results meet WCAG AA (≥ 4.5:1).

**Where the gloss is applied:**
- the logo and launcher icon (gradient, sheen and a shadow under the "K");
- the Home top bar and navigation rail (a soft gradient);
- the Home section panels, quick-action tiles and backup banner;
- the New note button;
- the main buttons in onboarding, the OpenAI popup and the backup banner.

Lists, the editor and settings stay flat on purpose. They are reading and working surfaces, where density and legibility matter more than depth.

**Switching it off:** Settings → Appearance → *3D glossy style*. Every glossy helper falls back to the flat Material 3 equivalent, so there is no separate layout to maintain.
