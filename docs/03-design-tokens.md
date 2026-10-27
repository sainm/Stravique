# Design Tokens

## Visual direction

Stravique uses a restrained engineering-tool aesthetic: warm neutral canvas, crisp ink typography, one violet accent, fine borders, and dense evidence-first layouts. The interface should feel precise and inspectable rather than decorative.

## Color

| Token | Value | Use |
| --- | --- | --- |
| `--canvas` | `#f5f4f7` | Application canvas |
| `--surface` | `#ffffff` | Primary workspace |
| `--surface-subtle` | `#faf9fb` | Secondary panes and inputs |
| `--surface-strong` | `#efedf2` | Selected or nested neutral area |
| `--ink` | `#19171f` | Primary text |
| `--ink-muted` | `#696474` | Secondary text |
| `--ink-faint` | `#99929f` | Metadata and placeholders |
| `--line` | `#e5e1e8` | Default borders |
| `--line-strong` | `#d4ced9` | Focused or emphasized borders |
| `--accent` | `#6650d9` | Brand and primary actions |
| `--accent-deep` | `#4934bd` | Hover and active states |
| `--accent-soft` | `#efebff` | Selected rows and highlights |
| `--success` | `#247a5b` | Completed and confirmed states |
| `--success-soft` | `#e8f5ef` | Success surfaces |
| `--warning` | `#a66613` | Review and partial states |
| `--warning-soft` | `#fff4df` | Warning surfaces |
| `--danger` | `#b63d4c` | Failure states |
| `--danger-soft` | `#fdecef` | Failure surfaces |
| `--info` | `#49668f` | Informational states |
| `--info-soft` | `#edf3fa` | Information surfaces |

## Typography

| Token | Value |
| --- | --- |
| `--font-sans` | `"Avenir Next", "Segoe UI", "Hiragino Sans", "Noto Sans CJK SC", sans-serif` |
| `--font-mono` | `"SFMono-Regular", Consolas, "Liberation Mono", monospace` |
| `--text-display` | `clamp(2rem, 4vw, 3.25rem)` / 1.04 |
| `--text-title` | `1.25rem` / 1.25 |
| `--text-body` | `0.875rem` / 1.55 |
| `--text-meta` | `0.75rem` / 1.4 |
| `--text-micro` | `0.6875rem` / 1.35 |

Display and page titles are tight. Body copy balances readability with engineering density.

## Spacing and sizing

Base unit: 4 px.

Common values: 4, 8, 12, 16, 20, 24, 32, 40, 48, 64.

- Rail width: 232 px.
- Compact rail: 72 px.
- Top bar: 64 px.
- Control height: 36 px.
- Primary control height: 40 px.
- Inspector row min-height: 44 px.

## Shape and elevation

| Token | Value |
| --- | --- |
| `--radius-xs` | `4px` |
| `--radius-sm` | `7px` |
| `--radius-md` | `10px` |
| `--radius-lg` | `14px` |
| `--shadow-popover` | `0 16px 48px rgba(25, 23, 31, 0.13)` |
| `--shadow-dialog` | `0 24px 80px rgba(25, 23, 31, 0.2)` |

Borders carry most hierarchy. Shadows are limited to overlays.

## Motion

- Fast feedback: 120 ms.
- Normal transitions: 180 ms.
- Dialog entry: 220 ms.
- Easing: `cubic-bezier(0.2, 0.8, 0.2, 1)`.
- Scanning status reflects persisted state; no looping synthetic progress is shown.
- `prefers-reduced-motion` disables transforms and nonessential animation.

## Accessibility

- Text and interactive colors meet WCAG AA where used as content.
- Focus rings use a 3 px translucent violet halo.
- Status is communicated by icon and text, not color alone.
- Interactive targets are at least 32 px; primary actions are 40 px.
- Code evidence is horizontally scrollable and preserves whitespace.
- Every icon-only control has a localized accessible name and tooltip.
