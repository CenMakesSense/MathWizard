# MathWizard - Game Guide

*A terminal dungeon crawler where every spell is a math problem.*

## Lore

Beneath the village of Numeria lies the **Spire of Sums**, a five-floor dungeon built by a forgotten arithmancer.
Its monsters feed on wrong answers, and every spell cast inside only works if the caster can solve the riddle woven
into it. A young wizard, armed with a staff and a head for numbers, descends floor by floor to reach the **Dragon**
that sleeps in the castle at the bottom and guards the Spire's last secret.

## Controls

| Key | What it does |
|---|---|
| Arrow keys | move the cyan highlight in any menu (Up/Down in lists, all four arrows in the 2x2 battle and answer grids) |
| Enter | confirm the highlighted entry at once, no extra prompt |
| 1-9 | jump the highlight to that entry |
| Typing + Enter | answer hard questions (fractions as `3/4`; `2/4` is accepted for `1/2`) |
| Ctrl+D | quit from any menu or prompt |
| Ctrl+C | force quit (the terminal is restored) |

Arrow-key menus work in PowerShell, cmd, Windows Terminal, the VS Code terminal, Linux, macOS and Termux when the JLine jar is
in `lib/` (the run scripts download it). Without it the menus are numbered instead: type the number and press Enter.
The title screen has **Start Game**, **How to Play** (a 4-page tutorial) and **Quit**.

## Combat

Each turn you pick an action. A **timed math question** decides whether it works. A wrong answer or a timeout
loses the turn, then the enemy acts. A mistyped answer (not a number or fraction) is simply asked again and the
timer keeps running.

| Action | Category | When answered correctly |
|---|---|---|
| **Attack** | PEMDAS + word problems | casts a damaging spell |
| **Defend** | Fractions | raises a barrier that **fully blocks the next hit**, even a charged or double one, and **counters for half** of the blocked damage. It does not heal, so potions stay useful |
| **Strategy** (10 MP) | Percentages | the enemy deals 25% less damage for the rest of the fight, and your next Attack deals +50% |
| **Items** | none | potion, mana potion or bomb (costs the turn, no question) |

MP regenerates **1 per round**, so spend Strategy wisely. The barrier stays up until a hit actually lands, which makes
it the best answer to a monster that "is charging power!".

**Attack damage** = (ATK + 4 x question difficulty + 0-3) x fast bonus x streak bonus x focus.

| Bonus | Condition | Multiplier |
|---|---|---|
| Fast answer | under **half the base timer** (6s / 9s / 14s) | x1.5 |
| Streak | each correct answer in a row after the first | +10%, max +50% |
| Focus | after a successful Strategy | x1.5 on the next Attack |

A wrong answer or timeout resets the streak. Example: fast x1.5, streak of 3 x1.2 and focus x1.5 gives x2.7.

## Math categories

Question difficulty comes from the monster. Easy and medium questions are **multiple choice** (answer grid);
hard questions are **typed**.

| Difficulty | Base timer | Answer style |
|---|---|---|
| Easy | **12 s** | multiple choice |
| Medium | **18 s** | multiple choice |
| Hard | **28 s** | typed |

| Category | Easy | Medium | Hard |
|---|---|---|---|
| **PEMDAS** | `8 + 5`, `6 × 7`, `7²` | `3 + 4 × 5`, `(3 + 4) × 5` | `4 + 3 × (6 - 2)²`, `(2 + 5)² - 4 × 2` |
| **Word problems** (about 30% of Attack) | one step | two steps | three steps |
| **Fractions** | same denominators | different denominators, `×` | bigger denominators: `+`, `-`, `×` (and `÷` from floor 3) |
| **Percentages** | `25% of 80` | `80 increased by 25%`, discounts | `Price 120, 20% off`, `120 gold, spend 25%` |

- Questions use `×` and `÷`. Squares never use a base above 10, brackets that get squared stay at 10 or less, and
  two-digit operands are rare.
- Percent answers can be typed with or without the `%`.
- Fraction and percentage questions have **one clear target**: a single operation or a single percentage, never two
  separate calculations stuck together.
- The game avoids repeating any of the last 10 questions.
- Word problems are short and fantasy-themed, using basic arithmetic only: *"6 chests hold 7 gold each. You spend 20.
  Gold left?"*

### Division is locked to Floor 3+

Floors 1 and 2 only use addition, subtraction and multiplication. These appear from **Floor 3** on and never earlier:
the `÷` sign, "Simplify a fraction", "a fraction of a number", fraction division, "X is what % of Y?", and the
word problems that share things out equally.

## The dungeon

Each floor is **2-3 random fights** followed by the floor's **guardian**. The same monster never appears twice in a
row. Every floor has its own border pattern. After a win you recover 20% HP and 25% MP.

| Floor | Name | Regular monsters | Guardian |
|---|---|---|---|
| 1 | The Cave | Slime, Bat, Goblin | Goblin Chief |
| 2 | The Crypt | Skeleton, Ghost | Skeleton King |
| 3 | The Swamp | Orc, Troll | Troll Warlord |
| 4 | The Lava Pit | Fire Imp, Golem | Stone Colossus |
| 5 | The Castle | Dark Mage, Knight | **Dragon** (final boss) |

### Enemy stats (base values)

`Q` is the question style: **MC** = multiple choice, **Typed** = you type the answer.

| Floor | Monster | HP | Damage | Q | Special |
|---|---|---|---|---|---|
| 1 | Slime | 30 | 5 | MC | Regenerates |
| 1 | Bat | 25 | 6 | MC | Double strike |
| 1 | Goblin | 35 | 6 | MC | none |
| 1 | **Goblin Chief** | 80 | 9 | MC | Double strike |
| 2 | Skeleton | 45 | 9 | MC | Double strike |
| 2 | Ghost | 40 | 8 | MC | Regenerates |
| 2 | **Skeleton King** | 110 | 12 | Typed | Double strike |
| 3 | Orc | 65 | 12 | MC | Charge |
| 3 | Troll | 75 | 11 | MC | Regenerates |
| 3 | **Troll Warlord** | 150 | 15 | Typed | Regenerates |
| 4 | Fire Imp | 60 | 14 | MC | Double strike |
| 4 | Golem | 95 | 13 | Typed | Charge |
| 4 | **Stone Colossus** | 180 | 17 | Typed | Charge |
| 5 | Dark Mage | 85 | 16 | Typed | Regenerates |
| 5 | Knight | 105 | 15 | Typed | Double strike |
| 5 | **Dragon** | 280 | 20 | Typed | Charge |

**Scaling.** Enemy HP and damage are multiplied by `1 + 0.05 x (your level - 1)`. Floors 4 and 5 add a further
**x1.12 and x1.24**. Each enemy hit also varies by +-15%.

**Specials**

- **Double strike**: two hits, each at 60% damage.
- **Regenerates**: 30% chance each turn to heal 1/8 of its max HP instead of attacking.
- **Charge**: 50% chance to wind up (shown as `CHARGING!`), then the next hit deals **x2.5**.

Guardians are marked with `*` in battle and give **3x** XP and gold.

## Progression

| Rule | Value |
|---|---|
| Starting kit | 60 HP, 20 MP, ATK 5, 20 gold, 2 Health Potions, 1 Mana Potion, 1 Bomb |
| XP needed per level | `40 + 30 x (level - 1)` |
| Level-up gains | +12 max HP, +4 max MP, +2 ATK, full heal |
| XP per kill | `12 + 6 x floor + 4 x question difficulty` (x3 for guardians) |
| Gold per kill | `8 + 4 x floor + 0-5` (x3 for guardians) |

The reward screen shows the **killing blow** and your rewards.

### If you fall

You keep your level and items, lose **half your gold**, are fully healed, and restart the current floor.
The game over screen shows the **hit that killed you**, how many **times you have died this run**, your run
summary, and two choices: **Return to Game** or **Exit** (Exit asks for confirmation, because the run is lost).

## Shop

The shop opens after floors 1-4 and once more **right before the Dragon**, each time after a full rest.

| # | Item | Price | Effect |
|---|---|---|---|
| 1 | Health Potion | 20g | heals 50% of max HP (costs the turn in battle) |
| 2 | Mana Potion | 20g | restores 50% of max MP |
| 3 | Bomb | 35g | instant damage of `20 + 5 x floor`, no question needed |
| 4 | Sharpen Staff | 50g | +3 ATK permanently |
| 5 | **Hourglass** | 70g | **+5 seconds on every question timer, permanently** |
| 6 | Sturdy Robe | 50g | +15 max HP permanently |

### Time-boost mechanics (Hourglass)

- Each Hourglass adds **+5s** to every timer for the rest of the run, capped at **+20s** (4 Hourglasses), so the
  timers top out at 32s / 38s / 48s.
- The fast-answer bonus still uses the **base** timer (6s / 9s / 14s), so Hourglasses buy thinking time, not free damage.

## Scoring and endings

The stairs screen after floors 1-4 shows your **accuracy so far**. The victory and game over screens show a
summary box: **accuracy**, **fast-answer bonuses**, **floors cleared**, **final level** and **total gold earned**.
Very accurate runs may discover a **secret ending**.

## Screen and fonts

The game is drawn for a terminal at least 52 columns wide and about 22 rows tall. Symbols such as `✧ ⚔ ⛨ ❯` need a
UTF-8 font; run with `MW_ASCII=1` for plain-ASCII look-alikes. See `INSTALLATION.md` for setup and fixes.

## For modders

- **Add a monster:** one row in `Enemy.java` (`POOL`) plus an `art/<name>.txt` file.
- **Tune the game:** timers are in `Battle.TIMER`, prices in `Game.PRICE`, enemy numbers in `Enemy.java`, the screen
  width in `Display.W`.
- **Art rules:** monster art about 8 lines tall, each line at most 40 characters, symmetric around its centre.
