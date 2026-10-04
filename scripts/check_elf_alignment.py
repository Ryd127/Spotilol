#!/usr/bin/env python3
"""Verify native ELF layout for Android 16 KiB page-size compatibility.

Checks 64-bit native libraries packaged in an APK (arm64-v8a/x86_64):
  * every PT_LOAD segment advertises p_align >= 0x4000;
  * GNU_RELRO, when present, ends on a 16 KiB boundary.

The APK itself must still be checked separately with:
    zipalign -c -P 16 -v 4 app.apk

Uses only the Python standard library so it can run in CI without NDK helper
scripts.
"""
from __future__ import annotations

import argparse
import struct
import sys
import zipfile
from dataclasses import dataclass

PT_LOAD = 1
PT_GNU_RELRO = 0x6474E552
MIN_ALIGN = 0x4000
CHECKED_ABIS = ("arm64-v8a", "x86_64")


@dataclass(frozen=True)
class ElfLayout:
    load_alignments: list[int]
    relro_ends: list[int]


def inspect_elf(blob: bytes) -> ElfLayout:
    if len(blob) < 0x34 or blob[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")

    elf_class = blob[4]
    data_encoding = blob[5]
    if data_encoding == 1:
        endian = "<"
    elif data_encoding == 2:
        endian = ">"
    else:
        raise ValueError(f"unsupported ELF data encoding {data_encoding}")

    if elf_class == 2:  # ELF64
        if len(blob) < 64:
            raise ValueError("truncated ELF64 header")
        e_phoff = struct.unpack_from(endian + "Q", blob, 32)[0]
        e_phentsize = struct.unpack_from(endian + "H", blob, 54)[0]
        e_phnum = struct.unpack_from(endian + "H", blob, 56)[0]
        min_ph_size = 56
        vaddr_off, memsz_off, align_off = 16, 40, 48
        word_fmt = "Q"
    elif elf_class == 1:  # ELF32
        if len(blob) < 52:
            raise ValueError("truncated ELF32 header")
        e_phoff = struct.unpack_from(endian + "I", blob, 28)[0]
        e_phentsize = struct.unpack_from(endian + "H", blob, 42)[0]
        e_phnum = struct.unpack_from(endian + "H", blob, 44)[0]
        min_ph_size = 32
        vaddr_off, memsz_off, align_off = 8, 20, 28
        word_fmt = "I"
    else:
        raise ValueError(f"unsupported ELF class {elf_class}")

    if e_phentsize < min_ph_size:
        raise ValueError(f"invalid program-header size {e_phentsize}")

    loads: list[int] = []
    relro_ends: list[int] = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        end = off + e_phentsize
        if end > len(blob):
            raise ValueError("truncated program-header table")

        p_type = struct.unpack_from(endian + "I", blob, off)[0]
        if p_type == PT_LOAD:
            loads.append(struct.unpack_from(endian + word_fmt, blob, off + align_off)[0])
        elif p_type == PT_GNU_RELRO:
            p_vaddr = struct.unpack_from(endian + word_fmt, blob, off + vaddr_off)[0]
            p_memsz = struct.unpack_from(endian + word_fmt, blob, off + memsz_off)[0]
            relro_ends.append(p_vaddr + p_memsz)

    return ElfLayout(load_alignments=loads, relro_ends=relro_ends)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("apk", help="APK to inspect")
    args = ap.parse_args()

    failures: list[str] = []
    checked = 0

    try:
        zf = zipfile.ZipFile(args.apk)
    except Exception as exc:
        print(f"ERROR: cannot open APK: {exc}", file=sys.stderr)
        return 2

    with zf:
        names = sorted(
            n for n in zf.namelist()
            if n.startswith("lib/") and n.endswith(".so")
            and any(n.startswith(f"lib/{abi}/") for abi in CHECKED_ABIS)
        )
        if not names:
            print("ELF alignment: no 64-bit native libraries found; nothing to check")
            return 0

        for name in names:
            try:
                layout = inspect_elf(zf.read(name))
            except Exception as exc:
                failures.append(f"{name}: parse error: {exc}")
                print(f"FAIL {name}: {exc}")
                continue

            checked += 1
            if not layout.load_alignments:
                failures.append(f"{name}: no PT_LOAD segments")
                print(f"FAIL {name}: no PT_LOAD segments")
                continue

            minimum = min(layout.load_alignments)
            load_pretty = ", ".join(f"0x{x:x}" for x in layout.load_alignments)
            library_failed = False

            if minimum < MIN_ALIGN:
                failures.append(
                    f"{name}: PT_LOAD alignment below 16 KiB ({load_pretty})"
                )
                print(f"FAIL {name}: PT_LOAD alignments [{load_pretty}]")
                library_failed = True

            bad_relro = [end for end in layout.relro_ends if end % MIN_ALIGN != 0]
            if bad_relro:
                pretty = ", ".join(f"0x{x:x}" for x in bad_relro)
                failures.append(
                    f"{name}: GNU_RELRO end is not 16 KiB aligned ({pretty})"
                )
                print(f"FAIL {name}: GNU_RELRO end(s) [{pretty}] not 16 KiB aligned")
                library_failed = True

            if not library_failed:
                relro_text = (
                    "none"
                    if not layout.relro_ends
                    else ", ".join(f"0x{x:x}" for x in layout.relro_ends)
                )
                print(
                    f"OK   {name}: PT_LOAD [{load_pretty}], "
                    f"GNU_RELRO end(s) [{relro_text}]"
                )

    if failures:
        print("\n16 KiB ELF layout FAILED:", file=sys.stderr)
        for item in failures:
            print(f"  - {item}", file=sys.stderr)
        return 1

    print(f"\n16 KiB ELF layout OK for {checked} native librar{'y' if checked == 1 else 'ies'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
