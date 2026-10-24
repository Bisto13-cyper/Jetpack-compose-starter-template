# Files Viewer

Replaces the old "Markdown / Coming soon" node. Id: `filesviewer`, title: "Files Viewer".

## How it behaves
- In **Files**, tapping a viewable file (json, jsonc, xml, svg, toml, sh, .env, csv, tsv, txt, log, md, yml, ini...) opens it in the Files Viewer node. Any other file opens with an external app exactly as before.
- Back in the viewer returns to **Files, in the same folder** (Files remembers its last location in memory).
- Opening the node from the Home circle / side menu with no file shows the Files screen in **pick mode** ("Choose a file"). Tapping any file opens it right there.
- Top bar: back, name + type + size, Raw/Pretty toggle (non-text types), Copy, and the folder button to pick another file.
- Works for Local, SAF and SSH files (read through `FileSource.openInput`). Files over 512 KB are cut with a notice; binary files show a message.

## New files (features/filesviewer/)
| File | Job |
|---|---|
| FilesViewerFeature.kt | The FeatureModule (circle + route) |
| FilesViewerScreen.kt | Picker mode, loading, top bar, picks the right viewer |
| FileOpenRouter.kt | Bridge: Files hands a file over, viewer picks it up once |
| FileKinds.kt | Name/extension -> viewer type. Add new types here |
| viewers/*.kt | The 7 viewers (package moved from `favorites.viewers`) |
| features/files/FilesLocationMemory.kt | Remembers the last Files folder |

## Modified files (complete content included)
- AppFeatures.kt: `ComingSoonFeature("markdown"...)` replaced by `FilesViewerFeature()`. The ComingSoonFeature class is kept (unused) in case other code references it.
- MainActivity.kt: connects `FileOpenRouter` (navigate through the secured route entry, goBack direct).
- FilesController.kt: `fileOpener` hook, `snapshot()` / `restore()`.
- FilesScreen.kt: `pickMode` / `onPick` params, installs the hook, saves the location on exit.

## Viewer fixes
- JsonViewer: string values were shown as numbers/booleans (quotes lost by re-parsing toString()). Now walks the org.json tree. Big arrays/objects load 200 items at a time. Icons replaced by text arrows (no material-icons-extended needed).
- XmlViewer: `<a>text</a>` was drawn as `<a />` plus text; DOCTYPE/CDATA broke the tree; entities not decoded; long tag lines were cut. All fixed.
- CsvViewer: wide tables can now scroll sideways; `delimiter` parameter (TSV); BOM and blank lines handled.
- Toml/Sh/Env: padding; TOML multi-line strings no longer mis-coloured.

## Add a file type
Add its extension to a set in `FileKinds.kt`. For a new viewer: add a `ViewerKind` entry, a `when` branch in `ShowViewer` (FilesViewerScreen.kt) and one line in `kindOf()`.
