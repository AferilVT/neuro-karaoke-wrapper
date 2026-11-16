# Third-Party Notices

Neuro Karaoke as a whole is distributed under the **GNU General Public License,
version 3.0** (see [LICENSE](LICENSE)). This file records the third-party works
incorporated into the project and their licenses.

---

## GPL-3.0 components (copyleft — the reason this project is GPLv3)

### NeurolingsCE mascot packs
- **What:** The "Neurolings" desktop-pet mascot packs bundled under
  `Android/app/src/main/assets/mascots/` — `Cerber`, `Eviling`, `Neuron`,
  `Tuteling`, `Vedaling`, `Weuron` (sprite frames + `actions.xml` / `behaviors.xml`).
- **Source project:** NeurolingsCE ("Custom Edition"), maintained by **Luda** — a
  Neuro-themed edition in the Neurolings / Shimeji lineage.
- **License:** GNU General Public License v3.0.
- **Project references:**
  - Neurolings project: https://neurofumo.itch.io/neurolings
  - NeurolingsCE source repository: https://github.com/qingchenyouforcc/NeurolingsCE
- **Per-pack credits** (each pack also ships its original `info.json` credits,
  bundled unmodified):
  - `Neuron`, `Weuron`, `Eviling` — art by **Paccha** (https://pacchacomms.carrd.co/);
    configuration by **@promote.** and **@dalekcraft**
  - `Tuteling`, `Vedaling` — art by **Moneka** (https://linktr.ee/monikaphobia);
    configuration by **@promote.** and **@dalekcraft**
  - `Cerber` — art by **AnoWan, 于陌Wan, 六水Wan, 加斯科涅Wan, 士灰Wan** (bilibili);
    configuration by **qingchenyou (@轻尘呦)**
- **Note:** Redistributed here under the same GPL-3.0 terms.

Because the NeurolingsCE mascot packs above are GPL-3.0 and are combined into this
application, the combined work is licensed under GPL-3.0.

---

## Shimeji-ee — animation engine (permissive, BSD/zlib-style)

- **What:** The Kotlin Neurolings animation engine in
  `Android/app/src/main/java/com/soul/neurokaraoke/ui/tv/neurolings/` is a **port
  of the Shimeji-ee desktop-pet engine** (its XML action/behavior model, the
  `${…}` expression subset, the action/behavior state machine, and the standard
  Shimeji physics constants).
- **Upstream:** Shimeji-ee, maintained by **Kilkakon**; based on the original
  **Shimeji** by **Yuki Yamada** (Group Finity), with later contributions in the
  shimeji4mac lineage.
- **License:** a permissive **BSD / zlib-style** license — **not GPL**. It is
  GPL-compatible, so the port ships as part of this GPL-3.0 app while the original
  notice and attribution below are preserved, as that license requires.
- **Attribution required:** credit Kilkakon and the original authors; do not
  misrepresent the origin of the software. A link to <https://kilkakon.com> is
  appreciated. Project home: <https://kilkakon.com/shimeji/>.

Original license text, as published in the Shimeji-ee lineage:

```
Copyright (c) 2009-2011 Yuki Yamada, 2011-2012 Yusaku Hashimoto, 2016 AlanJager

This software is provided 'as-is', without any express or implied warranty. In no
event will the authors be held liable for any damages arising from the use of this
software.

Permission is granted to anyone to use this software for any purpose, including
commercial applications, and to alter it and redistribute it freely, subject to
the following restrictions:

1. The origin of this software must not be misrepresented; you must not claim that
   you wrote the original software. If you use this software in a product, an
   acknowledgment in the product documentation would be appreciated but is not
   required.

2. Altered source versions must be plainly marked as such, and must not be
   misrepresented as being the original software.

3. This notice may not be removed or altered from any source distribution.

Kilkakon (Shimeji-ee): You are welcome to use this work in your own projects if
you credit Kilkakon and the original people who worked on this. A link to
kilkakon.com would also be nice. Incorporates work from TigerHix
(https://github.com/TigerHix/shimeji-universal).
```

---

## Permissively licensed dependencies

These are used under their own terms; their licenses do not impose copyleft on
the project.

**Android app**
- AndroidX / Jetpack (Core, Lifecycle, Activity, Navigation, Compose, Compose for
  TV, Media3/ExoPlayer, ProfileInstaller, Benchmark, Car App Library) — Apache-2.0
- Coil — Apache-2.0
- Kotlin / kotlinx-coroutines — Apache-2.0
- org.json (unit tests) — JSON License / Apache-2.0

**Desktop (Electron wrapper)**
- Electron — MIT
- electron-updater / electron-builder — MIT
- discord-rpc — MIT
- lodash — MIT

---

## Notes

- Full license texts: GPL-3.0 is in [LICENSE](LICENSE). Apache-2.0 and MIT texts
  are available from the respective projects.
- If any attribution here is incomplete or a source URL needs correcting, please
  open an issue or contact the maintainer — corrections are welcome.
