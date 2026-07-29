import { Page, PageHeader, PageBody } from "brassui-react";
import { Wrench, ScanLine, Camera, ImageDown } from "lucide-react";
import { Code, Mono } from "../components/Code";
import { Figure } from "../components/Figure";
import { Link } from "react-router-dom";

/** A keyboard shortcut, rendered as a little key. */
function Kbd({ children }: { children: React.ReactNode }) {
  return (
    <kbd className="rounded-md border border-edge bg-ink-900 px-1.5 py-0.5 font-mc text-[11px] text-brass-200">
      {children}
    </kbd>
  );
}

function Block({
  icon: Icon,
  title,
  lead,
  children,
}: {
  icon: typeof Wrench;
  title: string;
  lead: string;
  children: React.ReactNode;
}) {
  return (
    <section className="grid grid-cols-1 gap-5 border-t border-edge py-7 lg:grid-cols-[minmax(0,20rem)_1fr]">
      <div>
        <div className="flex items-center gap-2">
          <div className="grid h-8 w-8 place-items-center rounded-lg bg-brass-500/12 text-brass-300">
            <Icon size={15} />
          </div>
          <h2 className="font-mc text-base text-gray-100">{title}</h2>
        </div>
        <p className="mt-2 text-sm leading-relaxed text-ink-600">{lead}</p>
      </div>
      <div className="min-w-0 space-y-4">{children}</div>
    </section>
  );
}

function P({ children }: { children: React.ReactNode }) {
  return <p className="text-sm leading-relaxed text-ink-600">{children}</p>;
}

export function DevTools() {
  return (
    <Page>
      <PageHeader
        title="Dev tools"
        subtitle="The inspector and the capture tools built into every screen"
        icon={<Wrench size={18} className="text-brass-400" />}
      />
      <PageBody className="stagger">
        <p className="max-w-3xl text-sm leading-relaxed text-ink-600">
          Every <Link to="/elementa" className="text-brass-300 hover:text-brass-200">BrassScreen</Link>{" "}
          ships with a layout inspector and two ways to photograph what is on it - the same tools that
          produced the screenshots across this wiki. They work identically in game and in the standalone
          desktop app, and none of it needs a special build.
        </p>

        <Block
          icon={ScanLine}
          title="Layout inspector"
          lead="A browser-style dev panel: the component tree, a live perf readout, and a Chrome box model on whatever you point at."
        >
          <P>
            Press <Kbd>Ctrl+Shift+D</Kbd> on any screen. A panel docks to the right edge and the content
            squeezes over to meet it, exactly like a browser&apos;s dev tools. The panel carries a compact
            performance readout - fps and frame time, painted widgets, quads, components and glyph runs -
            the full UI tree, colour-tagged by kind and expand/collapse, and a details pane for the
            selected element. Over the finished frame it draws a box model for whatever is under the
            cursor: content in blue, padding in green, and the keycap&apos;s bleed as margin in orange.
          </P>
          <Figure
            src="screenshots/dev/devmode.png"
            alt="The brassui layout inspector docked to the gallery"
            caption={
              <>
                The inspector open on the desktop gallery. Right: the docked panel - perf readout, the
                component tree with a <Mono>BrassButton</Mono> selected, and its details (position, size,
                flags). Left: every widget outlined, with the picked button&apos;s box model and a metadata
                tooltip.
              </>
            }
          />
          <P>
            Two toggles live in the panel header. <strong>Outlines</strong> turns the blue box grid on
            every widget on and off - on by default, since seeing the whole layout at once is most of the
            point, off when you want to read one element against the real UI. <strong>Pick</strong> is
            Chrome&apos;s inspect-element arrow: the next click selects whatever is under the cursor (and
            still activates it), then reveals it in the tree.
          </P>
          <P>
            It is careful not to lie about its own cost: the panel draws under a layer that pauses the stats
            collector, so opening the inspector never moves the numbers it reports.
          </P>
        </Block>

        <Block
          icon={Camera}
          title="Widget capture - the demo browser"
          lead="Every widget declares a demo beside itself; the browser stages it live and the shutter reads it back off the frame."
        >
          <P>
            The captures on each widget page come from here. A widget declares a <Mono>BrassDemo</Mono> -
            how to build one, at what size, on what surface - and the demo browser lays that out live so you
            can drive it by hand: open the accordion, drag the slider, type in the field, then capture the
            state you want. Reach it from the gallery&apos;s <Mono>Demos</Mono> section (or{" "}
            <Mono>/brassui demo &lt;name&gt;</Mono> in game).
          </P>
          <Figure
            src="screenshots/dev/demo-browser.png"
            alt="The demo browser with a button demo staged"
            caption={
              <>
                The demo browser: pick a widget on the left, it stages live in the centre, and{" "}
                <Mono>Screenshot</Mono> / <Mono>Record</Mono> capture it. The caption shows the demo id and
                its exact size - here <Mono>button · 146 × 36</Mono>.
              </>
            }
          />
          <P>
            <Mono>Screenshot</Mono> (<Kbd>Ctrl+S</Kbd>) writes a PNG of the exact rectangle the widget
            occupies; <Mono>Record</Mono> (<Kbd>Ctrl+R</Kbd>) captures until you stop - a run that moves
            becomes a GIF, a run that sits still collapses back to a PNG. It reads the widget straight off
            the framebuffer rather than re-drawing it offscreen, because a scissor clip is resolved in
            screen pixels: a widget that masks its contents only comes out right photographed where it
            actually rendered. The file is named for the demo&apos;s stable id, which is the same id this
            wiki looks for under <Mono>public/screenshots</Mono>.
          </P>
        </Block>

        <Block
          icon={ImageDown}
          title="Showcase capture - a whole screen, isolated"
          lead="Photograph an entire screen and cut it out onto transparency, background dropped."
        >
          <P>
            Where the demo browser captures one widget, the showcase capture takes a <em>whole screen</em>{" "}
            for a hero shot. Press <Kbd>Ctrl+Shift+S</Kbd> on any screen (or right-click the gallery and
            choose <Mono>Capture showcase</Mono>). It writes a transparent PNG of just the brassui pixels -
            the game world, or whatever sat behind the UI, dropped.
          </P>
          <Figure
            src="screenshots/screens/gallery.png"
            alt="The gallery screen isolated onto transparency"
            caption={
              <>
                The gallery captured this way - the window cut out onto transparency, its own soft shadow
                and rounded corners preserved in the alpha. The same shot leads the{" "}
                <Link to="/gallery" className="text-brass-300 hover:text-brass-200">Gallery</Link> page.
              </>
            }
          />
          <P>
            The framebuffer has no usable alpha once the GUI has drawn, so the cut-out is not a colour key.
            The capture repaints the UI twice in one frame - once over black, once over white - and the gap
            between the two readings is exactly how much background still showed through, which recovers a
            true per-pixel alpha. Anti-aliased corners and the window&apos;s shadow come out as real partial
            transparency rather than a hard fringe. Drop the result into{" "}
            <Mono>public/screenshots/screens/</Mono> and it appears on the gallery page.
          </P>
          <Code title="capture.kt">{`// point either capture at the wiki, and the files land where the pages look:
// -Dbrassui.shots.dir=<repo>/wiki/public/screenshots           # widget shots
// -Dbrassui.shots.dir=<repo>/wiki/public/screenshots/screens   # showcase shots`}</Code>
        </Block>
      </PageBody>
    </Page>
  );
}
