import { useState } from "react";
import { ImageOff } from "lucide-react";

/** Extensions tried in order: a still first, then an animated in-game capture. */
const EXTS = ["png", "gif"] as const;

/**
 * A widget preview.
 *
 * Every preview renders at the *same* scale (a constant fraction of the capture's own pixels), so a
 * checkbox stays small and a slider stays wide instead of each being blown up to fill an identical
 * frame. The frame is a graph-paper canvas with a modest minimum size: a big widget nearly fills it, a
 * tiny one sits centred on it like a product shot, so the small ones read as deliberate rather than lost.
 *
 * Drops in `public/screenshots/<slug>.{png,gif}` when it exists, and shows a labelled placeholder until
 * then, so the catalog is complete now and the pictures can be filled in later without touching code.
 */
export function Shot({
  slug,
  alt,
  scale = 0.5,
  className,
}: {
  slug: string;
  alt: string;
  /** CSS pixels per source pixel. Constant across previews, which is what makes their scale equal. */
  scale?: number;
  className?: string;
}) {
  const [ext, setExt] = useState(0);
  const [dims, setDims] = useState<{ w: number; h: number } | null>(null);
  const failed = ext >= EXTS.length;
  const src = failed ? "" : `${import.meta.env.BASE_URL}screenshots/${slug}.${EXTS[ext]}`;
  // Only known once the source that actually loaded is a .gif.
  const isGif = !failed && dims != null && EXTS[ext] === "gif";
  const width = dims ? dims.w * scale : undefined;

  if (failed) {
    return (
      <div
        className={`grid aspect-[16/10] place-items-center rounded-xl border border-edge bg-ink-950 p-4 text-center ${className ?? ""}`}
      >
        <div className="flex flex-col items-center gap-2 text-ink-600">
          <ImageOff size={22} />
          <div className="text-sm">No screenshot yet</div>
          <code className="rounded-md border border-edge bg-ink-900 px-2 py-1 text-[11px] text-brass-300">
            public/screenshots/{slug}.png
          </code>
        </div>
      </div>
    );
  }

  return (
    <div
      className={`shot-stage grid min-h-[190px] place-items-center overflow-hidden rounded-xl border border-edge p-5 ${className ?? ""}`}
    >
      <div className="relative flex w-fit max-w-full flex-col items-center gap-2">
        {isGif && (
          <span className="pointer-events-none absolute -right-1.5 -top-1.5 z-10 rounded-md border border-brass-600/40 bg-ink-950/90 px-1.5 py-0.5 font-mc text-[9px] uppercase tracking-widest text-brass-300">
            gif
          </span>
        )}
        <img
          // Keyed by ext so React remounts the <img> and retries the next source on error.
          key={ext}
          src={src}
          alt={alt}
          loading="lazy"
          onLoad={(e) => setDims({ w: e.currentTarget.naturalWidth, h: e.currentTarget.naturalHeight })}
          onError={() => setExt((x) => x + 1)}
          // Width is the capture's own pixels times the shared scale; height follows. max-w keeps it
          // inside a narrow column, shrinking uniformly there rather than overflowing.
          style={{ width, maxWidth: "100%" }}
          className="block h-auto rounded-md shadow-lg shadow-black/30 ring-1 ring-edge"
        />
        {isGif && (
          // A looping sweep under the capture, so it reads as an animation rather than a still.
          <div
            className="h-1 w-full overflow-hidden rounded-full bg-ink-800/80 ring-1 ring-edge"
            style={{ width, maxWidth: "100%" }}
            title="Animated capture"
          >
            <div className="gif-bar h-full rounded-full bg-brass-500" />
          </div>
        )}
      </div>
    </div>
  );
}
