# silicium

A Minecraft performance mod for Apple Silicon Macs. No Sodium needed.

silicium redraws Minecraft using Apple's own graphics technology instead of the old OpenGL path the game normally uses on a Mac. It is an early alpha: expect bugs, and back up your worlds.

## What it does

Minecraft Java Edition was built for OpenGL, an old graphics API that Apple no longer supports properly. On a Mac, OpenGL is translated into Metal — the API the M chip actually speaks — every time the game draws a frame. silicium cuts out that translation and talks to Metal directly. It also replaces several of the game's busiest jobs with faster versions:

- **Drawing the world.** Minecraft normally rebuilds and submits lots of small pieces of terrain every frame. silicium packs terrain into compact shared buffers and lets the GPU decide what is visible, so the CPU sends far fewer draw commands.
- **Lighting and chunks.** Saving chunks, updating light and copying light data normally create a lot of unnecessary work and large temporary allocations. silicium skips writes when nothing really changed and shares light data instead of copying it.
- **Animated textures.** The vanilla code re-renders and copies texture animations many times per tick. silicium does it in one pass.
- **Startup.** Classes and other data are prepared ahead of time on background threads while the game is loading.
- **Far terrain** (optional). A second, much cheaper representation of distant land with smooth transitions into the real world. It is off by default and can be enabled with `-Dsilicium.lod=true`.
- **Recording** (optional). Records straight from the GPU to video with `-Dsilicium.rec=out.mp4`.
- **FPS counter.** A small `NN fps` label in the top-left corner, hidden by F1 or F3; add `-Dsilicium.fps=false` to turn it off entirely.

## Why it is much faster on M-series Macs

Several things work together:

- **No OpenGL translation layer.** OpenGL on macOS is emulated on top of Metal. silicium uses Metal natively, so the game and the GPU speak the same language.
- **Shared memory.** Apple Silicon keeps the CPU and GPU on the same memory. silicium hands the GPU data where it already sits instead of copying buffers back and forth.
- **Built for tile-based GPUs.** Apple GPUs prefer to keep render data on-chip. silicium keeps passes open, avoids pointless store-and-reload cycles and throws away data it knows it will never read again.
- **The GPU does more of the work.** Visibility checks and even the creation of draw commands happen in GPU compute shaders. The CPU is left with much less to do each frame.
- **The right core for each job.** M chips mix fast performance cores with efficient ones. silicium labels its threads so the render work gets the fast cores while background work stays out of its way.
- **Noise that fits the chip.** Terrain generation uses hand-written, vectorised code for noisy math that produces exactly the same results as vanilla.
- **Smoother presentation.** Frame timing follows the Mac's display refresh instead of relying on macOS timers that can coalesce and drop frames; a 60 fps cap in vanilla can end up running at 51–57 fps.
- **It chooses for your Mac.** silicium detects the chip, GPU core count and memory, then enables only the paths that measured faster on that hardware. Anything unmeasured or slower falls back to the vanilla path. There is nothing to configure.

During development these changes produced, for example, a 17% average fps and 63% better 1% lows in a busy area, 25% lower server tick time, and 57–71% better worst-case tick times on a hot laptop. Individual results depend on the Mac and the scene.

## Requirements

- An Apple Silicon Mac (M1 or newer) on macOS 26 or later
- Java 25 (the official launcher and Prism download it automatically)
- Minecraft: Java Edition 26.3
- Fabric Loader 0.19.5 or newer
- No Sodium: silicium refuses to start alongside it, so remove Sodium from your mods folder

Fabric API is not required. Mods that need Sodium (such as Iris) cannot be used with this build. Distant Horizons works: with its Metal renderer it runs next to silicium's terrain, and if you set Distant Horizons to OpenGL, silicium turns its own renderer off while the other optimizations stay on.

Improved Transparency (the game's OIT mode) is turned off while silicium's own renderer is active: OIT needs the vanilla opaque section buffers, which silicium replaces with its own geometry, so translucent terrain such as water would otherwise disappear. The classic transparency path draws those surfaces correctly.

## Install

1. Install Fabric for Minecraft 26.3: <https://fabricmc.net/use/installer/> (in Prism: new instance, version 26.3, loader Fabric).
2. Put the silicium jar in your mods folder:
   - Official launcher: `~/Library/Application Support/minecraft/mods` (in Finder: Go > Go to Folder, paste the path; create the folder if needed)
   - Prism: Edit the instance > Mods > Add file
3. Start Minecraft with the Fabric profile.

The jar is a mod, not an app — double-clicking it only shows install steps.

For the most fps, turn VSync off and set Max Framerate to Unlimited in Options > Video Settings.

To check it is working, press F3 (fn + F3 on a Mac keyboard). The bottom right says `Metal 4 (silicium)` on an M5, or `Metal 3 (silicium)` on other chips. On a Mac the mod cannot run on, the game stops at launch and tells you what is missing; remove the silicium jar to play there.

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE), and keep both when redistributing. silicium began as `mcopt` by Noah Dunnagan; the NOTICE file carries the original attribution.
