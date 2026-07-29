import { useState } from "react";
import { ImageOff } from "lucide-react";

/**
 * A whole-screen showcase preview for the gallery.
 *
 * The source is a transparent PNG the in-app capture writes - a brassui screen with everything behind
 * it dropped - so it is shown on the graph-paper stage the widget shots use, where its cut-out
 * silhouette (rounded corners, its own soft shadow) reads as a product shot rather than a speck in a
 * box. No ring or added shadow around the image: the frame would trace the transparent bounding box
 * rather than the UI, and the capture already carries its own shadow in the alpha.
 *
 * Drops in `public/screenshots/screens/<slug>.png` when it exists and shows a labelled placeholder with
 * the exact path until then, so the list in `data/gallery.ts` can lead the pictures.
 */
export function ScreenShot({ slug, title }: { slug: string; title: string }) {
  const [failed, setFailed] = useState(false);
  const src = `${import.meta.env.BASE_URL}screenshots/screens/${slug}.png`;

  if (failed) {
    return (
      <div className="grid aspect-[16/10] place-items-center rounded-xl border border-edge bg-ink-950 p-6 text-center">
        <div className="flex flex-col items-center gap-2 text-ink-600">
          <ImageOff size={24} />
          <div className="text-sm">No capture yet</div>
          <code className="rounded-md border border-edge bg-ink-900 px-2 py-1 text-[11px] text-brass-300">
            public/screenshots/screens/{slug}.png
          </code>
        </div>
      </div>
    );
  }

  return (
    <div className="shot-stage flex items-center justify-center overflow-hidden rounded-xl border border-edge p-4">
      <img
        src={src}
        alt={`${title} screenshot`}
        loading="lazy"
        onError={() => setFailed(true)}
        className="pixelated block h-auto w-full max-w-full"
      />
    </div>
  );
}
