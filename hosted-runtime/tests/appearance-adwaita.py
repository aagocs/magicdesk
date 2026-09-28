#!/usr/bin/env python3
"""GTK4/libadwaita portal preference peer for either display protocol."""
import gi
gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, Gtk


def activate(application):
    window = Adw.ApplicationWindow(application=application)
    window.set_default_size(520, 300)
    body = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=16,
                   margin_top=24, margin_bottom=24, margin_start=24, margin_end=24)
    label = Gtk.Label()
    body.append(label)
    body.append(Gtk.Entry(placeholder_text="Text input"))
    window.set_content(body)
    manager = Adw.StyleManager.get_default()

    def report(*_):
        name = "dark" if manager.get_dark() else "light"
        label.set_text(name)
        window.set_title("Portal appearance: " + name)
        print("THEME " + name, flush=True)

    manager.connect("notify::dark", report)
    report()
    window.present()


app = Adw.Application(application_id="io.github.mekhontsev.magicdesk.AppearanceCheck")
app.connect("activate", activate)
app.run(None)
