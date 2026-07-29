//! A 5x7 bitmap font, for the progress window's one line of status text.
//!
//! ### Why not a font crate
//!
//! Because the alternative is worse for this specific job. Rendering real text means a rasteriser, a
//! font file to embed, and a licence to honour for that file — three dependencies and a legal
//! question, to draw the words "Downloading Java…" once, in a window most users never see. A bitmap
//! font is a few hundred bytes of data with no dependencies and no licence beyond this file's.
//!
//! The trade is that it covers uppercase, digits and a little punctuation only. [`fit`] upper-cases
//! whatever it is given and drops the rest, so an unsupported character degrades to a space rather
//! than to a panic.
//!
//! Each glyph is five columns; each column is a bitmask of seven rows, bit 0 at the top.

/// Glyph width in pixels, before spacing.
pub const W: usize = 5;
/// Glyph height in pixels.
pub const H: usize = 7;
/// Pixels between one glyph and the next.
pub const GAP: usize = 1;

/// The columns for `c`, or `None` if this font has no glyph for it.
pub fn glyph(c: char) -> Option<[u8; W]> {
    let g = match c.to_ascii_uppercase() {
        ' ' => [0x00, 0x00, 0x00, 0x00, 0x00],
        'A' => [0x7e, 0x09, 0x09, 0x09, 0x7e],
        'B' => [0x7f, 0x49, 0x49, 0x49, 0x36],
        'C' => [0x3e, 0x41, 0x41, 0x41, 0x22],
        'D' => [0x7f, 0x41, 0x41, 0x41, 0x3e],
        'E' => [0x7f, 0x49, 0x49, 0x49, 0x41],
        'F' => [0x7f, 0x09, 0x09, 0x09, 0x01],
        'G' => [0x3e, 0x41, 0x49, 0x49, 0x7a],
        'H' => [0x7f, 0x08, 0x08, 0x08, 0x7f],
        'I' => [0x00, 0x41, 0x7f, 0x41, 0x00],
        'J' => [0x20, 0x40, 0x41, 0x3f, 0x01],
        'K' => [0x7f, 0x08, 0x14, 0x22, 0x41],
        'L' => [0x7f, 0x40, 0x40, 0x40, 0x40],
        'M' => [0x7f, 0x02, 0x0c, 0x02, 0x7f],
        'N' => [0x7f, 0x04, 0x08, 0x10, 0x7f],
        'O' => [0x3e, 0x41, 0x41, 0x41, 0x3e],
        'P' => [0x7f, 0x09, 0x09, 0x09, 0x06],
        'Q' => [0x3e, 0x41, 0x51, 0x21, 0x5e],
        'R' => [0x7f, 0x09, 0x19, 0x29, 0x46],
        'S' => [0x46, 0x49, 0x49, 0x49, 0x31],
        'T' => [0x01, 0x01, 0x7f, 0x01, 0x01],
        'U' => [0x3f, 0x40, 0x40, 0x40, 0x3f],
        'V' => [0x1f, 0x20, 0x40, 0x20, 0x1f],
        'W' => [0x7f, 0x20, 0x18, 0x20, 0x7f],
        'X' => [0x63, 0x14, 0x08, 0x14, 0x63],
        'Y' => [0x03, 0x04, 0x78, 0x04, 0x03],
        'Z' => [0x61, 0x51, 0x49, 0x45, 0x43],
        '0' => [0x3e, 0x51, 0x49, 0x45, 0x3e],
        '1' => [0x00, 0x42, 0x7f, 0x40, 0x00],
        '2' => [0x42, 0x61, 0x51, 0x49, 0x46],
        '3' => [0x21, 0x41, 0x45, 0x4b, 0x31],
        '4' => [0x18, 0x14, 0x12, 0x7f, 0x10],
        '5' => [0x27, 0x45, 0x45, 0x45, 0x39],
        '6' => [0x3c, 0x4a, 0x49, 0x49, 0x30],
        '7' => [0x01, 0x71, 0x09, 0x05, 0x03],
        '8' => [0x36, 0x49, 0x49, 0x49, 0x36],
        '9' => [0x06, 0x49, 0x49, 0x29, 0x1e],
        '.' => [0x00, 0x00, 0x40, 0x00, 0x00],
        ',' => [0x00, 0x00, 0x60, 0x00, 0x00],
        ':' => [0x00, 0x00, 0x24, 0x00, 0x00],
        '-' => [0x08, 0x08, 0x08, 0x08, 0x08],
        '+' => [0x08, 0x08, 0x3e, 0x08, 0x08],
        '%' => [0x23, 0x13, 0x08, 0x64, 0x62],
        '/' => [0x20, 0x10, 0x08, 0x04, 0x02],
        '(' => [0x00, 0x1c, 0x22, 0x41, 0x00],
        ')' => [0x00, 0x41, 0x22, 0x1c, 0x00],
        '!' => [0x00, 0x00, 0x5f, 0x00, 0x00],
        '?' => [0x02, 0x01, 0x59, 0x09, 0x06],
        _ => return None,
    };
    Some(g)
}

/// Width in pixels of `text` as [`draw`](crate::window) would lay it out.
pub fn width(text: &str) -> usize {
    let n = text.chars().count();
    if n == 0 {
        0
    } else {
        n * (W + GAP) - GAP
    }
}

/// `text` reduced to characters this font can draw.
///
/// Unsupported characters become spaces rather than being dropped, so a string keeps its shape —
/// notably the `…` the stage messages end with, which would otherwise pull the following text left.
pub fn fit(text: &str) -> String {
    text.chars()
        .map(|c| if glyph(c).is_some() { c.to_ascii_uppercase() } else { ' ' })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn measures_by_glyph_and_gap() {
        assert_eq!(width(""), 0);
        assert_eq!(width("A"), W);
        assert_eq!(width("AB"), W * 2 + GAP);
    }

    #[test]
    fn substitutes_rather_than_dropping() {
        // The ellipsis has no glyph, but the string must keep its length so nothing shifts.
        let out = fit("Downloading Java…");
        assert_eq!(out.chars().count(), "Downloading Java…".chars().count());
        assert!(out.starts_with("DOWNLOADING JAVA"));
        assert!(out.ends_with(' '));
    }

    #[test]
    fn covers_every_character_the_stage_messages_use() {
        use crate::progress::Stage;
        for stage in [
            Stage::FindingJava,
            Stage::Resolving,
            Stage::Downloading,
            Stage::Unpacking,
            Stage::Launching,
        ] {
            for c in stage.message().chars() {
                // The ellipsis is the one deliberate exception; everything else must be drawable.
                assert!(
                    c == '…' || glyph(c).is_some(),
                    "no glyph for {c:?} in {:?}",
                    stage.message()
                );
            }
        }
    }
}
