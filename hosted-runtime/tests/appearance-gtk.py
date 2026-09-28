#!/usr/bin/env python3
"""GTK3 appearance and local-file-chooser peer for either display protocol."""
import gi
gi.require_version("Gtk", "3.0")
from gi.repository import Gtk

settings = Gtk.Settings.get_default()
window = Gtk.Window()
window.set_default_size(520, 300)
window.connect("destroy", Gtk.main_quit)
body = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=16, margin=24)
window.add(body)
label = Gtk.Label()
body.pack_start(label, False, False, 0)
body.pack_start(Gtk.Entry(placeholder_text="Text input"), False, False, 0)


def report(*_):
    name = settings.get_property("gtk-theme-name")
    label.set_text(name)
    window.set_title("Appearance: " + name)
    print("THEME " + name, flush=True)


def choose_file(*_):
    chooser = Gtk.FileChooserNative.new("Appearance file chooser", window, Gtk.FileChooserAction.OPEN,
                                        "Open", "Cancel")
    chooser.connect("response", lambda dialog, response: dialog.destroy())
    window.chooser = chooser
    chooser.show()


button = Gtk.Button(label="Open file chooser")
button.connect("clicked", choose_file)
body.pack_start(button, False, False, 0)
settings.connect("notify::gtk-theme-name", report)
report()
window.show_all()
Gtk.main()
