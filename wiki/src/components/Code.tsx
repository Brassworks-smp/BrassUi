import { useMemo, useState } from "react";
import { Check, Copy } from "lucide-react";
import { SegmentedTabs, cx } from "brassui-react";
import { tokenize } from "../lib/highlight";

/** Render one code string as highlighted spans. */
function Highlighted({ code }: { code: string }) {
  const toks = useMemo(() => tokenize(code), [code]);
  return (
    <code className="block">
      {toks.map((t, i) =>
        t.t === "ws" || t.t === "plain" ? (
          <span key={i}>{t.v}</span>
        ) : (
          <span key={i} className={`tok-${t.t}`}>
            {t.v}
          </span>
        ),
      )}
    </code>
  );
}

function CopyButton({ text }: { text: string }) {
  const [done, setDone] = useState(false);
  return (
    <button
      onClick={() => {
        navigator.clipboard?.writeText(text).then(
          () => {
            setDone(true);
            setTimeout(() => setDone(false), 1200);
          },
          () => {},
        );
      }}
      title="Copy"
      aria-label="Copy code"
      className="pressable absolute right-2 top-2 grid h-6 w-6 place-items-center rounded-md border border-edge bg-ink-900/70 text-ink-600 opacity-0 transition hover:border-brass-600/40 hover:text-brass-300 group-hover:opacity-100"
    >
      {done ? <Check size={12} strokeWidth={3} className="text-brass-400" /> : <Copy size={12} />}
    </button>
  );
}

/**
 * A Kotlin/code block on the ink sink, syntax-highlighted, with a copy button that shows on hover.
 * The bar of dots up top is just there to read as "a snippet", the way an editor chrome would.
 */
export function Code({
  children,
  className,
  title,
}: {
  children: string;
  className?: string;
  title?: string;
}) {
  const code = children.replace(/\s+$/, "");
  return (
    <div className={cx("group relative overflow-hidden rounded-lg border border-edge bg-ink-950", className)}>
      {title && (
        <div className="flex items-center gap-1.5 border-b border-edge px-3 py-1.5">
          <span className="h-2 w-2 rounded-full bg-ink-700" />
          <span className="h-2 w-2 rounded-full bg-ink-700" />
          <span className="h-2 w-2 rounded-full bg-ink-700" />
          <span className="ml-1.5 font-mc text-[11px] text-ink-600">{title}</span>
        </div>
      )}
      <CopyButton text={code} />
      <pre className="overflow-x-auto p-3.5 text-[12.5px] leading-relaxed">
        <Highlighted code={code} />
      </pre>
    </div>
  );
}

/**
 * A build-script block with the two Gradle dialects side by side. Devs land on whichever they use, so
 * paste-ability beats picking a side. Remembers the last choice for the session.
 */
export function GradleBlock({ kotlin, groovy }: { kotlin: string; groovy: string }) {
  const [dsl, setDsl] = useState<string>(() => {
    try {
      return localStorage.getItem("brass-gradle-dsl") || "kotlin";
    } catch {
      return "kotlin";
    }
  });
  const pick = (v: string) => {
    setDsl(v);
    try {
      localStorage.setItem("brass-gradle-dsl", v);
    } catch {
      /* ignore */
    }
  };
  return (
    <div>
      <div className="mb-2 flex items-center justify-between gap-2">
        <SegmentedTabs
          value={dsl}
          onChange={pick}
          size="sm"
          options={[
            { id: "kotlin", label: "build.gradle.kts" },
            { id: "groovy", label: "build.gradle" },
          ]}
        />
        <span className="font-mc text-[11px] text-ink-600">{dsl === "kotlin" ? "Kotlin DSL" : "Groovy DSL"}</span>
      </div>
      <Code>{dsl === "kotlin" ? kotlin : groovy}</Code>
    </div>
  );
}

/** A short inline code chip, in the Monocraft code face. */
export function Mono({ children }: { children: string }) {
  return (
    <code className="rounded-md border border-edge bg-ink-950 px-1.5 py-0.5 font-mono text-[0.85em] text-brass-300">
      {children}
    </code>
  );
}
