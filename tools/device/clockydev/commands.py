"""Command handlers. Each is registered with @command and returns an Out."""
from . import constants as C
from .cli import Out, command
from .devices import STATE_HINTS, fill_identities, parse_devices, select_device
from .errors import CdevError
from .session import load_state, sessions_size_mb


def _sessions_warning(ctx):
    mb = sessions_size_mb(ctx.build_dir)
    if mb > C.SESSIONS_WARN_MB:
        return ["build/device/sessions is %d MB; delete old sessions manually when no longer needed" % mb]
    return []


@command("devices")
def cmd_devices(ctx, args):
    """List connected devices and which one would be selected."""
    if getattr(ctx, "broker", None):
        dev = ctx.device()
        return Out({"selected": dev.serial, "devices": [dev.summary()]},
                   ["selected: %s (broker; other devices not probed)" % dev.serial])
    raw = ctx.raw_adb()
    res = raw.run(["devices", "-l"], timeout=20)
    cands = parse_devices(res.text)
    fill_identities(cands, lambda s: raw.bind(s).shell("getprop ro.serialno", timeout=10).text)
    st = load_state(ctx.build_dir)
    selected, sel_err = None, None
    try:
        selected, _w, how = select_device(cands, ctx.flag_serial, ctx.env, st.get("pinned"))
    except CdevError as e:
        sel_err = e.to_json()
    lines = []
    for c in cands:
        mark = "*" if selected and c["serial"] == selected["serial"] else " "
        extra = c["model"] or ""
        if c["state"] != "device":
            extra = (extra + " " + STATE_HINTS.get(c["state"], "")).strip()
        lines.append("%s %-28s %-12s %-8s %s" % (mark, c["serial"], c["state"], c["transport"], extra))
    if not cands:
        lines.append("(no devices) connect a device or start an emulator")
    if sel_err:
        lines.append("selection: %s - %s" % (sel_err["code"], sel_err["message"]))
        if sel_err.get("hint"):
            lines.append("NEXT  " + sel_err["hint"])
    elif selected:
        lines.append("selected: %s (%s)" % (selected["serial"], how))
    pinned = st.get("pinned")
    if pinned:
        lines.append("pinned identity: %s (%s)" % (pinned.get("identity"), pinned.get("transport")))
    return Out({"devices": cands, "selected": selected["serial"] if selected else None,
                "selection_error": sel_err, "pinned": pinned},
               lines, warnings=_sessions_warning(ctx))


from . import cmd_session  # noqa: E402,F401
from . import cmd_ui  # noqa: E402,F401
from . import cmd_widget  # noqa: E402,F401
from . import cmd_misc  # noqa: E402,F401
from . import cmd_collect  # noqa: E402,F401
from . import cmd_lease  # noqa: E402,F401
