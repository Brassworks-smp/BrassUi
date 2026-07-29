import { Page, PageHeader, PageBody } from "brassui-react";
import { Boxes, Ruler, Paintbrush, MousePointer2, Layers, MonitorPlay } from "lucide-react";
import { Code, Mono } from "../components/Code";
import { WidgetLink } from "../components/WidgetLink";
import { Link } from "react-router-dom";

/** Section shell: an icon, a title, a lead line, then whatever you pass. Mirrors the "How it works" page. */
function Block({
  icon: Icon,
  title,
  lead,
  children,
}: {
  icon: typeof Boxes;
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
      <div className="min-w-0 space-y-3">{children}</div>
    </section>
  );
}

function P({ children }: { children: React.ReactNode }) {
  return <p className="text-sm leading-relaxed text-ink-600">{children}</p>;
}

export function Elementa() {
  return (
    <Page>
      <PageHeader
        title="Using Elementa"
        subtitle="The library brassui is built on, and how you actually drive it"
        icon={<Boxes size={18} className="text-brass-400" />}
      />
      <PageBody className="stagger">
        <p className="max-w-3xl text-sm leading-relaxed text-ink-600">
          brassui is a <em>look and a widget set</em> on top of{" "}
          <a href="https://github.com/EssentialGG/Elementa" target="_blank" rel="noreferrer" className="text-brass-300 hover:text-brass-200">Elementa</a>,
          the constraint-based UI library from Essential. Elementa gives you a tree of components and a
          language for placing them - &quot;this box is 4&nbsp;pixels below that one, half as wide as its
          parent, centred&quot;. You will spend most of your time writing exactly that, so it is worth
          knowing the five moving parts. Everything below is plain Elementa; where brassui adds something,
          it says so.
        </p>

        <Block
          icon={Boxes}
          title="Everything is a component"
          lead="A screen is a tree of UIComponents. You build one by making children and parenting them with childOf."
        >
          <P>
            <Mono>UIComponent</Mono> is the base of the whole tree. <Mono>UIContainer</Mono> is an
            invisible grouping box, <Mono>UIBlock</Mono> is a solid rectangle, <Mono>ScrollComponent</Mono>{" "}
            scrolls its children. You assemble a UI by constructing components and attaching each to a
            parent with the <Mono>childOf</Mono> infix - draw order is insertion order, so a later child
            draws on top.
          </P>
          <Code title="tree.kt">{`val row = UIContainer().constrain {
    width = 100.percent(); height = 20.pixels()
} childOf parent

BrassLabel("Ready").constrain { x = 4.pixels() } childOf row
BrassButton("Go", BrassAccent.BRASS) { start() }.constrain {
    x = 0.pixels(true)   // pin to the right edge
} childOf row`}</Code>
          <P>
            brassui&apos;s widgets are ordinary components - <WidgetLink name="BrassButton" />,{" "}
            <WidgetLink name="BrassPanel" />, <WidgetLink name="BrassLabel" /> all extend{" "}
            <Mono>UIComponent</Mono> (through <WidgetLink name="BrassWidget" />) - so they slot into the
            same tree as Elementa&apos;s own, and you never leave one world for the other.
          </P>
        </Block>

        <Block
          icon={Ruler}
          title="Constraints, not coordinates"
          lead="You don't set x = 40. You describe a relationship, and Elementa solves for the pixel every frame."
        >
          <P>
            Inside a <Mono>{"constrain { }"}</Mono> block you assign <Mono>x</Mono>, <Mono>y</Mono>,{" "}
            <Mono>width</Mono> and <Mono>height</Mono> to <em>constraints</em>. The common ones read like
            English: <Mono>12.pixels()</Mono>, <Mono>100.percent()</Mono>, <Mono>CenterConstraint()</Mono>,{" "}
            <Mono>SiblingConstraint(6f)</Mono> (below the previous sibling with a gap). They compose with
            arithmetic - <Mono>100.percent() - 24.pixels()</Mono> - and when you need a value nothing
            canned covers, <Mono>{"basicWidthConstraint { c -> … }"}</Mono> hands you the component and
            lets you return any float.
          </P>
          <Code title="constraints.kt">{`panel.constrain {
    x = CenterConstraint()
    y = 12.pixels()
    width = 100.percent() - 24.pixels()
    height = basicHeightConstraint { c -> c.children.sumOf { it.getHeight().toDouble() }.toFloat() }
}`}</Code>
          <P>
            Because a constraint is re-solved every frame, a resize or a font change just re-flows - you
            do not reposition anything by hand. This is also why brassui&apos;s{" "}
            <Link to="/layout" className="text-brass-300 hover:text-brass-200">layout guide</Link>{" "}
            leans on <WidgetLink name="BrassFlow" fallback="flow" /> and{" "}
            <WidgetLink name="BrassPanel" fallback="panels" /> rather than absolute positions.
          </P>
        </Block>

        <Block
          icon={Paintbrush}
          title="Drawing: one method, called every frame"
          lead="A component paints by overriding draw(). brassui narrows that to a friendlier hook."
        >
          <P>
            Elementa calls <Mono>draw(matrixStack)</Mono> on every component each frame. Override it,
            call <Mono>beforeDraw</Mono> first and <Mono>super.draw</Mono> last (that draws your children),
            and paint in between using the current resolved bounds from <Mono>getLeft()</Mono>/
            <Mono>getTop()</Mono>. brassui widgets rarely touch <Mono>draw</Mono> directly:{" "}
            <WidgetLink name="BrassWidget" /> already handles the keycap, hover, press and entrance, and
            hands subclasses a single <Mono>drawContent</Mono> with the pixel box and the live animated
            colours.
          </P>
          <Code title="Badge.kt">{`class Badge(var text: String) : BrassWidget() {
    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        // the frame, ring and shadow are already painted - just the interior
        BrassFont.draw(m, this, text, x + 4f, y + 4f, textColor, true)
    }
}`}</Code>
          <P>
            Clipping is Elementa&apos;s <Mono>ScissorEffect</Mono>, computed in real framebuffer pixels -
            which is the reason brassui widgets reserve a small bleed and why you scroll with{" "}
            <WidgetLink name="BrassScrollArea" /> rather than a bare container. The{" "}
            <Link to="/architecture" className="text-brass-300 hover:text-brass-200">how-it-works page</Link>{" "}
            has the full story.
          </P>
        </Block>

        <Block
          icon={MousePointer2}
          title="Events and the update loop"
          lead="Input is callbacks on the component; anything that changes over time runs in a per-frame hook."
        >
          <P>
            Attach input with <Mono>onMouseClick</Mono>, <Mono>onMouseEnter</Mono>,{" "}
            <Mono>onMouseScroll</Mono> and friends; the event carries <Mono>mouseButton</Mono> and{" "}
            <Mono>absoluteX/absoluteY</Mono>. The topmost component under the cursor gets it first. For
            anything that animates or polls, register <Mono>addUpdateFunc</Mono> - it fires once per
            frame, which is where a live chart or a ticking progress bar gets fed.
          </P>
          <Code title="events.kt">{`card.onMouseClick { e ->
    if (e.mouseButton == 1) showContextMenu(e.absoluteX, e.absoluteY)
}

bar.addUpdateFunc { _, _ -> bar.progress = (bar.progress + 0.004f) % 1f }`}</Code>
          <P>
            Keyboard is routed by <WidgetLink name="BrassScreen" />: Tab moves focus, Enter/Space activate
            whatever a click would, and it owns the toolkit shortcuts (Escape peels one layer at a time,
            and the two on the <Link to="/dev-tools" className="text-brass-300 hover:text-brass-200">dev-tools page</Link>).
          </P>
        </Block>

        <Block
          icon={Layers}
          title="A screen ties it together"
          lead="Extend BrassScreen, add your tree to its background root, and you have a working screen."
        >
          <P>
            <WidgetLink name="BrassScreen" /> is an Elementa <Mono>WindowScreen</Mono> pinned to the newest{" "}
            <Mono>ElementaVersion</Mono>, with the backdrop, theme, cursor and entrance cascade already
            wired. You parent content to its <Mono>background</Mono> and open it like any Minecraft screen.
          </P>
          <Code title="TeamScreen.kt">{`class TeamScreen : BrassScreen() {
    init {
        BrassPanel("TEAM").add(roster, BrassLabel("Restrictions")).constrain {
            x = CenterConstraint(); y = CenterConstraint(); width = 260.pixels()
        } childOf background
    }
}

Minecraft.getInstance().setScreen(TeamScreen())   // in game`}</Code>
          <P>
            That is the whole loop: components in a tree, placed by constraints, painting themselves each
            frame, taking input through callbacks. New to it? The{" "}
            <Link to="/getting-started" className="text-brass-300 hover:text-brass-200">getting-started page</Link>{" "}
            walks the first screen end to end.
          </P>
        </Block>

        <Block
          icon={MonitorPlay}
          title="Where it runs: in game and on the desktop"
          lead="The same tree, unchanged, runs inside Minecraft and as a standalone desktop app."
        >
          <P>
            brassui core is pure Kotlin and Elementa - it deliberately links against no Minecraft classes.
            In game it runs as a self-contained <strong>NeoForge mod</strong> (Elementa and UniversalCraft
            folded in, jar-in-jar); open the built-in gallery with <Mono>/brassui</Mono>. Off game the very
            same widgets run on <strong>standalone UniversalCraft</strong>, which gives a real GLFW/LWJGL
            window and an OpenGL surface with no game under it - every screenshot in this wiki was captured
            there.
          </P>
          <P>
            The two only differ where the game is genuinely needed - drawing an item, an entity, a player
            head, or reading pixels off the frame - and each of those is an interface a host binds
            (<Mono>BrassPlatform</Mono>, <Mono>BrassDemoCapture</Mono>); off game the widget falls back to a
            placeholder rather than crashing. That is the whole reason one component tree serves both.
          </P>
          <P>
            To <em>ship</em> the desktop build as something a user can double-click, it is wrapped by{" "}
            <Mono>jvm-bootstrap</Mono>, a small <strong>Rust</strong> launcher in{" "}
            <Mono>launcher/</Mono>. It bakes the app jar into a native single-file executable: on launch it
            finds a JRE that will actually run the bytecode (verifying each candidate by running it),
            downloads one from Adoptium into a per-user cache only if it has to - the one step that shows a
            progress window - then runs the jar and matches its exit code. A machine with Java sees the app
            start with no splash; a machine without it gets Java fetched once. Window size and scale are
            plain system properties, so the wrapper (and IDE run configs) drive geometry without a rebuild.
          </P>
        </Block>
      </PageBody>
    </Page>
  );
}
