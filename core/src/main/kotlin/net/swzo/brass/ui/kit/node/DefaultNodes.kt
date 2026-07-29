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
            makeFields = { listOf(EnumField("wave", "Wave", listOf("Sine", "Saw", "Square")), SliderField("speed", "Speed", 0.4f)) }))

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
            }))

        register(NodeType("gradient", "Gradient", BrassAccent.BRASS,
            inputs = listOf(Port("t", PortType.NUMBER)), outputs = listOf(Port("colour", PortType.COLOR)),
            makeFields = {
                listOf(
                    ColorField("a", "A", Colors.BRASS_400),
                    ColorField("b", "B", Colors.PATINA_400),
                    EnumField("ease", "Ease", listOf("Linear", "Smooth", "Steps")),
                )
            }))

        register(NodeType("transform", "Transform", BrassAccent.DEFAULT,
            inputs = listOf(Port("pos", PortType.VECTOR), Port("amount", PortType.NUMBER)),
            outputs = listOf(Port("out", PortType.VECTOR)),
            makeFields = {
                val space = EnumField("space", "Space", listOf("Local", "World"))
                listOf(
                    space,
                    Vec2Field("offset", "Offset", 0f, 0f),
                    StepperField("octaves", "Octaves", 3, 1, 8),
                    ToggleField("clamp", "Clamp", false).onlyWhen { space.current == "World" },
                )
            }))

        register(NodeType("output", "Output", BrassAccent.DANGER,
            inputs = listOf(Port("colour", PortType.COLOR), Port("displace", PortType.VECTOR)),
            makeFields = { listOf(ToggleField("preview", "Preview", true), SliderField("exposure", "Exposure", 0.5f)) }))
    }
}
