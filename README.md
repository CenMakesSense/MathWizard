# MathWizard

A turn-based dungeon crawler for the terminal, drawn in ASCII and Unicode art. Every spell is a timed math problem:
solve it and the spell works, miss it or run out of time and you lose the turn. Descend five floors, beat each
floor's guardian, and defeat the **Dragon**.

- **Math you practise:** PEMDAS expressions and fantasy word problems (Attack), fractions (Defend), percentages (Strategy).
- **Made for small screens:** works in Termux, Linux and Mac terminals, Windows Terminal and VS Code's terminal.
- **Easy to drive:** arrow keys move a highlighted menu and Enter confirms (via JLine, so it works in PowerShell and VS Code too), with numbered menus as a fallback.

## Documentation

| File | What it covers |
|---|---|
| [INSTALLATION.md](INSTALLATION.md) | Installing Java, running the game on Termux, Linux, Mac, Windows and VS Code, and fixing common problems |
| [GAME_GUIDE.md](GAME_GUIDE.md) | Lore, controls, combat rules, math categories, every monster's stats, the shop and how runs are scored |

## Quick start

```sh
./run.sh        # Linux, macOS, Termux
run.bat         # Windows (PowerShell, cmd, Windows Terminal)
```

The scripts download JLine on the first run. Run them from the game folder, with the `art/` folder next to the `.java` files. The in-game **How to Play** page
shows the controls.
