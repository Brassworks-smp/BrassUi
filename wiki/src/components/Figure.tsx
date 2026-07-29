/**
 * A framed screenshot with a caption - for the full-window doc captures (dev tools, the demo browser),
 * as opposed to the per-widget `Shot`. Shown smooth rather than `pixelated`: these are large captures
 * scaled *down* to fit a column, where nearest-neighbour would only add aliasing.
 */
export function Figure({ src, alt, caption }: { src: string; alt: string; caption?: React.ReactNode }) {
  return (
    <figure className="overflow-hidden rounded-xl border border-edge bg-ink-950/60">
      <img
        src={`${import.meta.env.BASE_URL}${src}`}
        alt={alt}
        loading="lazy"
        className="block h-auto w-full"
      />
      {caption && (
        <figcaption className="border-t border-edge px-4 py-2.5 text-xs leading-relaxed text-ink-600">
          {caption}
        </figcaption>
      )}
    </figure>
  );
}
