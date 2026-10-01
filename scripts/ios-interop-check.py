#!/usr/bin/env python3
"""Guards the Kotlin -> Swift boundary of the shared framework.

Run from the repo root:  python3 scripts/ios-interop-check.py
Exits 1 when a rule fails, so it can gate a build.

Rule 1  Two exported declarations must not share a simple name.
Rule 2  Swift must not construct an exported Kotlin class that has default arguments.
Rule 3  A Swift call must pass every parameter, defaults included.
Rule 4  Nested in a class -> Parent.Child in Swift. Nested in an interface -> ParentChild.
Rule 5  doCopy() takes every property; use a named withX() helper instead.
"""

import os
import re
import sys

EXPORTED = [
    "core/common", "core/model", "core/richtext", "core/database", "core/network",
    "core/data", "core/filesys", "core/drawing", "feature/auth", "feature/notes", "shared",
]
SWIFT_ROOT = "iosApp"

# Known and accepted: the Notes table and the Notes response wrapper collide, Swift already
# spells the wrapper Notes_ in NotesBridgeAdapter. Do not add to this list without a reason.
ALLOWED_COLLISIONS = {"Notes"}

DECL = re.compile(
    r"^(?P<indent>[ \t]*)"
    r"(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public\s+|internal\s+|private\s+)?"
    r"(?:(?:sealed|data|value|abstract|open|enum|annotation|inner)\s+)*"
    r"(?P<kw>class|object|interface)\s+(?P<name>[A-Z]\w*)"
)
FUN = re.compile(r"^[ \t]*(?:@\w+\s*)*(?:public\s+|internal\s+)?fun\s+(?:<[^>]+>\s*)?(?P<name>\w+)\s*\(")


def balanced(text, start):
    depth, i = 0, start
    while i < len(text):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return text[start + 1:i], i
        i += 1
    return "", start


def split_params(raw):
    out, depth, current = [], 0, ""
    for ch in raw:
        if ch in "(<[":
            depth += 1
        elif ch in ")>]":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(current)
            current = ""
        else:
            current += ch
    if current.strip():
        out.append(current)
    return [p.strip() for p in out if p.strip()]


def param_names(raw):
    names, defaulted = [], []
    for part in split_params(raw):
        m = re.match(r"(?:va[lr]\s+)?(\w+)\s*:", part)
        if not m:
            continue
        names.append(m.group(1))
        if "=" in part.split(":", 1)[1]:
            defaulted.append(m.group(1))
    return names, defaulted


def blank_noise(src):
    """Replace comments and string bodies with spaces so offsets and line numbers survive."""
    out, i, n = [], 0, len(src)
    while i < n:
        two = src[i:i + 2]
        if two == "//":
            while i < n and src[i] != "\n":
                out.append(" ")
                i += 1
            continue
        if two == "/*":
            while i < n and src[i:i + 2] != "*/":
                out.append("\n" if src[i] == "\n" else " ")
                i += 1
            out.append("  ")
            i += 2
            continue
        if src[i] == '"':
            out.append('"')
            i += 1
            while i < n and src[i] != '"':
                if src[i] == "\\":
                    out.append("  ")
                    i += 2
                    continue
                out.append("\n" if src[i] == "\n" else " ")
                i += 1
            if i < n:
                out.append('"')
                i += 1
            continue
        out.append(src[i])
        i += 1
    return "".join(out)


def kotlin_files():
    for module in EXPORTED:
        for root, _, files in os.walk(os.path.join(module, "src")):
            if "/build/" in root or "Test" in root:
                continue
            for name in files:
                if name.endswith(".kt"):
                    yield os.path.join(root, name)


def scan_kotlin():
    classes, funcs, nested, owners = {}, {}, {}, {}
    for path in kotlin_files():
        module = next(m for m in EXPORTED if path.startswith(m))
        text = blank_noise(open(path, encoding="utf-8", errors="ignore").read())
        stack = []
        for line in text.splitlines():
            hit = DECL.match(line)
            if hit:
                indent = len(hit.group("indent").expandtabs(4))
                while stack and stack[-1][1] >= indent:
                    stack.pop()
                name = hit.group("name")
                if stack:
                    parent, _, parent_kw = stack[-1]
                    nested.setdefault(parent + name, (parent, name, parent_kw))
                else:
                    owners.setdefault(name, set()).add(module)
                    idx = line.find("(", hit.end("name") - 1)
                    if idx >= 0 and hit.group("kw") == "class":
                        raw, _ = balanced(line, idx)
                        classes[name] = param_names(raw)
                stack.append((name, indent, hit.group("kw")))
            call = FUN.match(line)
            if call:
                raw, _ = balanced(line, line.index("(", call.end("name")))
                funcs.setdefault(call.group("name"), []).append(param_names(raw))
    return classes, funcs, nested, owners


def _sq_files():
    for module in EXPORTED:
        for root, _, files in os.walk(os.path.join(module, "src")):
            if "/build/" in root:
                continue
            for name in files:
                if name.endswith(".sq"):
                    yield os.path.join(root, name)


def swift_files():
    for root, _, files in os.walk(SWIFT_ROOT):
        if "/build/" in root or ".xcodeproj" in root:
            continue
        for name in files:
            if name.endswith(".swift"):
                yield os.path.join(root, name)


def call_labels(text, open_paren):
    raw, _ = balanced(text, open_paren)
    labels = []
    for part in split_params(raw):
        m = re.match(r"(\w+)\s*:", part)
        labels.append(m.group(1) if m else None)
    return labels


def main():
    classes, funcs, nested, owners = scan_kotlin()
    problems = []

    for path in _sq_files():
        for table in re.findall(r"CREATE TABLE\s+(\w+)\s*\(", open(path, encoding="utf-8").read()):
            owners.setdefault(table, set()).add(f"{path} (SQLDelight)")

    for name, modules in sorted(owners.items()):
        if len(modules) > 1 and name not in ALLOWED_COLLISIONS:
            problems.append(
                f"[rule 1] '{name}' is declared in {sorted(modules)} — one of them will be "
                f"renamed '{name}_' in shared.framework. Rename the type or its table."
            )

    swift_decls = set()
    sources = {}
    for path in swift_files():
        text = blank_noise(open(path, encoding="utf-8", errors="ignore").read())
        sources[path] = text
        swift_decls |= set(re.findall(r"\b(?:struct|class|enum|protocol)\s+(\w+)", text))
        swift_decls |= set(re.findall(r"\bfunc\s+(\w+)", text))

    for path, text in sources.items():
        where = path

        for m in re.finditer(r"\bdoCopy\s*\(", text):
            line = text[:m.start()].count("\n") + 1
            problems.append(
                f"[rule 5] {where}:{line} doCopy() requires every property. "
                f"Add a named withX() helper on the Kotlin type, like Note.withContents()."
            )

        for flat, (parent, child, parent_kw) in nested.items():
            if parent_kw == "interface":
                pattern, wrong, right = r"\b%s\.%s\b" % (parent, child), f"{parent}.{child}", flat
            else:
                pattern, wrong, right = r"(?<![\w.])%s\b" % flat, flat, f"{parent}.{child}"
            for m in re.finditer(pattern, text):
                line = text[:m.start()].count("\n") + 1
                problems.append(
                    f"[rule 4] {where}:{line} '{wrong}' does not exist in Swift — "
                    f"'{child}' is nested in {parent_kw} '{parent}', so Swift spells it '{right}'."
                )

        for name, (params, defaults) in classes.items():
            if name in swift_decls or not params:
                continue
            for m in re.finditer(r"(?<![\w.])%s\s*\(" % re.escape(name), text):
                labels = [x for x in call_labels(text, m.end() - 1) if x]
                if not labels and not params:
                    continue
                missing = [p for p in params if p not in labels]
                if missing and set(missing) & set(defaults):
                    line = text[:m.start()].count("\n") + 1
                    problems.append(
                        f"[rule 2] {where}:{line} {name}(...) omits {missing}. Kotlin default "
                        f"arguments are not exported; pass them all or add a factory function."
                    )

        for m in re.finditer(r"\.(?:shared|companion)\.(\w+)\s*\(", text):
            name = m.group(1)
            if name not in funcs:
                continue
            labels = [x for x in call_labels(text, m.end() - 1) if x]
            signatures = funcs[name]
            if any(set(sig[0]) == set(labels) or not sig[0] for sig in signatures):
                continue
            best = min(signatures, key=lambda s: len(set(s[0]) ^ set(labels)))
            missing = [p for p in best[0] if p not in labels]
            if missing and set(missing) & set(best[1]):
                line = text[:m.start()].count("\n") + 1
                problems.append(
                    f"[rule 3] {where}:{line} {name}(...) omits {missing}. Kotlin default "
                    f"arguments are not exported to Swift."
                )

    if problems:
        print("iOS interop check FAILED\n")
        for problem in sorted(set(problems)):
            print(" ", problem)
        print(f"\n{len(set(problems))} problem(s). See .ai/project-decisions.md G11 and G12.")
        return 1

    print("iOS interop check passed.")
    print(f"  {len(owners)} exported types, {len(nested)} nested types, {len(funcs)} functions scanned.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
