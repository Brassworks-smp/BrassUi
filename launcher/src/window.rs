//! The optional first-run progress window.
//!
//! ### Why the work runs on a thread
//!
//! A windowing event loop wants to own a thread, and on macOS it must specifically own the *main*
//! one. The bootstrap sequence is ordinary blocking code. Rather than contort one to fit the other,
//! [`run`] puts the bootstrap on a worker thread and keeps the event loop on the main thread, with a
//! channel between them: the worker's [`Progress`] implementation is a channel sender, and the event
//! loop drains it and repaints.
//!
//! That also gets the window's lifetime right for free. The loop exits when the worker reports
//! [`Stage::Launching`], so the window is gone before the application's own window appears rather
//! than lingering behind it.

use std::sync::mpsc::{channel, Receiver, Sender};

use winit::application::ApplicationHandler;
use winit::dpi::LogicalSize;
use winit::event::WindowEvent;
use winit::event_loop::{ActiveEventLoop, ControlFlow, EventLoop};
use winit::window::{Window as WinitWindow, WindowId};

use crate::font;
use crate::progress::{Progress, Stage};
use crate::{Bootstrap, Error};

/// Window size in logical pixels. Small on purpose — it reports one thing.
const WIDTH: u32 = 420;
const HEIGHT: u32 = 130;

/// The palette, in `0x00RRGGBB` as softbuffer wants it.
const BG: u32 = 0x0016141c;
const BAR_BG: u32 = 0x0026222e;
const BAR_FILL: u32 = 0x00c9a27a;
const TEXT: u32 = 0x00e8e3f2;
const TEXT_DIM: u32 = 0x008d8598;
const ERROR: u32 = 0x00e07a8a;

/// What the worker tells the event loop.
enum Update {
    Stage(Stage),
    Bytes(u64, Option<u64>),
    Failed(String),
    /// The bootstrap finished, successfully or not; the loop can stop.
    Done,
}

/// A [`Progress`] that forwards to the event loop.
struct Channel(Sender<Update>);

impl Progress for Channel {
    fn stage(&mut self, stage: Stage) {
        let _ = self.0.send(Update::Stage(stage));
    }

    fn bytes(&mut self, done: u64, total: Option<u64>) {
        let _ = self.0.send(Update::Bytes(done, total));
    }

    fn failed(&mut self, message: &str) {
        let _ = self.0.send(Update::Failed(message.to_string()));
    }
}

/// Run `bootstrap` with a progress window.
///
/// Returns whatever the bootstrap returned. The window is only actually shown once there is something
/// worth showing — see [`App::should_show`].
pub fn run(bootstrap: Bootstrap, title: &str) -> Result<(), Error> {
    let (tx, rx) = channel();

    let worker = std::thread::spawn(move || {
        let mut progress = Channel(tx.clone());
        let result = bootstrap.try_run_with(&mut progress);
        if let Err(e) = &result {
            let _ = tx.send(Update::Failed(e.to_string()));
        }
        let _ = tx.send(Update::Done);
        result
    });

    let mut app = App::new(rx, title.to_string());

    // A failure to get an event loop at all — no display, a headless CI box — must not take the
    // launch down with it: the work is already running on the worker and does not need a window.
    if let Ok(event_loop) = EventLoop::new() {
        event_loop.set_control_flow(ControlFlow::Wait);
        let _ = event_loop.run_app(&mut app);
    }

    worker.join().unwrap_or(Err(Error::NoJava(
        "the bootstrap thread panicked".to_string(),
    )))
}

/// Show a native error box.
///
/// Deliberately not a dependency: this is one platform call each, and pulling a dialog crate in for
/// it would add more to the tree than the whole window feature.
pub fn message_box(title: &str, message: &str) {
    #[cfg(target_os = "macos")]
    {
        let script = format!(
            "display dialog {} with title {} buttons {{\"OK\"}} default button 1 with icon stop",
            applescript_string(message),
            applescript_string(title),
        );
        let _ = std::process::Command::new("osascript")
            .arg("-e")
            .arg(script)
            .status();
    }
    #[cfg(target_os = "linux")]
    {
        // Whichever of the two common dialog tools is installed; if neither is, stderr already has it.
        let _ = std::process::Command::new("zenity")
            .args(["--error", "--title", title, "--text", message])
            .status()
            .or_else(|_| {
                std::process::Command::new("kdialog")
                    .args(["--error", message, "--title", title])
                    .status()
            });
    }
    #[cfg(target_os = "windows")]
    {
        // PowerShell rather than a winapi binding, for the same reason as above.
        let script = format!(
            "Add-Type -AssemblyName PresentationFramework; \
             [System.Windows.MessageBox]::Show('{}','{}','OK','Error')",
            message.replace('\'', "''"),
            title.replace('\'', "''"),
        );
        let _ = std::process::Command::new("powershell")
            .args(["-NoProfile", "-WindowStyle", "Hidden", "-Command", &script])
            .status();
    }
    let _ = (title, message);
}

#[cfg(target_os = "macos")]
fn applescript_string(s: &str) -> String {
    format!("\"{}\"", s.replace('\\', "\\\\").replace('"', "\\\""))
}

/// The event-loop side: owns the window and paints the latest update.
struct App {
    rx: Receiver<Update>,
    title: String,
    window: Option<std::sync::Arc<WinitWindow>>,
    surface: Option<softbuffer::Surface<std::sync::Arc<WinitWindow>, std::sync::Arc<WinitWindow>>>,
    context: Option<softbuffer::Context<std::sync::Arc<WinitWindow>>>,
    stage: Stage,
    done: u64,
    total: Option<u64>,
    error: Option<String>,
    finished: bool,
}

impl App {
    fn new(rx: Receiver<Update>, title: String) -> Self {
        Self {
            rx,
            title,
            window: None,
            surface: None,
            context: None,
            stage: Stage::FindingJava,
            done: 0,
            total: None,
            error: None,
            finished: false,
        }
    }

    /// Whether this stage is worth opening a window for.
    ///
    /// Finding Java is normally over in milliseconds, and flashing a window up for it would be the
    /// splash screen this crate specifically avoids. The window appears when a download starts, which
    /// is the only part slow enough for a user to wonder whether anything is happening.
    fn should_show(&self) -> bool {
        self.error.is_some()
            || matches!(
                self.stage,
                Stage::Resolving | Stage::Downloading | Stage::Unpacking
            )
    }

    /// Drain the channel. Returns false when the loop should stop.
    fn pump(&mut self) -> bool {
        while let Ok(update) = self.rx.try_recv() {
            match update {
                Update::Stage(s) => {
                    // Launching means the JVM is about to take over the screen; the window has done
                    // its job and should be gone before the app's own window appears.
                    if s == Stage::Launching {
                        return false;
                    }
                    self.stage = s;
                    self.done = 0;
                    self.total = None;
                }
                Update::Bytes(d, t) => {
                    self.done = d;
                    self.total = t;
                }
                Update::Failed(e) => self.error = Some(e),
                Update::Done => {
                    self.finished = true;
                    // A failure stays on screen; the error box in `run` is what the user acts on, and
                    // closing this first would leave the screen empty behind it.
                    if self.error.is_none() {
                        return false;
                    }
                }
            }
        }
        true
    }

    fn ensure_window(&mut self, event_loop: &ActiveEventLoop) {
        if self.window.is_some() || !self.should_show() {
            return;
        }
        let attrs = WinitWindow::default_attributes()
            .with_title(&self.title)
            .with_inner_size(LogicalSize::new(WIDTH, HEIGHT))
            .with_resizable(false);
        let Ok(window) = event_loop.create_window(attrs) else {
            return;
        };
        let window = std::sync::Arc::new(window);
        if let Ok(context) = softbuffer::Context::new(window.clone()) {
            if let Ok(surface) = softbuffer::Surface::new(&context, window.clone()) {
                self.surface = Some(surface);
                self.context = Some(context);
            }
        }
        self.window = Some(window);
    }

    fn paint(&mut self) {
        let (Some(window), Some(surface)) = (&self.window, &mut self.surface) else {
            return;
        };
        let size = window.inner_size();
        let (Some(w), Some(h)) = (
            std::num::NonZeroU32::new(size.width),
            std::num::NonZeroU32::new(size.height),
        ) else {
            return;
        };
        if surface.resize(w, h).is_err() {
            return;
        }
        let Ok(mut buffer) = surface.buffer_mut() else {
            return;
        };

        let (w, h) = (size.width as usize, size.height as usize);
        let mut canvas = Canvas {
            pixels: &mut buffer,
            w,
            h,
        };
        canvas.clear(BG);

        // Scale everything from the window height so a HiDPI window is not a quarter-size UI in the
        // corner: winit gives us physical pixels, and this window's logical size is fixed.
        let scale = (h / HEIGHT as usize).max(1);
        let pad = 16 * scale;

        if let Some(err) = &self.error {
            canvas.text(pad, pad, "COULD NOT START", ERROR, scale);
            // One line's worth; the full message is in the error box and on stderr.
            let short: String = err.chars().take((w - pad * 2) / ((font::W + font::GAP) * scale)).collect();
            canvas.text(pad, pad + 14 * scale, &short, TEXT_DIM, scale);
        } else {
            canvas.text(pad, pad, self.title.as_str(), TEXT, scale);
            canvas.text(pad, pad + 16 * scale, self.stage.message(), TEXT_DIM, scale);

            let bar_y = h - pad - 8 * scale;
            let bar_w = w - pad * 2;
            canvas.rect(pad, bar_y, bar_w, 8 * scale, BAR_BG);

            match self.total.filter(|t| *t > 0) {
                Some(total) => {
                    let filled = (bar_w as u64 * self.done.min(total) / total) as usize;
                    canvas.rect(pad, bar_y, filled, 8 * scale, BAR_FILL);
                    let percent = (self.done * 100 / total).min(100);
                    let label = format!("{percent}%");
                    canvas.text(
                        w - pad - font::width(&label) * scale,
                        pad + 16 * scale,
                        &label,
                        TEXT,
                        scale,
                    );
                }
                None => {
                    // No Content-Length: a sweeping block, so the window still reads as "working"
                    // rather than as a bar stuck at zero.
                    let period = 2_000u128;
                    let t = std::time::SystemTime::now()
                        .duration_since(std::time::UNIX_EPOCH)
                        .map(|d| d.as_millis() % period)
                        .unwrap_or(0);
                    let block = bar_w / 4;
                    let x = (t as usize * (bar_w + block)) / period as usize;
                    let start = x.saturating_sub(block).min(bar_w);
                    let end = x.min(bar_w);
                    canvas.rect(pad + start, bar_y, end - start, 8 * scale, BAR_FILL);
                }
            }
        }

        let _ = buffer.present();
    }
}

impl ApplicationHandler for App {
    fn resumed(&mut self, event_loop: &ActiveEventLoop) {
        self.ensure_window(event_loop);
    }

    fn about_to_wait(&mut self, event_loop: &ActiveEventLoop) {
        if !self.pump() {
            event_loop.exit();
            return;
        }
        self.ensure_window(event_loop);
        if let Some(window) = &self.window {
            window.request_redraw();
        }
        // Poll rather than Wait: updates arrive on a channel, which is not something the event loop
        // can be woken by. ~60 Hz keeps the indeterminate sweep smooth without spinning a core.
        event_loop.set_control_flow(ControlFlow::WaitUntil(
            std::time::Instant::now() + std::time::Duration::from_millis(16),
        ));
    }

    fn window_event(&mut self, event_loop: &ActiveEventLoop, _id: WindowId, event: WindowEvent) {
        match event {
            // Closing the window cancels nothing — the worker owns the download and there is no way
            // to interrupt it mid-write safely. Hiding the window and letting the work finish is
            // less surprising than appearing to cancel and then launching anyway.
            WindowEvent::CloseRequested => {
                self.window = None;
                self.surface = None;
                self.context = None;
                if self.finished {
                    event_loop.exit();
                }
            }
            WindowEvent::RedrawRequested => self.paint(),
            _ => {}
        }
    }
}

/// A raw pixel buffer with the three drawing operations this window needs.
struct Canvas<'a, 'b> {
    pixels: &'a mut softbuffer::Buffer<'b, std::sync::Arc<WinitWindow>, std::sync::Arc<WinitWindow>>,
    w: usize,
    h: usize,
}

impl Canvas<'_, '_> {
    fn clear(&mut self, color: u32) {
        self.pixels.fill(color);
    }

    fn rect(&mut self, x: usize, y: usize, w: usize, h: usize, color: u32) {
        for row in y..(y + h).min(self.h) {
            for col in x..(x + w).min(self.w) {
                self.pixels[row * self.w + col] = color;
            }
        }
    }

    /// Draw `text` with the bitmap font, each source pixel becoming a `scale`-sized block.
    fn text(&mut self, x: usize, y: usize, text: &str, color: u32, scale: usize) {
        let mut cx = x;
        for c in font::fit(text).chars() {
            if let Some(glyph) = font::glyph(c) {
                for (col, bits) in glyph.iter().enumerate() {
                    for row in 0..font::H {
                        if bits & (1 << row) != 0 {
                            self.rect(
                                cx + col * scale,
                                y + row * scale,
                                scale,
                                scale,
                                color,
                            );
                        }
                    }
                }
            }
            cx += (font::W + font::GAP) * scale;
        }
    }
}
