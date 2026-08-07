import { useMemo, useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import {
  Search,
  Home,
  Rocket,
  Cpu,
  Puzzle,
  Palette,
  LayoutGrid,
  Boxes,
  Images,
  Wrench,
  Workflow,
  Radio,
  ExternalLink,
} from "lucide-react";
import { inputCls, cx } from "brassui-react";
import { WIDGETS, CATEGORIES } from "../data/widgets";
import { META } from "../data/meta";
import { Logo } from "./Logo";
import { AccentPicker } from "./AccentPicker";

const GUIDE = [
  { to: "/", label: "Overview", icon: Home, end: true },
  { to: "/getting-started", label: "Getting started", icon: Rocket },
  { to: "/architecture", label: "How it works", icon: Cpu },
  { to: "/elementa", label: "Using Elementa", icon: Puzzle },
  { to: "/design", label: "Design guide", icon: Palette },
  { to: "/layout", label: "Layout guide", icon: LayoutGrid },
  { to: "/widgets", label: "All widgets", icon: Boxes, end: true },
  { to: "/gallery", label: "Gallery", icon: Images, end: true },
  { to: "/dev-tools", label: "Dev tools", icon: Wrench },
  { to: "/node-editor", label: "Node editor", icon: Workflow },
  { to: "/networking", label: "Networking", icon: Radio },
];

function itemCls(active: boolean) {
  return cx(
    "flex items-center gap-2 rounded-md px-2.5 py-1.5 text-sm transition",
    active ? "bg-brass-500/15 text-brass-200" : "text-ink-600 hover:bg-ink-800 hover:text-gray-200",
  );
}

export function Shell() {
  const [q, setQ] = useState("");
  const query = q.trim().toLowerCase();

  const groups = useMemo(() => {
    return CATEGORIES.map((c) => ({
      ...c,
      items: WIDGETS.filter(
        (w) =>
          w.categoryDir === c.dir &&
          (!query ||
            w.name.toLowerCase().includes(query) ||
            w.summary.toLowerCase().includes(query)),
      ),
    })).filter((c) => c.items.length > 0);
  }, [query]);

  return (
    <div className="flex h-full">
      <aside className="flex w-64 shrink-0 flex-col border-r border-edge bg-ink-950/40">
        <div className="flex items-center gap-2.5 border-b border-edge px-4 py-4">
          <Logo size={34} />
          <div className="min-w-0">
            <div className="font-mc text-sm text-gray-100">brassui</div>
            <div className="text-[11px] text-ink-600">widget wiki</div>
          </div>
        </div>

        <div className="p-3">
          <div className="relative">
            <Search
              size={14}
              className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-ink-600"
            />
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search widgets…"
              className={cx(inputCls, "pl-8")}
            />
          </div>
        </div>

        <nav className="menu-scroll flex-1 overflow-y-auto px-3 pb-6">
          {!query && (
            <div className="mb-4 flex flex-col gap-0.5">
              {GUIDE.map((g) => (
                <NavLink key={g.to} to={g.to} end={g.end} className={({ isActive }) => itemCls(isActive)}>
                  <g.icon size={15} />
                  {g.label}
                </NavLink>
              ))}
            </div>
          )}

          {groups.map((c) => (
            <div key={c.dir} className="mb-4">
              <div className="mb-1 px-2.5 font-mc text-[11px] uppercase tracking-wide text-brass-300/80">
                {c.label}
              </div>
              <div className="flex flex-col gap-0.5">
                {c.items.map((w) => (
                  <NavLink
                    key={w.slug}
                    to={`/widgets/${w.slug}`}
                    className={({ isActive }) => itemCls(isActive)}
                  >
                    <span className="truncate font-mc text-[13px]">{w.name}</span>
                  </NavLink>
                ))}
              </div>
            </div>
          ))}
          {groups.length === 0 && (
            <div className="px-2.5 text-sm text-ink-600">Nothing matches “{q}”.</div>
          )}
        </nav>

        <AccentPicker />

        <a
          href={META.repoUrl}
          target="_blank"
          rel="noreferrer"
          className="flex items-center gap-2 border-t border-edge px-4 py-3 text-xs text-ink-600 transition hover:text-brass-300"
        >
          <ExternalLink size={13} />
          {META.owner}/{META.repo}
        </a>
      </aside>

      <main className="flex min-w-0 flex-1 flex-col">
        <Outlet />
      </main>
    </div>
  );
}
