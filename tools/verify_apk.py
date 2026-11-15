#!/usr/bin/env python3
"""
Static sanity checks on a release APK, for when you can't run it on a device.

  pip install androguard
  python3 tools/verify_apk.py aria.apk [extra/class/prefix ...]

Checks:
  1. Every activity/service/receiver/provider in the manifest exists in the dex and still has a
     public no-arg constructor (R8 under AGP 9 can strip these; the system instantiates them).
  2. No invoke in our code (and Media3) points at a method that doesn't exist in the APK
     (a library compiled against a different version than the one shipped => NoSuchMethodError).

Get the latest release APK without the GraphQL API (blocked in agent sessions):
  ID=$(gh api repos/savar95x/aria-player/releases/latest --jq '.assets[]|select(.name=="aria.apk")|.id')
  gh api -H "Accept: application/octet-stream" repos/savar95x/aria-player/releases/assets/$ID > aria.apk
"""
import logging
import sys

logging.disable(logging.CRITICAL)
try:
    from loguru import logger
    logger.remove()
except ImportError:
    pass
from androguard.misc import AnalyzeAPK

apk_path = sys.argv[1]
prefixes = ["Ldev/savar/", "Landroidx/media3/"] + ["L" + p.strip("L;").replace(".", "/") for p in sys.argv[2:]]

a, dexes, _ = AnalyzeAPK(apk_path)
classes = {c.name: c for d in dexes for c in d.get_classes()}
problems = 0


def jvm(name: str) -> str:
    if name.startswith("."):
        name = a.get_package() + name
    return "L" + name.replace(".", "/") + ";"


def has_method(cls, name, desc):
    seen = set()
    stack = [cls]
    while stack:
        c = stack.pop()
        if c is None or c.name in seen:
            continue
        seen.add(c.name)
        if any(m.get_name() == name and m.get_descriptor() == desc for m in c.get_methods()):
            return True
        stack.append(classes.get(c.get_superclassname()))
        stack.extend(classes.get(i) for i in c.get_interfaces())
    # Reached a framework class (android.*, java.*): assume it's provided by the OS.
    return any(n not in classes for n in [classes[s].get_superclassname() for s in seen if s in classes])


components = a.get_activities() + a.get_services() + a.get_receivers() + a.get_providers()
for comp in components:
    c = classes.get(jvm(comp))
    if c is None:
        print(f"MISSING component class: {comp}")
        problems += 1
        continue
    if not any(m.get_name() == "<init>" and m.get_descriptor() == "()V" for m in c.get_methods()):
        print(f"NO no-arg constructor: {comp}")
        problems += 1
print(f"components checked: {len(components)}")

dangling = set()
for c in classes.values():
    if not any(c.name.startswith(p) for p in prefixes):
        continue
    for m in c.get_methods():
        code = m.get_code()
        if not code:
            continue
        for ins in code.get_bc().get_instructions():
            if not ins.get_name().startswith("invoke"):
                continue
            ref = ins.get_output().split(", ")[-1]
            if "->" not in ref:
                continue
            owner, rest = ref.split("->", 1)
            if owner not in classes:
                continue  # framework / JDK
            name, desc = rest.split("(", 1)[0], "(" + rest.split("(", 1)[1]
            if not has_method(classes[owner], name, desc):
                dangling.add(f"{c.name} -> {ref}")
for d in sorted(dangling)[:80]:
    print("DANGLING", d)
problems += len(dangling)
print(f"dangling calls: {len(dangling)}")
print("OK" if problems == 0 else f"{problems} problem(s)")
sys.exit(1 if problems else 0)
