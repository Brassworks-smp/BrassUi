package net.swzo.brass.ui.kit.node

import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent

/**
 * A ready-made palette of node types - the ones the demo shows and a sensible starting set for an app.
 *
 * Kept as its own GL-free object (no editor, no window, no input classes) so the node model can be built
 * and its save/load exercised with nothing but the toolkit's data classes - which is what lets the
 * serialization be unit-tested off-game.
 *
 * An app builds its own [NodeRegistry] the same way: `NodeRegistry().register(NodeType(...))`, with its
 * own [NodeField]s (including custom subclasses).
 */
object DefaultNodes {

    fun registry(): NodeRegistry = NodeRegistry().apply {
        register(NodeType("time", "Time", BrassAccent.CALM,
            outputs = listOf(Port("value", PortType.NUMBER)),
            makeFields = { listOf(EnumField("wave", "Wave", listOf("Sine", "Saw", "Square")), SliderField("speed", "Speed", 0.4f)) },
            executor = NodeExecutor { ctx ->
                java.util.concurrent.CompletableFuture.completedFuture(
                    NodeResult(outputs = mapOf(0 to ((ctx.field("speed") as? Number)?.toFloat() ?: 0f))),
                )
            }))

        register(NodeType("noise", "Noise", BrassAccent.DEFAULT,
            inputs = listOf(Port("seed", PortType.NUMBER)), outputs = listOf(Port("n", PortType.NUMBER)),
            makeFields = {
                val type = EnumField("type", "Type", listOf("Perlin", "Simplex", "Worley"))
                val scale = SliderField("scale", "Scale", 0.6f)
                listOf(
                    type,
                    scale,
                    ButtonField("roll", "Seed", "Roll") { scale.value = Math.random().toFloat() },
                    ToggleField("ridged", "Ridged", true).onlyWhen { type.current != "Worley" },
                    StepperField("cells", "Cells", 4, 1, 16).onlyWhen { type.current == "Worley" },
                )
            },
            executor = NodeExecutor { ctx ->
                val seed = (ctx.inputs.first(0) as? Number)?.toDouble() ?: 0.0
                val scale = (ctx.field("scale") as? Number)?.toDouble() ?: 0.5
                java.util.concurrent.CompletableFuture.completedFuture(
                    NodeResult(outputs = mapOf(0 to ((kotlin.math.sin(seed * 12.9898) + 1.0) * 0.5 * scale))),
                )
            }))

        register(NodeType("gradient", "Gradient", BrassAccent.BRASS,
            inputs = listOf(Port("t", PortType.NUMBER)), outputs = listOf(Port("colour", PortType.COLOR)),
            makeFields = {
                listOf(
                    ColorField("a", "A", Colors.BRASS_400),
                    ColorField("b", "B", Colors.PATINA_400),
                    EnumField("ease", "Ease", listOf("Linear", "Smooth", "Steps")),
                )
            },
            executor = NodeExecutor { ctx ->
                val t = ((ctx.inputs.first(0) as? Number)?.toFloat() ?: 0f).coerceIn(0f, 1f)
                val a = java.awt.Color((ctx.field("a") as? Number)?.toInt() ?: Colors.BRASS_400.rgb, true)
                val b = java.awt.Color((ctx.field("b") as? Number)?.toInt() ?: Colors.PATINA_400.rgb, true)
                java.util.concurrent.CompletableFuture.completedFuture(
                    NodeResult(outputs = mapOf(0 to Colors.mix(a, b, t))),
                )
            }))

        register(NodeType("transform", "Transform", BrassAccent.DEFAULT,
            inputs = listOf(Port("pos", PortType.VECTOR, optional = true), Port("amount", PortType.NUMBER, optional = true)),
            outputs = listOf(Port("out", PortType.VECTOR)),
            makeFields = {
                val space = EnumField("space", "Space", listOf("Local", "World"))
                listOf(
                    space,
                    Vec2Field("offset", "Offset", 0f, 0f),
                    StepperField("octaves", "Octaves", 3, 1, 8),
                    ToggleField("clamp", "Clamp", false).onlyWhen { space.current == "World" },
                )
            },
            executor = NodeExecutor { ctx ->
                java.util.concurrent.CompletableFuture.completedFuture(
                    NodeResult(outputs = mapOf(0 to (ctx.inputs.first(0) ?: ctx.field("offset")))),
                )
            }))

        register(NodeType("output", "Output", BrassAccent.DANGER,
            inputs = listOf(Port("colour", PortType.COLOR), Port("displace", PortType.VECTOR)),
            makeFields = { listOf(ToggleField("preview", "Preview", true), SliderField("exposure", "Exposure", 0.5f)) },
            executor = NodeExecutor { java.util.concurrent.CompletableFuture.completedFuture(NodeResult()) }))

        register(NodeType("sequence", "Sequence", BrassAccent.CALM,
            inputs = listOf(Port("in", PortType.FLOW, shape = PortShape.DOT, optional = true, showLabel = false)),
            outputs = listOf(Port("then", PortType.FLOW, shape = PortShape.CROSS, maxConnections = 8, showLabel = false)),
            executor = NodeExecutor {
                java.util.concurrent.CompletableFuture.completedFuture(NodeResult(eventOutputs = setOf(0)))
            }))
    }
}
