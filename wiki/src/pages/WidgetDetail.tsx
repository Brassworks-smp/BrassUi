import { useParams, useNavigate, Link } from "react-router-dom";
import { Page, PageHeader, PageBody, cx } from "brassui-react";
import { ChevronLeft, ChevronRight, Boxes, Camera } from "lucide-react";
import { WIDGETS } from "../data/widgets";
import { Shot } from "../components/Shot";
import { Code, Mono } from "../components/Code";

const KIND_TINT: Record<string, string> = {
  class: "border-brass-600/40 text-brass-300",
  object: "border-patina-500/40 text-patina-400",
  interface: "border-edge text-ink-600",
};

/** Section heading used down the page. */
function Head({ children }: { children: string }) {
  return (
    <h2 className="mb-3 font-mc text-[11px] uppercase tracking-widest text-brass-300/90">{children}</h2>
  );
}

export function WidgetDetail() {
  const { slug } = useParams();
  const navigate = useNavigate();
  const idx = WIDGETS.findIndex((w) => w.slug === slug);
  const w = WIDGETS[idx];

  if (!w) {
    return (
      <Page>
        <PageHeader title="Not found" onBack={() => navigate("/widgets")} />
        <PageBody>
          <p className="text-sm text-ink-600">
            No widget named “{slug}”.{" "}
            <Link to="/widgets" className="text-brass-300">
              Back to all widgets.
            </Link>
          </p>
        </PageBody>
      </Page>
    );
  }

  const prev = WIDGETS[idx - 1];
  const next = WIDGETS[idx + 1];
  const importPath = `net.swzo.brass.ui.kit.${w.categoryDir}.${w.name}`;
  const shotFile = w.demoName ?? w.slug;

  return (
    <Page key={w.slug}>
      <PageHeader
        title={w.name}
        subtitle={w.summary}
        icon={<Boxes size={18} className="text-brass-400" />}
        onBack={() => navigate("/widgets")}
        actions={
          <span className={cx("rounded-md border px-2 py-0.5 text-xs", KIND_TINT[w.kind])}>{w.kind}</span>
        }
      />
      <PageBody className="stagger">
        {/* Screenshot + import, side by side. */}
        <div className="grid grid-cols-1 gap-5 lg:grid-cols-[1.4fr_1fr]">
          <Shot slug={shotFile} alt={`${w.name} screenshot`} />
          <div className="flex flex-col gap-3">
            <div className="rounded-xl border border-edge bg-ink-900/50 p-4">
              <Head>Import</Head>
              <Code>{`import ${importPath}`}</Code>
              <p className="mt-2 text-xs leading-relaxed text-ink-600">
                A <Mono>{w.kind}</Mono> in the <Mono>{`kit.${w.categoryDir}`}</Mono> package.
              </p>
            </div>
            {w.demoName && (
              <div className="flex items-center gap-2 rounded-xl border border-edge bg-ink-950/40 px-4 py-3 text-xs text-ink-600">
                <Camera size={14} className="shrink-0 text-brass-400" />
                <span>
                  Live demo: <Mono>{`/brassui demo ${w.demoName}`}</Mono>
                </span>
              </div>
            )}
          </div>
        </div>

        {/* Usage - the whole point. Real construction, straight from the source. */}
        {w.examples.length > 0 && (
          <section className="mt-8">
            <Head>Usage</Head>
            <div className="flex flex-col gap-3">
              {w.examples.map((ex, i) => (
                <Code key={i} title={`${w.name}.kt`}>
                  {ex}
                </Code>
              ))}
            </div>
          </section>
        )}

        {/* Parameters - name, type, default, note. */}
        {w.params.length > 0 && (
          <section className="mt-8">
            <Head>Parameters</Head>
            <div className="overflow-hidden rounded-xl border border-edge">
              <div className="overflow-x-auto">
                <table className="w-full min-w-[560px] border-collapse text-sm">
                  <thead>
                    <tr className="bg-ink-900/60 text-left font-mc text-[11px] uppercase tracking-wide text-ink-600">
                      <th className="px-4 py-2 font-normal">Name</th>
                      <th className="px-4 py-2 font-normal">Type</th>
                      <th className="px-4 py-2 font-normal">Default</th>
                    </tr>
                  </thead>
                  <tbody>
                    {w.params.map((p) => (
                      <tr key={p.name} className="border-t border-edge align-top">
                        <td className="whitespace-nowrap px-4 py-2.5 font-mono text-[13px] text-brass-200">
                          {p.name}
                          {p.doc && (
                            <div className="mt-1 max-w-[22ch] whitespace-normal font-sans text-[11px] leading-snug text-ink-600">
                              {p.doc}
                            </div>
                          )}
                        </td>
                        <td className="px-4 py-2.5 font-mono text-[12px] text-gray-300">{p.type}</td>
                        <td className="whitespace-nowrap px-4 py-2.5 font-mono text-[12px] text-ink-600">
                          {p.default ? p.default : <span className="italic text-ink-700">required</span>}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </section>
        )}

        {/* Declaration - the full signature for the curious. */}
        <section className="mt-8">
          <Head>Declaration</Head>
          <Code title={`${w.name}.kt`}>{w.signature}</Code>
        </section>

        {(prev || next) && (
          <div className="mt-10 flex items-stretch justify-between gap-3">
            {prev ? (
              <Link
                to={`/widgets/${prev.slug}`}
                className="hover-lift flex min-w-0 items-center gap-2 rounded-lg border border-edge bg-ink-900/50 px-3 py-2 text-sm"
              >
                <ChevronLeft size={15} className="shrink-0 text-ink-600" />
                <span className="min-w-0">
                  <span className="block text-[11px] text-ink-600">Previous</span>
                  <span className="block truncate font-mc text-brass-300">{prev.name}</span>
                </span>
              </Link>
            ) : (
              <span />
            )}
            {next && (
              <Link
                to={`/widgets/${next.slug}`}
                className="hover-lift flex min-w-0 items-center gap-2 rounded-lg border border-edge bg-ink-900/50 px-3 py-2 text-right text-sm"
              >
                <span className="min-w-0">
                  <span className="block text-[11px] text-ink-600">Next</span>
                  <span className="block truncate font-mc text-brass-300">{next.name}</span>
                </span>
                <ChevronRight size={15} className="shrink-0 text-ink-600" />
              </Link>
            )}
          </div>
        )}

        {/* Related widgets in the same category. */}
        <RelatedRail w={w} />
      </PageBody>
    </Page>
  );
}

function RelatedRail({ w }: { w: (typeof WIDGETS)[number] }) {
  const siblings = WIDGETS.filter((o) => o.categoryDir === w.categoryDir && o.slug !== w.slug).slice(0, 8);
  if (siblings.length === 0) return null;
  return (
    <section className="mt-10 border-t border-edge pt-5">
      <Head>{`More ${w.category.toLowerCase()}`}</Head>
      <div className="flex flex-wrap gap-2">
        {siblings.map((o) => (
          <Link
            key={o.slug}
            to={`/widgets/${o.slug}`}
            className="pressable rounded-md border border-edge bg-ink-900/50 px-2.5 py-1 font-mc text-[12px] text-ink-600 transition hover:border-brass-600/40 hover:text-brass-200"
          >
            {o.name}
          </Link>
        ))}
      </div>
    </section>
  );
}
