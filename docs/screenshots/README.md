# Screenshots

The three READMEs (`README.md`, `README.zh-CN.md`, `README.zh-TW.md`) each reference **five** images from this folder, laid out as a **single row of five columns** directly under the badges. They are committed alongside the documentation.

> **On narrow screens** (GitHub mobile, or a window under ~900 px) a five-column table squeezes each image to roughly 70 px, and GitHub makes the table scroll horizontally. That is the known trade-off of the one-row layout: the wide desktop view is compact and reads as a screenshot strip, while phone users see small thumbnails.

## Files referenced

| Filename | Screen | What it captures |
| --- | --- | --- |
| `library.png` | Library | Grid view with several books imported, so covers and progress are visible |
| `reader.png` | Reader | Reading view with the HUD visible |
| `selection.png` | Reader — text selection | Long-pressed selection with the action bubble (highlight / note / translate / ask AI / rewrite / continue) |
| `ai_chat.png` | Global AI tab | A conversation with the cross-book AI |
| `notes.png` | Notes | The notes centre with several annotation types present |

> **There is no `graph.png`.** An earlier draft of the READMEs referenced a relationship-graph screenshot; it was removed because the capture was not available. If you add one later, extend the **single** five-column table in all three READMEs to six columns — do not add a second row.

## Capture guidelines

- **Capture from a real device, not a design mockup.**
- Use one language for the whole set. The current set is **English**, matching the English-primary `README.md`. For localized sets, name them `library.en.png` / `library.zh-CN.png` / `library.zh-TW.png` and keep the default filenames as the English set.
- Use the **default appearance theme (Sky Blue)** and keep light/dark mode consistent across the set.
- Keep the same device, portrait orientation and (ideally) the same status-bar state.
- Import two or three **public-domain** books so nothing private appears.
- **Downscale to 720–1080 px wide before committing.** Full-resolution phone captures run 1–2 MB each and the current set totals ~4.8 MB; at 720 px they are typically 100–200 KB each. `library.png` (1.96 MB) and `selection.png` (1.35 MB) are the two worth re-exporting smallest.
- PNG is fine for UI captures. If you convert to WebP for size, update the extensions in all three READMEs too.
- **Check that nothing private is visible** — no real reading history, no personal file paths, no notification content in the status bar.

## How to replace or add

1. Copy the PNGs into this folder (`docs/screenshots/`), using exactly the filenames in the table above.
2. Verify:

   ```powershell
   Get-ChildItem docs\screenshots
   ```

3. Commit and push:

   ```powershell
   git add docs/screenshots
   git commit -m "docs: update screenshots"
   git push
   ```

No README edits are needed when replacing an existing file — all three already reference these five paths.

## Other captures worth having later

Not referenced by any README today:

- **Relationship graph** — the most distinctive feature; worth adding as a sixth slot
- Reading statistics with the bar chart
- **Settings → Language** on its own (its own page now, one language per row with a check mark)
- Settings → Feedback, showing the three contact channels
- Settings → Appearance (colour theme, light/dark mode, book grid)
- AI provider settings
