import { Page, PageHeader, PageBody, Card } from "brassui-react";
import { Terminal, Boxes, Package, BookOpen } from "lucide-react";
import { Link } from "react-router-dom";
import { Code, GradleBlock, Mono } from "../components/Code";
import { WidgetLink } from "../components/WidgetLink";
import { META } from "../data/meta";

// Built from META (generated from the build's gradle.properties), so the repo and the version shown
// here follow the real build automatically — a release just needs the site regenerated, which CI does.
const KTS = `repositories {
    maven("${META.packagesUrl}") {
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
        }
        content { includeGroup("net.swzo.brass") }
    }
}

dependencies {
    // the mod jar bundles the toolkit + its game libraries, jar-in-jar
    implementation("net.swzo.brass:brassui:${META.version}")
    jarJar("net.swzo.brass:brassui:${META.version}") { isTransitive = false }
}`;

const GROOVY = `repositories {
    maven {
        url = '${META.packagesUrl}'
        credentials {
            username = project.findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
            password = project.findProperty('gpr.token') ?: System.getenv('GITHUB_TOKEN')
        }
        content { includeGroup 'net.swzo.brass' }
    }
}

dependencies {
    // the mod jar bundles the toolkit + its game libraries, jar-in-jar
    implementation 'net.swzo.brass:brassui:${META.version}'
    jarJar('net.swzo.brass:brassui:${META.version}') { transitive = false }
}`;

export function GettingStarted() {
  return (
    <Page>
      <PageHeader title="Getting started" subtitle="From zero to a screen on the display" />
      <PageBody className="stagger">
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1fr_18rem]">
          {/* Main column */}
          <div className="flex min-w-0 flex-col gap-4">
            <Card title="1 · Depend on the toolkit">
              <p className="text-sm leading-relaxed text-ink-600">
                brassui ships as a self-contained NeoForge mod jar, with Elementa and UniversalCraft
                folded in, from a private GitHub Packages repo. Add the repository with a token that has{" "}
                <Mono>read:packages</Mono>, then depend on it. Pick your build dialect:
              </p>
              <GradleBlock kotlin={KTS} groovy={GROOVY} />
            </Card>

            <Card title="2 · Put a screen on the display">
              <p className="text-sm leading-relaxed text-ink-600">
                Extend <WidgetLink name="BrassScreen" fallback="BrassScreen" />, an Elementa{" "}
                <Mono>WindowScreen</Mono> that hands you a <Mono>background</Mono> root, the theme, the
                cursor, and the entrance cascade. Parent your widgets to it and you are done.
              </p>
              <Code title="HelloScreen.kt">{`class HelloScreen : BrassScreen() {
    init {
        BrassPanel("HELLO").add(
            BrassLabel("Welcome to brassui"),
            BrassButton("Click me", BrassAccent.BRASS) { println("clicked") },
        ).constrain {
            x = CenterConstraint(); y = CenterConstraint()
            width = 220.pixels()
        } childOf background
    }
}

// open it
Minecraft.getInstance().setScreen(HelloScreen())`}</Code>
            </Card>

            <Card title="3 · Follow the four rules">
              <p className="text-sm leading-relaxed text-ink-600">
                Everything is a <WidgetLink name="BrassPanel" fallback="card" />. Pages split into a header
                over a scrolling <WidgetLink name="BrassScrollArea" fallback="scroll area" />. Rows{" "}
                <WidgetLink name="BrassFlow" fallback="wrap" /> before they overflow. Colours come from{" "}
                <Mono>Colors</Mono> roles so a theme swap retints everything at once. That is the whole
                contract, and the <Link to="/design" className="text-brass-300 hover:text-brass-200">design guide</Link>{" "}
                and <Link to="/layout" className="text-brass-300 hover:text-brass-200">layout guide</Link> go
                deeper on each.
              </p>
            </Card>
          </div>

          {/* Side rail */}
          <aside className="flex flex-col gap-3">
            <div className="rounded-xl border border-edge bg-ink-900/50 p-4">
              <div className="flex items-center gap-2 font-mc text-sm text-gray-100">
                <Terminal size={15} className="text-brass-400" /> See it running
              </div>
              <p className="mt-2 text-xs leading-relaxed text-ink-600">
                The toolkit ships a live gallery of every widget in-game. Run the mod and open it:
              </p>
              <Code>{`/brassui`}</Code>
            </div>

            <RailLink to="/architecture" icon={Package} title="How it works" body="The render, the bleed, the theme seam." />
            <RailLink to="/widgets" icon={Boxes} title="Widget catalog" body="Every widget, with usage and params." />
            <RailLink to="/design" icon={BookOpen} title="Design guide" body="The house style, in seven rules." />
          </aside>
        </div>
      </PageBody>
    </Page>
  );
}

function RailLink({
  to,
  icon: Icon,
  title,
  body,
}: {
  to: string;
  icon: typeof Package;
  title: string;
  body: string;
}) {
  return (
    <Link to={to} className="hover-lift flex items-start gap-3 rounded-xl border border-edge bg-ink-900/50 p-4">
      <Icon size={16} className="mt-0.5 shrink-0 text-brass-300" />
      <div className="min-w-0">
        <div className="font-mc text-sm text-gray-100">{title}</div>
        <p className="mt-0.5 text-xs leading-relaxed text-ink-600">{body}</p>
      </div>
    </Link>
  );
}
