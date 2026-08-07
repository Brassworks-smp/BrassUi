import { Page, PageHeader, PageBody } from "brassui-react";
import { Radio, ShieldCheck, Gauge, Zap, MessagesSquare, Activity, Cable, BookOpen } from "lucide-react";
import { Code, Mono } from "../components/Code";
import { Link } from "react-router-dom";

function Feature({
  icon: Icon,
  title,
  children,
}: {
  icon: typeof Radio;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-xl border border-edge bg-ink-950/50 p-4">
      <div className="flex items-center gap-2">
        <div className="grid h-7 w-7 place-items-center rounded-lg bg-brass-500/12 text-brass-300">
          <Icon size={14} />
        </div>
        <h3 className="font-mc text-sm text-gray-100">{title}</h3>
      </div>
      <p className="mt-2 text-sm leading-relaxed text-ink-600">{children}</p>
    </div>
  );
}

function H3({ children }: { children: React.ReactNode }) {
  return <h3 className="font-mc text-sm text-gray-100">{children}</h3>;
}

function P({ children, className }: { children: React.ReactNode; className?: string }) {
  return <p className={`text-sm leading-relaxed text-ink-600 ${className ?? ""}`}>{children}</p>;
}

export function Networking() {
  return (
    <Page>
      <PageHeader
        title="Networking"
        subtitle="UI → server logic with no packets, no codecs, and no registration calls"
        icon={<Radio size={18} className="text-brass-400" />}
      />
      <PageBody className="stagger">
        <P>
          The networking module is the server half of the toolkit. An action is a single declaration
          written inline next to the screen that uses it - the same single jar discovers it on the
          client and on a dedicated server, serializes it as JSON, authorizes it before the handler
          runs, and pushes state changes back to every subscribed screen. The desktop app runs the
          exact same handlers in-process, so the whole pipeline is demoable without launching a game.
        </P>

        <div className="mt-8 grid gap-4 lg:grid-cols-3">
          <Feature icon={ShieldCheck} title="Authorization baked in">
            Every action declares its permission; the server checks it before any handler runs, and the
            button greys itself out (with the reason as a tooltip) via the synced permission mirror.
          </Feature>
          <Feature icon={Gauge} title="Rate limits & validation">
            A per-player budget per action, plus a <Mono>validate</Mono> hook that short-circuits with a
            translated failure code before the handler is touched.
          </Feature>
          <Feature icon={Zap} title="Async handlers">
            Handlers return a <Mono>CompletableFuture</Mono> for slow work, so the server main thread is
            never blocked; replies are sent when the future completes.
          </Feature>
          <Feature icon={Activity} title="Server-pushed state">
            <Mono>brassValue</Mono> broadcasts every change and snapshots the current value to late
            subscribers. Coalesce high-frequency values to a few pushes per second.
          </Feature>
          <Feature icon={MessagesSquare} title="Targeted & optimistic">
            Publish to one player instead of the whole server, and apply changes optimistically with
            reconciliation - the next authoritative value replaces them, or a failed action reverts.
          </Feature>
          <Feature icon={Cable} title="Built for the real world">
            Protocol versioning, payload compression past 1 KB, connection-lifecycle cleanup, an audit
            hook, PermissionAPI support, and a <Mono>/brassui action</Mono> test command.
          </Feature>
        </div>

        <section className="mt-10 grid grid-cols-1 gap-5 border-t border-edge py-7 lg:grid-cols-[minmax(0,20rem)_1fr]">
          <div>
            <H3>One declaration, both sides</H3>
            <P className="mt-2">
              The action object lives next to the screen - in the same file. The client keeps it so
              widgets can send it and mirror its permission; the server runs its own copy of the
              handler. Only the id and the JSON input cross the wire.
            </P>
          </div>
          <div className="min-w-0">
            <Code title="TeamActions.kt">
              {`@BrassActionSet
object TeamActions {
    val rename = brassAction<RenameTeam>(
        id = "brassui.team.rename",
        permission = "brassui.team.rename",
        minOpLevel = 3,
        validate = { if (it.name.isBlank()) "demo.name.blank" else null },
    ) { ctx, input ->
        teamName.value = input.name
        ctx.publish("brassui.team.name", input.name)
        ok()
    }
}`}
            </Code>
            <Code className="mt-3" title="TeamSettingsScreen.kt">
              {`actionButton("Rename", TeamActions.rename) {
    RenameTeam(teamId, field.text)
}`}
            </Code>
          </div>
        </section>

        <section className="grid grid-cols-1 gap-5 border-t border-edge py-7 lg:grid-cols-[minmax(0,20rem)_1fr]">
          <div>
            <H3>State, the way screens want it</H3>
            <P className="mt-2">
              Server-side values push themselves; clients bind widgets straight to them. A value that
              changes 20 times a second can coalesce to four pushes; a whisper can target one player.
            </P>
          </div>
          <div className="min-w-0">
            <Code title="Shared state">
              {`val teamName = brassValue("brassui.team.name", "Brassworks")
val live = brassValue("brassui.live", 0, coalesceMillis = 250)

// client:
val name = BrassNet.state("brassui.team.name", String::class.java)
name.onChange { label.text = it ?: "—" }

// targeted, optimistic:
stateOnline.optimistic(!online)   // instant UI
// ... server pushes the authoritative value, or the action fails and you revert.`}
            </Code>
          </div>
        </section>

        <section className="grid grid-cols-1 gap-5 border-t border-edge py-7 lg:grid-cols-[minmax(0,20rem)_1fr]">
          <div>
            <H3>See it live</H3>
            <P className="mt-2">
              The gallery's <Mono>Networking</Mono> section runs the whole pipeline on both hosts:
              rename, op-gated reset, rate-limited spam, a spam kill-switch, an async slow task, a
              coalesced live ticker, a targeted whisper, an audit log, and the permission mirror. In
              game, <Mono>/brassui action &lt;id&gt; &lt;json&gt;</Mono> fires any action from chat.
            </P>
            <P className="mt-2">
              Error messages are translated through{" "}
              <Link to="/getting-started" className="text-brass-300 hover:text-brass-200">
                Minecraft's own language system
              </Link>{" "}
              in game (resource packs can override them) with a built-in English catalog and host-side
              overrides everywhere else.
            </P>
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <Feature icon={BookOpen} title="The only thing you write">
              An action set object and an <Mono>actionButton</Mono> line. Registration, discovery,
              serialization, authorization, throttling and state sync are all derived.
            </Feature>
            <Feature icon={Activity} title="Zero-touch packaging">
              One jar serves clients and dedicated servers. No <Mono>Dist</Mono> splits, no separate
              server artifact, no host-side registration code.
            </Feature>
          </div>
        </section>
      </PageBody>
    </Page>
  );
}
