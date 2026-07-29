import { Page, PageHeader, PageBody } from "brassui-react";
import { SCREENS } from "../data/gallery";
import { ScreenShot } from "../components/ScreenShot";

/**
 * The gallery: whole brassui screens, not single widgets. Each is isolated onto transparency by the
 * in-app capture; this page just lays the list in `data/gallery.ts` out over the graph-paper stage.
 * A showcase, so the pictures lead - only a short label under each, no prose.
 */
export function Gallery() {
  return (
    <Page>
      <PageHeader title="Gallery" subtitle={`${SCREENS.length} screen${SCREENS.length === 1 ? "" : "s"}`} />
      <PageBody className="stagger">
        <div className="grid grid-cols-1 gap-8 md:grid-cols-2">
          {SCREENS.map((s) => (
            <figure key={s.slug} className="m-0">
              <ScreenShot slug={s.slug} title={s.title} />
              <figcaption className="mt-2 font-mc text-sm text-gray-100">{s.title}</figcaption>
            </figure>
          ))}
        </div>
      </PageBody>
    </Page>
  );
}
