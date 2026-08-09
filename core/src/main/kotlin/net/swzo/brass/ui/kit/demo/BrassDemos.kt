@file:Suppress("unused")
package net.swzo.brass.ui.kit.demo

import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.input.BrassCheckbox
import net.swzo.brass.ui.kit.input.BrassChips
import net.swzo.brass.ui.kit.input.BrassColorPicker
import net.swzo.brass.ui.kit.input.BrassConfirmSlider
import net.swzo.brass.ui.kit.input.BrassDropdown
import net.swzo.brass.ui.kit.input.BrassIconButton
import net.swzo.brass.ui.kit.input.BrassInventoryGrid
import net.swzo.brass.ui.kit.input.BrassKeybind
import net.swzo.brass.ui.kit.input.BrassNumberInput
import net.swzo.brass.ui.kit.input.BrassRadioGroup
import net.swzo.brass.ui.kit.input.BrassRangeSlider
import net.swzo.brass.ui.kit.input.BrassScrollSelector
import net.swzo.brass.ui.kit.input.BrassSearchField
import net.swzo.brass.ui.kit.input.BrassSlider
import net.swzo.brass.ui.kit.input.BrassSquareButton
import net.swzo.brass.ui.kit.input.BrassTabSwitch
import net.swzo.brass.ui.kit.input.BrassToggle
import net.swzo.brass.ui.kit.layout.BrassDivider
import net.swzo.brass.ui.kit.layout.BrassFlow
import net.swzo.brass.ui.kit.layout.BrassForm
import net.swzo.brass.ui.kit.layout.BrassGrid
import net.swzo.brass.ui.kit.layout.BrassPagination
import net.swzo.brass.ui.kit.layout.BrassSplitPane
import net.swzo.brass.ui.kit.media.BrassBlockPreview
import net.swzo.brass.ui.kit.media.BrassEffectIcon
import net.swzo.brass.ui.kit.media.BrassEntity
import net.swzo.brass.ui.kit.media.BrassImage
import net.swzo.brass.ui.kit.media.BrassItem
import net.swzo.brass.ui.kit.media.BrassPlayerHead
import net.swzo.brass.ui.kit.media.BrassSkeleton
import net.swzo.brass.ui.kit.surface.BrassAccordion
import net.swzo.brass.ui.kit.surface.BrassBarChart
import net.swzo.brass.ui.kit.surface.BrassChart
import net.swzo.brass.ui.kit.surface.BrassChat
import net.swzo.brass.ui.kit.surface.BrassPanel
import net.swzo.brass.ui.kit.surface.BrassEmptyState
import net.swzo.brass.ui.kit.node.BrassNodeEditor
import net.swzo.brass.ui.kit.surface.BrassLoading
import net.swzo.brass.ui.kit.surface.BrassProgressBar
import net.swzo.brass.ui.kit.surface.BrassTable
import net.swzo.brass.ui.kit.surface.BrassTreeView
import net.swzo.brass.ui.kit.text.BrassCodeView
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassMarkdown
import net.swzo.brass.ui.kit.text.BrassTag
import net.swzo.brass.ui.kit.text.BrassTextArea
import net.swzo.brass.ui.kit.text.BrassTextInput
import net.swzo.brass.ui.kit.text.BrassTimeAgo
import net.swzo.brass.ui.kit.text.BrassWrappedLabel

/**
 * Every widget that declares a demo, in the order a wiki would introduce them.
 * ### Why there is still a list
 * Because ordering is editorial and discovery is not free. The alternative — scanning the classpath
 * for companions implementing [BrassDemoSource] — sounds tidier and is worse on both counts: it
 * produces an arbitrary order that no reader would choose, and classpath scanning inside a mod loader
 * is exactly the kind of thing that works on one launcher and mysteriously finds nothing on another.
 * What this list is *not* is the old catalogue. It carries no sizes, no sample data, no interaction
 * scripts — one line per widget, naming the companion that owns all of that. Adding a widget to the
 * showcase is one line here plus a demo beside the widget; changing how a widget demonstrates itself
 * does not touch this file at all.
 * ### Not every widget is here
 * A demo is optional by design (see [BrassDemoSource]), and some widgets genuinely have no sensible
 * standalone demo. The overlay surfaces — toast, popup, context menu, command palette, tooltip — are
 * the honest gap: each is *shown into a screen root* through a layer stack rather than existing as a
 * component in a box, so demonstrating one means demonstrating the screen around it. They are worth
 * adding once the demo model grows a notion of a host screen; faking it by hoisting a popup into a
 * bare container would document a widget the toolkit does not have.
 * The pure helpers — [net.swzo.brass.ui.kit.layout.BrassLayout],
 * [net.swzo.brass.ui.kit.layout.BrassCull], [net.swzo.brass.ui.kit.text.BrassFuzzy] and the rest —
 * are not widgets and have nothing to show.
 */
object BrassDemos {

    val ALL: List<BrassDemo> get() = SOURCES.map { it.demo() }

    private val SOURCES: List<BrassDemoSource> = listOf(
        // Buttons and clickable controls
        BrassButton,
        BrassSquareButton,
        BrassIconButton,
        BrassCheckbox,
        BrassRadioGroup,
        BrassToggle,
        BrassTabSwitch,
        BrassConfirmSlider,
        BrassKeybind,
        BrassDropdown,
        BrassScrollSelector,

        // Value inputs
        BrassSlider,
        BrassRangeSlider,
        BrassNumberInput,
        BrassSearchField,
        BrassTextInput,
        BrassTextArea,
        BrassChips,
        BrassColorPicker,

        // Text and static surfaces
        BrassLabel,
        BrassWrappedLabel,
        BrassTag,
        BrassTimeAgo,
        BrassDivider,
        BrassEmptyState,
        BrassCodeView,
        BrassMarkdown,

        // Progress and motion
        BrassProgressBar,
        BrassLoading,
        BrassSkeleton,

        // Data surfaces
        BrassBarChart,
        BrassChart,
        BrassTable,
        BrassTreeView,
        BrassAccordion,
        BrassChat,

        // Canvas
        BrassNodeEditor,

        // Layout
        BrassPanel,
        BrassFlow,
        BrassGrid,
        BrassForm,
        BrassSplitPane,
        BrassPagination,
        BrassImage,

        // Game content
        // Item, effect and inventory render off-world; entity, block and player head need a client
        // level and mark themselves worldRequired, which the browser flags before you capture one.
        BrassItem,
        BrassEffectIcon,
        BrassInventoryGrid,
        BrassEntity,
        BrassBlockPreview,
        BrassPlayerHead,
    )

    fun byName(name: String): BrassDemo? = ALL.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun names(): List<String> = ALL.map { it.name }
}
