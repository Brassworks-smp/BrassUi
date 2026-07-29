import { Page, PageHeader, PageBody } from "brassui-react";
import { SCREENS } from "../data/gallery";
import { ScreenShot } from "../components/ScreenShot";
import { Mono } from "../components/Code";

/**
 * The gallery: whole brassui screens, not single widgets. Each is isolated onto transparency by the
 * in-app capture; this page just lays the list in `data/gallery.ts` out over the graph-paper stage.
 */
export function Gallery() {
  return (
    <Page>
      <PageHeader title="Gallery" subtitle={`${SCREENS.length} screen${SCREENS.length === 1 ? "" : "s"}`} />
      <PageBody className="stagger">
        <p className="mb-6 max-w-2xl text-sm leading-relaxed text-ink-600">
          Whole screens, not single widgets - each cut out onto transparency so only the brassui parts
          remain. Capture one with <Mono>Ctrl+Shift+S</Mono> on any screen (or right-click the gallery
          and choose “Capture showcase”), then drop the PNG into{" "}
          <Mono>public/screenshots/screens/</Mono>.
        </p>

        <div className="flex flex-col gap-8">
          {SCREENS.map((s) => (
            <section key={s.slug}>
              <h2 className="font-mc text-sm text-gray-100">{s.title}</h2>
              <p className="mb-3 mt-1 max-w-2xl text-xs leading-relaxed text-ink-600">{s.blurb}</p>
              <ScreenShot slug={s.slug} title={s.title} />
            </section>
          ))}
        </div>
      </PageBody>
    </Page>
  );
}
