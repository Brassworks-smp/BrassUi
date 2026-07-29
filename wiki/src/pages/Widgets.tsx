import { useState, useMemo } from "react";
import { Link } from "react-router-dom";
import { Page, PageHeader, PageBody, SegmentedTabs, inputCls, cx } from "brassui-react";
import { Search } from "lucide-react";
import { WIDGETS, CATEGORIES } from "../data/widgets";

const KIND_TINT: Record<string, string> = {
  class: "border-brass-600/40 text-brass-300",
  object: "border-patina-500/40 text-patina-400",
  interface: "border-edge text-ink-600",
};

export function Widgets() {
  const [cat, setCat] = useState("all");
  const [q, setQ] = useState("");
  const query = q.trim().toLowerCase();

  const shown = useMemo(
    () =>
      WIDGETS.filter(
        (w) =>
          (cat === "all" || w.categoryDir === cat) &&
          (!query || w.name.toLowerCase().includes(query) || w.summary.toLowerCase().includes(query)),
      ),
    [cat, query],
  );

  return (
    <Page>
      <PageHeader
        title="Widgets"
        subtitle={`${WIDGETS.length} in the toolkit`}
        actions={
          <div className="relative w-56 max-w-full">
            <Search size={14} className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-ink-600" />
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Filter…" className={cx(inputCls, "pl-8")} />
          </div>
        }
      />
      <PageBody>
        <div className="mb-4">
          <SegmentedTabs
            value={cat}
            onChange={setCat}
            options={[{ id: "all", label: "All" }, ...CATEGORIES.map((c) => ({ id: c.dir, label: c.label }))]}
          />
        </div>

        <div className="stagger grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
          {shown.map((w) => (
            <Link
              key={w.slug}
              to={`/widgets/${w.slug}`}
              className="hover-lift flex flex-col gap-2 rounded-xl border border-edge bg-ink-900/50 p-4"
            >
              <div className="flex items-center gap-2">
                <span className="truncate font-mc text-sm text-gray-100">{w.name}</span>
                <span className={cx("ml-auto shrink-0 rounded-md border px-1.5 py-0.5 text-[10px]", KIND_TINT[w.kind])}>
                  {w.kind}
                </span>
              </div>
              <p className="line-clamp-3 text-xs leading-relaxed text-ink-600">
                {w.summary || "No description yet."}
              </p>
              <span className="mt-auto font-mc text-[11px] uppercase tracking-wide text-brass-300/70">
                {w.category}
              </span>
            </Link>
          ))}
        </div>
        {shown.length === 0 && <p className="text-sm text-ink-600">Nothing matches “{q}”.</p>}
      </PageBody>
    </Page>
  );
}
