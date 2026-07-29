import { Page, PageHeader, PageBody, Card } from "brassui-react";
import { Code, Mono } from "../components/Code";
import { WidgetLink } from "../components/WidgetLink";

export function LayoutGuide() {
  return (
    <Page>
      <PageHeader title="Layout guide" subtitle="Arranging a screen" />
      <PageBody className="stagger">
        <p className="mb-5 max-w-3xl text-sm leading-relaxed text-ink-600">
          The primitives a screen is assembled from, and when to reach for each. Most UIs are a{" "}
          <WidgetLink name="BrassPanel" /> or two inside a <WidgetLink name="BrassScrollArea" />, with a{" "}
          <WidgetLink name="BrassFlow" /> for the rows that wrap. Get those three right and the rest falls
          into place.
        </p>

        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <Card title="BrassPanel · the card" className="h-full">
            <p className="text-sm leading-relaxed text-ink-600">
              A titled, self-contained card with padded content. It never clips its own border, the ring
              is drawn <i>inside</i> its bounds, so it is safe flush inside a scroll. It pads from{" "}
              <WidgetLink name="BrassSpacing" />. <Mono>.add()</Mono> stacks rows, <Mono>.row()</Mono> lays
              a wrapping button row, and <Mono>BrassPanel.hug()</Mono> sizes the card to its tallest child.
            </p>
            <Code title="TeamPanel.kt">{`BrassPanel("TEAM").add(
    roster,
    BrassLabel("Restrictions"),
).also {
    it.row(18f, editButton, copyButton, deleteButton) // wraps when narrow
}`}</Code>
          </Card>

          <Card title="BrassScrollArea · scroll the body" className="h-full">
            <p className="text-sm leading-relaxed text-ink-600">
              A scroll view with the scrollbar gutter reserved, the bar attached, and the content inset by
              the keycap bleed, so a card placed flush inside keeps its frame. Add rows to{" "}
              <Mono>.content</Mono>, or <Mono>.add(...)</Mono> for a managed stack. Reach for it whenever
              content can be taller than its box, instead of guessing a fixed height.
            </p>
            <Code title="List.kt">{`val list = BrassScrollArea().constrain { /* fill */ } childOf host
list.add(rowA, rowB, rowC)`}</Code>
          </Card>

          <Card title="BrassModal · one dialog" className="h-full">
            <p className="text-sm leading-relaxed text-ink-600">
              The standard dialog: modal defaults, a bleed-safe <Mono>body</Mono> so buttons do not clip
              against the frame, and a <Mono>footer(...)</Mono> that wraps its buttons. Never hand-roll a
              popup.
            </p>
            <Code title="Rename.kt">{`val modal = BrassModal("Rename", width = 220f, height = 120f)
modal.body { host -> field childOf host }
modal.footer(cancelButton, saveButton)
modal.show(root)`}</Code>
          </Card>

          <Card title="Wrap and stacks" className="h-full">
            <p className="text-sm leading-relaxed text-ink-600">
              <WidgetLink name="BrassFlow" /> is flexbox wrap: items flow left to right and drop onto new
              lines, optionally stretching to fill. <WidgetLink name="BrassVBox" /> and{" "}
              <WidgetLink name="BrassHBox" /> are the plain stacks for a short, known run. All three
              reserve the keycap bleed so nothing clips. Prefer the flow for open-ended content, a box for
              a fixed pair.
            </p>
          </Card>

          <Card title="Responsive · BrassBreakpoint" className="h-full">
            <p className="text-sm leading-relaxed text-ink-600">
              At Minecraft&apos;s GUI scales a layout must survive a very wide range of sizes.{" "}
              <WidgetLink name="BrassBreakpoint" /> gives size bands and{" "}
              <Mono>columns(width, minItem)</Mono>, how many cells actually fit, so a grid drops columns
              instead of clipping when squeezed. Combined with wrapping rows, a screen degrades by getting
              tighter, never by overlapping.
            </p>
          </Card>

          <Card title="Forms and lists" className="h-full">
            <p className="text-sm leading-relaxed text-ink-600">
              <WidgetLink name="BrassForm" /> builds a scrolling stack of labelled rows with a chaining API
              and reads its values back. <WidgetLink name="BrassVirtualList" /> and{" "}
              <WidgetLink name="BrassTable" /> handle long, uniform data without paying to lay out rows off
              screen.
            </p>
          </Card>
        </div>

        <div className="mt-5 grid grid-cols-1 gap-4 lg:grid-cols-[1fr_1fr]">
          <div className="rounded-xl border border-edge bg-ink-950/40 p-5">
            <div className="mb-3 font-mc text-xs uppercase tracking-wide text-brass-300">The shape of a screen</div>
            <Code>{`BrassScreen
└─ header row  (title + actions, over a BrassDivider)
└─ BrassScrollArea
   └─ BrassPanel "General"
      ├─ fields (BrassVBox)
      └─ BrassFlow → chips (wrap when narrow)`}</Code>
          </div>
          <div className="rounded-xl border border-brass-600/30 bg-brass-500/5 p-5">
            <div className="mb-2 font-mc text-xs uppercase tracking-wide text-brass-300">Pick in one line</div>
            <ul className="space-y-2 text-sm leading-relaxed text-ink-600">
              <li><Mono>BrassPanel</Mono> for a group of controls that belong together.</li>
              <li><Mono>BrassScrollArea</Mono> the moment content can exceed its box.</li>
              <li><Mono>BrassFlow</Mono> for any row that might not fit on one line.</li>
              <li><Mono>BrassModal</Mono> for anything that interrupts to ask a question.</li>
              <li><Mono>BrassTable</Mono> or <Mono>BrassVirtualList</Mono> for long, uniform data.</li>
            </ul>
          </div>
        </div>
      </PageBody>
    </Page>
  );
}
