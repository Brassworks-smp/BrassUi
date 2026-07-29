# Screen showcases

Whole-screen captures for the **Gallery** page (per-widget shots live one folder up).

Each is a brassui screen isolated onto transparency by the in-app capture: press **Ctrl+Shift+S** on
the screen - or right-click the gallery and choose **Capture showcase**. Point the capture here with

```
-Dbrassui.shots.dir=<repo>/wiki/public/screenshots/screens
```

and it writes `<slug>.png` straight in. The slug is the capture's `showcaseName`.

- File name: `<slug>.png` - e.g. `gallery.png` for the gallery screen.
- Add the entry to `src/data/gallery.ts` first; until the file exists the page shows a labelled
  placeholder with the exact path, so the list can lead the images.
- The captures are transparent PNGs of pixel art - the page renders them `image-rendering: pixelated`.
