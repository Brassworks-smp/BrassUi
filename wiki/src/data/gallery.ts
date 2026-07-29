// Hand-edited - the full-screen showcases on the Gallery page. Unlike widgets.ts this is not
// generated: which whole screens are worth showing is an editorial call, not something read out of the
// Kotlin sources.
//
// Each entry is one brassui screen isolated onto transparency by the in-app capture - press
// Ctrl+Shift+S on the screen (or right-click the gallery and choose "Capture showcase"). Point the
// capture at this folder with
//   -Dbrassui.shots.dir=<repo>/wiki/public/screenshots/screens
// and it writes `<slug>.png` straight in. Until that file exists the page shows a labelled placeholder,
// so this list can lead the images - add the entry now, drop the capture in later.

export type Screen = {
  /** The capture's file stem (its showcaseName) and `public/screenshots/screens/<slug>.png`. */
  slug: string;
  title: string;
  blurb: string;
};

export const SCREENS: Screen[] = [
  {
    slug: "gallery",
    title: "Gallery screen",
    blurb:
      "The widget gallery itself - a nav rail of sections over a live overview, every control in the " +
      "toolkit on one screen. Captured straight off the running UI and cut out onto transparency.",
  },
];
