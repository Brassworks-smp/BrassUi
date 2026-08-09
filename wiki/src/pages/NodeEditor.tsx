import type { ReactNode } from "react";
import {
  Activity,
  Boxes,
  Braces,
  Bug,
  Cable,
  FileJson,
  FolderTree,
  Gauge,
  Keyboard,
  Layers3,
  MousePointer2,
  Network,
  Palette,
  Plug,
  Route,
  Shapes,
  Sparkles,
  Workflow,
} from "lucide-react";
import { Card, Page, PageBody, PageHeader, cx } from "brassui-react";
import { Code, Mono } from "../components/Code";
import { Figure } from "../components/Figure";

const TOC = [
  ["overview", "Overview"],
  ["quick-start", "Quick start"],
  ["architecture", "Architecture"],
  ["interaction", "Interaction"],
  ["features", "Features"],
  ["nodes", "Nodes and fields"],
  ["connections", "Ports and wires"],
  ["execution", "Execution and debugging"],
  ["organization", "Notes, groups and workflow"],
  ["persistence", "Persistence and export"],
  ["plugins", "Plugin API"],
  ["collaboration", "Collaboration"],
  ["reference", "API reference"],
  ["shortcuts", "Shortcuts"],
] as const;

function Section({
  id,
  icon: Icon,
  title,
  lead,
  children,
}: {
  id: string;
  icon: typeof Workflow;
  title: string;
  lead: string;
  children: ReactNode;
}) {
  return (
    <section id={id} className="scroll-mt-6 border-t border-edge py-9">
      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(15rem,20rem)_minmax(0,1fr)]">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="grid h-9 w-9 shrink-0 place-items-center rounded-lg bg-brass-500/12 text-brass-300">
              <Icon size={16} />
            </div>
            <h2 className="font-mc text-lg text-gray-100">{title}</h2>
          </div>
          <p className="mt-3 max-w-sm text-sm leading-relaxed text-ink-600">{lead}</p>
        </div>
        <div className="min-w-0 space-y-4">{children}</div>
      </div>
    </section>
  );
}

function P({ children, className }: { children: ReactNode; className?: string }) {
  return <p className={cx("text-sm leading-relaxed text-ink-600", className)}>{children}</p>;
}

function H3({ children }: { children: ReactNode }) {
  return <h3 className="font-mc text-sm text-gray-100">{children}</h3>;
}

function Callout({
  title,
  children,
  tone = "brass",
}: {
  title: string;
  children: ReactNode;
  tone?: "brass" | "patina";
}) {
  return (
    <div
      className={cx(
        "rounded-xl border p-4",
        tone === "brass"
          ? "border-brass-600/25 bg-brass-500/[0.06]"
          : "border-patina-600/25 bg-patina-500/[0.06]",
      )}
    >
      <div className={cx("mb-1.5 font-mc text-xs", tone === "brass" ? "text-brass-300" : "text-patina-300")}>
        {title}
      </div>
      <div className="text-sm leading-relaxed text-ink-600">{children}</div>
    </div>
  );
}

function Feature({
  icon: Icon,
  title,
  children,
}: {
  icon: typeof Workflow;
  title: string;
  children: ReactNode;
}) {
  return (
    <div className="rounded-xl border border-edge bg-ink-950/35 p-4">
      <div className="flex items-center gap-2">
        <Icon size={14} className="text-brass-400" />
        <H3>{title}</H3>
      </div>
      <P className="mt-2">{children}</P>
    </div>
  );
}

function ApiTable({
  rows,
}: {
  rows: Array<[signature: string, description: ReactNode]>;
}) {
  return (
    <div className="overflow-x-auto rounded-xl border border-edge">
      <table className="w-full min-w-[38rem] border-collapse text-left">
        <thead className="bg-ink-950/70">
          <tr>
            <th className="w-[42%] px-3 py-2 font-mc text-[11px] font-normal uppercase tracking-wide text-brass-300">
              API
            </th>
            <th className="px-3 py-2 font-mc text-[11px] font-normal uppercase tracking-wide text-brass-300">
              Purpose
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map(([signature, description]) => (
            <tr key={signature} className="border-t border-edge align-top">
              <td className="px-3 py-2.5 font-mono text-xs text-gray-300">{signature}</td>
              <td className="px-3 py-2.5 text-sm leading-relaxed text-ink-600">{description}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Key({ children }: { children: ReactNode }) {
  return (
    <kbd className="inline-flex min-h-6 items-center rounded-md border border-edge bg-ink-800 px-2 font-mono text-[11px] text-gray-200 shadow-[0_2px_0_var(--color-ink-950)]">
      {children}
    </kbd>
  );
}

export function NodeEditor() {
  return (
    <Page>
      <PageHeader
        title="Node editor"
        subtitle="Canvas workflow, runtime, extension APIs and architecture"
        icon={<Workflow size={18} className="text-brass-400" />}
      />
      <PageBody className="stagger">
        <section id="overview" className="scroll-mt-6">
          <div className="grid grid-cols-1 gap-5 2xl:grid-cols-[minmax(0,1fr)_17rem]">
            <div className="min-w-0">
              <Figure
                src="screenshots/node-editor.gif"
                alt="The brassui node editor demonstrating nodes, typed wires, inline controls, notes and groups"
                caption={
                  <>
                    The production widget: a zoomable typed graph with animated inline controls,
                    reroutable wires, notes, groups, execution state and context-first editing.
                  </>
                }
              />
            </div>
            <aside className="h-fit rounded-xl border border-edge bg-ink-950/45 p-4 2xl:sticky 2xl:top-4">
              <div className="font-mc text-xs uppercase tracking-wide text-brass-300">On this page</div>
              <nav className="mt-3 grid grid-cols-2 gap-x-3 gap-y-1 2xl:grid-cols-1">
                {TOC.map(([id, label], index) => (
                  <button
                    key={id}
                    type="button"
                    onClick={() => document.getElementById(id)?.scrollIntoView({ behavior: "smooth" })}
                    className="rounded-md px-2 py-1.5 text-left text-xs text-ink-600 transition hover:bg-ink-800 hover:text-gray-200"
                  >
                    <span className="mr-2 font-mono text-[10px] text-ink-700">
                      {String(index + 1).padStart(2, "0")}
                    </span>
                    {label}
                  </button>
                ))}
              </nav>
            </aside>
          </div>

          <div className="mt-5 grid grid-cols-1 gap-4 lg:grid-cols-3">
            <Feature icon={Shapes} title="Typed by construction">
              Every socket has a stable type, shape and connection policy. Invalid links are rejected
              before the graph mutates.
            </Feature>
            <Feature icon={Activity} title="Executable">
              Nodes can return synchronous or asynchronous stages. The scheduler handles data values,
              FLOW events, breakpoints, stepping and watches.
            </Feature>
            <Feature icon={Plug} title="Host-extensible">
              Register node types, port types, connection rules, custom renderers and context actions
              without modifying editor input code.
            </Feature>
          </div>

          <Callout title="Package">
            Import the public API from <Mono>net.swzo.brass.ui.kit.node</Mono>. The editor is an Elementa{" "}
            <Mono>UIComponent</Mono>, so it uses the same constraints, parent/child lifecycle and render
            surface as every other brassui widget.
          </Callout>
        </section>

        <Section
          id="quick-start"
          icon={Sparkles}
          title="Quick start"
          lead="Create a registry, construct the editor, constrain it, then add graph content through the editor API."
        >
          <Code title="NodeEditorScreen.kt">{`val registry = DefaultNodes.registry()
val editor = BrassNodeEditor(registry).constrain {
    x = 8.pixels()
    y = 8.pixels()
    width = 100.percent() - 16.pixels()
    height = 100.percent() - 16.pixels()
} childOf content

val time = editor.spawn("time", 24f, 48f)!!
val noise = editor.spawn("noise", 230f, 48f)!!
editor.link(time, fromPort = 0, noise, toPort = 0)

// Optional for a responsive demo or preview surface.
editor.reframeOnResize = true`}</Code>
          <P>
            Coordinates passed to <Mono>spawn</Mono> are world coordinates, not screen pixels. The
            editor owns the world-to-screen transform, so graph data stays independent of the widget's
            size, pan and zoom.
          </P>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <Card title="Use the ready-made palette">
              <P>
                <Mono>DefaultNodes.registry()</Mono> provides Time, Noise, Gradient, Transform, Output
                and Sequence examples. It is useful for demos and as a readable model for your own
                registry.
              </P>
            </Card>
            <Card title="Use an application registry">
              <P>
                Production apps normally construct one <Mono>NodeRegistry</Mono>, register their domain
                types and pass it to every editor that must open those graph files. Type IDs are the
                persistence contract.
              </P>
            </Card>
          </div>
        </Section>

        <Section
          id="architecture"
          icon={Layers3}
          title="Architecture"
          lead="The editor is intentionally split into data, services, runtime and presentation. Each layer can be tested or hosted independently."
        >
          <div className="rounded-xl border border-edge bg-ink-950/45 p-4">
            <div className="grid grid-cols-1 gap-3 lg:grid-cols-[1fr_auto_1fr_auto_1fr] lg:items-stretch">
              <div className="rounded-lg border border-brass-600/25 bg-brass-500/[0.06] p-3">
                <div className="font-mc text-xs text-brass-300">BrassNodeEditor</div>
                <div className="mt-1 text-xs leading-relaxed text-ink-600">
                  Drawing, input modes, viewport, context menus and undo boundaries
                </div>
              </div>
              <div className="hidden items-center text-ink-700 lg:flex">→</div>
              <div className="rounded-lg border border-edge bg-ink-900/60 p-3">
                <div className="font-mc text-xs text-gray-200">Services</div>
                <div className="mt-1 text-xs leading-relaxed text-ink-600">
                  Selection, hit testing, workflow, diagnostics, navigation and inspection
                </div>
              </div>
              <div className="hidden items-center text-ink-700 lg:flex">→</div>
              <div className="rounded-lg border border-patina-600/25 bg-patina-500/[0.05] p-3">
                <div className="font-mc text-xs text-patina-300">NodeGraph</div>
                <div className="mt-1 text-xs leading-relaxed text-ink-600">
                  Nodes, links, reroutes, groups, notes, bookmarks and validation
                </div>
              </div>
            </div>
            <div className="my-3 text-center text-ink-700">↓</div>
            <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
              {[
                ["GraphScheduler", "Async execution, FLOW gating, breakpoints, stepping and watches"],
                ["NodeIO / Export", "Versioned BSON native save + JSON export, SVG and host-backed PNG capture"],
                ["Plugin / Collaboration", "Extension registration and transport-independent shared editing"],
              ].map(([name, text]) => (
                <div key={name} className="rounded-lg border border-edge bg-ink-900/60 p-3">
                  <div className="font-mc text-xs text-gray-200">{name}</div>
                  <div className="mt-1 text-xs leading-relaxed text-ink-600">{text}</div>
                </div>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <Card title="Model is the source of truth">
              <P>
                <Mono>NodeGraph</Mono> owns graph structure and validates every connection. It has no
                dependency on mouse state, menus or the viewport. JSON loading builds a candidate from
                stable type IDs and field primitives.
              </P>
            </Card>
            <Card title="Editor is the interaction boundary">
              <P>
                <Mono>BrassNodeEditor</Mono> translates screen input into world-space operations,
                records undoable snapshots, draws the graph and exposes safe host-level commands. Use{" "}
                <Mono>editor.edit(label)</Mono> for external mutations that should be undoable.
              </P>
            </Card>
            <Card title="Runtime is optional">
              <P>
                A node type may omit its executor and still remain editable, serializable and
                exportable. This is useful for visual planning tools or hosts that compile the graph
                into another runtime.
              </P>
            </Card>
            <Card title="Panels consume snapshots">
              <P>
                The optional minimap and inspector read navigator and inspector snapshots. They do not
                own graph mutation, which makes them safe to place in a host sidebar, drawer or debug
                workspace.
              </P>
            </Card>
          </div>
        </Section>

        <Section
          id="interaction"
          icon={MousePointer2}
          title="Interaction model"
          lead="The canvas keeps common actions direct and puts occasional actions in context menus instead of permanent HUD chrome."
        >
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <Feature icon={MousePointer2} title="Navigate">
              Drag empty canvas or middle-drag to pan. Scroll zooms toward the cursor. Home frames the
              graph; period frames the selection.
            </Feature>
            <Feature icon={Boxes} title="Select and move">
              Click a node to select it, Ctrl-click to toggle, and Shift/Ctrl-drag empty canvas for box
              selection. Dragging one selected node moves the full selection.
            </Feature>
            <Feature icon={Cable} title="Wire">
              Drag from an output to a compatible input. Pull an occupied input to reconnect it. Drop a
              wire on empty canvas to open a compatibility-filtered add menu and auto-connect.
            </Feature>
            <Feature icon={Route} title="Reroute">
              Add a reroute from a wire's context menu, then drag its pin directly. Reroutes belong to
              the link and are saved, copied, templated and exported with it.
            </Feature>
            <Feature icon={Palette} title="Edit inline">
              Toggle, enum, stepper, slider, vector, button and colour controls live inside nodes. Their
              hover, press, value and conditional-layout transitions use the toolkit animation system.
            </Feature>
            <Feature icon={FolderTree} title="Organize">
              Notes edit directly on canvas. Group headers drag their contents. Ellipsis and
              right-click menus expose rename, palette, sizing, nesting and removal actions.
            </Feature>
          </div>
          <Callout title="Read-only mode" tone="patina">
            Set <Mono>editor.readOnly = true</Mono> for presentations or spectator sessions. Pan, zoom,
            selection, navigation and inspection remain active; mutations are blocked.
          </Callout>
        </Section>

        <Section
          id="features"
          icon={Gauge}
          title="Feature map"
          lead="Everything included in the current editor, grouped by what it helps the user accomplish."
        >
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <Card title="Graph editing">
              <ul className="space-y-2 text-sm leading-relaxed text-ink-600">
                <li>Typed inputs and outputs with capacity and compatibility rules</li>
                <li>Connect, reconnect, remove and reroute wires</li>
                <li>Multi-selection, box selection, duplicate, copy and paste</li>
                <li>Undo and redo for structural and inline edits</li>
                <li>Collapsible nodes and animated conditional field layouts</li>
                <li>Optional grid snapping and smart drag alignment guides</li>
              </ul>
            </Card>
            <Card title="Discovery and workflow">
              <ul className="space-y-2 text-sm leading-relaxed text-ink-600">
                <li>Context add menu and compatible-node drop menu</li>
                <li>Fuzzy command palette for nodes, commands, bookmarks and diagnostics</li>
                <li>Recent node types and host-persistable favorites</li>
                <li>Reusable templates created from the current selection</li>
                <li>JSON file drop import with version classification</li>
                <li>View bookmarks and keyboard node navigation</li>
              </ul>
            </Card>
            <Card title="Runtime and review">
              <ul className="space-y-2 text-sm leading-relaxed text-ink-600">
                <li>Deterministic topological scheduling</li>
                <li>Asynchronous executors and cancellation</li>
                <li>FLOW event gating, breakpoints, run, pause and single-step</li>
                <li>Watched and previewed output values</li>
                <li>Structural diagnostics without executing the graph</li>
                <li>Inspector, minimap and accessibility snapshots</li>
              </ul>
            </Card>
            <Card title="Presentation and transport">
              <ul className="space-y-2 text-sm leading-relaxed text-ink-600">
                <li>Versioned, tolerant BSON save format (plus JSON export/import)</li>
                <li>Portable SVG graph export and capture-host PNG export</li>
                <li>Read-only presentation mode</li>
                <li>Ordered graph-change snapshots</li>
                <li>Transport-independent collaborative editing session</li>
                <li>Responsive viewport support with optional reframe-on-resize</li>
              </ul>
            </Card>
          </div>
        </Section>

        <Section
          id="nodes"
          icon={Boxes}
          title="Nodes and fields"
          lead="A NodeType is an immutable reusable definition. A GraphNode is one live instance with its own field values and canvas state."
        >
          <Code title="ScaleNode.kt">{`val scaleType = NodeType(
    id = "math.scale",
    title = "Scale",
    accent = BrassAccent.BRASS,
    inputs = listOf(Port("value", PortType.NUMBER)),
    outputs = listOf(Port("result", PortType.NUMBER)),
    makeFields = {
        listOf(
            SliderField("amount", "Amount", value = 0.5f),
            ToggleField("clamp", "Clamp", on = true),
        )
    },
    executor = NodeExecutor { ctx ->
        val value = (ctx.inputs.first(0) as? Number)?.toFloat() ?: 0f
        val amount = (ctx.field("amount") as Number).toFloat()
        val result = value * amount
        CompletableFuture.completedFuture(
            NodeResult(outputs = mapOf(0 to result))
        )
    },
)

val registry = NodeRegistry().register(scaleType)`}</Code>
          <Callout title="Stable identity">
            Treat node type IDs, field keys and port list indices as schema. A title or label may change
            safely; changing one of those identifiers changes how existing JSON resolves.
          </Callout>

          <H3>Built-in field types</H3>
          <ApiTable
            rows={[
              ["ToggleField(key, label, on)", "Boolean switch with an animated keycap and state indicator."],
              ["SliderField(key, label, value, ...)", "Continuous float input; click or scrub horizontally."],
              ["EnumField(key, label, options, index)", "Cycles through a non-empty string option list."],
              ["StepperField(key, label, value, min, max, step)", "Bounded integer value with centered minus and plus controls."],
              ["ColorField(key, label, color)", "Colour swatch that opens the toolkit colour picker."],
              ["Vec2Field(key, label, x, y, ...)", "Two-axis float control with direct scrubbing."],
              ["ButtonField(key, label, text, onClick)", "Momentary action using the same raised widget chrome."],
            ]}
          />

          <H3>Conditional layouts</H3>
          <Code>{`val algorithm = EnumField(
    "algorithm", "Algorithm", listOf("Perlin", "Worley")
)

val fields = listOf(
    algorithm,
    SliderField("roughness", "Roughness", 0.5f)
        .onlyWhen { algorithm.current == "Perlin" },
    StepperField("cells", "Cells", 4, 1, 16)
        .onlyWhen { algorithm.current == "Worley" },
)`}</Code>
          <P>
            <Mono>onlyWhen</Mono> changes both row visibility and node height. The editor eases the
            affected rows and card bounds together, so downstream content reflows without jumping.
          </P>

          <H3>Custom fields</H3>
          <P>
            Subclass <Mono>NodeField</Mono> when a value needs domain-specific rendering or input. Draw
            in world units through <Mono>NodeDrawCtx</Mono>, return a drag callback from{" "}
            <Mono>onPress</Mono> when needed, and keep the encoded value to a Boolean, Number or String.
          </P>
          <Code title="SeedField.kt">{`class SeedField(
    key: String,
    label: String,
    var value: Int = 0,
) : NodeField(key, label) {
    override fun drawControl(
        ctx: NodeDrawCtx,
        x1: Float, y1: Float, x2: Float, y2: Float,
        h: Float, p: Float,
    ) {
        // Draw in world coordinates. Use BrassCard / BrassPaint
        // helpers to match the rest of the toolkit.
    }

    override fun onPress(
        wx: Float, x1: Float, x2: Float,
    ): ((Float) -> Unit)? {
        value++
        return null
    }

    override fun encode(): Any = value
    override fun decode(v: Any?) {
        value = (v as? Number)?.toInt() ?: value
    }
}`}</Code>
        </Section>

        <Section
          id="connections"
          icon={Network}
          title="Ports and wires"
          lead="PortType defines semantic compatibility and wire styling. Port defines one socket's layout and capacity policy."
        >
          <Code title="SignalPorts.kt">{`val signal = PortType(
    id = "signal",
    wireStyle = WireStyle.DASHED,
    arrow = true,
    symbol = "S",
    accepts = { incoming ->
        incoming.id == "signal" || incoming == PortType.FLOW
    },
) { Colors.PATINA_400 }

val trigger = Port(
    name = "trigger",
    type = signal,
    shape = PortShape.DIAMOND,
    size = 1.25f,
    maxConnections = 4,
    optional = true,
    endLabel = "event",
)`}</Code>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <Card title="Built-in types">
              <P>
                <Mono>FLOW</Mono>, <Mono>NUMBER</Mono>, <Mono>COLOR</Mono> and <Mono>VECTOR</Mono> are
                registered automatically. Their colours resolve from the live theme on every draw.
              </P>
            </Card>
            <Card title="Shapes and styles">
              <P>
                Port shapes are <Mono>ROUND</Mono>, <Mono>SQUARE</Mono>, <Mono>DIAMOND</Mono>,{" "}
                <Mono>DOT</Mono> and <Mono>CROSS</Mono>. Wire styles are <Mono>SOLID</Mono>,{" "}
                <Mono>DASHED</Mono> and <Mono>FLOW</Mono>.
              </P>
            </Card>
          </div>
          <ApiTable
            rows={[
              ["maxConnections = 0", "Use the direction default: one connection for an input, unlimited for an output."],
              ["optional = true", "Exclude this input from missing-required-input diagnostics."],
              ["hidden = true", "Keep its stable index but omit the socket from layout and hit testing."],
              ["dynamic = true", "Marks a socket as host/plugin-generated metadata."],
              ["showLabel = false", "Render a compact unlabeled socket, useful for dot/cross flow nodes."],
              ["endLabel = \"event\"", "Add meaning at the far end of a connection."],
            ]}
          />
          <H3>Validate before mutating</H3>
          <Code>{`val validation = editor.graph.validateLink(
    from = producer,
    fromPort = 0,
    to = consumer,
    toPort = 1,
)

if (validation.allowed) {
    editor.link(producer, 0, consumer, 1)
} else {
    showReason(validation.rejection, validation.detail)
}`}</Code>
          <P>
            Validation covers missing ports, same-node links, type mismatch, duplicates, input/output
            capacity and custom plugin rules. A normal single-capacity input replaces its previous wire
            when a new valid connection is dropped on it.
          </P>
        </Section>

        <Section
          id="execution"
          icon={Bug}
          title="Execution and debugging"
          lead="GraphScheduler runs live nodes in stable topological order and serializes debugger state even when node stages complete asynchronously."
        >
          <Code title="RunGraph.kt">{`editor.scheduler.onUpdate { update ->
    renderRuntimeState(
        state = update.state,
        nodeId = update.nodeId,
        nodeState = update.nodeState,
        watched = update.watched,
        error = update.error,
    )
}

editor.toggleBreakpoint(transform)
editor.watch(transform, outputPort = 0)

val future = editor.runGraph()
// runGraph returns null when it resumes a paused run or is already running.
future?.thenAccept { report ->
    println(report.state)
    println(report.order)
    println(report.watched)
}`}</Code>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <Card title="Data flow">
              <P>
                Values travel by output/input index. <Mono>NodeInputs.first(port)</Mono> is the common
                single-input path; <Mono>all(port)</Mono> retains every value for multi-connect inputs.
                Initial values may be passed to <Mono>scheduler.run</Mono> by <Mono>PortRef</Mono>.
              </P>
            </Card>
            <Card title="Event flow">
              <P>
                A node with a FLOW input executes only after an upstream result fires that output index
                through <Mono>NodeResult.eventOutputs</Mono>. Nodes without incoming FLOW links are
                initially activated.
              </P>
            </Card>
            <Card title="Async and cancellation">
              <P>
                <Mono>NodeExecutor</Mono> returns a <Mono>CompletionStage&lt;NodeResult&gt;</Mono>. Long
                work should check <Mono>context.isCancelled()</Mono>; the scheduler can be stopped
                through <Mono>editor.cancelRun()</Mono>.
              </P>
            </Card>
            <Card title="Debug state">
              <P>
                Breakpoints pause before execution. Step runs exactly one node and pauses before the
                next. Reports include order, per-node traces, results, watched values and the terminal
                error.
              </P>
            </Card>
          </div>
          <ApiTable
            rows={[
              ["ExecutionState", "IDLE, RUNNING, PAUSED, COMPLETED, FAILED or CANCELLED."],
              ["NodeRunState", "WAITING, RUNNING, COMPLETED, SKIPPED or FAILED for each node."],
              ["run(initial, paused)", "Start a fresh execution and return a CompletableFuture report."],
              ["runStep(initial)", "Start fresh, execute one available node, then pause."],
              ["continueExecution()", "Resume a paused run and skip the current breakpoint once."],
              ["step()", "Give a paused run a one-node execution budget."],
              ["watches: MutableSet<PortRef>", "Capture selected output values in live updates and the final report."],
            ]}
          />
          <Callout title="Cycles and failures">
            Cycles fail the run before node execution. Executor exceptions produce a failed trace and a
            failed report. Structural diagnostics can find a cycle or required-input issue before the
            user presses Run.
          </Callout>
        </Section>

        <Section
          id="organization"
          icon={FolderTree}
          title="Notes, groups and workflow"
          lead="Organization objects are part of the graph—not temporary overlay state—so they survive save/load, templates and export."
        >
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
            <Card title="Notes">
              <P>
                Notes are edited directly on the canvas. Click the body to type, drag the header to
                move, and use the ellipsis or context menu for palette, custom colour, size and remove.
              </P>
            </Card>
            <Card title="Groups">
              <P>
                Group the selection, rename it, recolour it, resize to contents or nest it. Dragging a
                group header carries its member nodes and child groups.
              </P>
            </Card>
            <Card title="Bookmarks">
              <P>
                A bookmark stores pan and zoom under a unique name. Save or recall them through API,
                keyboard slots or the command palette.
              </P>
            </Card>
          </div>
          <Code title="Organization.kt">{`editor.groupSelection("Data preparation")

val note = editor.addComment(
    "Normalize values before export",
    wx = 80f,
    wy = 24f,
)
note?.tone = FrameTone.PATINA

editor.saveBookmark("overview")
editor.goToBookmark("overview")

val template = editor.createTemplate("Reusable transform")
val copies = editor.instantiateTemplate(
    template!!.name,
    wx = 420f,
    wy = 160f,
)`}</Code>
          <P>
            Favorites, recent types and templates are held by <Mono>NodeWorkflowService</Mono>. The host
            may seed or persist <Mono>editor.favoriteTypeIds</Mono> and <Mono>editor.templates</Mono> in
            its own settings layer.
          </P>
          <Callout title="Undo boundaries">
            Direct model mutations are suitable while constructing a graph before presentation. For
            user-visible commands after the editor is live, use <Mono>editor.edit("Label")</Mono> or the
            higher-level editor methods so undo, redo and graph-change listeners stay correct.
          </Callout>
        </Section>

        <Section
          id="persistence"
          icon={FileJson}
          title="Persistence and export"
          lead="The native format is compact, versioned BSON — the JSON export below is the human-readable twin. Both record graph content and organization while leaving viewport and transient runtime state out."
        >
          <Code title="graph.json">{`{
  "version": 5,
  "nodes": [
    {
      "id": 1,
      "type": "time",
      "x": 24.0,
      "y": 48.0,
      "collapsed": false,
      "fields": { "wave": "Sine", "speed": 0.4 }
    }
  ],
  "links": [],
  "frames": [],
  "comments": [],
  "bookmarks": []
}`}</Code>
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <Card title="What is saved">
              <P>
                Node IDs, type IDs, positions, collapse state and primitive field values; links and
                reroutes; groups, nesting, palette/custom colour and bounds; notes and their bounds;
                named view bookmarks.
              </P>
            </Card>
            <Card title="What is transient">
              <P>
                Selection, hover/animation state, undo history, clipboard, current viewport, scheduler
                state, breakpoints, watches, previews, diagnostics cache and open menus.
              </P>
            </Card>
          </div>
          <Code title="Persistence.kt">{`val bytes = editor.saveBson()   // native binary, fast wire round-trip
val json = editor.save()       // portable JSON export/import

when (NodeIO.compatibility(json)) {
    NodeIO.Compatibility.CURRENT -> editor.load(json)
    NodeIO.Compatibility.LEGACY -> editor.load(json)
    NodeIO.Compatibility.FUTURE -> showReadOnlyWarning()
    NodeIO.Compatibility.INVALID -> rejectFile()
}

val result = editor.acceptDroppedFiles(listOf(path))
editor.exportSvg(output.resolve("graph.svg"))

// Requires a BrassDemoCapture host.
val pngName: String? = editor.exportPng("graph-review")`}</Code>
          <P>
            The current schema is version <Mono>NodeIO.CURRENT_VERSION</Mono> 5 and the oldest supported
            version is 1. Unknown node types and unknown field keys are skipped.{" "}
            <Mono>NodeGraph.load</Mono> rejects invalid input without destroying the graph already open.
          </P>
          <Callout title="Forward compatibility">
            <Mono>compatibility</Mono> classifies a future document so the host can warn or offer a
            read-only preview. The tolerant reader may still recover known nodes, but silently saving
            over a future file could discard data the current build does not understand.
          </Callout>
        </Section>

        <Section
          id="plugins"
          icon={Plug}
          title="Plugin API"
          lead="Plugins register through a narrow capability object. They do not need access to editor input modes or private rendering state."
        >
          <Code title="MyNodePlugin.kt">{`object MyNodePlugin : NodeEditorPlugin {
    override val id = "com.example.signal-nodes"

    override fun install(api: NodePluginApi) {
        api.register(signalPortType)
        api.register(scaleType)

        api.connectionRule { graph, from, fromPort, to, toPort ->
            if (crossesLockedGroup(graph, from, to)) {
                "Connections cannot leave a locked group"
            } else {
                null // null means allowed
            }
        }

        api.nodeAction(
            NodeEditorAction("Reset amount") { editor, node ->
                editor.edit("Reset amount") {
                    node?.field("amount")?.decode(0.5)
                }
            }
        )

        api.canvasAction(
            NodeEditorAction("Add starter graph") { editor, _ ->
                editor.edit("Starter graph") { graph ->
                    graph.spawn("time", 20f, 20f)
                }
            }
        )
    }
}

val registry = DefaultNodes.registry().install(MyNodePlugin)
val editor = BrassNodeEditor(registry)`}</Code>
          <ApiTable
            rows={[
              ["register(NodeType)", "Add or replace a node definition by stable ID."],
              ["register(PortType)", "Add or replace a semantic connection type by stable ID."],
              ["connectionRule(ConnectionRule)", "Return a rejection detail string or null to allow the candidate link."],
              ["nodeAction(NodeEditorAction)", "Add an action to a node context menu."],
              ["canvasAction(NodeEditorAction)", "Add an action to the empty-canvas context menu."],
              ["NodeType.renderer", "Replace the complete visual rendering for instances of one node type."],
            ]}
          />
          <P>
            Plugin installation is idempotent per <Mono>NodeEditorPlugin.id</Mono> on a registry. Install
            plugins before loading graphs that reference their node or port types.
          </P>
          <Callout title="Custom renderers">
            A <Mono>NodeRenderer</Mono> receives <Mono>NodeDrawCtx</Mono>, the graph and node. It replaces
            the default node body completely; selection, hit testing, port layout and interaction still
            use the node model, so keep custom visuals aligned with <Mono>NodeLayout</Mono>.
          </Callout>
        </Section>

        <Section
          id="collaboration"
          icon={Network}
          title="Collaboration"
          lead="The collaboration layer exchanges graph snapshots over a two-method transport and resolves concurrent edits deterministically."
        >
          <Code title="Collaboration.kt">{`class WebSocketNodeTransport(
    private val socket: Socket,
) : NodeCollaborationTransport {
    override fun publish(edit: NodeCollaborativeEdit) {
        socket.send(encode(edit))
    }

    override fun subscribe(
        listener: (NodeCollaborativeEdit) -> Unit,
    ): () -> Unit {
        return socket.onMessage { listener(decode(it)) }
    }
}

val session = NodeCollaborationSession(
    editor = editor,
    actorId = currentUser.id,
    transport = WebSocketNodeTransport(socket),
    dispatch = uiThread::execute,
)

val stopConflicts = session.onConflict { incoming ->
    log("Concurrent edit resolved from \${incoming.actorId}")
}

// Dispose with the screen.
stopConflicts()
session.close()`}</Code>
          <P>
            Local graph changes publish a label and complete JSON snapshot. The session suppresses
            echoes and applies remote snapshots outside local undo history. A Lamport clock orders
            edits; actor ID is the stable tie-breaker for truly concurrent snapshots.
          </P>
          <Callout title="Threading" tone="patina">
            Pass a <Mono>dispatch</Mono> function when transport callbacks can arrive off the UI thread.
            The session uses it before applying a remote graph. <Mono>InMemoryNodeCollaborationHub</Mono>{" "}
            is available for tests and same-process editors.
          </Callout>
        </Section>

        <Section
          id="reference"
          icon={Braces}
          title="API reference"
          lead="The host-facing surface, organized by ownership. These are the methods most applications need outside custom node definitions."
        >
          <H3>BrassNodeEditor</H3>
          <ApiTable
            rows={[
              ["registry, graph, scheduler", "The registered schema, live graph model and execution runtime."],
              ["selection, workflow, hitTester", "Extracted selection, workflow-memory and world-space hit-test services."],
              ["snapToGrid: Boolean", "Snap moved nodes to NodeLayout.GRID on release."],
              ["reframeOnResize: Boolean", "Refit content after the host viewport size changes."],
              ["readOnly: Boolean", "Keep navigation and inspection while blocking mutation."],
              ["spawn(typeId, wx, wy)", "Create a node and emit a graph change; returns null for an unknown type."],
              ["link(from, fromPort, to, toPort)", "Validate and create a link, then emit a graph change."],
              ["save() / load(json)", "Serialize or replace the graph. Load clears history and reframes."],
              ["edit(label, mutation)", "Record a host/plugin mutation as one undoable snapshot command."],
              ["groupSelection(title)", "Create an auto-sized group from selected nodes."],
              ["addComment(text, wx, wy)", "Create a note in world space."],
              ["saveBookmark(name) / goToBookmark(name)", "Store or restore pan and zoom."],
              ["toggleBreakpoint(node)", "Toggle a scheduler breakpoint and return its new state."],
              ["watch(node, outputPort, enabled)", "Include or remove an output in runtime watch values."],
              ["preview(node, outputPort, enabled)", "Mark an output for visual preview and ensure it is watched."],
              ["runGraph() / stepGraph() / cancelRun()", "Control execution with pause-aware editor semantics."],
              ["setFavorite(typeId, favorite)", "Update host-persistable node discovery memory."],
              ["recentTypes()", "Return up to eight recently used registered node types."],
              ["createTemplate(name)", "Capture the current selected subgraph as reusable JSON."],
              ["instantiateTemplate(name, wx, wy)", "Paste a template at a new world-space origin."],
              ["nestFrame(child, parent)", "Nest or unnest a group while preventing hierarchy cycles."],
              ["onGraphChange(listener)", "Subscribe to revisioned JSON snapshots; returns an unsubscribe function."],
              ["applyRemoteSnapshot(json, label)", "Apply remote state, clear local history and emit a change."],
              ["acceptDroppedFiles(paths)", "Import the first JSON graph and return Imported or Rejected."],
              ["exportSvg(path) / exportPng(name)", "Write a vector graph or ask the active capture host for a PNG."],
              ["diagnostics()", "Return cached structural diagnostics for the current revision."],
              ["navigatorSnapshot()", "Return graph bounds and lightweight node rectangles."],
              ["inspect(node) / inspectSelection()", "Return fields, link counts, runtime state and diagnostics."],
              ["focusNode(nodeId)", "Select and frame a node by stable graph ID."],
              ["accessibilityEntries() / accessibilitySummary()", "Expose text descriptions for host accessibility surfaces."],
            ]}
          />

          <H3>NodeGraph</H3>
          <ApiTable
            rows={[
              ["nodes, links", "Live ordered graph structure."],
              ["frames, comments, bookmarks", "Persistent organization and view references."],
              ["spawn / remove / byId", "Create, delete or resolve live node instances."],
              ["validateLink / link", "Check the complete connection contract or mutate when allowed."],
              ["reroute(link, x, y)", "Append a serialized bend pin to a link."],
              ["frame / comment / bookmark", "Create persistent organization objects."],
              ["toJson() / load(json)", "Persist or atomically replace graph content."],
              ["NodeGraph.fromJson(registry, json)", "Construct a separate graph from a document."],
            ]}
          />

          <H3>Optional host surfaces</H3>
          <Code>{`BrassNodeMiniMap(editor).constrain {
    width = 140.pixels()
    height = 90.pixels()
} childOf sidebar

BrassNodeInspector(editor).constrain {
    width = 180.pixels()
    height = 220.pixels()
} childOf sidebar`}</Code>
          <P>
            These widgets are deliberately not mounted by <Mono>BrassNodeEditor</Mono>. The host decides
            whether its layout has room for them and can place them beside the canvas without an
            overlapping in-canvas HUD.
          </P>

          <H3>Diagnostics</H3>
          <P>
            <Mono>NodeGraphDiagnostics.inspect(graph)</Mono> reports missing required inputs,
            incompatible or stale wires, cycles and stale group references. Every{" "}
            <Mono>NodeDiagnostic</Mono> contains severity, stable code, message and optional node/port
            location.
          </P>
        </Section>

        <Section
          id="shortcuts"
          icon={Keyboard}
          title="Keyboard and mouse reference"
          lead="Shortcuts are active while the editor owns window focus. Ctrl means the platform control modifier used by the current host."
        >
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <Card title="Editing">
              <div className="grid grid-cols-[auto_1fr] items-center gap-x-3 gap-y-3 text-sm text-ink-600">
                <Key>Delete</Key><span>Remove selected nodes or wires</span>
                <Key>Ctrl D</Key><span>Duplicate selected nodes</span>
                <Key>Ctrl C / V</Key><span>Copy and paste the selection</span>
                <Key>Ctrl Z</Key><span>Undo</span>
                <Key>Ctrl Shift Z</Key><span>Redo</span>
                <Key>Ctrl A</Key><span>Select all nodes</span>
                <Key>Ctrl Shift I</Key><span>Invert node selection</span>
                <Key>Shift A</Key><span>Open add-node menu</span>
                <Key>Ctrl G</Key><span>Group selected nodes</span>
                <Key>G</Key><span>Toggle grid snapping</span>
                <Key>Ctrl P</Key><span>Open the command palette</span>
              </div>
            </Card>
            <Card title="Navigation">
              <div className="grid grid-cols-[auto_1fr] items-center gap-x-3 gap-y-3 text-sm text-ink-600">
                <Key>Home</Key><span>Frame all graph content</span>
                <Key>.</Key><span>Frame selected nodes</span>
                <Key>+ / −</Key><span>Zoom in or out</span>
                <Key>Tab</Key><span>Cycle node selection</span>
                <Key>Arrow</Key><span>Nudge selection by one world unit</span>
                <Key>Shift Arrow</Key><span>Nudge by the grid interval</span>
                <Key>Ctrl 1–9</Key><span>Recall a bookmark slot</span>
                <Key>Ctrl Shift 1–9</Key><span>Save a bookmark slot</span>
              </div>
            </Card>
            <Card title="Execution">
              <div className="grid grid-cols-[auto_1fr] items-center gap-x-3 gap-y-3 text-sm text-ink-600">
                <Key>F5</Key><span>Run or continue</span>
                <Key>F6</Key><span>Start step mode or execute one step</span>
                <Key>Shift F5</Key><span>Stop the active run</span>
                <Key>F9</Key><span>Toggle breakpoints on selected nodes</span>
              </div>
            </Card>
            <Card title="Mouse">
              <div className="grid grid-cols-[auto_1fr] items-center gap-x-3 gap-y-3 text-sm text-ink-600">
                <Key>Drag canvas</Key><span>Pan</span>
                <Key>Middle drag</Key><span>Pan from any canvas point</span>
                <Key>Scroll</Key><span>Zoom toward cursor</span>
                <Key>Shift/Ctrl drag</Key><span>Box-select</span>
                <Key>Right click</Key><span>Open the contextual menu</span>
                <Key>Drag port</Key><span>Connect, reconnect or add compatible node</span>
              </div>
            </Card>
          </div>
          <Callout title="Responsive hosting">
            Constrain the editor from the actual content container, not the window, when the screen has
            a sidebar or debug panel. Elementa will update its viewport bounds; set{" "}
            <Mono>reframeOnResize</Mono> only when resizing should also replace the user's deliberate
            pan and zoom.
          </Callout>
        </Section>
      </PageBody>
    </Page>
  );
}
