import { Link } from "react-router-dom";
import { Page, PageBody } from "brassui-react";
import { Rocket, Palette, LayoutGrid, Boxes, Cpu, Puzzle, Wrench, ArrowRight } from "lucide-react";
import { WIDGETS, CATEGORIES } from "../data/widgets";
import { Code } from "../components/Code";
import { Logo } from "../components/Logo";

const GUIDES = [
  { to: "/getting-started", icon: Rocket, title: "Getting started", body: "Drop brassui into a mod and get a real screen on the display in a few minutes." },
  { to: "/architecture", icon: Cpu, title: "How it works", body: "The widget tree, the paint pass, the scissor bleed, and why colour is a role." },
  { to: "/elementa", icon: Puzzle, title: "Using Elementa", body: "Components, constraints, drawing and events - the library you actually drive." },
  { to: "/design", icon: Palette, title: "Design guide", body: "The house style: keycaps on ink, one brass accent, split pages, colour by role." },
  { to: "/layout", icon: LayoutGrid, title: "Layout guide", body: "Panels, scroll areas, modals, and the wrap-by-default rule that keeps screens honest." },
  { to: "/dev-tools", icon: Wrench, title: "Dev tools", body: "The layout inspector and the capture tools built into every screen." },
];

export function Home() {
  const withDemo = WIDGETS.filter((w) => w.demoName).length;
  return (
    <Page>
      <PageBody className="stagger">
        <section className="relative overflow-hidden rounded-2xl border border-edge p-8 play-hero-glass">
          <div className="relative z-10 flex items-start gap-4">
            <Logo size={46} />
            <div>
              <div className="font-mc text-xs uppercase tracking-widest text-brass-300">brassui</div>
              <h1 className="mt-1.5 max-w-2xl font-mc text-3xl leading-tight text-gray-100">
                Minecraft screens that don&apos;t look like Minecraft screens.
              </h1>
            </div>
          </div>
          <p className="relative z-10 mt-4 max-w-2xl text-sm leading-relaxed text-ink-600">
            brassui is the widget kit behind the Brassworks launcher, ported into the game. Raised keycaps
            on near-black ink, one brass accent doing all the pointing, cards that never clip their own
            border, and pages that wrap instead of overflow. You compose Elementa components; the kit
            handles the pixels, the theming, and the thousand small alignment fights so you don&apos;t.
          </p>
          <div className="relative z-10 mt-5 flex flex-wrap gap-2">
            <Link
              to="/getting-started"
              className="pressable inline-flex items-center gap-2 rounded-lg bg-brass-500 px-4 py-2 text-sm font-medium text-ink-950 transition hover:bg-brass-400"
            >
              <Rocket size={15} /> Get started
            </Link>
            <Link
              to="/widgets"
              className="pressable inline-flex items-center gap-2 rounded-lg border border-edge px-4 py-2 text-sm text-gray-200 transition hover:border-brass-600/40 hover:text-brass-200"
            >
              <Boxes size={15} /> Browse {WIDGETS.length} widgets
            </Link>
          </div>
        </section>

        {/* A taste of the API, right up front. */}
        <div className="mt-6 grid grid-cols-1 gap-5 lg:grid-cols-[1fr_1.1fr]">
          <div className="rounded-2xl border border-edge bg-ink-900/50 p-6">
            <h2 className="font-mc text-sm text-gray-100">The shape of it</h2>
            <p className="mt-2 text-sm leading-relaxed text-ink-600">
              A screen is a header over a scrolling body. The body holds panels. Panels hold rows, and
              rows wrap when the window gets tight. That is the whole mental model, and it is the same one
              the launcher uses.
            </p>
            <div className="mt-4 flex flex-wrap gap-4 text-center">
              <Stat n={WIDGETS.length} label="widgets" />
              <Stat n={CATEGORIES.length} label="categories" />
              <Stat n={withDemo} label="live demos" />
            </div>
          </div>
          <Code title="TeamScreen.kt">{`BrassScrollArea().childOf(this).add(
    BrassPanel("TEAM").add(
        roster,
        BrassLabel("Restrictions"),
    ).also {
        // this row wraps onto a second line when it runs out of width
        it.row(18f, editButton, copyButton, deleteButton)
    },
)`}</Code>
        </div>

        <h2 className="mb-3 mt-8 font-mc text-xs uppercase tracking-widest text-brass-300">Read next</h2>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {GUIDES.map((g) => (
            <Link
              key={g.to}
              to={g.to}
              className="hover-lift group flex items-start gap-3 rounded-xl border border-edge bg-ink-900/50 p-4"
            >
              <div className="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-brass-500/12 text-brass-300">
                <g.icon size={16} />
              </div>
              <div className="min-w-0">
                <div className="flex items-center gap-1.5 font-mc text-sm text-gray-100">
                  {g.title}
                  <ArrowRight size={13} className="opacity-0 transition group-hover:translate-x-0.5 group-hover:opacity-100" />
                </div>
                <p className="mt-1 text-xs leading-relaxed text-ink-600">{g.body}</p>
              </div>
            </Link>
          ))}
        </div>

        <h2 className="mb-3 mt-8 font-mc text-xs uppercase tracking-widest text-brass-300">The catalog</h2>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {CATEGORIES.map((c) => {
            const count = WIDGETS.filter((w) => w.categoryDir === c.dir).length;
            return (
              <Link
                key={c.dir}
                to="/widgets"
                className="hover-lift rounded-xl border border-edge bg-ink-900/50 p-4"
              >
                <div className="flex items-baseline justify-between">
                  <span className="font-mc text-sm text-gray-100">{c.label}</span>
                  <span className="font-mc text-xs tabular-nums text-brass-300">{count}</span>
                </div>
                <p className="mt-1.5 text-xs leading-relaxed text-ink-600">{c.blurb}</p>
              </Link>
            );
          })}
        </div>
      </PageBody>
    </Page>
  );
}

function Stat({ n, label }: { n: number; label: string }) {
  return (
    <div className="rounded-lg border border-edge bg-ink-950/40 px-4 py-2">
      <div className="font-mc text-xl tabular-nums text-brass-300">{n}</div>
      <div className="text-[11px] uppercase tracking-wide text-ink-600">{label}</div>
    </div>
  );
}
