silicium @VERSION@

A Minecraft performance mod for Apple Silicon Macs. No Sodium needed. VERY MUCH ALPHA: expect bugs, and back up your
worlds. The NOTICE file carries the original attribution required by the licence.

You need
- An Apple Silicon Mac (M1 or newer) on macOS 26 or later
- Java 25 (the official launcher and Prism download it for 26.3; nothing to install)
- Minecraft: Java Edition 26.3
- Fabric Loader 0.19.5 or newer for 26.3

Install
1. Install Fabric for Minecraft 26.3: https://fabricmc.net/use/installer/
   (in Prism: new instance, version 26.3, loader Fabric)
2. Put silicium-@VERSION@.jar in your mods folder.
   Official launcher: ~/Library/Application Support/minecraft/mods
   (in Finder: Go > Go to Folder, paste that path; create the mods folder if it's missing)
   In Prism: Edit the instance > Mods > Add file.
3. Start Minecraft with the Fabric profile.

No Sodium needed: silicium won't start alongside it (Fabric says so), so take Sodium out of the mods folder.
Fabric API isn't required; keep it if your other mods need it.
Mods that require Sodium (for example Better Block Entities or Iris) can't be used with this build.
Distant Horizons runs on the Metal renderer next to silicium's own terrain. With DH set to its OpenGL renderer, silicium
switches its renderer off for it.

The jar is a mod, not an app: double-clicking it only shows these install steps.

For the most fps: Options > Video Settings, VSync off and Max Framerate Unlimited.
Shows an fps counter in the top-left corner (hidden with F1 or F3; fn + F1 or fn + F3 on a Mac keyboard).

On a computer the mod can't run on (not an Apple Silicon Mac, or macOS older than 26), the game stops at launch
and says what's missing; remove the silicium jar from the mods folder to play there.
To check it's working: press F3 (fn + F3 on a Mac keyboard); the bottom right says Metal 4 (silicium) on an M5,
or Metal 3 (silicium) on other chips.

There is nothing to configure: every finished optimization is always on. silicium detects this Mac and keeps paths
that the hardware can't run or that measure slower on it off, so the settings are chosen from the machine itself.

Licence: Apache-2.0 (LICENSE, NOTICE). Keep both, including the original attribution in NOTICE.
