import { Boxes } from "lucide-react";

/**
 * The brassui mark: the three-cubes glyph in brass on a faint brass tile. Matches `public/logo.svg`,
 * which is the standalone asset for use elsewhere (favicon, README, docs).
 */
export function Logo({ size = 32 }: { size?: number }) {
  return (
    <span
      className="grid shrink-0 place-items-center rounded-xl"
      style={{
        width: size,
        height: size,
        background: "color-mix(in srgb, var(--color-brass-500) 12%, transparent)",
      }}
    >
      <Boxes size={size * 0.6} strokeWidth={1.9} className="text-brass-400" />
    </span>
  );
}
