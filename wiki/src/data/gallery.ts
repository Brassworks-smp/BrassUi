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
  /** Short label shown under the shot. Not a description - this is a showcase, the picture leads. */
  title: string;
};

export const SCREENS: Screen[] = [
  { slug: "node-editor", title: "Node editor" },
  { slug: "gallery", title: "Gallery screen" },
  { slug: "exampleteamsui", title: "War room admin" },
  { slug: "skinchangerui", title: "Skin studio" },
  { slug: "showcase", title: "Title Machine" },
  { slug: "prettyinpink", title: "Rose accent" },
  { slug: "areyousure", title: "Teleport request" },
];
