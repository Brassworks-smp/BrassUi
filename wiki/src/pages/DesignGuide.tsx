import { type ReactNode } from "react";
import { Page, PageHeader, PageBody, Card } from "brassui-react";
import { Code, Mono } from "../components/Code";
import { WidgetLink } from "../components/WidgetLink";

function Rule({ n, title, children }: { n: number; title: string; children: ReactNode }) {
  return (
    <Card title={`${n} · ${title}`} className="h-full">
      <div className="flex flex-col gap-2 text-sm leading-relaxed text-ink-600">{children}</div>
    </Card>
  );
}

export function DesignGuide() {
  return (
    <Page>
      <PageHeader title="Design guide" subtitle="The house rules" />
      <PageBody className="stagger">
        <p className="mb-5 max-w-3xl text-sm leading-relaxed text-ink-600">
          Follow these seven and a new screen looks like it belongs. They are the same principles the
          Brassworks launcher and the war-room mod are built on, one language whether it renders in a
          browser or in the game.
        </p>

        <div className="grid grid-cols-1 gap-4 lg:grid-cols-2 xl:grid-cols-3">
          <Rule n={1} title="Keycaps on ink">
            <p>
              Every control is a raised <b className="text-gray-200">keycap</b>: a flat fill, a one-pixel
              inner border, a near-black outer ring, and a bottom lip, sitting on a near-black{" "}
              <Mono>ink</Mono> surface. That render, the hover lift, and the entrance cascade all come from
              the <Mono>BrassWidget</Mono> base for free. You never draw them.
            </p>
            <p>
              A control gets its colour from a <WidgetLink name="BrassButton" fallback="BrassAccent" />:{" "}
              <Mono>DEFAULT</Mono> for neutral, <Mono>BRASS</Mono> or <Mono>NICE</Mono> for a call to
              action, <Mono>DANGER</Mono> for a destructive one. Reach for an accent, never a raw colour.
            </p>
          </Rule>

          <Rule n={2} title="Split every page">
            <p>
              A screen is a fixed header over a scrolling body, never one long scroll. Put the title and
              the page actions in a header band with a <WidgetLink name="BrassDivider" fallback="hairline" />{" "}
              under it, and the content below in a <WidgetLink name="BrassScrollArea" fallback="scroll area" />.
              The separator is a colour role, so it tracks the theme live.
            </p>
          </Rule>

          <Rule n={3} title="Group into cards">
            <p>
              Nothing sits directly on the background. Related controls live in a <WidgetLink name="BrassPanel" />,
              so a screen reads as regions rather than a pile of widgets. One concern per panel. A second
              unrelated thing is a second panel.
            </p>
            <Code>{`BrassPanel("SERVER").add(
    BrassTextInput("", "address"),
    BrassButton("Connect", BrassAccent.BRASS) { connect() },
) childOf parent`}</Code>
          </Rule>

          <Rule n={4} title="Wrap by default">
            <p>
              Any row of more than a couple of things, chips, tags, a toolbar, is a{" "}
              <WidgetLink name="BrassFlow" />, which reflows onto another line before it overflows.{" "}
              <Mono>BrassPanel.row(...)</Mono> lays a wrapping button row out for you. A non-wrapping single
              line is the deliberate exception, not the default.
            </p>
          </Rule>

          <Rule n={5} title="Colour by role, never by literal">
            <p>
              Read colours from <Mono>Colors</Mono>: <Mono>UI_INNER_BG</Mono>, <Mono>UI_TEXT</Mono>,{" "}
              <Mono>UI_TEXT_DARK</Mono>, <Mono>UI_ACCENT</Mono>, <Mono>UI_INNER_BORDER</Mono>, never a{" "}
              <Mono>java.awt.Color</Mono> literal. Roles are what make a theme swap retint the whole UI at
              once. Assign <Mono>Colors.theme</Mono> and widgets ease over to the new palette.
            </p>
            <p className="text-ink-600/80">
              A colour captured once into a field will <i>not</i> follow a theme change. Hold the role and
              resolve it each frame.
            </p>
          </Rule>

          <Rule n={6} title="Spacing from the scale">
            <p>
              Pad and gap from <WidgetLink name="BrassSpacing" />: <Mono>PAD</Mono> inside a card,{" "}
              <Mono>GAP</Mono> between cards, both the same number so the rhythm is even across a card and
              between two of them. <WidgetLink name="BrassPanel" /> already applies it. Do not invent magic
              numbers.
            </p>
          </Rule>

          <Rule n={7} title="Motion is short and shared">
            <p>
              Do not invent animations. The base widget fades and rises a screenful in on a short diagonal
              cascade, keycaps lift on hover and sink on press, and frames animate open and closed. It all
              runs on one clock so the whole UI moves as one.
            </p>
          </Rule>

          <div className="rounded-xl border border-brass-600/30 bg-brass-500/5 p-4 lg:col-span-2 xl:col-span-1">
            <div className="font-mc text-xs uppercase tracking-wide text-brass-300">The five-second check</div>
            <ol className="mt-2 list-decimal space-y-1 pl-5 text-sm text-ink-600">
              <li>Split page, header over a scroll area?</li>
              <li>Every control group in a <WidgetLink name="BrassPanel" />?</li>
              <li>Multi-item rows <WidgetLink name="BrassFlow" fallback="wrap" />?</li>
              <li>Colours are <Mono>Colors</Mono> roles, no literals?</li>
              <li>Accents via <Mono>BrassAccent</Mono>, spacing via <WidgetLink name="BrassSpacing" />?</li>
            </ol>
          </div>
        </div>
      </PageBody>
    </Page>
  );
}
