import { useEffect, useRef, useState } from "react";
import { Check } from "lucide-react";
import { ACCENT_COLORS, applyAccent } from "brassui-react";

const KEY = "brass-accent";

/** The launcher's swatch fill: a soft top-left-lit gradient, so a flat hex reads as a physical chip. */
const swatchBg = (c: string) =>
  `linear-gradient(to bottom right, color-mix(in srgb, ${c} 88%, #fff), color-mix(in srgb, ${c} 78%, #000))`;

const RAINBOW = "conic-gradient(from 0deg,#f43f5e,#f59e0b,#84cc16,#06b6d4,#6366f1,#a855f7,#f43f5e)";

/** Read the saved accent (call before first paint to avoid a flash). */
export function savedAccent(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

/**
 * The accent row, straight out of the launcher: gradient chips that pop on hover and wear a check when
 * chosen, plus a rainbow chip that opens the OS colour picker for anything off-palette. Picking one
 * re-derives the whole brass ramp live and remembers it.
 */
export function AccentPicker() {
  const [accent, setAccent] = useState<string | null>(savedAccent);
  const customRef = useRef<HTMLInputElement>(null);
  const onPalette = accent != null && ACCENT_COLORS.some((c) => c.toLowerCase() === accent.toLowerCase());
  const customActive = accent != null && !onPalette;

  useEffect(() => {
    applyAccent(accent);
    try {
      if (accent) localStorage.setItem(KEY, accent);
      else localStorage.removeItem(KEY);
    } catch {
      /* ignore */
    }
  }, [accent]);

  return (
    <div className="border-t border-edge px-4 py-3">
      <div className="mb-2 font-mc text-[11px] uppercase tracking-wide text-ink-600">Accent</div>
      <div className="flex flex-wrap gap-1.5">
        {ACCENT_COLORS.map((c) => {
          const active = accent?.toLowerCase() === c.toLowerCase();
          return (
            <button
              key={c}
              onClick={() => setAccent(active ? null : c)}
              title={c}
              aria-label={`Accent ${c}`}
              style={{ backgroundImage: swatchBg(c) }}
              className={`grid h-6 w-6 place-items-center rounded-md shadow-sm transition hover:scale-110 ${
                active ? "scale-110 ring-2 ring-white/80" : "ring-1 ring-black/20"
              }`}
            >
              {active && (
                <Check size={12} strokeWidth={3.5} className="text-white [filter:drop-shadow(0_1px_1.5px_rgba(0,0,0,0.6))]" />
              )}
            </button>
          );
        })}

        {/* Custom colour - rainbow chip opens the native picker. */}
        <button
          onClick={() => customRef.current?.click()}
          title="Custom colour"
          aria-label="Custom accent colour"
          style={customActive ? { backgroundImage: swatchBg(accent!) } : { backgroundImage: RAINBOW }}
          className={`relative grid h-6 w-6 place-items-center rounded-md shadow-sm transition hover:scale-110 ${
            customActive ? "scale-110 ring-2 ring-white/80" : "ring-1 ring-black/20"
          }`}
        >
          {customActive && (
            <Check size={12} strokeWidth={3.5} className="text-white [filter:drop-shadow(0_1px_1.5px_rgba(0,0,0,0.6))]" />
          )}
          <input
            ref={customRef}
            type="color"
            value={accent ?? "#1fbf63"}
            onChange={(e) => setAccent(e.target.value)}
            className="absolute inset-0 h-full w-full cursor-pointer opacity-0"
            tabIndex={-1}
            aria-hidden
          />
        </button>
      </div>
    </div>
  );
}
