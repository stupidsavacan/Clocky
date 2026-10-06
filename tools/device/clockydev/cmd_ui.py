"""inspect, tap, long-press, drag, swipe, scroll, key, wait."""
import os
import time

from . import devstate, gestures, ui
from .cli import Out, command
from .cmd_session import pending_lines
from .errors import ui_err, usage
from .session import get_setting, load_state, next_step_dir


# ------------------------------------------------------------ selector args

def add_selector_args(p, positional=True):
    if positional:
        p.add_argument("label", nargs="?", help="text or content-desc to match (exact; see --contains)")
    p.add_argument("--text", help="match text exactly")
    p.add_argument("--desc", help="match content-desc exactly")
    p.add_argument("--id", help="resource id: full 'pkg:id/name' or suffix 'name'")
    p.add_argument("--class", dest="cls", help="widget class (full or simple name)")
    p.add_argument("--package", dest="pkg", help="restrict to a package")
    p.add_argument("--contains", action="store_true", help="substring match for label/text/desc")
    p.add_argument("--index", type=int, help="pick the Nth match (0-based, document order)")


def selector_from(args):
    return ui.Selector(getattr(args, "label", None), args.text, args.desc, args.id, args.cls, args.pkg,
                       args.contains, args.index)


def add_xy(p, name="--xy", dest="xy"):
    p.add_argument(name, dest=dest, nargs=2, type=int, metavar=("X", "Y"), help="raw pixel coordinates (escape hatch)")


def add_wait_scroll(p):
    p.add_argument("--wait", type=float, default=0, metavar="S", help="poll for the node up to S seconds")
    p.add_argument("--scroll", action="store_true", help="scroll the largest scrollable area to find the node")
    p.add_argument("--from-top", action="store_true", help="with --scroll: scroll to the top first")
    p.add_argument("--in", dest="in_sel", help="with --scroll: LABEL/id of the scrollable container to use")


# ------------------------------------------------------------------ helpers

def observe(dev):
    xml = ui.dump_xml(dev.adb)
    nodes, screen = ui.parse_hierarchy(xml)
    return xml, nodes, screen


def usable(nodes, screen):
    return ui.visible(nodes, screen)


def save_dump(ctx, label, xml, nodes):
    d = next_step_dir(ctx, label)
    with open(os.path.join(d, "ui.xml"), "w", encoding="utf-8") as f:
        f.write(xml)
    with open(os.path.join(d, "ui.txt"), "w", encoding="utf-8") as f:
        f.write(ui.render_text(nodes))
    return d, [ctx.rel(os.path.join(d, "ui.xml")), ctx.rel(os.path.join(d, "ui.txt"))]


def acquire(ctx, dev, sel, wait=0, scroll=False, from_top=False, in_sel=None, label="ui"):
    """Dump (polling/scrolling as asked) until `sel` yields exactly one node. Returns (node, nodes, xml, dir, files)."""
    if scroll and not wait:
        wait = 3      # screens often are still inflating right after an Activity switch
    deadline = time.time() + wait
    xml, nodes, screen = observe(dev)
    if scroll and from_top:
        prev = None
        for _ in range(10):
            vis = usable(nodes, screen)
            h = ui.content_hash(vis)
            if h == prev:
                break
            prev = h
            cont = _container(vis, in_sel)
            if not cont:
                break
            a, b = gestures.scroll_points(cont.bounds, "up", screen[1])
            dev.adb.shell(gestures.swipe_cmd(a[0], a[1], b[0], b[1], 300), timeout=10)
            time.sleep(0.6)
            xml, nodes, screen = observe(dev)
    scrolls, prev_hash = 0, None
    while True:
        vis = usable(nodes, screen)
        if ui.find(vis, sel):
            node, _ms = ui.pick(vis, sel)
            d, files = save_dump(ctx, label, xml, nodes)
            return node, nodes, xml, d, files
        if scroll and scrolls < 10:
            h = ui.content_hash(vis)
            if h == prev_hash:
                scroll = False
                continue
            prev_hash = h
            cont = _container(vis, in_sel)
            if cont is None:
                scroll = False
                continue
            a, b = gestures.scroll_points(cont.bounds, "down", screen[1])
            dev.adb.shell(gestures.swipe_cmd(a[0], a[1], b[0], b[1], 300), timeout=10)
            scrolls += 1
            time.sleep(0.6)
            xml, nodes, screen = observe(dev)
            continue
        if time.time() < deadline:
            time.sleep(0.5)
            xml, nodes, screen = observe(dev)
            continue
        d, files = save_dump(ctx, label + "-notfound", xml, nodes)
        try:
            ui.pick(vis, sel)
        except Exception as e:
            e.hint = ((e.hint or "") + " (dump saved: %s)" % files[0]).strip() if hasattr(e, "hint") else None
            raise


def _container(vis, in_sel):
    if in_sel:
        ms = ui.find(vis, ui.Selector(label=in_sel)) or ui.find(vis, ui.Selector(id=in_sel))
        scr = [n for n in ms if n.scrollable]
        if scr:
            return max(scr, key=lambda n: n.area)
        return None
    return ui.largest_scrollable(vis)


def screen_size(dev):
    """(w, h) honoring current rotation, from wm size + window rotation."""
    w = devstate.parse_wm(dev.adb.shell("wm size", timeout=10).text)
    size = w.get("override") or w.get("physical")
    if not size:
        raise ui_err("NO_SCREEN_SIZE", "could not read the screen size", "Run `cdev status`.")
    rot = devstate.parse_window(dev.adb.shell("dumpsys window", timeout=30).text).get("rotation") or 0
    return (size[1], size[0]) if rot % 2 else size


def check_xy(dev, x, y):
    w, h = screen_size(dev)
    if not (0 <= x < w and 0 <= y < h):
        raise usage("XY_OFFSCREEN", "(%d,%d) is outside the %dx%d screen" % (x, y, w, h),
                    "Take coordinates from `cdev inspect` or the screenshot.")


def focus_after(dev, delay=0.6):
    time.sleep(delay)
    return devstate.parse_window(dev.adb.shell("dumpsys window", timeout=30).text).get("focus")


def resolve_point(ctx, dev, args, label, xy=None, sel=None):
    """Return (x, y, node_brief|None, artifacts). Uses --xy when given, else selector."""
    xy = xy or getattr(args, "xy", None)
    if xy:
        check_xy(dev, xy[0], xy[1])
        return xy[0], xy[1], None, []
    sel = sel or selector_from(args)
    node, nodes, xml, d, files = acquire(ctx, dev, sel, args.wait, args.scroll, args.from_top, args.in_sel, label)
    x, y = node.center
    brief = node.brief()
    anc = ui.clickable_ancestor(node)
    if anc is not None and anc is not node:
        brief["clickable_ancestor"] = anc.brief()
    return x, y, brief, files


def _need_target(args):
    if not args.xy and selector_from(args).is_empty():
        raise usage("NO_TARGET", "give a LABEL/selector or --xy X Y",
                    "Example: cdev tap \"OK\"   |   cdev tap --id clocky_widget_save --scroll")


# ----------------------------------------------------------------- inspect

def _inspect_setup(p):
    p.add_argument("--all", action="store_true", help="list every node, not only labelled/interactive ones")
    p.add_argument("--no-screenshot", action="store_true")
    p.add_argument("--wait-for", metavar="LABEL", help="wait until a node with this text/desc appears")
    p.add_argument("--timeout", type=float, default=10)


@command("inspect", _inspect_setup)
def cmd_inspect(ctx, args):
    """Dump the screen: focus, node table, screenshot."""
    dev = ctx.device()
    deadline = time.time() + (args.timeout if args.wait_for else 0)
    warnings = list(dev.warnings)
    shot = None
    try:
        while True:
            xml, nodes, screen = observe(dev)
            if not args.wait_for or ui.find(usable(nodes, screen), ui.Selector(label=args.wait_for)):
                break
            if time.time() >= deadline:
                raise ui_err("WAIT_TIMEOUT", "%r did not appear within %ss" % (args.wait_for, args.timeout),
                             "Run `cdev inspect` without --wait-for to see what is on screen.")
            time.sleep(0.5)
    except Exception:
        # keep evidence for the human/Claude even when the dump fails
        if not args.no_screenshot:
            try:
                d = next_step_dir(ctx, "inspect-failed")
                p = os.path.join(d, "screen.png")
                with open(p, "wb") as f:
                    f.write(ui.screenshot(dev.adb))
                warnings.append("screenshot saved: " + ctx.rel(p))
            except Exception:
                pass
        raise
    vis = usable(nodes, screen)
    d, files = save_dump(ctx, "inspect", xml, nodes)
    if not args.no_screenshot:
        p = os.path.join(d, "screen.png")
        with open(p, "wb") as f:
            f.write(ui.screenshot(dev.adb))
        files.append(ctx.rel(p))
        small = ui.save_small(p)
        if small:
            files.append(ctx.rel(small))
        else:
            warnings.append("Pillow not installed: screen_small.png not created")
    win = devstate.parse_window(dev.adb.shell("dumpsys window", timeout=30).text)
    ime = devstate.parse_ime(dev.adb.shell("dumpsys input_method", timeout=30).text)
    st = {"model": dev.model_name(), "sdk": dev.sdk(), "rotation": win.get("rotation"), "keyguard": win.get("keyguard"),
          "wakefulness": "Awake", "ime_shown": ime,
          "settings": {"accelerometer_rotation": get_setting(dev.adb, "system/accelerometer_rotation")}}
    lines = [devstate.human_line(dev.serial, dev.transport, st), "focus  %s" % win.get("focus"),
             "saved  " + ", ".join(files)]
    lines += pending_lines(dev.adb, load_state(ctx.build_dir))
    lines += ui.render_table(vis, only_interesting=not args.all)
    return Out({"focus": win.get("focus"), "rotation": win.get("rotation"), "ime_shown": ime,
                "nodes": [n.brief(i) for i, n in enumerate(vis) if args.all or n.label or n.is_host or n.scrollable
                          or (n.clickable and n.rid)]},
               lines, warnings, files)


# --------------------------------------------------------------------- tap

def _tap_setup(p):
    add_selector_args(p)
    add_xy(p)
    add_wait_scroll(p)


@command("tap", _tap_setup)
def cmd_tap(ctx, args):
    """Tap a node chosen by selector (or --xy)."""
    _need_target(args)
    dev = ctx.device()
    x, y, brief, files = resolve_point(ctx, dev, args, "tap")
    dev.adb.shell(gestures.tap_cmd(x, y), timeout=10, check=True)
    focus = focus_after(dev)
    desc = brief["label"] if brief and brief["label"] else (brief or {}).get("id") or "xy"
    return Out({"tapped": brief, "xy": [x, y], "focus_after": focus},
               ["tap %s at (%d,%d)" % (desc, x, y), "focus %s" % focus], dev.warnings, files)


def _lp_setup(p):
    add_selector_args(p)
    add_xy(p)
    add_wait_scroll(p)
    p.add_argument("--ms", type=int, default=1500)


@command("long-press", _lp_setup)
def cmd_long_press(ctx, args):
    """Long-press a node (or --xy)."""
    _need_target(args)
    dev = ctx.device()
    x, y, brief, files = resolve_point(ctx, dev, args, "long-press")
    dev.adb.shell(gestures.long_press_cmd(x, y, args.ms), timeout=args.ms // 1000 + 15, check=True)
    focus = focus_after(dev, 0.4)
    return Out({"pressed": brief, "xy": [x, y], "ms": args.ms, "focus_after": focus},
               ["long-press at (%d,%d) for %dms" % (x, y, args.ms), "focus %s" % focus], dev.warnings, files)


# -------------------------------------------------------------------- drag

def _drag_setup(p):
    add_selector_args(p)
    add_xy(p)
    add_wait_scroll(p)
    p.add_argument("--to", metavar="LABEL", help="destination node (exact text/desc)")
    p.add_argument("--to-id", metavar="ID", help="destination node by resource id")
    p.add_argument("--to-xy", nargs=2, type=int, metavar=("X", "Y"))
    p.add_argument("--hold-ms", type=int, default=1500)
    p.add_argument("--steps", type=int, default=10)
    p.add_argument("--move-ms", type=int, default=1000)


@command("drag", _drag_setup)
def cmd_drag(ctx, args):
    """Press-hold-move-release gesture (launcher drag, resize handles)."""
    _need_target(args)
    if not (args.to or args.to_id or args.to_xy):
        raise usage("NO_DESTINATION", "drag needs --to LABEL, --to-id ID or --to-xy X Y")
    dev = ctx.device()
    gestures.require_motionevent(dev.sdk())
    x, y, brief, files = resolve_point(ctx, dev, args, "drag-from")
    if args.to_xy:
        check_xy(dev, *args.to_xy)
        tx, ty, tbrief = args.to_xy[0], args.to_xy[1], None
    else:
        sel = ui.Selector(label=args.to, id=args.to_id)
        node, _n, _x, _d, f2 = acquire(ctx, dev, sel, 0, False, False, None, "drag-to")
        files = files + f2
        tx, ty = node.center
        tbrief = node.brief()
    script = gestures.motion_script((x, y), (tx, ty), args.hold_ms, args.steps, args.move_ms)
    dev.adb.shell(script, timeout=(args.hold_ms + args.move_ms) // 1000 + 20, check=True)
    focus = focus_after(dev, 0.8)
    return Out({"from": brief, "from_xy": [x, y], "to": tbrief, "to_xy": [tx, ty], "focus_after": focus},
               ["drag (%d,%d) -> (%d,%d)" % (x, y, tx, ty), "focus %s" % focus], dev.warnings, files)


# ------------------------------------------------------------ swipe/scroll

def _swipe_setup(p):
    p.add_argument("x1", type=int)
    p.add_argument("y1", type=int)
    p.add_argument("x2", type=int)
    p.add_argument("y2", type=int)
    p.add_argument("--ms", type=int, default=300)


@command("swipe", _swipe_setup)
def cmd_swipe(ctx, args):
    """Swipe between two coordinates."""
    dev = ctx.device()
    check_xy(dev, args.x1, args.y1)
    check_xy(dev, args.x2, args.y2)
    dev.adb.shell(gestures.swipe_cmd(args.x1, args.y1, args.x2, args.y2, args.ms), timeout=15, check=True)
    return Out({"from": [args.x1, args.y1], "to": [args.x2, args.y2]},
               ["swipe (%d,%d)->(%d,%d)" % (args.x1, args.y1, args.x2, args.y2)], dev.warnings)


def _scroll_setup(p):
    p.add_argument("direction", choices=["down", "up"])
    p.add_argument("--times", type=int, default=1)
    p.add_argument("--in", dest="in_sel", help="LABEL/id of the scrollable container")


@command("scroll", _scroll_setup)
def cmd_scroll(ctx, args):
    """Scroll the largest scrollable area; reports whether content changed."""
    dev = ctx.device()
    xml, nodes, screen = observe(dev)
    changed = False
    for _ in range(args.times):
        vis = usable(nodes, screen)
        cont = _container(vis, args.in_sel)
        if cont is None:
            raise ui_err("NO_SCROLLABLE", "no scrollable node on screen", "Use `cdev swipe` with coordinates.")
        before = ui.content_hash(vis)
        a, b = gestures.scroll_points(cont.bounds, args.direction, screen[1])
        dev.adb.shell(gestures.swipe_cmd(a[0], a[1], b[0], b[1], 300), timeout=10)
        time.sleep(0.6)
        xml, nodes, screen = observe(dev)
        if ui.content_hash(usable(nodes, screen)) != before:
            changed = True
    return Out({"changed": changed}, ["scroll %s x%d: content %s" % (args.direction, args.times,
                                                                    "changed" if changed else "unchanged (end reached)")],
               dev.warnings)


# --------------------------------------------------------------------- key

def _key_setup(p):
    p.add_argument("key", help="back|home|recents|wakeup|enter|menu|tab|dpad_*|KEYCODE_*")


@command("key", _key_setup)
def cmd_key(ctx, args):
    """Send a key event."""
    code = gestures.key_code(args.key)
    dev = ctx.device()
    dev.adb.shell("input keyevent %s" % code, timeout=10, check=True)
    focus = focus_after(dev, 0.5)
    return Out({"key": code, "focus_after": focus}, ["key %s" % code, "focus %s" % focus], dev.warnings)


# -------------------------------------------------------------------- wait

def _wait_setup(p):
    add_selector_args(p)
    p.add_argument("--gone", action="store_true", help="wait for the node to disappear")
    p.add_argument("--activity", metavar="SUBSTR", help="wait until the focused window contains SUBSTR")
    p.add_argument("--timeout", type=float, default=10)


@command("wait", _wait_setup)
def cmd_wait(ctx, args):
    """Wait for a node, its disappearance, or a focus change."""
    sel = selector_from(args)
    if not args.activity and sel.is_empty():
        raise usage("NO_TARGET", "give a selector or --activity SUBSTR")
    dev = ctx.device()
    t0 = time.time()
    while True:
        if args.activity:
            focus = devstate.parse_window(dev.adb.shell("dumpsys window", timeout=30).text).get("focus") or ""
            ok = (args.activity in focus) != args.gone
            info = {"focus": focus}
        else:
            xml, nodes, screen = observe(dev)
            ms = ui.find(usable(nodes, screen), sel)
            ok = (not ms) if args.gone else bool(ms)
            info = {"matches": len(ms)}
        if ok:
            el = round(time.time() - t0, 1)
            return Out(dict(info, elapsed=el), ["condition met after %.1fs" % el], dev.warnings)
        if time.time() - t0 >= args.timeout:
            raise ui_err("WAIT_TIMEOUT", "condition not met within %ss (%s)" % (args.timeout, info),
                         "Run `cdev inspect` to see the current screen.")
        time.sleep(0.5 if not args.activity else 1.0)
