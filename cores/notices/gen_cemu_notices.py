"""Writes cemu-deps.txt: the copyright files of the vcpkg packages linked into the Cemu core (one per distinct text,
with the packages that share it) and the licences of the submodules compiled in.

Run from anywhere after `gmake -C cores cemu CORES=cemu`; with no arguments it reads cores/build and cores/src:
    python3 cores/notices/gen_cemu_notices.py [vcpkg-share-dir] [cemu-source-dir] [out-file]
"""
import os
import sys

cores = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
defaults = [os.path.join(cores, "build/cemu/vcpkg_installed/arm64-android/share"),
            os.path.join(cores, "src/cemu"),
            os.path.join(cores, "notices/cemu-deps.txt")]
share, src, out = (sys.argv[1:] + defaults[len(sys.argv) - 1:])[:3]

groups = {}
order = []
for port in sorted(os.listdir(share)):
    f = os.path.join(share, port, 'copyright')
    if not os.path.isfile(f):
        continue
    text = open(f, encoding='utf-8', errors='replace').read().strip('\n')
    if text not in groups:
        groups[text] = []
        order.append(text)
    groups[text].append(port)
parts = ["Third-party code statically linked into the Cemu core: the vcpkg packages it is built with (their copyright\n"
         "files, one per distinct text, with the packages that share it) and the libraries and data compiled in from its\n"
         "submodules."]
for text in order:
    parts.append("## vcpkg: " + ", ".join(groups[text]) + "\n\n" + text)
for name, path in [("xbyak_aarch64", "dependencies/xbyak_aarch64/LICENSE"),
                   ("imgui", "dependencies/imgui/LICENSE.txt"),
                   ("ZArchive", "dependencies/ZArchive/LICENSE"),
                   ("ih264d", "dependencies/ih264d/NOTICE"),
                   ("Vulkan-Headers", "dependencies/Vulkan-Headers/LICENSE.md"),
                   ("Vulkan-Headers: LICENSES/Apache-2.0.txt", "dependencies/Vulkan-Headers/LICENSES/Apache-2.0.txt"),
                   ("cemu_graphic_packs (embedded graphic packs)", "dependencies/cemu_graphic_packs/LICENSE.md")]:
    text = open(os.path.join(src, path), encoding='utf-8', errors='replace').read().strip('\n')
    parts.append("## " + name + "\n\n" + text)
open(out, 'w', encoding='utf-8').write("\n\n".join(parts) + "\n")
