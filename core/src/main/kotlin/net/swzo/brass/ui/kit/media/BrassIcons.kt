@file:Suppress("unused")
package net.swzo.brass.ui.kit.media

import com.google.gson.Gson
import gg.essential.elementa.components.UIImage
import gg.essential.universal.UMatrixStack
import net.swzo.brass.ui.kit.base.BrassAmbientFade
import net.swzo.brass.ui.kit.base.BrassStats
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.InputStreamReader
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO

/**
 * The toolkit's icon set: a **sprite sheet** at `assets/brassui/textures/gui/icons.png` with an
 * accompanying `icons.json` naming each sprite's rectangle within it.
 * ```json
 * { "width": 96, "height": 36, "cell": 12,
 *   "sprites": { "close": { "x": 0, "y": 0, "w": 12, "h": 12 }, ... } }
 * ```
 * Adding an icon means painting it into the sheet and adding one entry to the JSON - no code change,
 * and no per-icon file. Sprites are white masks on transparency, so every icon tints with its
 * widget's animated text colour.
 * ### Loading
 * The sheet is read through **this class's own** class loader, not Elementa's `UIImage.ofResource`:
 * Elementa resolves that path against `UIImage`'s class, and under NeoForge's module layers Elementa
 * is a GAMELIBRARY in a different module from the mod, so it cannot see `assets/brassui/...` and
 * silently falls back to a placeholder.
 * The sheet is decoded once, on first use. Each sprite is then cropped out lazily and cached, so an
 * icon that never appears costs nothing beyond its JSON entry. Sprites are sampled NEAREST and
 * snapped to whole pixels, keeping the pixel-art crisp at any GUI scale.
 */
object BrassIcons {

    private const val SHEET = "/assets/brassui/textures/gui/icons.png"
    private const val ATLAS = "/assets/brassui/textures/gui/icons.json"

    class Icon(private val name: String) {
        val image: UIImage? by lazy { sprite(name) }

        val present: Boolean get() = name.isNotEmpty()
    }


    private class Rect {
        var x: Int = 0
        var y: Int = 0
        var w: Int = 0
        var h: Int = 0
    }

    private val sheet: BufferedImage? by lazy {
        runCatching {
            BrassIcons::class.java.getResourceAsStream(SHEET)?.use(ImageIO::read)
        }.getOrNull()
    }

    private class AtlasFile {
        var sprites: Map<String, Rect>? = null
    }

    private val atlas: Map<String, Rect> by lazy {
        runCatching {
            val stream = BrassIcons::class.java.getResourceAsStream(ATLAS) ?: return@runCatching emptyMap()
            val file = stream.use { Gson().fromJson(InputStreamReader(it), AtlasFile::class.java) }
            file?.sprites.orEmpty()
        }.getOrDefault(emptyMap())
    }

    private val cache = HashMap<String, UIImage?>()

    @Synchronized
    private fun sprite(name: String): UIImage? {
        cache[name]?.let { return it }
        if (cache.containsKey(name)) return null   // already tried and failed

        val img = sheet
        val r = atlas[name]
        val out = if (img == null || r == null || r.x + r.w > img.width || r.y + r.h > img.height) {
            null
        } else {
            UIImage(CompletableFuture.completedFuture(img.getSubimage(r.x, r.y, r.w, r.h))).also {
                it.textureMinFilter = UIImage.TextureScalingMode.NEAREST
                it.textureMagFilter = UIImage.TextureScalingMode.NEAREST
            }
        }
        cache[name] = out
        return out
    }

    fun spriteNames(): Set<String> = atlas.keys

    /**
     * Draw [icon] in a [size]x[size] box at ([x],[y]), tinted [color]. Coordinates snap to whole
     * pixels so the sprite lands on the pixel grid instead of rendering half-blurred. A missing
     * sprite draws nothing rather than a placeholder - a silent gap is easier to spot in the dev
     * inspector than Elementa's fallback checkerboard, and never obscures neighbouring widgets.
     */
    fun draw(m: UMatrixStack, icon: Icon, x: Float, y: Float, size: Float, color: Color) {
        val img = icon.image ?: return
        BrassStats.quad()
        img.drawImage(
            m,
            Math.round(x).toDouble(), Math.round(y).toDouble(),
            Math.round(size).toDouble(), Math.round(size).toDouble(),
            BrassAmbientFade.apply(color),
        )
    }


    val NONE = Icon("")

    val CLOSE = Icon("close")
    val MINIMIZE = Icon("minimize")
    val MAXIMIZE = Icon("maximize")
    val RESTORE = Icon("restore")

    val CHECK = Icon("check")
    val PLUS = Icon("plus")
    val MINUS = Icon("minus")
    val SEARCH = Icon("search")
    val GEAR = Icon("gear")
    val FOLDER = Icon("folder")
    val HEART = Icon("heart")
    val PLAY = Icon("play")
    val INFO = Icon("info")
    val WARN = Icon("warn")
    val DOTS = Icon("dots")

    val CHEVRON_LEFT = Icon("chevron_left")
    val CHEVRON_RIGHT = Icon("chevron_right")
    val CHEVRON_UP = Icon("chevron_up")
    val CHEVRON_DOWN = Icon("chevron_down")

    val PENCIL = Icon("pencil")
    val TRASH = Icon("trash")
    val COPY = Icon("copy")
    val PASTE = Icon("paste")
    val CUT = Icon("cut")
    val SAVE = Icon("save")
    val UNDO = Icon("undo")
    val REDO = Icon("redo")
    val REFRESH = Icon("refresh")
    val DOWNLOAD = Icon("download")
    val UPLOAD = Icon("upload")
    val IMPORT = Icon("import")
    val EXPORT = Icon("export")
    val LINK = Icon("link")
    val UNLINK = Icon("unlink")
    val SHARE = Icon("share")
    val SEND = Icon("send")
    val FILTER = Icon("filter")
    val SORT = Icon("sort")
    val SORT_ASC = Icon("sort_asc")
    val SORT_DESC = Icon("sort_desc")
    val DRAG = Icon("drag")
    val MENU = Icon("menu")
    val MORE = Icon("more")
    val MORE_VERTICAL = Icon("more_vertical")
    val EXPAND = Icon("expand")
    val SHRINK = Icon("shrink")
    val EXTERNAL = Icon("external")
    val PIN = Icon("pin")
    val LOCK = Icon("lock")
    val UNLOCK = Icon("unlock")
    val KEY = Icon("key")
    val EYE = Icon("eye")
    val EYE_OFF = Icon("eye_off")
    val STOP = Icon("stop")
    val PAUSE = Icon("pause")
    val POWER = Icon("power")

    val ARROW_LEFT = Icon("arrow_left")
    val ARROW_RIGHT = Icon("arrow_right")
    val ARROW_UP = Icon("arrow_up")
    val ARROW_DOWN = Icon("arrow_down")
    val HOME = Icon("home")
    val BACK = Icon("back")
    val FIRST = Icon("first")
    val LAST = Icon("last")

    val STAR = Icon("star")
    val STAR_OUTLINE = Icon("star_outline")
    val BELL = Icon("bell")
    val BELL_OFF = Icon("bell_off")
    val CLOCK = Icon("clock")
    val CALENDAR = Icon("calendar")
    val QUESTION = Icon("question")
    val ERROR = Icon("error")
    val SUCCESS = Icon("success")
    val FLAG = Icon("flag")
    val BOLT = Icon("bolt")
    val LOADING = Icon("loading")
    val DOT = Icon("dot")

    val USER = Icon("user")
    val USERS = Icon("users")
    val CHAT = Icon("chat")
    val MAIL = Icon("mail")
    val CROWN = Icon("crown")

    val FILE = Icon("file")
    val FILE_TEXT = Icon("file_text")
    val IMAGE = Icon("image")
    val LIST = Icon("list")
    val GRID = Icon("grid")
    val TABLE = Icon("table")
    val CHART = Icon("chart")
    val CODE = Icon("code")
    val TERMINAL = Icon("terminal")
    val DATABASE = Icon("database")
    val LAYERS = Icon("layers")
    val BOOKMARK = Icon("bookmark")
    val TAG = Icon("tag")

    val GLOBE = Icon("globe")
    val MARKER = Icon("marker")
    val COMPASS = Icon("compass")
    val SERVER = Icon("server")
    val WIFI = Icon("wifi")
    val CUBE = Icon("cube")
    val CHEST = Icon("chest")
    val SWORD = Icon("sword")
    val PICKAXE = Icon("pickaxe")
    val SHIELD = Icon("shield")
    val POTION = Icon("potion")
    val COIN = Icon("coin")
    val GEM = Icon("gem")
    val TROPHY = Icon("trophy")
    val GIFT = Icon("gift")
    val CART = Icon("cart")
    val FIRE = Icon("fire")
    val LEAF = Icon("leaf")
    val DROP = Icon("drop")
    val SUN = Icon("sun")
    val MOON = Icon("moon")
    val CLOUD = Icon("cloud")
    val SKULL = Icon("skull")

    val MUSIC = Icon("music")
    val SOUND = Icon("sound")
    val MUTE = Icon("mute")
    val CAMERA = Icon("camera")
    val WRENCH = Icon("wrench")
    val HAMMER = Icon("hammer")
    val BRUSH = Icon("brush")
    val PALETTE = Icon("palette")
    val PUZZLE = Icon("puzzle")
    val PLUG = Icon("plug")
    val ROBOT = Icon("robot")

    val PLUS_CIRCLE = Icon("plus_circle")
    val MINUS_CIRCLE = Icon("minus_circle")
    val CLOSE_CIRCLE = Icon("close_circle")
    val CHECKBOX = Icon("checkbox")
    val RADIO = Icon("radio")
    val TOGGLE = Icon("toggle")
    val SLIDERS = Icon("sliders")
    val ZOOM_IN = Icon("zoom_in")
    val ZOOM_OUT = Icon("zoom_out")
    val FULLSCREEN = Icon("fullscreen")
    val WINDOW = Icon("window")
    val SIDEBAR = Icon("sidebar")
    val CROP = Icon("crop")
    val ROTATE = Icon("rotate")
    val FLIP = Icon("flip")
    val ALIGN_LEFT = Icon("align_left")
    val ALIGN_CENTER = Icon("align_center")
    val ALIGN_RIGHT = Icon("align_right")
    val TEXT = Icon("text")
    val HASH = Icon("hash")
    val AT = Icon("at")
    val PERCENT = Icon("percent")
    val PAPERCLIP = Icon("paperclip")
    val INBOX = Icon("inbox")
    val ARCHIVE = Icon("archive")
    val FOLDER_OPEN = Icon("folder_open")
    val FOLDER_PLUS = Icon("folder_plus")
    val FILE_PLUS = Icon("file_plus")
    val HISTORY = Icon("history")
    val HOURGLASS = Icon("hourglass")
    val HAND = Icon("hand")
    val POINTER = Icon("pointer")
    val THUMBS_UP = Icon("thumbs_up")
    val THUMBS_DOWN = Icon("thumbs_down")
    val SMILE = Icon("smile")
    val FROWN = Icon("frown")
    val LIGHTBULB = Icon("lightbulb")
    val SPARKLE = Icon("sparkle")
    val INFINITY = Icon("infinity")
    val PHONE = Icon("phone")
    val MONITOR = Icon("monitor")
    val KEYBOARD = Icon("keyboard")
    val MOUSE = Icon("mouse")
    val GAMEPAD = Icon("gamepad")
    val HEADPHONES = Icon("headphones")
    val MIC = Icon("mic")
    val VIDEO = Icon("video")
    val BATTERY = Icon("battery")
    val CHIP = Icon("chip")
    val BUG = Icon("bug")
    val SIGNAL = Icon("signal")
    val TREE = Icon("tree")
    val MOUNTAIN = Icon("mountain")
    val WAVE = Icon("wave")
    val SNOWFLAKE = Icon("snowflake")
    val RAIN = Icon("rain")
    val THERMOMETER = Icon("thermometer")
    val FLOWER = Icon("flower")
    val MUSHROOM = Icon("mushroom")
    val PORTAL = Icon("portal")
    val ANCHOR = Icon("anchor")
    val ROCKET = Icon("rocket")
    val TARGET = Icon("target")
    val DICE = Icon("dice")
    val BOOK = Icon("book")
    val SCROLL = Icon("scroll")
    val AXE = Icon("axe")
    val SHOVEL = Icon("shovel")
    val BOW = Icon("bow")
    val ARROW = Icon("arrow")
    val APPLE = Icon("apple")
    val BREAD = Icon("bread")
    val MEAT = Icon("meat")
    val FISH = Icon("fish")
    val EGG = Icon("egg")
    val BONE = Icon("bone")
    val FEATHER = Icon("feather")
    val INGOT = Icon("ingot")
    val TORCH = Icon("torch")
    val LANTERN = Icon("lantern")
    val BED = Icon("bed")
    val DOOR = Icon("door")
    val TNT = Icon("tnt")
    val CREEPER = Icon("creeper")
    val XP = Icon("xp")
    val ENCHANT = Icon("enchant")
    val HEART_HALF = Icon("heart_half")
    val HUNGER = Icon("hunger")
    val ARMOR = Icon("armor")
    val COMPASS_ROSE = Icon("compass_rose")
    val BEACON = Icon("beacon")
}
