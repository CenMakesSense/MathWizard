import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Entry point and dungeon flow.
 *
 * <p>Flow: title menu -> (optional tutorial) -> floors 1-5. Each floor is 2-3 random fights followed by that
 * floor's guardian. After a floor the wizard rests and visits the shop. Losing a fight costs half the gold and
 * restarts the floor; the level is kept. Beating the Dragon on floor 5 wins the game.</p>
 *
 * <p><b>How it works:</b> {@link #main} sets up UTF-8 output and calls {@link #titleMenu}, then {@link #run}.
 * {@code run} walks floor by floor; {@link #playFloor} creates each {@link Enemy}, hands it to a {@link Battle}
 * and pays out XP, gold and recovery after every win. All screens come from {@link Display} and all keyboard
 * input from {@link Input}, so this class only decides <i>what happens next</i>.</p>
 */
public class Game {
    /** Shared random number generator (one place, so tests could seed it). */
    static final Random R = new Random();
    static final int FLOORS = 5;
    /** Shop prices, indexed by the item number shown in the shop (index 0 unused). */
    static final int[] PRICE = {0, 20, 20, 35, 50, 70, 50};
    /** Hourglasses stop helping beyond +20s (4 of them), so timers never become trivial. */
    static final int MAX_TIME_BONUS = 20;
    /** The combat log line that ended the last lost fight (shown on the game over screen). */
    private static String lastHit = "";

    /** Sets up UTF-8 output, shows the title menu and runs the game; exits cleanly when input closes. */
    public static void main(String[] args) {
        Input.start();                                // open the keyboard first (JLine if available, else plain System.in)
        // Windows consoles: write through JLine's terminal, which handles Unicode and ANSI codes there.
        // Everywhere else: force UTF-8 so the box/star characters print correctly whatever the platform default is.
        if (System.getProperty("os.name").toLowerCase().contains("win") && Input.output() != null)
            System.setOut(new PrintStream(Input.output(), true, Input.charset()));
        else
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        Display.init();                               // Windows only: switch the console to UTF-8
        try {
            if (titleMenu()) run();                   // title menu first; run() is the whole game
            System.out.println("\n Farewell, wizard!");
        } catch (Input.Quit q) {                      // Ctrl+D / Ctrl+C / closed input
            System.out.println("\n Farewell, wizard!");
        } finally {
            Input.shutdown();                         // always give the terminal back in its normal state
        }
    }

    /**
     * Title menu: Start Game, How to Play (the tutorial returns to this menu), Quit.
     *
     * @return true to start the game, false to quit
     */
    private static boolean titleMenu() {
        while (true) {                                // the tutorial returns here, so keep asking until Start or Quit
            switch (Display.title()) {
                case 0 -> { return true; }
                case 1 -> Display.tutorial();
                default -> { return false; }
            }
        }
    }

    /**
     * The main game loop: play floors in order, handle defeat (retry or exit) and show the victory screen.
     * After floors 1-4 the wizard rests on the stairs and visits the shop (floor 5 has its own shop before the Dragon).
     */
    private static void run() {
        Player p = new Player();                      // one wizard for the whole run (stats and counters persist)
        int floor = 1;
        while (floor <= FLOORS) {                     // 1..5; a lost floor is simply played again
            if (playFloor(p, floor)) {
                p.floors++;
                if (floor < FLOORS) {
                    p.hp = p.maxHp; p.mp = p.maxMp;   // rest on the stairs
                    Display.stairs(floor, p);                   // also shows the accuracy so far
                    shop(p);
                }
                floor++;
            } else {                                  // defeat: keep level, lose half the gold, retry the floor
                int lost = p.gold / 2;
                p.gold -= lost;
                p.hp = p.maxHp; p.mp = p.maxMp;
                p.deaths++;
                if (!Display.defeat(floor, lost, p, lastHit)) return;   // "Exit" was chosen: leave without a victory screen
            }
        }
        Display.victory(p);
        // Secret ending: 85% or better over EVERY question of the run (correct * 100 >= 85 * asked, no rounding tricks)
        if (p.asked > 0 && p.correct * 100 >= 85 * p.asked) Display.secretEnding(p);
    }

    /**
     * Plays one floor: 2-3 random fights, then the guardian. After each win the wizard gets XP, gold and a
     * small recovery (20% HP, 25% MP).
     * On floor 5 the wizard also rests and shops right before the Dragon.
     *
     * @return false if the player was defeated, true if the floor was cleared
     */
    static boolean playFloor(Player p, int floor) {
        Display.floorIntro(floor);                    // 1. intro card with the floor's monsters and guardian
        int fights = 2 + R.nextInt(2);                // 2 or 3 regular fights
        String last = "";                             // previous monster, so the same one never repeats back-to-back
        for (int i = 0; i <= fights; i++) {           // the final iteration (i == fights) is the guardian
            if (i == fights && floor == FLOORS) {     // last chance to prepare for the final boss
                p.hp = p.maxHp; p.mp = p.maxMp;
                Display.lair();
                shop(p);
            }
            Enemy e = i == fights ? Enemy.guardian(floor, p.level) : Enemy.spawn(floor, p.level, last);
            last = e.name;
            Battle b = new Battle(p, e, floor);
            if (!b.run()) { lastHit = b.lastLine(); return false; }   // remember the lethal hit for the game over screen
            // 2. the fight was won: pay out rewards, level up if the XP bar is full, then a small recovery
            int xp = e.xpReward(floor), gold = e.goldReward(floor);
            p.gold += gold;
            p.goldEarned += gold;
            int levels = p.addXp(xp);
            p.heal(p.maxHp / 5);
            p.restoreMp(p.maxMp / 4);
            Display.reward(e.name, xp, gold, levels > 0 ? p.level : 0, b.lastLines());   // shows the killing blow
        }
        return true;
    }

    /**
     * The shop loop. Items: 1 Health Potion, 2 Mana Potion, 3 Bomb, 4 Sharpen Staff (+3 ATK),
     * 5 Hourglass (+5s on every timer, max {@link #MAX_TIME_BONUS}), 6 Sturdy Robe (+15 max HP), 7 leaves.
     * Purchases you cannot afford only change the message line.
     */
    static void shop(Player p) {
        String msg = "Welcome, brave wizard!";        // feedback line shown under the shop picture
        while (true) {
            int i = Display.shop(p, msg) + 1;                  // 1-6 = items, 7 = Leave shop
            if (i == 7) return;
            if (i == 5 && p.timeBonus >= MAX_TIME_BONUS) { msg = "Hourglasses maxed out (+" + MAX_TIME_BONUS + "s)."; continue; }
            if (p.gold < PRICE[i]) { msg = "Not enough gold!"; continue; }   // nothing is bought, just redraw with a message
            p.gold -= PRICE[i];                           // pay first, then apply the effect of the item
            switch (i) {
                case 1 -> { p.potions++; msg = "Bought a Health Potion."; }
                case 2 -> { p.manaPots++; msg = "Bought a Mana Potion."; }
                case 3 -> { p.bombs++; msg = "Bought a Bomb."; }
                case 4 -> { p.atk += 3; msg = "Staff sharpened! +3 ATK."; }
                case 5 -> { p.timeBonus += 5; msg = "Hourglass! Timers +5s (now +" + p.timeBonus + "s)."; }
                default -> { p.maxHp += 15; p.hp += 15; msg = "Sturdy robe! +15 max HP."; }
            }
        }
    }
}
