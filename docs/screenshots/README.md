# Screenshots

The three READMEs (`README.md`, `README.zh-CN.md`, `README.zh-TW.md`) each reference **six** images from this folder, laid out as two rows of three. The images are **not committed yet** — please add real captures rather than mockups. Until they exist, each README shows six broken image placeholders.

## Files to provide

| Filename | Screen | What to capture |
| --- | --- | --- |
| `library.png` | Library | Grid view with several books imported, so covers and progress are visible |
| `reader.png` | Reader | Reading view with the HUD visible, ideally with the **Aa** panel open |
| `selection.png` | Reader — text selection | Long-pressed selection with the action bubble showing highlight / note / translate / ask AI / rewrite / continue |
| `graph.png` | Relationship graph | A generated graph in `GraphSheet` with visible nodes, edges and the legend |
| `ai_chat.png` | Global AI tab | A conversation, ideally with the `@` book-picker panel open |
| `notes.png` | Notes | The notes centre with several annotation types present |

> `stats.png` is **not** referenced by the READMEs. If you would rather show reading statistics than notes, replace the `notes.png` rows in all three READMEs — do not add a seventh image without also editing the tables.

## Capture guidelines

- **Capture from a real device or emulator, not a design mockup.**
- **Use one language for the whole set.** The current plan is **English**, matching the English-primary `README.md`. If you later add localized sets, name them `library.en.png` / `library.zh-CN.png` / `library.zh-TW.png` and keep the default filenames as the English set.
- Use the **default appearance theme (Sky Blue)**, and keep light/dark mode consistent across all six.
- Keep the same device, portrait orientation and (ideally) the same status-bar state across the set.
- Import two or three **public-domain** books so the screenshots contain nothing private.
- Downscale to **720–1080 px wide** before committing. Full-resolution phone captures are 3–5 MB each and six of them would add 20–30 MB to the repository; at 720 px they are typically 100–200 KB each.
- PNG is fine for UI captures. If you convert to WebP for size, update the file extensions in all three READMEs too.
- **Check that nothing private is visible** — no real reading history, no personal file paths, no notification content in the status bar.

## How to submit

1. Copy the six PNGs into this folder (`docs/screenshots/`), using exactly the filenames in the table above.
2. Verify they are in place:

   ```powershell
   Get-ChildItem docs\screenshots
   ```

3. Commit and push:

   ```powershell
   git add docs/screenshots
   git commit -m "docs: add UI screenshots"
   git push
   ```

No README edits are needed — all three already reference these six paths, so the broken placeholders become real images as soon as the files land.

## Other captures worth having later

Useful for future docs, but not referenced by any README today:

- Reading statistics with the bar chart
- **Settings → Language** on its own (it is its own page now, one language per row with a check mark)
- Settings → Feedback, showing the three contact channels
- Settings → Appearance (colour theme, light/dark mode, book grid)
- AI provider settings
- The trilingual pair, to demonstrate localization
