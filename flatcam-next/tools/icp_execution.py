"""Independent, fail-closed execution reader for the small absolute ICP subset.

Not a machine simulator: GETTOOL internals and workpiece-reference changes are
opaque. Never reinterpret WPCLEAR as a harmless comment or an ordinary G53.
"""
import re


class UnsupportedIcp(ValueError):
    def __init__(self, message, prefix):
        super().__init__(message)
        self.prefix = prefix


def read_icp(program):
    xyz = [0.0, 0.0, 0.0]  # Explicit comparison assumption, not actual machine position.
    tool, spindle, rpm = 0, 0, None  # No RPM word is not evidence of an actual zero-speed spindle.
    feed = rapid = None
    stopped = False
    header = ended = False
    events, cuts = [], []

    def snapshot():
        return dict(events=events.copy(), cuts=cuts.copy(), end=xyz.copy(), units="MM",
                    explicitTermination=ended, icpControls=dict(
                        coordinateUnit="micrometer", velocityUnit="micrometer/second", waitUnit="millisecond",
                        rapidFeed=rapid, toolChanges=[e for e in events if e["kind"] == "nativeChange"],
                        finalSpindleCommand=spindle, explicitProgramEnd=ended,
                        limits="Initial commanded XYZ assumed zero. GETTOOL internal moves and origin changes are NOT simulated."))

    def integer(text, positive=False):
        if not re.fullmatch(r"[+-]?\d+", text):
            raise ValueError("ICP requires integer native values: " + text)
        value = int(text)
        if not -(2**31) <= value < 2**31 or positive and value <= 0:
            raise ValueError("ICP integer out of range: " + text)
        return value

    for number, raw in enumerate(program.lstrip("\ufeff").splitlines(), 1):
        line = raw.split(";", 1)[0].strip()
        if not line or re.fullmatch(r"\([^()]*\)", line):
            continue
        if ended:
            raise ValueError("ICP command after PROGEND")
        parts = line.split(maxsplit=1)
        command = parts[0].upper()
        arg = parts[1].strip() if len(parts) == 2 else ""
        if command == "IMF_PBL":
            if header or not arg or len(arg.split()) != 1:
                raise ValueError("Invalid/duplicate ICP header")
            header = True
            events.append(dict(kind="units", value="MM"))
            continue
        if not header:
            raise ValueError("ICP command before IMF_PBL")
        if command in ("VEL", "FASTVEL"):
            rate = integer(arg, positive=True) * 60 / 1000
            if command == "VEL": feed = rate
            else: rapid = rate
        elif command in ("FASTABS", "MOVEABS"):
            axes, cursor = {}, 0
            for word in re.finditer(r"([XYZ])([+-]?\d+)", arg.upper()):
                if arg[cursor:word.start()].strip() or word[1] in axes:
                    raise ValueError("Malformed/duplicate ICP axis")
                axes[word[1]] = integer(word[2]) / 1000
                cursor = word.end()
            if not axes or arg[cursor:].strip():
                raise ValueError("Invalid ICP movement: " + line)
            target = [axes.get(axis, xyz[i]) for i, axis in enumerate("XYZ")]
            if command == "MOVEABS" and feed is None:
                raise ValueError("MOVEABS without explicit VEL")
            if target != xyz:
                event = dict(kind="rapid" if command == "FASTABS" else "linear", tool=tool,
                             **{"from": xyz.copy(), "to": target.copy()}, feed=rapid if command == "FASTABS" else feed,
                             spindle=spindle, rpm=rpm if spindle else 0)
                events.append(event)
                if command == "MOVEABS" and min(xyz[2], target[2]) < 0:
                    cuts.append(event)
            xyz = target
        elif command == "GETTOOL":
            selected = integer(arg, positive=True)
            if selected != tool:
                tool = selected
                events.append(dict(kind="tool", tool=tool))
            events.append(dict(kind="nativeChange", tool=tool, at=xyz.copy(), spindleStopped=stopped))
        elif command == "WAIT":
            value = integer(arg)
            if value < 0: raise ValueError("Negative WAIT")
            events.append(dict(kind="dwell", tool=tool, seconds=value / 1000))
        elif command == "SPINDLE":
            match = re.fullmatch(r"(CW|CCW)(?:\s+RPM(\d+))?", arg.upper())
            if arg.upper() == "OFF":
                selected, stopped = 0, True
            elif match:
                selected, stopped = (3 if match[1] == "CW" else 4), False
                if match[2] is not None: rpm = integer(match[2])
            else:
                raise ValueError("Unsupported SPINDLE: " + line)
            if selected != spindle or match and match[2] is not None:
                spindle = selected
                events.append(dict(kind="spindle", tool=tool, direction=spindle, rpm=rpm if spindle else 0))
        elif command == "PROGEND":
            if arg: raise ValueError("PROGEND takes no arguments")
            ended = True
            events.append(dict(kind="programEnd", tool=tool, at=xyz.copy()))
        else:
            raise UnsupportedIcp(f"ICP command not modeled at line {number}: {line}; prefix is diagnostic ONLY", snapshot())
    if not header or not ended or not cuts:
        raise ValueError("ICP lacks header, PROGEND or cutting motion")
    return snapshot()
