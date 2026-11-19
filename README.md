<h1 align="center">BrassUi</h1>

<p align="center">
  Minecraft screens that don't look like Minecraft screens.
</p>

<p align="center">
  <a href="https://github.com/Brassworks-smp/BrassUi/wiki"><img src="https://img.shields.io/badge/docs-wiki-34d27a?style=flat-square" alt="Wiki" /></a>
  <img src="https://img.shields.io/badge/minecraft-1.21.1%20%7C%2026.1-blue?style=flat-square" alt="Minecraft 1.21.1 and 26.1" />
  <img src="https://img.shields.io/badge/loader-NeoForge-orange?style=flat-square" alt="NeoForge" />
  <img src="https://img.shields.io/badge/license-PolyForm%20Noncommercial-lightgrey?style=flat-square" alt="License" />
</p>

<p align="center">
  <img src="https://raw.githubusercontent.com/wiki/Brassworks-smp/BrassUi/images/example-hub.gif" alt="Server hub example" width="720" />
</p>

BrassUi is the widget kit behind the Brassworks launcher, ported into the game. It sits on
[Elementa](https://github.com/EssentialGG/Elementa) and gives it a look: raised keycaps on near-black ink, one
accent colour, cards that never clip their own border, and layouts that reflow instead of overflowing. It also
lets a button run code on the server without writing packets.

The same widgets run inside Minecraft as a NeoForge mod and on the desktop as a standalone app, which is where
every recording here was made.

**Everything is documented on the [wiki](https://github.com/Brassworks-smp/BrassUi/wiki)**: guides, every
widget with a recording, complete examples, and an API reference for every class.

## Examples

<table>
  <tr>
    <td><img src="https://raw.githubusercontent.com/wiki/Brassworks-smp/BrassUi/images/example-mod-settings.gif" alt="Mod settings" width="360" /></td>
    <td><img src="https://raw.githubusercontent.com/wiki/Brassworks-smp/BrassUi/images/example-market.gif" alt="Networked market" width="360" /></td>
  </tr>
  <tr>
    <td>A settings screen: nav rail, sliding pages, drill-down keybinds, pinned footer.</td>
    <td>A shop that runs on the server: actions, shared stock, a per-player wallet, permission-gated admin key.</td>
  </tr>
  <tr>
    <td><img src="https://raw.githubusercontent.com/wiki/Brassworks-smp/BrassUi/images/reference-quests.gif" alt="Quest journal" width="360" /></td>
    <td><img src="https://raw.githubusercontent.com/wiki/Brassworks-smp/BrassUi/images/reference-guild.gif" alt="Guild manager" width="360" /></td>
  </tr>
  <tr>
    <td>The quest book: sidebar, quest lines as living trees, a hidden line revealed.</td>
    <td>A multi-tab manager: list rows with actions, confirm dialogs, tables, empty states.</td>
  </tr>
</table>

<p align="center">
  <img src="https://raw.githubusercontent.com/wiki/Brassworks-smp/BrassUi/images/toggle.gif" alt="Toggle" height="60" />
  <img src="https://raw.githubusercontent.com/wiki/Brassworks-smp/BrassUi/images/slider.gif" alt="Slider" height="60" />
  <img src="https://raw.githubusercontent.com/wiki/Brassworks-smp/BrassUi/images/tab-switch.gif" alt="Tab switch" height="60" />
  <img src="https://raw.githubusercontent.com/wiki/Brassworks-smp/BrassUi/images/confirm-slider.gif" alt="Confirm slider" height="60" />
</p>

The full source of each example is on the [Examples](https://github.com/Brassworks-smp/BrassUi/wiki/Examples)
page, and every widget is on the [Widgets](https://github.com/Brassworks-smp/BrassUi/wiki/Widgets) page.

## Quick start

```kotlin
repositories {
    maven("https://maven.opnsoc.org/releases")
    mavenCentral()
}

dependencies {
    implementation(jarJar("net.swzo.brass:brassui:2.17.1-mc1.21.1") {}) // or 2.17.1-mc26.1
}
```

A screen:

```kotlin
class HelloScreen : BrassAppScreen("Hello", subtitle = "brassui", maxWidth = 260f, maxHeight = 140f) {
    init {
        BrassPanel("WELCOME").add(
            BrassLabel("Your first brassui screen"),
            BrassButton("Click me", BrassAccent.BRASS) { BrassToast.show(background, "Clicked", BrassToast.Type.SUCCESS) }
                .constrain { height = 20.pixels() },
        ).constrain { width = 100.percent(); height = 100.percent() } childOf body
    }
}
```

An action that runs on the server, with no packets or registration:

```kotlin
@BrassActionSet
object TeamActions : BrassActions {
    data class Rename(val teamId: String, val name: String)

    val teamName = brassValue("example.team.name", "Brassworks")

    val rename = brassAction<Rename>(id = "example.team.rename", permission = "example.team.rename", minOpLevel = 2) { _, input ->
        teamName.value = input.name
        ok()
    }
}

// in a screen
actionButton("Rename", TeamActions.rename) { TeamActions.Rename("main", field.text) }
BrassNet.state<String>("example.team.name").onChange { label.text = it ?: "" }
```

[Getting started](https://github.com/Brassworks-smp/BrassUi/wiki/Getting-Started) and
[Networking](https://github.com/Brassworks-smp/BrassUi/wiki/Networking) take it from there.

## Try it

```bash
./gradlew :desktop:run                          # the widget gallery, no Minecraft needed
./gradlew :desktop:run -Dui.screen=market       # one of the examples
./gradlew :mod:1.21.1-neoforge:runClient        # in game, then /brassui
```

Building, testing and publishing are covered in
[Building from source](https://github.com/Brassworks-smp/BrassUi/wiki/Building-from-Source).

## License

Source-available under the [PolyForm Noncommercial License 1.0.0](LICENSE.md). Noncommercial use is granted;
commercial use by anyone other than the copyright holder requires a separate commercial license. The copyright
holder retains the right to use BrassUi commercially and offer it under additional license terms.
