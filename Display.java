import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * All screen drawing lives here, so the game logic never prints decorations itself.
 *
 * <p><b>How it works:</b> every method here prints one complete screen ({@link #clear} first, then art, text and
 * dividers). {@link #menu} is the one place that reads a choice: it prints the entries, highlights one in cyan and
 * redraws it in place with ANSI cursor codes while {@link Input#choose} reads the arrow keys. Art is read from
 * disk once and cached. Nothing here changes game state, so {@link Game} and {@link Battle} stay free of layout code.</p>
 *
 * <p>Frames are kept to about 20 lines so nothing scrolls off small terminals (Termux, cmd, PowerShell).
 * Art is loaded from {@code art/<name>.txt} (the folder may also be called Art, arts or Arts).
 * Decorative Unicode symbols are used on modern terminals; on old consoles (or with the environment
 * variable {@code MW_ASCII=1}) plain ASCII look-alikes are used instead.</p>
 */
class Display {
    // ------------------------------------------------------------------ terminal capabilities

    /** Width of dividers, borders and centred text. Wide enough that the battle menu row never sticks out past the divider. */
    static final int W = 52;
    static final boolean WIN = System.getProperty("os.name").toLowerCase().contains("win");
    /** True when the terminal understands ANSI escape codes (Linux, Mac, Termux, Windows Terminal, Git Bash, VS Code). */
    static final boolean ANSI = !WIN || System.getenv("WT_SESSION") != null || System.getenv("TERM") != null
            || "vscode".equals(System.getenv("TERM_PROGRAM"))        // VS Code's integrated terminal on Windows
            || Input.ansiCapable();                                  // JLine opened a real terminal (PowerShell, cmd, ...)
    /** True when the fancy Unicode symbols are used. Set MW_ASCII=1 to force plain ASCII. */
    static final boolean UNI = ANSI && System.getenv("MW_ASCII") == null;

    /** Picks the Unicode text when supported, otherwise the ASCII fallback. */
    private static String u(String uni, String ascii) { return UNI ? uni : ascii; }

    // ------------------------------------------------------------------ decorative symbols (escapes keep the source ASCII-safe)

    /** The star cluster used in dividers (U+22C6 U+22C5 U+2606 U+22C5 U+22C6). */
    private static final String STARS = "\u22C6\u22C5\u2606\u22C5\u22C6";
    /** The gem ornament (U+169B9) used on the victory screen. It is a rare glyph: if your font shows a box, change it here. */
    private static final String GEM = "\uD81A\uDDB9";

    /** Prompt for answering a math question: star symbols, the word Answer, then a chevron. */
    static final String ASK = u(" \u272E \u22C6 \u02DA Answer \u276F ", " Answer > ");
    /** Pointer shown in front of the highlighted answer in the 2x2 multiple-choice grid. */
    static final String PTR = u("\u276F", ">");

    /** Prompt for menu choices: a four-point star, [ CHOOSE ], then a chevron. */
    static final String MENU = u(" \u2726 [ CHOOSE ] \u276F ", " [ CHOOSE ] > ");

    // Small icons in front of the battle actions: crossed swords (attack), shield (defend), white star (strategy), black star (items)
    private static final String I_ATK = u("\u2694 ", ""), I_DEF = u("\u26E8 ", ""), I_STR = u("\u2727 ", ""), I_ITM = u("\u2726 ", "");

    /** The four battle actions, in menu order (Attack, Defend, Strategy, Items). */
    static final String[] ACTIONS = {I_ATK + "Attack (PEMDAS)", I_DEF + "Defend (Frac.)", I_STR + "Strategy (%) 10MP", I_ITM + "Items"};

    private static final String CYAN = "\033[1;36m", OFF = "\033[0m";

    private static final String[] DIRS = {"art", "Art", "arts", "Arts"};
    /** Art files already read from disk. */
    private static final Map<String, String> CACHE = new HashMap<>();

    // ------------------------------------------------------------------ terminal helpers

    /** Windows only: switches the console to UTF-8 so the symbols and shading characters draw correctly. */
    static void init() { if (WIN) sh("chcp 65001 >nul"); }

    /** Runs a Windows shell command attached to the current console. Failures are ignored. */
    private static void sh(String cmd) {
        try { new ProcessBuilder("cmd", "/c", cmd).inheritIO().start().waitFor(); } catch (Exception ignored) { }
    }

    /** Clears the screen: ANSI codes where supported, otherwise `cls` on Windows or blank lines elsewhere. */
    static void clear() {
        if (ANSI) { System.out.print("\033[H\033[2J"); System.out.flush(); }
        else if (WIN) sh("cls");
        else System.out.println("\n".repeat(30));
    }

    /**
     * Loads an art file (cached). Tries art/, Art/, arts/ and Arts/ and both "slime.txt" and "Slime.txt".
     * Returns a visible placeholder instead of crashing if the file is missing.
     */
    static String art(String name) {
        return CACHE.computeIfAbsent(name, n -> {
            String cap = Character.toUpperCase(n.charAt(0)) + n.substring(1);
            for (String d : DIRS)
                for (String f : new String[]{n, cap}) {
                    Path path = Path.of(d, f + ".txt");
                    try { if (Files.isRegularFile(path)) return Files.readString(path).replace("\r", "").stripTrailing(); }
                    catch (IOException ignored) { }
                }
            return "[ missing art/" + n + ".txt ]";
        });
    }

    /**
     * Battle-frame borders, one short pattern per floor, repeated to the full width. Each floor has a top and a bottom
     * pattern (they are the same except in the cave): cave \/-\/\/-\/ above and /\_/\_/\_/\ below (each tiled from a short repeating unit), crypt, swamp, lava pit,
     * and castle (a row of #). ASCII look-alikes are used on old consoles. Floor 1..5 = index 0..4.
     */
    private static final String[] TOP_U = {"\\/-\\/", "\u256C\u2550", "\u2248~", "^~", "#"};
    private static final String[] BOT_U = {"/\\_", "\u256C\u2550", "\u2248~", "^~", "#"};
    private static final String[] TOP_A = {"\\/-\\/", "+=", "~-", "^~", "#"};
    private static final String[] BOT_A = {"/\\_", "+=", "~-", "^~", "#"};

    /** The floor's top or bottom border line: its pattern repeated across the full width. */
    private static String border(int floor, boolean top) {
        String p = top ? u(TOP_U[floor - 1], TOP_A[floor - 1]) : u(BOT_U[floor - 1], BOT_A[floor - 1]);
        return p.repeat(W / p.length() + 1).substring(0, W);
    }

    /** Centres a (possibly multi-line) block: every line gets the same left padding, so the shape is kept. */
    static String center(String block) {
        int max = block.lines().mapToInt(String::length).max().orElse(0);
        String pad = " ".repeat(Math.max(0, (W - max) / 2));
        return block.lines().map(l -> pad + l).collect(Collectors.joining("\n"));
    }

    /** Width of the game's display area: the 52-column layout, or the terminal itself when it is narrower than that. */
    private static int area() {
        int c = Input.columns();
        return c > 0 ? Math.min(W, c) : W;
    }

    /**
     * Centres a piece of artwork (a floor's cave/crypt/swamp/lava/castle art) in the display area WITHOUT changing the
     * file. The artwork's real width is measured first (longest line without trailing spaces, minus the leading
     * spaces every line shares), then every line is shifted by the same amount, so the shape and the line spacing stay
     * exactly as drawn. If the art is wider than the area it is simply left-aligned.
     */
    static String centerArt(String artText) {
        List<String> lines = artText.lines().map(String::stripTrailing).collect(Collectors.toList());
        int lead = lines.stream().filter(l -> !l.isEmpty())
                .mapToInt(l -> l.length() - l.stripLeading().length()).min().orElse(0);   // spaces shared by every line
        int maxLen = lines.stream().mapToInt(String::length).max().orElse(0);
        int visible = maxLen - lead;                                                     // width of the drawing itself
        int shift = Math.max(-lead, (area() - visible) / 2 - lead);                      // >0 add spaces, <0 remove shared ones
        return lines.stream()
                .map(l -> l.isEmpty() ? l : shift >= 0 ? " ".repeat(shift) + l : l.substring(-shift))
                .collect(Collectors.joining("\n"));
    }

    /** Text health bar, e.g. [#####-----]. */
    static String bar(int cur, int max, int w) {
        int f = max <= 0 ? 0 : Math.max(0, Math.min(w, Math.round((float) cur * w / max)));
        return "[" + "#".repeat(f) + "-".repeat(w - f) + "]";
    }

    // ------------------------------------------------------------------ decorative dividers

    /** Full-width divider with an ornament in the middle: edge + fill... + " mid " + ...fill + edge. */
    private static String rule(String edge, String fill, String mid) {
        String m = " " + mid + " ";
        int room = W - 2 * edge.length() - m.codePointCount(0, m.length());
        int left = room / 2;
        return edge + fill.repeat(left) + m + fill.repeat(room - left) + edge;
    }

    /** Star-cluster divider with a white star at each end (used on menus, battle frames and most screens). */
    static String star() { return rule(u("\u2727", "+"), u("\u2500", "-"), u(STARS, "*")); }

    /** Double-line divider with the gem ornament in the middle (used on the victory screen). */
    static String gem() { return rule("", u("\u2550", "="), u(GEM, "*")); }

    /** One row of a menu: cells r*cols .. r*cols+cols-1. The selected cell is bold cyan with a pointer in front. */
    private static String menuRow(String[] l, int cols, int r, int sel, int w) {
        StringBuilder sb = new StringBuilder(" ");
        for (int i = r * cols; i < Math.min(l.length, r * cols + cols); i++) {
            String cell = (i == sel ? PTR : " ") + " [" + (i + 1) + "] " + String.format("%-" + w + "s", l[i]);
            sb.append(i == sel ? CYAN + cell + OFF : cell).append(cols > 1 ? "  " : "");
        }
        return sb.toString();
    }

    /**
     * Shows a menu and returns the index of the chosen entry. Every menu in the game uses this.
     *
     * <p>On a real terminal the first entry starts highlighted (cyan), the arrow keys move the highlight instantly
     * and Enter confirms at once; there is no prompt. Where raw keys are unavailable it falls back to a numbered
     * list and a typed number, re-asking on invalid input.</p>
     *
     * @param labels the entries
     * @param cols   1 = vertical list, 2 = two-column grid
     * @param footer true to print a divider under the menu (it is part of the screen the cursor must skip)
     */
    static int menu(String[] labels, int cols, boolean footer) {
        int n = labels.length, rows = (n + cols - 1) / cols, w = 0;
        for (String l : labels) w = Math.max(w, l.length());
        if (!(ANSI && Input.rawSupported())) {                    // plain fallback: numbered list + typed choice
            for (int r = 0; r < rows; r++) {
                StringBuilder sb = new StringBuilder(" ");
                for (int i = r * cols; i < Math.min(n, r * cols + cols); i++)
                    sb.append(String.format("%-" + (w + 6) + "s", "[" + (i + 1) + "] " + labels[i]));
                System.out.println(sb.toString().stripTrailing());
            }
            if (footer) System.out.println(star());
            while (true) {
                System.out.print(MENU);
                String s = Input.line();
                if (s.matches("[1-" + n + "]")) return s.charAt(0) - '1';
                System.out.println(" ! Invalid choice. Enter 1-" + n + ".");
            }
        }
        final int ww = w, up = rows + (footer ? 1 : 0);          // lines between the first row and the cursor
        for (int r = 0; r < rows; r++) System.out.println(menuRow(labels, cols, r, 0, ww)); // first entry selected
        if (footer) System.out.println(star());
        System.out.print("\033[?25l");                            // hide the cursor while choosing
        try {
            return Input.choose(n, cols, 1L << 40, i -> {         // repaint the whole menu in place on every move
                StringBuilder sb = new StringBuilder("\0337\033[" + up + "A");   // save cursor, go to the first row
                for (int r = 0; r < rows; r++)
                    sb.append(r > 0 ? "\033[1B" : "").append('\r').append(menuRow(labels, cols, r, i, ww)).append("\033[K");
                System.out.print(sb.append("\0338").toString());                 // restore cursor
                System.out.flush();
            });
        } finally {
            System.out.print("\033[?25h");                        // always show the cursor again
            System.out.flush();
        }
    }

    /** Splits text at spaces into lines of at most {@code width} characters (long word problems take two lines). */
    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : text.split(" ")) {
            if (cur.length() > 0 && cur.length() + 1 + w.length() > width) { out.add(cur.toString()); cur.setLength(0); }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        out.add(cur.toString());
        return out;
    }

    /** "Press ENTER" pause between screens. */
    static void pause() {
        System.out.print("\n  Press ENTER to continue...");
        Input.line();
    }

    // ------------------------------------------------------------------ screens

    /**
     * Title screen: banner, wizard, and the highlighted main menu (Start Game, How to Play, Quit).
     *
     * @return 0 = Start Game, 1 = How to Play (Tutorial), 2 = Quit
     */
    static int title() {
        clear();
        System.out.println(star());
        System.out.println(center(art("title")));
        System.out.println(center(art("wizard")));
        System.out.println(star());
        return menu(new String[]{"Start Game", "How to Play (Tutorial)", "Quit"}, 1, false);
    }

    /** Prints one tutorial page: title, a block of text, decorative dividers, and waits for ENTER. */
    private static void page(String title, String body) {
        clear();
        System.out.println(star());
        System.out.println(center(u("\u2726 ", "") + title + u(" \u2726", "")));
        System.out.println(body);
        System.out.println(star());
        pause();
    }

    /**
     * The "How to Play" walkthrough, one page per screen: 1 combat actions, 2 speed and streak bonuses,
     * 3 the shop and Hourglass, 4 controls (arrow keys, Enter, typed answers, how to quit). Each page waits for ENTER.
     */
    static void tutorial() {
        page("HOW TO PLAY 1/4 - COMBAT",
            " Each turn pick an action. A timed math\n"
          + " question decides if it works. A wrong\n"
          + " answer or a timeout loses the turn.\n\n"
          + " [1] Attack    PEMDAS + word problems\n"
          + "     Solve it to cast a damaging spell.\n"
          + " [2] Defend    Fractions\n"
          + "     Barrier: blocks the next hit fully\n"
          + "     (even a charged one) + a counter.\n"
          + "     (2/4 counts as 1/2: any equal form)\n"
          + " [3] Strategy  Percentages (10 MP)\n"
          + "     Weakens the enemy, next Attack +50%.\n"
          + " [4] Items     potions, mana, bombs\n\n"
          + " Easy/Medium: pick 1-4. Hard: type it.\n"
          + " Division only appears from Floor 3.");
        page("HOW TO PLAY 2/4 - SPEED & STREAKS",
            " FAST ANSWER\n"
          + "  Answer in under HALF the base timer\n"
          + "  (6s Easy / 9s Medium / 14s Hard)\n"
          + "  and your Attack deals x1.5 damage!\n\n"
          + " STREAK\n"
          + "  Each correct answer in a row adds +10%\n"
          + "  damage (up to +50%). A wrong answer or\n"
          + "  a timeout resets it.\n\n"
          + " FOCUS  (Strategy) next Attack x1.5\n\n"
          + " Stack them: fast x1.5, streak-3 x1.2,\n"
          + " focus x1.5 = x2.7 damage!\n"
          + " Base timers: 12s / 18s / 28s");
        page("HOW TO PLAY 3/4 - SHOP & HOURGLASS",
            " After every floor, and again before the\n"
          + " Dragon, you rest (full HP/MP) and shop:\n\n"
          + " [1] Health Potion  " + Game.PRICE[1] + "g  heal 50% HP\n"
          + " [2] Mana Potion    " + Game.PRICE[2] + "g  restore 50% MP\n"
          + " [3] Bomb           " + Game.PRICE[3] + "g  instant damage\n"
          + " [4] Sharpen Staff  " + Game.PRICE[4] + "g  +3 ATK, forever\n"
          + " [5] Hourglass      " + Game.PRICE[5] + "g  +5s on EVERY timer,\n"
          + "                         forever (max +" + Game.MAX_TIME_BONUS + "s)\n"
          + " [6] Sturdy Robe    " + Game.PRICE[6] + "g  +15 max HP\n\n"
          + " If you fall: keep your level, lose half\n"
          + " your gold and retry the floor.");
        page("HOW TO PLAY 4/4 - CONTROLS",
            " MENUS\n"
          + "  Up/Down/Left/Right  move the highlight\n"
          + "  Enter               confirm at once\n"
          + "  1-9                 jump to that entry\n"
          + "  (no arrow keys? type the number + Enter)\n\n"
          + " ANSWERS\n"
          + "  Multiple choice: arrows + Enter\n"
          + "  Typed: type it, then Enter\n"
          + "  Fractions like 3/4 (2/4 works too)\n"
          + "  A typo is re-asked, the timer keeps going\n\n"
          + " LEAVING\n"
          + "  Ctrl+D   quit from any menu or prompt\n"
          + "  Ctrl+C   force quit (terminal is restored)\n"
          + "  Title: Quit.  Game over: Exit (asks first)");
    }

    /** Intro card shown when a floor starts: the floor's art file, the monsters that lurk there, and its guardian. */
    static void floorIntro(int floor) {
        clear();
        System.out.println(star());
        System.out.println(centerArt(art(Enemy.BG[floor - 1])));       // the floor's own picture (cave/crypt/swamp/lava/castle.txt), centred; file untouched
        System.out.println(center("FLOOR " + floor + ": " + Enemy.FLOOR_NAMES[floor - 1].toUpperCase()));
        String names = Arrays.stream(Enemy.POOL[floor - 1]).map(Enemy.Monster::name).collect(Collectors.joining(", "));
        System.out.println(center("Lurking here: " + names));
        System.out.println(center("Guardian: " + Enemy.GUARDIANS[floor - 1].name()));
        System.out.println(star());
        pause();
    }

    /**
     * The battle frame (16 lines). Top to bottom: floor ceiling strip, enemy art, floor ground strip, enemy HP,
     * last two log lines, divider, player stats. The caller adds whatever follows: the action menu
     * ({@link #menu}), a question, or the item list.
     */
    static void battle(Player p, Enemy e, int floor, List<String> log) {
        clear();                                                   // 1. wipe the screen
        System.out.println(border(floor, true));                   // 2. the floor's top border above the monster
        System.out.println(center(art(e.art)));                    // 3. the monster, centred
        System.out.println(border(floor, false));                  // 4. and its bottom border below it
        // Guardians are marked with a leading *. Flags appear when the enemy is charging or weakened.
        // Enemy HP bar, centred under the monster. The flags are appended after centring so they never shift the bar.
        System.out.println(center((e.boss ? "*" : "") + e.name + " " + bar(e.hp, e.maxHp, 12) + " "
                + String.format("%3d/%d", Math.max(0, e.hp), e.maxHp))
                + (e.charging ? " CHARGING!" : "") + (e.weakened ? " weak" : ""));
        for (int i = 0; i < 2; i++) {                             // always two lines so the frame height is stable
            int idx = log.size() - 2 + i;
            System.out.println(idx >= 0 ? center(log.get(idx)) : "");   // log messages are centred
        }
        System.out.println(star());                                // 6. divider, then the wizard's stats
        System.out.println(" LV." + p.level + " XP " + p.xp + "/" + p.xpNeeded() + "  GOLD " + p.gold
                + (p.streak > 1 ? "  STREAK " + p.streak : "") + (p.guard ? " GUARD" : "") + (p.focus ? " FOCUS" : ""));
        System.out.println(" HP " + bar(p.hp, p.maxHp, 8) + " " + p.hp + "/" + p.maxHp
                + "  MP " + bar(p.mp, p.maxMp, 8) + " " + p.mp + "/" + p.maxMp);
    }

    /**
     * Shown after every victory: the last battle messages (so the killing blow is visible), the rewards and level-ups.
     *
     * @param lines the last one or two combat log lines of the fight that was just won
     */
    static void reward(String name, int xp, int gold, int newLevel, String[] lines) {
        clear();
        System.out.println(star());
        System.out.println(center(art("wizard")));
        System.out.println(center("*** " + name.toUpperCase() + " DEFEATED ***"));
        for (String l : lines) System.out.println(center(l));
        System.out.println(center("+" + xp + " XP     +" + gold + " gold"));
        if (newLevel > 0) System.out.println(center("LEVEL UP! You are now level " + newLevel + "!"));
        System.out.println(center("You recover some HP and MP."));
        System.out.println(star());
        pause();
    }

    /** The Dragon's door: shown after floor 5's regular fights, right before the shop and the final boss. */
    static void lair() {
        clear();
        System.out.println(star());
        System.out.println(center(art("dragon")));
        System.out.println(center("The Dragon waits beyond this door.\nYou rest: HP and MP fully restored.\nSpend your gold in the shop first!"));
        System.out.println(star());
        pause();
    }

    /** The staircase between floors. */
    static void stairs(int floor, Player p) {
        clear();
        System.out.println(star());
        System.out.println(art("stairs"));
        System.out.println(center("Floor " + floor + " cleared!"));
        System.out.println(center("Descending to " + Enemy.FLOOR_NAMES[floor] + "..."));
        System.out.println(center("You rest: HP and MP fully restored."));
        System.out.println(center("Accuracy so far: " + accuracy(p) + "% (" + p.correct + "/" + p.asked + ")"));
        System.out.println(star());
        pause();
    }

    /** Percentage of questions answered correctly in this run (0 if none were asked yet). */
    static int accuracy(Player p) { return p.asked == 0 ? 0 : Math.round(100f * p.correct / p.asked); }

    /**
     * The shop screen. Prices come from {@link Game#PRICE}; the purchase logic is in {@link Game#shop}.
     *
     * @param msg feedback line from the last action ("Bought a Bomb.", "Not enough gold!", ...)
     * @return 0-5 = the item chosen (Health Potion .. Sturdy Robe), 6 = Leave shop
     */
    static int shop(Player p, String msg) {
        clear();
        System.out.println(star());
        System.out.println(art("shop"));
        System.out.println(center(msg));
        System.out.println(star());
        System.out.println(" Gold: " + p.gold + "  ATK: " + p.atk + "  MaxHP: " + p.maxHp + "  Timer: +" + p.timeBonus + "s");
        return menu(new String[]{
                "Health Potion  " + Game.PRICE[1] + "g  (have " + p.potions + ")",
                "Mana Potion    " + Game.PRICE[2] + "g  (have " + p.manaPots + ")",
                "Bomb           " + Game.PRICE[3] + "g  (have " + p.bombs + ")",
                "Sharpen Staff  " + Game.PRICE[4] + "g  +3 ATK",
                "Hourglass      " + Game.PRICE[5] + "g  +5s timers" + (p.timeBonus >= Game.MAX_TIME_BONUS ? " (MAX)" : ""),
                "Sturdy Robe    " + Game.PRICE[6] + "g  +15 Max HP",
                "Leave shop"}, 1, false);
    }

    /** Run summary box (accuracy, fast bonuses, floors, level, gold) shown on the defeat and victory screens. */
    private static void summary(Player p) {
        int acc = accuracy(p);
        String[] rows = {"Accuracy: " + acc + "% (" + p.correct + "/" + p.asked + ")",
                "Fast Answer Bonuses: " + p.fast, "Floors Cleared: " + p.floors + "/" + Game.FLOORS,
                "Final Level: " + p.level, "Gold Earned: " + p.goldEarned};
        StringBuilder sb = new StringBuilder("+" + "-".repeat(32) + "+");
        for (String r : rows) sb.append("\n| ").append(String.format("%-30s", r)).append(" |");
        System.out.println(center(sb.append("\n+").append("-".repeat(32)).append("+").toString()));
    }

    /**
     * Defeat screen: the hit that killed you, the death counter and the run summary, then a highlighted menu with
     * "Return to Game" and "Exit". Exit asks for confirmation first because it throws the run away.
     * The floor restarts, half the gold is lost and the level is kept (that part is done by {@link Game}).
     *
     * @param lastHit the lethal combat log line
     * @return true to go back into the game, false to exit
     */
    static boolean defeat(int floor, int lost, Player p, String lastHit) {
        boolean confirming = false;
        while (true) {
            clear();
            System.out.println(star());
            System.out.println(art("gameover"));
            System.out.println(center("You fell on floor " + floor + ". Lost " + lost + " gold. Level kept."));
            System.out.println(center(lastHit));
            System.out.println(center("Deaths this run: " + p.deaths));
            summary(p);
            System.out.println(star());
            if (confirming) {
                System.out.println(center("Quit now? This run will be lost."));
                if (menu(new String[]{"Yes, exit", "No, keep playing"}, 1, false) == 0) return false;
                confirming = false;
            } else if (menu(new String[]{"Return to Game", "Exit"}, 1, false) == 0) {
                return true;
            } else {
                confirming = true;
            }
        }
    }

    /** Victory screen after the Dragon falls. */
    static void victory(Player p) {
        clear();
        System.out.println(gem());
        System.out.println(center(art("victory")));
        System.out.println(center("THE DRAGON IS SLAIN! Thanks for playing!"));
        summary(p);
        System.out.println(gem());
        pause();
    }

    /**
     * The secret ending: only shown after the victory screen when the whole run's accuracy is 85% or more
     * (the check is in {@link Game}). It is never announced anywhere else.
     */
    static void secretEnding(Player p) {
        clear();
        System.out.println(gem());
        System.out.println(center(art("wizard")));
        System.out.println(center(u("\u2726 ", "") + "SECRET ENDING: THE ARITHMANCER" + u(" \u2726", "")));
        System.out.println(center("The Dragon never guarded gold.\n"
                + "Under its hoard slept the First Equation,\n"
                + "a sum so perfect that every answer you\n"
                + "gave rang true beside it.\n\n"
                + "With " + accuracy(p) + "% accuracy the Spire bows.\n"
                + "You are no longer a wizard who casts math.\n"
                + "You ARE the math, Arithmancer of Numeria."));
        System.out.println(gem());
        pause();
    }
}
