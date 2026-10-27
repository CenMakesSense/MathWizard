import java.util.*;

/**
 * One fight. The loop is: draw the screen, the player picks an action and answers a timed math question,
 * then the enemy acts. The battle ends when either side reaches 0 HP.
 *
 * <p>The question category decides the action: Attack = PEMDAS / word problems, Defend = fractions,
 * Strategy = percentages. A wrong answer or a timeout forfeits the turn.</p>
 *
 * <p><b>How it works:</b> {@link #run} loops player turn / enemy turn. {@link #playerTurn} shows the highlighted action
 * menu; each action calls {@link #ask}, which draws a question, runs the live countdown on a helper thread, reads
 * the answer through {@link Input} and returns 0 (wrong/timeout), 1 (correct) or 2 (correct and fast). Everything
 * that happens is appended to {@code log}; the frame shows the last two lines, and {@link Game} reads them back
 * to show the killing blow (reward screen) or the lethal hit (game over screen).</p>
 */
class Battle {
    /** Base seconds per question by difficulty (index 1-3). Hourglasses add {@code Player.timeBonus} on top. */
    private static final int[] TIMER = {0, 12, 18, 28};

    private final Player p;
    private final Enemy e;
    private final int floor;
    /** Combat messages; the frame shows the newest two. */
    private final List<String> log = new ArrayList<>();
    /** How many lines above the cursor the "Time:" line is (the countdown thread needs this to redraw it). */
    private volatile int up = 1;

    Battle(Player p, Enemy e, int floor) { this.p = p; this.e = e; this.floor = floor; }

    /**
     * Runs the whole battle.
     *
     * @return true if the player wins, false if the player is defeated
     */
    boolean run() {
        p.streak = 0; p.guard = false; p.focus = false;           // buffs never carry between battles
        log.add(e.boss ? "The guardian " + e.name + " blocks your path!" : e.name + " appears!");
        while (true) {
            playerTurn();
            if (e.hp <= 0) return true;                           // enemy dead before it can act
            enemyTurn();
            if (e.hp <= 0) return true;                           // a Defend counter-hit can finish it too
            if (p.hp <= 0) return false;
            p.restoreMp(1);                                       // slow mana regeneration: 1 per round
        }
    }

    /** The last two log lines (the killing blow shows here on the reward screen). */
    String[] lastLines() {
        int n = log.size();
        return n >= 2 ? new String[]{log.get(n - 2), log.get(n - 1)} : new String[]{log.get(n - 1)};
    }

    /** The newest log line (the lethal enemy hit when the player was defeated). */
    String lastLine() { return log.get(log.size() - 1); }

    /** Draws the battle frame and the highlighted action menu until an action has been used (Back / no MP just redraw). */
    private void playerTurn() {
        while (true) {
            Display.battle(p, e, floor, log);
            switch (Display.menu(Display.ACTIONS, 2, true)) {
                case 0 -> { attack(); return; }
                case 1 -> { defend(); return; }
                case 2 -> { if (strategy()) return; }             // false = not enough MP, choose again
                default -> { if (items()) return; }               // false = Back, choose again
            }
        }
    }

    /**
     * Asks one math question and reads the answer within the time limit.
     *
     * <p>Easy/medium questions are multiple choice. On a real terminal they are shown as a 2x2 grid: arrow keys
     * move the cyan highlight and Enter confirms at once (no prompt, no extra step). Where raw keys are not
     * available they fall back to typing 1-4. Hard questions are always typed. Mistyped input is rejected with a
     * message and the clock keeps running, it is never marked wrong.</p>
     *
     * @return 0 = wrong or timeout, 1 = correct, 2 = correct and fast (under half the base timer)
     */
    private int ask(QuestionGenerator.Type type, int diff) {
        // Flow: 1 build the question, 2 draw it, 3 start the countdown, 4 read the answer, 5 score it.
        QuestionGenerator.Question q = QuestionGenerator.make(type, diff, floor);   // 1
        int base = TIMER[diff];
        int secs = base + p.timeBonus;                            // Hourglass bonus applies to every question
        boolean mc = diff < 3;                                    // easy/medium: multiple choice, hard: typed
        boolean arrows = mc && Display.ANSI && Input.rawSupported(); // arrow-key grid needs a real terminal
        Display.battle(p, e, floor, log);                         // 2. same frame, the question takes the menu's place
        for (String line : Display.wrap(q.text(), 38)) System.out.println(" " + line);   // long word problems use two lines
        List<String> opts = mc ? QuestionGenerator.choices(q) : List.of();
        if (arrows) {
            for (int r = 0; r < (opts.size() + 1) / 2; r++) System.out.println(row(opts, r, 0)); // first answer selected
        } else {
            for (int i = 0; i < opts.size(); i += 2)              // two options per line to save height
                System.out.println(String.format("   [%d] %-9s", i + 1, opts.get(i))
                        + (i + 1 < opts.size() ? String.format(" [%d] %s", i + 2, opts.get(i + 1)) : ""));
        }
        if (!mc) System.out.println(" Type your answer: " + q.hint());
        System.out.println(" [" + type + "] Time: " + secs + "s  "); // redrawn every second by countdown()
        up = 1;                                                   // the Time: line is directly above the cursor line
        Thread cd = Display.ANSI ? countdown(type.name(), secs) : null;
        long t0 = System.currentTimeMillis();                     // 3-4. clock starts; used for the timeout and the fast bonus
        String in = null;                                         // stays null if time runs out
        boolean ok = false;
        if (arrows) {
            System.out.print("\033[?25l");                        // hide the cursor while choosing
            try {
                int idx = Input.choose(opts.size(), 2, secs * 1000L, i -> drawOpts(opts, i));
                if (idx >= 0) { in = opts.get(idx); ok = in.equals(q.answer()); }
            } finally {
                System.out.print("\033[?25h");                    // always show the cursor again
                System.out.flush();
            }
        } else {
            while (true) {
                System.out.print(Display.ASK);
                long left = secs * 1000L - (System.currentTimeMillis() - t0);
                in = left > 0 ? Input.timed(left) : null;
                if (in == null) break;                            // time is up
                if (mc ? in.matches("[1-" + opts.size() + "]") : QuestionGenerator.valid(in)) {
                    ok = mc ? opts.get(in.charAt(0) - '1').equals(q.answer()) : QuestionGenerator.matches(in, q.answer());
                    break;
                }
                System.out.println(" ! " + (mc ? "Pick 1-" + opts.size() + "." : "Enter a number or a fraction like 3/4."));
                up += 2;                                          // the error line and new prompt pushed the timer line up
            }
        }
        double used = (System.currentTimeMillis() - t0) / 1000.0; // 5. seconds the player needed
        if (cd != null) cd.interrupt();                           // stop the countdown thread
        p.asked++;
        if (in == null) { log.add("Time's up! Turn lost."); p.streak = 0; return 0; }
        if (!ok) { log.add("Wrong! The answer was " + q.answer() + "."); p.streak = 0; return 0; }
        p.correct++;
        p.streak++;
        boolean fast = used < base / 2.0;                         // measured against the BASE timer, so Hourglasses don't make it easier
        if (fast) p.fast++;
        return fast ? 2 : 1;
    }

    private static final String CYAN = "\033[1;36m", OFF = "\033[0m";

    /** One line of the 2x2 answer grid (row r = cells 2r and 2r+1). The selected cell is bold cyan with a pointer. */
    private String row(List<String> o, int r, int sel) {
        StringBuilder sb = new StringBuilder(" ");
        for (int i = 2 * r; i < Math.min(o.size(), 2 * r + 2); i++) {
            String cell = (i == sel ? Display.PTR : " ") + " [" + (i + 1) + "] " + String.format("%-9s", o.get(i));
            sb.append(i == sel ? CYAN + cell + OFF : cell).append("  ");
        }
        return sb.toString();
    }

    /**
     * Repaints the answer grid in place after the selection moved. The cursor sits on the empty line below the
     * "Time:" line, so the grid is that many lines above it. Cursor is saved/restored and everything goes out in
     * one write, so it cannot interleave with the countdown thread's redraw.
     */
    private void drawOpts(List<String> o, int sel) {
        int rows = (o.size() + 1) / 2;
        StringBuilder sb = new StringBuilder("\0337\033[" + (rows + 1) + "A");   // save cursor, go to the first answer row
        for (int r = 0; r < rows; r++) sb.append(r > 0 ? "\033[1B" : "").append('\r').append(row(o, r, sel)).append("\033[K");
        System.out.print(sb.append("\0338").toString());                           // restore cursor
        System.out.flush();
    }

    /**
     * Starts a daemon thread that redraws the "Time:" line every second.
     * It saves the cursor, jumps {@code up} lines up, rewrites the line and restores the cursor, so whatever
     * the player is typing is not disturbed. Only used on terminals that understand ANSI codes.
     */
    private Thread countdown(String tag, int secs) {
        Thread t = new Thread(() -> {
            try {
                for (int left = secs - 1; left >= 0; left--) {
                    Thread.sleep(1000);
                    // ESC 7 = save cursor, ESC[nA = up n lines, \r = column 0, ESC 8 = restore cursor
                    System.out.print("\0337\033[" + up + "A\r [" + tag + "] Time: " + left + "s  \0338");
                    System.out.flush();
                }
            } catch (InterruptedException ignored) { }            // interrupted when the question is answered
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    /**
     * Attack (PEMDAS / word problems). Damage = (ATK + 4*difficulty + 0..3) x fast bonus (1.5)
     * x streak bonus (+10% per extra correct answer, max +50%) x focus (1.5 after Strategy).
     */
    private void attack() {
        int r = ask(QuestionGenerator.Type.ATTACK, e.diff);
        if (r == 0) return;
        double mult = (r == 2 ? 1.5 : 1.0) * (1 + Math.min(5, p.streak - 1) * 0.1) * (p.focus ? 1.5 : 1.0);
        // base damage = ATK + 4 per question difficulty + 0-3 luck, then multiplied by the bonuses above
        int dmg = (int) ((p.atk + 4 * e.diff + Game.R.nextInt(4)) * mult);
        p.focus = false;                                          // focus is used up by the attack
        e.hp -= dmg;
        log.add("Your spell hits " + e.name + " for " + dmg + (r == 2 ? " (FAST!)" : "") + "!");
    }

    /**
     * Defend (fractions): a correct answer raises a barrier that fully blocks the NEXT enemy hit, whatever it is
     * (even a charged or double strike), and counters for half of the blocked damage. It does not heal.
     * The barrier stays up until a hit actually lands, so it is also the answer to "is charging power!".
     */
    private void defend() {
        if (ask(QuestionGenerator.Type.DEFEND, e.diff) == 0) return;
        p.guard = true;
        log.add("Barrier up! The next hit will be blocked.");
    }

    /**
     * Strategy (percentages, costs 10 MP): a correct answer weakens the enemy (-25% damage for the rest of
     * the battle) and makes the next Attack deal +50%.
     *
     * @return false if the wizard lacks the mana (no turn used), true otherwise
     */
    private boolean strategy() {
        if (p.mp < 10) { log.add("Need 10 MP for Strategy."); return false; }
        p.mp -= 10;
        if (ask(QuestionGenerator.Type.STRATEGY, e.diff) == 0) return true; // mana is spent, turn is lost
        p.focus = true;
        e.weakened = true;
        log.add(e.name + " weakened! Next attack +50%.");
        return true;
    }

    /**
     * Item menu (highlighted list). Using an item costs the turn but needs no question.
     *
     * @return true if an item was used, false if the player chose Back
     */
    private boolean items() {
        while (true) {
            Display.battle(p, e, floor, log);
            String[] labels = {"Health Potion x" + p.potions + " (+50% HP)", "Mana Potion   x" + p.manaPots + " (+50% MP)",
                    "Bomb          x" + p.bombs + " (" + (20 + 5 * floor) + " dmg)", "Back"};
            switch (Display.menu(labels, 1, false)) {
                case 0 -> {
                    if (p.potions > 0) { p.potions--; int h = p.maxHp / 2; p.heal(h); log.add("Potion! +" + h + " HP."); return true; }
                    log.add("No Health Potions left.");
                }
                case 1 -> {
                    if (p.manaPots > 0) { p.manaPots--; int m = p.maxMp / 2; p.restoreMp(m); log.add("Mana potion! +" + m + " MP."); return true; }
                    log.add("No Mana Potions left.");
                }
                case 2 -> {
                    if (p.bombs > 0) { p.bombs--; int d = 20 + 5 * floor; e.hp -= d; log.add("BOOM! Bomb deals " + d + " damage."); return true; }
                    log.add("No Bombs left.");
                }
                default -> { return false; }
            }
        }
    }

    /**
     * The enemy's turn. Damage varies +-15% and drops 25% if weakened. Specials:
     * 1 = two hits at 60% each; 2 = 30% chance to heal 1/8 of its HP instead of attacking;
     * 3 = 50% chance to charge (no attack), then the next hit does 2.5x. A Defend barrier blocks the hit completely.
     */
    private void enemyTurn() {
        // damage varies +-15% every hit and is cut by 25% while the enemy is weakened (Strategy)
        double mult = (0.85 + Game.R.nextDouble() * 0.3) * (e.weakened ? 0.75 : 1.0);
        int hits = 1;                                             // number of hits this turn
        // 'power' scales each hit: 1.0 normal, 0.6 per hit of a double strike, 2.5 for a released charge
        double power = 1.0;
        switch (e.special) {
            case 1 -> { hits = 2; power = 0.6; }
            case 2 -> {
                if (Game.R.nextInt(100) < 30) {
                    int h = e.maxHp / 8;
                    e.hp = Math.min(e.maxHp, e.hp + h);
                    log.add(e.name + " heals " + h + " HP.");
                    return;                                       // healing uses its turn
                }
            }
            case 3 -> {
                if (!e.charging && Game.R.nextBoolean()) { e.charging = true; log.add(e.name + " is charging power!"); return; }
                if (e.charging) { power = 2.5; e.charging = false; } // release the stored power
            }
            default -> { }
        }
        // add up the hits (each is rounded down separately) to get the damage this turn would deal
        int total = 0;
        for (int i = 0; i < hits; i++) total += (int) (e.dmg * power * mult);
        if (p.guard) {                                            // Defend: block the whole hit, then counter for half of it
            p.guard = false;
            int counter = Math.max(1, total / 2);
            e.hp -= counter;
            log.add(e.name + "'s hit is BLOCKED! You counter for " + counter + ".");
            return;
        }
        total = Math.max(1, total);                               // every hit does at least 1
        p.hp -= total;                                            // the hit lands; the defeat check happens in run()
        log.add(e.name + " hits you for " + total + (hits > 1 ? " (double)" : "") + (power > 2 ? " (CHARGED!)" : "") + "!");
    }
}
