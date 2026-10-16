package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicWidthConstraint
import gg.essential.elementa.dsl.basicXConstraint
import gg.essential.elementa.dsl.basicYConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassFocus
import net.swzo.brass.ui.kit.base.BrassFocusable
import net.swzo.brass.ui.kit.base.BrassInert
import net.swzo.brass.ui.kit.base.BrassTree
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.input.BrassSquareButton
import net.swzo.brass.ui.kit.layout.BrassRowLayout
import net.swzo.brass.ui.kit.layout.BrassSpacing
import net.swzo.brass.ui.kit.layout.BrassVBox
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.text.BrassTag
import net.swzo.brass.ui.kit.text.BrassTagStyle
import java.awt.Color
import kotlin.math.floor

class BrassListRow @JvmOverloads constructor(
    var title: String,
    var subtitle: String? = null,
    var icon: BrassIcons.Icon? = null,
    private val onClick: ((BrassListRow) -> Unit)? = null,
) : BrassWidget(BrassAccent.DEFAULT), BrassFocusable {

    var meta: String? = null

    var titleColor: () -> Color = { Colors.UI_TEXT }

    var iconColor: () -> Color = { Colors.UI_TEXT_DARK }

    var onContextMenu: ((Float, Float) -> Unit)? = null

    private var tagText: String? = null
    private var tagStyle: BrassTagStyle = BrassTagStyle.NEUTRAL

    private var leadingComponent: UIComponent? = null
    private var leadingWidth = 0f

    var revealActions: Boolean = false

    private val actions = ArrayList<BrassSquareButton>()
    private val actionHolder = ActionHolder()

    private fun actionsShown(): Boolean =
        !revealActions || selected || (hoveredState && active) || BrassFocus.focused === this ||
            actions.any { BrassFocus.focused === it }

    private inner class ActionHolder : UIContainer(), BrassInert {
        override val inert: Boolean get() = !actionsShown()
        override fun draw(matrixStack: UMatrixStack) { if (!inert) super.draw(matrixStack) }
        override fun isPointInside(x: Float, y: Float): Boolean = !inert && super.isPointInside(x, y)
        override fun mouseMove(window: Window) { if (!inert) super.mouseMove(window) }
    }

    override val focusable: Boolean get() = onClick != null

    init {
        chrome = BrassChrome.NONE
        selectable = true
        clickable = onClick != null
        constrain {
            height = basicHeightConstraint { if (subtitle != null) HEIGHT_TWO_LINE else HEIGHT }
        }
        actionHolder.constrain {
            x = basicXConstraint { this@BrassListRow.getLeft() + solve().actionsX }
            y = basicYConstraint { this@BrassListRow.getTop() }
            width = basicWidthConstraint { actionsWidth() }
            height = basicHeightConstraint { this@BrassListRow.getHeight() }
        } childOf this

        onMouseClick { e ->
            if (BrassTree.isDescendantOf(e.target, actionHolder)) return@onMouseClick
            when (e.mouseButton) {
                0 -> if (active) onClick?.invoke(this@BrassListRow)
                1 -> onContextMenu?.invoke(e.absoluteX, e.absoluteY)
            }
        }
    }

    override fun proxyActivate() {
        if (active) onClick?.invoke(this)
    }

    override fun onActivate(): Boolean {
        if (!active || onClick == null) return false
        onClick.invoke(this)
        return true
    }

    fun tag(text: String?, style: BrassTagStyle = BrassTagStyle.NEUTRAL): BrassListRow = apply {
        tagText = text
        tagStyle = style
    }

    fun leading(component: UIComponent, width: Float): BrassListRow = apply {
        leadingComponent?.let { removeChild(it) }
        leadingWidth = width
        leadingComponent = component
        component.constrain {
            x = basicXConstraint { this@BrassListRow.getLeft() + BrassSpacing.PAD }
            y = basicYConstraint { this@BrassListRow.getTop() + floor((this@BrassListRow.getHeight() - component.getHeight()) / 2f) }
        } childOf this
    }

    /** A trailing key: [tooltip] names it, [detail] (optional) says what pressing it does. */
    fun action(
        icon: BrassIcons.Icon,
        tooltip: String,
        accent: BrassAccent = BrassAccent.DEFAULT,
        detail: String? = null,
        onPress: () -> Unit,
    ): BrassListRow = apply {
        val key = BrassSquareButton(icon, accent) { onPress() }
        key.entranceEnabled = false
        BrassTooltip.attach(key, tooltip, detail?.ifEmpty { null })
        val index = actions.size
        key.constrain {
            x = basicXConstraint { actionHolder.getLeft() + index * (KEY_W + KEY_GAP) }
            y = basicYConstraint {
                this@BrassListRow.getTop() +
                    floor((this@BrassListRow.getHeight() - KEY_H - BLEED_BOTTOM + BLEED_TOP) / 2f)
            }
            width = KEY_W.pixels()
            height = KEY_H.pixels()
        } childOf actionHolder
        actions.add(key)
    }

    private fun actionsWidth(): Float =
        if (actions.isEmpty()) 0f else actions.size * KEY_W + (actions.size - 1) * KEY_GAP + BLEED_X

    private fun leadingSlot(): Float = when {
        leadingComponent != null -> leadingWidth
        icon?.present == true -> ICON
        else -> 0f
    }

    private fun solve(): BrassRowLayout.Solved = BrassRowLayout.solve(
        width = getWidth(),
        leadingW = leadingSlot(),
        metaW = meta?.let { BrassFont.width(this, it) } ?: 0f,
        tagW = tagText?.let { BrassTag.measure(this, it) } ?: 0f,
        actionsW = actionsWidth(),
    )

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        val fx = x.toFloat(); val fy = y.toFloat()
        val hot = (hoveredState || proxyHovered) && active
        when {
            selected -> {
                BrassPaint.rect(m, fx, fy, fx + w, fy + h, withAlpha(Colors.UI_INNER_BG_SELECTED, entranceFade))
                BrassPaint.rect(m, fx, fy, fx + SELECT_BAR, fy + h, withAlpha(Colors.UI_ACCENT, entranceFade))
            }
            hot -> BrassPaint.rect(m, fx, fy, fx + w, fy + h, withAlpha(Colors.ROW_HOVER, entranceFade))
        }

        val s = solve()
        val twoLine = subtitle != null
        val titleY = if (twoLine) fy + TWO_LINE_TOP else fy + floor((h - BrassFont.LINE) / 2f) + 1f
        val textCol = if (!active) Colors.UI_TEXT_DARK else if (selected) Colors.UI_ACCENT_BRIGHT else titleColor()

        val ic = icon
        if (leadingComponent == null && ic != null && ic.present) {
            BrassIcons.draw(m, ic, fx + BrassSpacing.PAD, fy + floor((h - ICON) / 2f), ICON, withAlpha(iconColor(), entranceFade))
        }

        BrassFont.draw(m, this, BrassFont.fit(this, title, s.titleW), fx + s.titleX, titleY, withAlpha(textCol, entranceFade))
        subtitle?.let {
            BrassFont.draw(
                m, this, BrassFont.fit(this, it, s.titleW), fx + s.titleX, titleY + BrassFont.LINE + 2f,
                withAlpha(Colors.UI_TEXT_DARK, entranceFade),
            )
        }
        val singleY = fy + floor((h - BrassFont.LINE) / 2f) + 1f
        s.metaX?.let { mx ->
            BrassFont.draw(m, this, meta ?: "", fx + mx, singleY, withAlpha(Colors.UI_TEXT_DARK, entranceFade))
        }
        s.tagX?.let { tx ->
            BrassTag.drawPill(m, this, fx + tx, fy + floor((h - BrassTag.HEIGHT) / 2f), tagText ?: "", tagStyle.color, entranceFade)
        }
    }

    companion object : BrassDemoSource {
        const val HEIGHT = 18f
        const val HEIGHT_TWO_LINE = 26f

        private const val ICON = 8f
        private const val KEY_W = 14f
        private const val KEY_H = 11f
        private const val KEY_GAP = 3f
        private const val SELECT_BAR = 2f
        private const val TWO_LINE_TOP = 4f

        @JvmStatic
        fun selectOnly(rows: Iterable<BrassListRow>, chosen: BrassListRow?) {
            for (r in rows) r.selected = r === chosen
        }

        override fun demo() = BrassDemo("list-row", "List row", 230f, 72f) {
            val rows = ArrayList<BrassListRow>()
            val pick: (BrassListRow) -> Unit = { selectOnly(rows, it) }
            rows += BrassListRow("survival.example.net", "1.21.1 · 42 players", BrassIcons.PLAY, pick)
                .tag("ONLINE", BrassTagStyle.SUCCESS)
                .action(BrassIcons.GEAR, "Edit") {}
            rows += BrassListRow("A rather long server name that will not fit", null, BrassIcons.FOLDER, pick)
                .also { it.meta = "3d ago" }
                .action(BrassIcons.CLOSE, "Remove", BrassAccent.DANGER) {}
            rows[0].selected = true
            BrassVBox(gap = 2f, bleed = false).add(*rows.toTypedArray())
                .also { box -> rows.forEach { r -> r.constrain { width = basicWidthConstraint { box.getWidth() } } } }
        }
    }
}
