import { Page, PageHeader, PageBody } from "brassui-react";
import { Layers, Paintbrush, Scissors, Palette, Plug, Camera } from "lucide-react";
import { Code, Mono } from "../components/Code";
import { WidgetLink } from "../components/WidgetLink";

/** Section shell: an icon, a title, a lead line, then whatever you pass. Full width, two-up on lg. */
function Block({
  icon: Icon,
  title,
  lead,
  children,
}: {
  icon: typeof Layers;
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

export function Architecture() {
  return (
    <Page>
      <PageHeader title="How it works" subtitle="The parts under the widgets" icon={<Layers size={18} className="text-brass-400" />} />
      <PageBody className="stagger">
        <p className="max-w-3xl text-sm leading-relaxed text-ink-600">
          brassui sits on top of Elementa, the constraint-based UI library from Essential. Elementa gives
          you a component tree and a way to say &quot;this box is 4 pixels below that one&quot;. brassui
          gives that tree a look, a theme, and a set of widgets that already know how to not clip
          themselves. Here is what is actually happening when a screen draws.
        </p>

        <Block icon={Layers} title="The widget tree" lead="Everything is an Elementa component. brassui adds one base class the whole kit shares.">
          <P>
            <WidgetLink name="BrassWidget" fallback="BrassWidget" /> extends Elementa&apos;s{" "}
            <Mono>UIComponent</Mono>. Every control you see is a subclass of it, and a subclass implements
            exactly one drawing method, <Mono>drawContent</Mono>, which is handed the current pixel bounds
            and the live animated colours. The base owns the rest: the keycap render, the hover and press
            transitions, the entrance animation, and hit testing.
          </P>
          <Code title="MyWidget.kt">{`class Badge(var text: String) : BrassWidget() {
    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        // paint your interior here; the frame, ring and shadow are already drawn
        BrassFont.draw(m, this, text, x + 4f, y + 4f, textColor, true)
    }
}`}</Code>
          <P>
            Containers work the same way. A <WidgetLink name="BrassPanel" /> is a component with a{" "}
            <Mono>paint()</Mono> hook that draws its card behind its children, so nesting a panel inside a
            scroll area is just adding a child.
          </P>
        </Block>

        <Block icon={Paintbrush} title="The keycap render" lead="One shape gives the toolkit its feel: a flat cap raised off the surface on a coloured lip.">
          <P>
            A resting control is a flat fill, a one-pixel inner border on all four sides, a one-pixel
            outer ring just outside that, and a two-to-three-pixel bottom edge made of a soft shadow and a
            coloured lip. That bottom edge is the whole trick: it reads as a physical key sitting above the
            page. <Mono>flat</Mono> drops the ring and lip for something recessed; <Mono>rounded</Mono>{" "}
            swaps to a rounded rect.
          </P>
          <div className="rounded-xl border border-edge bg-ink-950/50 p-5">
            <div className="mx-auto w-full max-w-xs">
              <div className="rounded-[3px] border border-black bg-ink-700 shadow-[0_3px_0_0_var(--color-brass-700)]">
                <div className="rounded-[2px] border border-white/10 px-4 py-3 text-center font-mc text-sm text-gray-100">
                  Keycap
                </div>
              </div>
              <div className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-[11px] text-ink-600">
                <span>fill + inner border</span>
                <span className="text-right">outer ring</span>
                <span>bottom lip = raised look</span>
                <span className="text-right">colours lerp per frame</span>
              </div>
            </div>
          </div>
          <P>
            Every colour on that cap (fill, border, text, ring, lip) eases toward its target every frame,
            so hover, selection and accent changes glide instead of snapping.
          </P>
        </Block>

        <Block icon={Scissors} title="Scissor and the bleed" lead="Clipping is done in real screen pixels, which is why widgets reserve a margin around themselves.">
          <P>
            Anything that scrolls or masks its contents uses Elementa&apos;s <Mono>ScissorEffect</Mono>,
            which computes its clip rectangle in actual framebuffer pixels. The catch: the keycap&apos;s
            outer ring and bottom lip are drawn <em>outside</em> the widget&apos;s nominal bounds. Put a
            capped control flush against a scroll edge and the scissor slices the lip off.
          </P>
          <P>
            So the layout primitives reserve a small bleed. The stacks and the scroll area inset their
            content by a fixed margin (one pixel on the sides and top, four at the bottom where the lip
            lives) so a card placed flush inside keeps its whole frame. This is why{" "}
            <WidgetLink name="BrassScrollArea" /> and <WidgetLink name="BrassPanel" /> never clip, and why
            you should reach for them instead of a bare Elementa container.
          </P>
          <Code>{`// the keycap bleeds past its box: reserve it, don't clip it
BLEED_X = 1f      // left / right
BLEED_TOP = 1f    // top
BLEED_BOTTOM = 4f // the raised lip lives here`}</Code>
        </Block>

        <Block icon={Palette} title="Colour is a role, not a value" lead="No widget stores a hex. It stores the name of a role and asks the live theme every frame.">
          <P>
            <Mono>Colors</Mono> exposes roles like &quot;the fill of an interactive control&quot; rather
            than swatches, and each role forwards to the theme in force. Assign{" "}
            <Mono>Colors.theme</Mono> and the entire kit follows, code blocks, washes and drop shadows
            included, and because widgets recompute their targets every frame the swap animates.
          </P>
          <Code>{`Colors.theme = BrassTheme.DARK   // anywhere; widgets ease over to it`}</Code>
          <P>
            The one rule this creates: never capture a role into a value at construction time. A separator
            that reads <Mono>Colors.UI_INNER_BORDER</Mono> once and keeps the colour will not retint when
            the theme changes. Hold the role as a lambda and resolve it inside your draw, and it tracks the
            theme like everything else.
          </P>
        </Block>

        <Block icon={Plug} title="The platform seam" lead="The core toolkit has no Minecraft classes. Anything that needs the game goes through an interface a host binds.">
          <P>
            brassui core is pure Kotlin and Elementa. It deliberately cannot see the game, which is what
            lets the desktop gallery run the same widgets with no Minecraft under them. Anything that does
            need the game (drawing an item, an entity, a player head, reading pixels off the frame) is
            declared as an interface. The mod binds a real implementation at startup; off-game the widget
            falls back to a placeholder instead of crashing.
          </P>
          <P>
            <Mono>BrassPlatform</Mono> is the seam for item and entity rendering;{" "}
            <Mono>BrassDemoCapture</Mono> is the seam for screenshots. Same pattern, same reason.
          </P>
        </Block>

        <Block icon={Camera} title="The capture pipeline" lead="The screenshots in this wiki are grabbed off the real frame by the demo browser in game.">
          <P>
            Each widget can declare a <Mono>BrassDemo</Mono> beside itself: how to build one, at what size,
            on what surface. The demo browser lays that out live, you interact with it by hand, and the
            shutter reads back the exact rectangle the widget occupies from the framebuffer. It captures
            the real screen rather than drawing offscreen precisely because scissor clips are computed in
            screen pixels, so a widget that masks its contents only comes out right when photographed where
            it actually rendered.
          </P>
          <P>
            A run of identical frames collapses to a PNG; a run that moves becomes a GIF. The file name is
            the demo&apos;s stable id, which is the same id this wiki looks for under{" "}
            <Mono>public/screenshots</Mono>.
          </P>
        </Block>
      </PageBody>
    </Page>
  );
}
