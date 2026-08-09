@file:Suppress("unused")
package net.swzo.brass.ui.kit.surface

import gg.essential.elementa.UIComponent
import gg.essential.elementa.components.ScrollComponent
import gg.essential.elementa.components.UIContainer
import gg.essential.elementa.components.Window
import gg.essential.elementa.constraints.SiblingConstraint
import gg.essential.elementa.dsl.basicHeightConstraint
import gg.essential.elementa.dsl.basicXConstraint
import gg.essential.elementa.dsl.basicYConstraint
import gg.essential.elementa.dsl.childOf
import gg.essential.elementa.dsl.constrain
import gg.essential.elementa.dsl.minus
import gg.essential.elementa.dsl.percent
import gg.essential.elementa.dsl.pixels
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.Colors
import net.swzo.brass.ui.component.BrassText
import net.swzo.brass.ui.kit.base.BrassAccent
import net.swzo.brass.ui.kit.base.BrassChrome
import net.swzo.brass.ui.kit.base.BrassTree
import net.swzo.brass.ui.kit.base.BrassWidget
import net.swzo.brass.ui.kit.demo.BrassDemo
import net.swzo.brass.ui.kit.demo.BrassDemoSource
import net.swzo.brass.ui.kit.input.BrassButton
import net.swzo.brass.ui.kit.input.BrassIconButton
import net.swzo.brass.ui.kit.layout.BrassLayout
import net.swzo.brass.ui.kit.layout.BrassScrollbar
import net.swzo.brass.ui.kit.media.BrassIcons
import net.swzo.brass.ui.kit.media.BrassPlayerHead
import net.swzo.brass.ui.kit.paint.BrassCard
import net.swzo.brass.ui.kit.paint.BrassPaint
import net.swzo.brass.ui.kit.text.BrassCopyChip
import net.swzo.brass.ui.kit.text.BrassFont
import net.swzo.brass.ui.kit.text.BrassLabel
import net.swzo.brass.ui.kit.text.BrassTextArea
import net.swzo.brass.ui.kit.text.BrassWrappedLabel
import java.awt.Color
import java.time.Instant
import java.time.ZoneId

/**
 * A chat box: a scrolling message log with a multi-line composer, in the modern messenger idiom — an
 * avatar beside each author's messages, a name-and-time header, and consecutive lines from the same
 * person grouped together.
 * ```kotlin
 * val chat = BrassChat(placeholder = "Message your team…") { text -> sendToServer(text, chat.replyingTo) }
 * chat.constrain { width = 240.pixels(); height = 180.pixels() }
 * // ...as messages arrive from anywhere:
 * chat.add("swzo", "prep phase ends in 3 days", avatar = playerUuid)
 * chat.add("[server]", "The war has begun.", Colors.DANGER)   // no avatar -> a system line
 * ```
 * The widget only *shows* chat; it never decides what a message means. [onSend] is called with the
 * trimmed input when the user submits, and it is the caller's job to deliver that somewhere and to call
 * [add] when a message (theirs or anyone else's) should appear. That split is deliberate — a team chat,
 * a whisper, and a server log are the same widget with different plumbing.
 * ### How the log is built
 * The log is a **recessed well**, and each author's run of messages is a **card** floating in it. That
 * is the whole reason the grouping exists: the card is the visual unit that says "one person spoke
 * here", so the avatar and header are drawn once, at the top of the card, and every message in the run
 * shares them.
 * Within a card the messages stay individually addressable — each is its own row, separated by a
 * hairline, and each **brightens under the cursor on its own**. Grouping is a *typographic* economy
 * (don't repeat the head five times), not a claim that five messages are one thing; the hover and the
 * separator are what keep that distinction legible.
 * A message with no [Message.avatar] is a **system line**: it still gets a card, but no head, and its
 * text runs the full width — the shape a "the war has begun" broadcast wants, visibly not a person
 * talking.
 * ### The composer
 * Multi-line, and it **grows with what is typed** up to [MAX_COMPOSER_LINES] before it starts scrolling
 * — a message worth three lines should be visible as three lines while it is being written. Enter sends
 * and Shift+Enter breaks the line (see [BrassTextArea.onSubmit]).
 * Above it sits a **context bar**, shown only while replying or editing. It names what the next submit
 * will do and carries the button that cancels it — without one, a composer pre-filled for an edit is
 * indistinguishable from a composer someone left text in, and pressing Enter silently rewrites a
 * message the user had stopped thinking about.
 * ### Replies and edits
 * **Right-clicking any message** opens a context menu: copy, reply, and — when [canModify] says so —
 * edit and delete.
 * A reply carries [Message.replyTo] and renders the quoted message above its own body, behind an accent
 * bar. Replies never group into the card above them, because a card means "one person, one run" and a
 * reply is answering something outside that run.
 * An edited message is marked `(edited)` at the end of its text — tucked onto the end of the last
 * wrapped line where there is room, and onto a line of its own where there is not.
 */
class BrassChat(
    placeholder: String = "Message…",
    private val onSend: (String) -> Unit = {},
) : BrassWidget(BrassAccent.DEFAULT) {

    data class Message(
        val author: String,
        val text: String,
        val authorColor: Color = Colors.UI_ACCENT_BRIGHT,
        val avatar: String? = null,
        val avatarSource: BrassPlayerHead.Source = BrassPlayerHead.Source.BRASSWORKS,
        val timestamp: Long = System.currentTimeMillis(),
        val id: Long = nextId(),
        val replyTo: Long? = null,
        val edited: Boolean = false,
    )


    var onEdit: ((Message, String) -> Unit)? = null

    var onDelete: ((Message) -> Unit)? = null

    var canModify: (Message) -> Boolean = { false }

    var replyingTo: Message? = null
        private set

    var menuRoot: UIComponent? = null


    private val entry = BrassTextArea(placeholder = placeholder)
    private val send = BrassButton("Send", BrassAccent.BRASS) { submit() }

    private val composer = UIContainer()
    private val inputRow = UIContainer()

    private var contextBar: ContextBar? = null

    private var editing: Message? = null


    private val log = ScrollComponent(
        // No built-in "empty" text: an empty team chat is normal, not an error, and a big grey
        // "No messages" reads as the latter.
        emptyString = "",
        innerPadding = 0f,
    )

    private val rows = UIContainer().constrain {
        x = 0.pixels()
        y = 0.pixels()
        width = 100.percent()
        height = BrassLayout.contentHeight()
    } childOf log

    private var empty = true

    /** The card the next message joins if it groups, or null if the next message must start a new one. */
    private var openCard: GroupCard? = null

    private val byId = HashMap<Long, MessageRow>()

    private val messages = HashMap<Long, Message>()

    /**
     * Set when a message has been added and the log should scroll to follow it, cleared once it has.
     * The scroll is deferred to the next frame rather than done in [add] for two reasons, one of them a
     * crash: [ScrollComponent.scrollToBottom] measures by walking up to the enclosing [Window], so
     * calling it on a chat whose messages were **seeded before the screen is shown** — before the widget
     * is in any window at all — throws on the uninitialised parent. Deferring also means the freshly
     * added row has been laid out by the time we measure, so the scroll lands on it and not one row short.
     */
    private var pendingFollow = false

    init {
        // We draw our own card in drawContent, so no keycap chrome — but keep FLAT so the bleed that
        // reserves room for a shadow below stays, since the card genuinely casts one.
        chrome = BrassChrome.FLAT

        // Its height is computed from its two parts rather than measured off its children
        // (BrassLayout.contentHeight), and that is not a style choice: the column is bottom-aligned, so
        // its top is derived from its height — and a child positioned relative to that top, measured
        // back to find the height, is a cycle Elementa resolves by overflowing the stack.
        composer.constrain {
            x = PAD.pixels()
            y = PAD.pixels(alignOpposite = true)
            width = 100.percent() - (PAD * 2).pixels()
            height = basicHeightConstraint { barBand() + entryRowHeight() }
        } childOf this

        // The entry row sits below the context bar when there is one. Both offsets come from barBand(),
        // which reads a field rather than the bar's laid-out geometry — see the note above.
        inputRow.constrain {
            x = 0.pixels()
            y = basicYConstraint { c -> c.parent.getTop() + barBand() }
            width = 100.percent()
            height = basicHeightConstraint { entryRowHeight() }
        } childOf composer

        // Bottom-aligned, and a fixed one-line height: the send button should stay beside the *last*
        // line of a growing message rather than stretching into a tall slab next to it.
        send.constrain {
            x = 0.pixels(alignOpposite = true)
            y = 0.pixels(alignOpposite = true)
            width = SEND_W.pixels()
            height = BrassTextArea.heightForLines(1).pixels()
        } childOf inputRow

        entry.constrain {
            x = 0.pixels()
            y = 0.pixels()
            width = 100.percent() - (SEND_W + GAP).pixels()
            height = 100.percent()
        } childOf inputRow
        // Enter sends and keeps focus, so a run of messages is one uninterrupted stream of typing.
        entry.onSubmit = { submit() }

        log.constrain {
            x = (PAD + WELL_PAD).pixels()
            y = (PAD + WELL_PAD).pixels()
            // Leave room for the scrollbar on the right so it never sits on top of a message.
            width = 100.percent() - (PAD * 2 + WELL_PAD * 2 + BrassScrollbar.WIDTH + 2f).pixels()
            height = BrassLayout.fillAbove(composer, gap = PAD + WELL_PAD)
        } childOf this
        BrassScrollbar.attach(this, log)
    }

    // Both of these describe the composer's geometry *without consulting it*, which is what keeps the
    // bottom-pinned column out of the measurement cycle described in init.

    private fun barBand(): Float = if (contextBar == null) 0f else BAR_H + BAR_GAP

    private fun entryRowHeight(): Float =
        BrassTextArea.heightForLines(entry.lineCount.coerceAtMost(MAX_COMPOSER_LINES))


    /** Append a message and scroll to it. Safe to call from any UI code; not thread-safe. */
    fun add(message: Message) {
        messages[message.id] = message
        val card = openCard?.takeIf { it.accepts(message) } ?: newCard(message)
        byId[message.id] = card.append(message)

        // A system line never opens a group: the next player line must show its own head.
        openCard = if (message.avatar == null) null else card
        empty = false
        // Follow the conversation on the next frame — see pendingFollow.
        pendingFollow = true
    }

    fun add(
        author: String,
        text: String,
        authorColor: Color = Colors.UI_ACCENT_BRIGHT,
        avatar: String? = null,
        replyTo: Long? = null,
    ): Message = Message(author, text, authorColor, avatar, replyTo = replyTo).also { add(it) }

    private fun newCard(message: Message): GroupCard {
        val card = GroupCard(message)
        card.constrain {
            x = 0.pixels()
            y = if (empty) 0.pixels() else SiblingConstraint(CARD_GAP)
            width = 100.percent()
            height = BrassLayout.contentHeight(PAD)
        } childOf rows
        return card
    }


    fun edit(id: Long, text: String) {
        val row = byId[id] ?: return
        messages[id] = (messages[id] ?: return).copy(text = text, edited = true)
        row.setText(text)
    }

    fun remove(id: Long) {
        val row = byId.remove(id) ?: return
        messages.remove(id)
        val card = row.card
        card.removeChild(row)
        if (card.isEmptyOfMessages()) {
            rows.removeChild(card)
            if (openCard === card) openCard = null
        }
        empty = rows.children.isEmpty()
    }

    fun clear() {
        rows.clearChildren()
        byId.clear()
        messages.clear()
        empty = true
        openCard = null
        clearComposerContext()
    }


    val draft: String get() = entry.text.trim()

    fun setDraft(text: String) {
        entry.value = text
    }

    private fun submit() {
        val text = entry.text.trim()
        if (text.isEmpty()) return
        val target = editing
        if (target != null) {
            edit(target.id, text)
            clearComposerContext()
            onEdit?.invoke(target, text)
        } else {
            // replyingTo stays set across this call — see its docs; the host reads it here.
            onSend(text)
            clearComposerContext()
        }
        entry.value = ""
    }

    private fun beginReply(message: Message) {
        editing = null
        replyingTo = message
        showContextBar("Replying to ${message.author}", message.text, message.authorColor)
        send.label = "Send"
    }

    private fun beginEdit(message: Message) {
        replyingTo = null
        editing = message
        showContextBar("Editing message", message.text, Colors.UI_ACCENT_BRIGHT)
        send.label = "Save"
        entry.value = message.text
    }

    fun clearComposerContext() {
        replyingTo = null
        editing = null
        send.label = "Send"
        contextBar?.let { composer.removeChild(it) }
        contextBar = null
    }

    private fun showContextBar(title: String, preview: String, tint: Color) {
        contextBar?.let { composer.removeChild(it) }
        val bar = ContextBar(title, preview, tint)
        bar.constrain {
            x = 0.pixels()
            y = 0.pixels()
            width = 100.percent()
            height = BAR_H.pixels()
        } childOf composer
        contextBar = bar
    }


    /**
     * The menu a message offers. Copy and reply are always there; edit and delete only when [canModify]
     * allows, so a menu never advertises an action the host will refuse.
     */
    private fun menuFor(message: Message): List<BrassContextMenu.Item> = buildList {
        add(BrassContextMenu.Item("Copy text") { BrassCopyChip.copy(message.text) })
        add(BrassContextMenu.Item("Copy name") { BrassCopyChip.copy(message.author) })
        add(BrassContextMenu.Item("Reply") { beginReply(message) })
        if (canModify(message)) {
            add(BrassContextMenu.Item("Edit") { beginEdit(message) })
            add(BrassContextMenu.Item("Delete") {
                // Qualified: inside buildList, a bare remove() binds to the list's own.
                this@BrassChat.remove(message.id)
                if (editing?.id == message.id || replyingTo?.id == message.id) clearComposerContext()
                onDelete?.invoke(message)
            })
        }
    }

    private fun openMenu(message: Message, x: Float, y: Float) {
        val root = menuRoot ?: BrassTree.rootOf(this)
        BrassContextMenu(menuFor(message)).show(root, x, y)
    }


    private inner class GroupCard(private val head: Message) : BrassWidget(BrassAccent.DEFAULT) {

        private val indent = if (head.avatar == null) PAD else INDENT

        private var lastAt = head.timestamp

        var hoveredRow: MessageRow? = null

        init {
            chrome = BrassChrome.NONE

            if (head.avatar != null) {
                BrassPlayerHead(head.avatar, size = AVATAR, source = head.avatarSource).constrain {
                    x = PAD.pixels()
                    y = PAD.pixels()
                } childOf this
            }

            val header = UIContainer().constrain {
                x = indent.pixels()
                y = PAD.pixels()
                width = 100.percent() - (indent + PAD).pixels()
                height = HEADER_H.pixels()
            } childOf this

            val name = BrassLabel(head.author, head.authorColor).constrain {
                x = 0.pixels()
                y = 0.pixels()
            } childOf header
            BrassText.label(clock(head.timestamp), Colors.UI_TEXT_DARK).constrain {
                x = basicXConstraint { name.getRight() + 6f }
                y = 1.pixels()
            } childOf header
        }

        fun accepts(message: Message): Boolean =
            message.avatar != null &&
                head.avatar != null &&
                message.replyTo == null &&
                message.author == head.author &&
                message.timestamp - lastAt < GROUP_WINDOW_MS

        fun append(message: Message): MessageRow {
            val row = MessageRow(this, message, indent, separated = !isEmptyOfMessages())
            row.constrain {
                x = 0.pixels()
                y = SiblingConstraint(0f)
                width = 100.percent()
                // Spans whatever the row put in itself — the body, and the quote and edited marker when
                // they are there — so a reply or a wrapped `(edited)` grows the row without arithmetic.
                height = BrassLayout.contentHeight(LINE_PAD)
            } childOf this
            lastAt = message.timestamp
            return row
        }

        fun isEmptyOfMessages(): Boolean = children.none { it is MessageRow }

        override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
            BrassCard.flat(
                m,
                x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(),
                fill = Colors.UI_ELEMENT_BG,
            )
            // Inset by the card's 1-px border so the highlight brightens *within* the card rather than
            // painting over its outline.
            hoveredRow?.let { row ->
                BrassPaint.rect(
                    m,
                    x + 1f, row.getTop(), x + w - 1f, row.getBottom(),
                    Colors.UI_ELEMENT_BG_HOVER,
                )
            }
        }
    }

    private inner class MessageRow(
        val card: GroupCard,
        private val message: Message,
        private val indent: Float,
        private val separated: Boolean,
    ) : BrassWidget(BrassAccent.DEFAULT) {

        private val quote: BrassLabel? = message.replyTo?.let { messages[it] }?.let { target ->
            BrassLabel("${target.author}  ${snippet(target.text)}", Colors.UI_TEXT_DARK).constrain {
                x = (indent + QUOTE_BAR + 4f).pixels()
                y = LINE_PAD.pixels()
            } childOf this
        }

        private val body: BrassWrappedLabel = run {
            val above = quote
            BrassText.wrapped(message.text, Colors.UI_TEXT).constrain {
                x = indent.pixels()
                y = if (above == null) LINE_PAD.pixels() else basicYConstraint { above.getBottom() + 2f }
                width = 100.percent() - (indent + PAD).pixels()
            } childOf this
        }

        private var editedTag: BrassLabel? = null

        init {
            chrome = BrassChrome.NONE
            if (message.edited) markEdited()
            // The card paints the highlight, so it needs to know which row is under the cursor. See the
            // draw-order note on GroupCard.
            onMouseEnter { card.hoveredRow = this@MessageRow }
            onMouseLeave { if (card.hoveredRow === this@MessageRow) card.hoveredRow = null }
            onMouseClick { e ->
                if (e.mouseButton == 1) openMenu(messages[message.id] ?: message, e.absoluteX, e.absoluteY)
            }
        }

        fun setText(text: String) {
            body.text = text
            markEdited()
        }

        private fun markEdited() {
            if (editedTag != null) return
            val tag = BrassLabel(EDITED, Colors.UI_TEXT_DARK)
            fun fits(): Boolean = body.lastLineWidth() + 4f + tag.getWidth() <= body.getWidth()
            tag.constrain {
                x = basicXConstraint { if (fits()) body.getLeft() + body.lastLineWidth() + 4f else body.getLeft() }
                y = basicYConstraint { if (fits()) body.getBottom() - BrassFont.LINE else body.getBottom() }
            } childOf this
            editedTag = tag
        }

        override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
            // The hairline runs under the text column only, not the avatar gutter: a rule across the
            // full width would cut the card in two and undo the grouping the card exists to show.
            if (separated) {
                BrassPaint.rect(m, x + 1f + indent, y.toFloat(), x + w - 1f - PAD, y + 1f, Colors.UI_INNER_BORDER)
            }
            // The blockquote bar beside a reply's quoted line. A drawn bar rather than an arrow glyph:
            // it costs one quad and cannot come out as a missing-character box in the game's font.
            quote?.let {
                BrassPaint.rect(
                    m,
                    x + indent, it.getTop(), x + indent + QUOTE_BAR, it.getBottom(),
                    Colors.UI_ACCENT,
                )
            }
        }
    }

    private inner class ContextBar(title: String, preview: String, tint: Color) :
        BrassWidget(BrassAccent.DEFAULT) {

        init {
            chrome = BrassChrome.NONE

            val label = BrassLabel(title, tint).constrain {
                x = (QUOTE_BAR + 5f).pixels()
                y = 2.pixels()
            } childOf this
            BrassText.label(snippet(preview), Colors.UI_TEXT_DARK).constrain {
                x = basicXConstraint { label.getRight() + 6f }
                y = 2.pixels()
            } childOf this

            BrassIconButton("", BrassIcons.CLOSE, BrassAccent.DEFAULT) { clearComposerContext() }
                .also { it.iconSize = 5; it.iconPadding = 3 }
                .constrain {
                    x = 1.pixels(alignOpposite = true)
                    y = 0.pixels()
                    width = 11.pixels()
                    height = 11.pixels()
                } childOf this
        }

        override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
            BrassCard.flat(
                m,
                x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(),
                fill = Colors.UI_INNER_BG,
            )
            BrassPaint.rect(m, x + 1f, y + 1f, x + 1f + QUOTE_BAR, (y + h - 1f), Colors.UI_ACCENT)
        }
    }

    private fun clock(ts: Long): String {
        val t = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault())
        return "%02d:%02d".format(t.hour, t.minute)
    }

    private fun snippet(text: String): String {
        val flat = text.replace('\n', ' ').trim()
        return if (flat.length <= SNIPPET) flat else flat.take(SNIPPET - 1).trimEnd() + "…"
    }

    override fun drawContent(m: UMatrixStack, x: Int, y: Int, w: Int, h: Int) {
        // Drawing means we are in a window and this frame's layout is settled, so a deferred
        // scroll-to-follow is safe to run here (see pendingFollow).
        if (pendingFollow && BrassTree.rootOf(log) is Window) {
            pendingFollow = false
            log.scrollToBottom()
        }
        BrassCard.draw(m, x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), shadow = true)

        // The recessed well the cards float in. Measured from the panel's own bounds rather than the
        // log's, so it also covers the scrollbar gutter on the right — a well that stopped where the
        // log stops would leave the bar sitting on the panel fill, outside the surface it belongs to.
        BrassCard.flat(
            m,
            x + PAD, y + PAD,
            x + w - PAD, composer.getTop() - PAD,
            fill = Colors.UI_INNER_BG,
        )
    }

    companion object : BrassDemoSource {

        override fun demo() = BrassDemo("chat", "Chat", 232f, 150f, card = false) {
            BrassChat("Message the server…").apply {
                add("Notch", "welcome to the server!", avatar = "Notch")
                add("Steve", "thanks, how do i claim land?", avatar = "Steve")
                add("Server", "Use /claim while standing in a chunk.")
            }
        }

        const val PAD = 5f

        const val WELL_PAD = 3f

        const val SEND_W = 40f

        const val GAP = 4f

        const val MAX_COMPOSER_LINES = 4

        const val BAR_H = 13f
        const val BAR_GAP = 3f

        const val QUOTE_BAR = 2f

        const val SNIPPET = 42

        const val EDITED = "(edited)"

        const val AVATAR = 20f

        const val INDENT = PAD + AVATAR + 6f

        const val HEADER_H = 11f

        const val LINE_PAD = 2f

        const val CARD_GAP = 5f

        /** How close together two lines from the same author must be to group, in millis. */
        const val GROUP_WINDOW_MS = 5 * 60 * 1000L

        private var ids = 0L

        fun nextId(): Long = ++ids
    }
}
