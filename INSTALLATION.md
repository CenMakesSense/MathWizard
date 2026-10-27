# MathWizard - Installation Guide

## Requirements

| Need | Details |
|---|---|
| Java | **JDK 17 or newer** (a JRE alone cannot compile the game) |
| JLine | the `jline-3.26.3.jar` library in `lib/` (gives arrow-key menus on every platform). `run.sh` / `run.bat` download it on the first run, so the first run needs internet |
| Terminal | at least **52 columns x 22 rows**, with a UTF-8 font |
| Files | the `.java` files, the `art/` folder and the `lib/` folder together in one folder |

Folder layout:

```
MathWizard/
  Game.java  Battle.java  Display.java  Input.java
  Enemy.java  Player.java  QuestionGenerator.java
  run.sh  run.bat
  lib/        (jline-3.26.3.jar goes here)
  art/        (the .txt art files)
  .vscode/    (settings.json, launch.json)
```

**JLine is optional at compile time.** The game compiles with a plain `javac *.java`. Without the jar the game still runs
and shows numbered menus (type the number, press Enter); with the jar you get the highlighted arrow-key menus.

## Quick run

| Platform | Command |
|---|---|
| Linux, macOS, Termux | `./run.sh` |
| Windows (PowerShell, cmd, Windows Terminal) | `.\run.bat` (or double-click it) |

The scripts download the JLine jar if `lib/` has none, compile the game and start it.

### Running by hand

Linux, macOS, Termux:

```sh
javac -encoding UTF-8 -cp ".:lib/*" *.java
java --enable-native-access=ALL-UNNAMED -cp ".:lib/*" Game
```

Windows PowerShell or cmd (note the `;` separator and the quotes):

```powershell
javac -encoding UTF-8 -cp ".;lib\*" *.java
java --enable-native-access=ALL-UNNAMED -cp ".;lib\*" Game
```

### Adding the jar by hand

Save <https://repo1.maven.org/maven2/org/jline/jline/3.26.3/jline-3.26.3.jar> into the `lib/` folder.
Maven coordinates: `org.jline:jline:3.26.3`.

## Termux (Android)

```sh
pkg update && pkg upgrade
pkg install openjdk-17 curl unzip
termux-setup-storage                    # only if the files are in your phone's storage
cd ~/storage/downloads/MathWizard       # go to wherever you put the folder
./run.sh
```

- Use Termux's **extra keys row** for the arrow keys. **CTRL** then **d** quits.
- If lines wrap, turn the phone sideways or zoom out (pinch) until the screen is at least 52 columns wide.

## Linux

```sh
sudo apt install openjdk-17-jdk curl        # Debian / Ubuntu
sudo dnf install java-17-openjdk-devel curl # Fedora
cd MathWizard && ./run.sh
```

## macOS

```sh
brew install openjdk@17
cd MathWizard && ./run.sh
```

## Windows

1. Install a JDK 17+ (for example Eclipse Temurin or Microsoft Build of OpenJDK) and check it with `java -version`.
2. Open the MathWizard folder in PowerShell (or Windows Terminal) and run `.\run.bat`.
3. Arrow keys, the cyan highlight, the live timer and Unicode symbols work in **PowerShell, cmd, Windows Terminal and
   the VS Code terminal** once the JLine jar is in `lib/`.

## VS Code

1. Open the MathWizard folder in VS Code (the included `.vscode/` settings add `lib/*.jar` to the project).
2. Run `run.bat` (Windows) or `./run.sh` once in the VS Code terminal so the JLine jar is downloaded, or add the jar by hand.
3. Start the game with either:
   - the integrated terminal: `.\run.bat` or `./run.sh`, or
   - the **Run** button / F5: the included `launch.json` uses `"console": "integratedTerminal"` and the project folder as
     the working directory.
4. Do **not** use the Debug Console or Output panel: they are not real terminals, so the game cannot read keys there
   (it falls back to numbered menus if you try).

## Updating the game

Replace the changed `.java` files (or the `art/` files) and start it again with the run script; it recompiles every time.

## Troubleshooting

| Problem | Fix |
|---|---|
| Menus show `[1] ...` and ask for a number | JLine is not on the classpath. Start with `run.sh` / `run.bat`, or put `jline-3.26.3.jar` in `lib/` |
| The download fails (no internet, proxy) | Download the jar by hand into `lib/` (link above) |
| `javac: command not found` | Install a **JDK** (not just a JRE) and reopen the terminal |
| `missing art/...txt` on screen | Run the game from the folder that contains `art/` |
| Boxes or `?` instead of symbols | Use a UTF-8 terminal font, or run in plain ASCII: `MW_ASCII=1 java ...` (PowerShell: `$env:MW_ASCII=1`) |
| Screen jumps or wraps | Make the terminal at least 52 columns by 22 rows |
| Terminal looks broken after a crash | Type `reset` (Linux, macOS, Termux) and press Enter |
