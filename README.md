# GregTech Old Edition

This is GregTech: New Horizons made for Minecraft rd-132211, with a few changes of my own.
It is a standalone Java agent, meaning it does not need recompiling the game to work and works with Prism launcher.

## Features
Placing blocks with buttons 1 through 9 and simple layered terrain generation.

Left-clicks will place blocks and right-clicks remove, just like in the original version.

World generation table:

| Y levels | Block ID | Block |
| --- | --- | --- |
| 0–7 | 5 | Bedrock |
| 8–19 | 4 | Deep Stone |
| 20–31 | 3 | Stone |
| 32–39 | 2 | Dirt |
| 40-42 | 1 | Grass |

## Prism Launcher Installation

### Releases (Stable Release)

This method will get you the newest stable version of the mod that should have the least bugs.

Go to the releases, find the latest release, and download the .jar file.

In the **rd-132211** instance, open **Edit → Settings → Java**, enable the
instance-specific Java arguments field, and add:

```
-javaagent: C:\pathToJar\gtoe.jar
```

### Source Code (Unstable Version)

This method will get you the newest version of the mod, but it may be unstable. Download the zip from the main GitHub page and unzip it somewhere.

In the **rd-132211** instance, open **Edit → Settings → Java**, enable the
instance-specific Java arguments field, and add:

```
-javaagent: C:\pathToDownloadedZip\GregTechOldEdition-main\build\libs\gtoe.jar
```

## Build

Use this guide if you wish to build the mod yourself.

The Gradle 8.8 wrapper needs JDK 17–22 to run. The produced agent is compiled
for Java 8, matching the Java runtime Prism assigns to rd-132211.

On this Windows machine:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat clean build
```

The standalone agent is created at:

```text
build/libs/gtoe.jar
```

`build` runs bytecode-level verification using an ASM-generated fixture. The
additional `verifyInstalledGame` task can structurally verify the transformations
against an installed client JAR without changing it:

```powershell
.\gradlew.bat verifyInstalledGame `
  "-PgameJar=C:\path\to\minecraft-rd-132211-client.jar"
```

Expected Prism console messages include the configured Y layers, successful
transformation messages for RubyDung/Level/Tile/Chunk, and:

```text
[rd-132211-agent] RubyDung.init() entered
```
