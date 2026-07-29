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
          ships with a layout inspector and two capture tools - the same ones that shot this wiki. They
          work the same in game and on the desktop, no special build.
        </p>

        <Block
          icon={ScanLine}
          title="Layout inspector"
          lead="Browser-style dev panel: component tree, live perf readout, box model on hover."
        >
          <P>
            <Kbd>Ctrl+Shift+D</Kbd> docks a panel to the right edge. It shows a perf readout (fps, frame
            time, painted widgets/quads/components/glyphs), the full UI tree tagged by kind, and details
            for the selected element. On hover it draws a box model: content blue, padding green, margin
            orange.
          </P>
          <Figure
            src="screenshots/dev/devmode.png"
            alt="The brassui layout inspector docked to the gallery"
            caption={
              <>
                The inspector on the desktop gallery: docked panel with a <Mono>BrassButton</Mono>{" "}
                selected, every widget outlined, box model and tooltip on the pick.
              </>
            }
          />
          <P>
            Two header toggles: <strong>Outlines</strong> shows the box grid on every widget (on by
            default); <strong>Pick</strong> is Chrome&apos;s inspect arrow - next click selects and reveals
            in the tree. The panel pauses the stats collector while it draws, so it never skews its own
            numbers.
          </P>
        </Block>

        <Block
          icon={Camera}
          title="Widget capture - the demo browser"
          lead="Stages a widget's declared demo live; the shutter reads it back off the frame."
        >
          <P>
            The per-widget shots come from here. Each widget declares a <Mono>BrassDemo</Mono>; the browser
            stages it live so you can drive it - open the accordion, drag the slider - then capture. Reach
            it from the gallery&apos;s <Mono>Demos</Mono> section (or <Mono>/brassui demo &lt;name&gt;</Mono>{" "}
            in game).
          </P>
          <Figure
            src="screenshots/dev/demo-browser.png"
            alt="The demo browser with a button demo staged"
            caption={
              <>
                Pick a widget, it stages in the centre, <Mono>Screenshot</Mono> / <Mono>Record</Mono>{" "}
                capture it - here <Mono>button · 146 × 36</Mono>.
              </>
            }
          />
          <P>
            <Mono>Screenshot</Mono> (<Kbd>Ctrl+S</Kbd>) writes a PNG of the widget&apos;s exact rectangle;{" "}
            <Mono>Record</Mono> (<Kbd>Ctrl+R</Kbd>) captures until you stop - a moving run becomes a GIF, a
            still one a PNG. It reads straight off the framebuffer, so clipped widgets come out right. Files
            are named for the demo id this wiki looks up under <Mono>public/screenshots</Mono>.
          </P>
        </Block>

        <Block
          icon={ImageDown}
          title="Showcase capture - a whole screen, isolated"
          lead="Photograph an entire screen, cut out onto transparency."
        >
          <P>
            <Kbd>Ctrl+Shift+S</Kbd> on any screen (or right-click the gallery &rarr;{" "}
            <Mono>Capture showcase</Mono>) writes a transparent PNG of just the brassui pixels, background
            dropped - for a hero shot of a whole screen instead of one widget.
          </P>
          <Figure
            src="screenshots/screens/gallery.png"
            alt="The gallery screen isolated onto transparency"
            caption={
              <>
                The gallery cut out onto transparency, shadow and rounded corners kept in the alpha. Leads
                the <Link to="/gallery" className="text-brass-300 hover:text-brass-200">Gallery</Link> page.
              </>
            }
          />
          <P>
            The GUI leaves no usable alpha, so it isn&apos;t a colour key: the UI is repainted over black
            and over white in one frame, and the difference recovers true per-pixel alpha - clean corners
            and shadow, no fringe. Drop the result into <Mono>public/screenshots/screens/</Mono>.
          </P>
          <Code title="capture.kt">{`// point either capture at the wiki, and the files land where the pages look:
// -Dbrassui.shots.dir=<repo>/wiki/public/screenshots           # widget shots
// -Dbrassui.shots.dir=<repo>/wiki/public/screenshots/screens   # showcase shots`}</Code>
        </Block>
      </PageBody>
    </Page>
  );
}
