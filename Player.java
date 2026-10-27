/**
 * The wizard controlled by the player: core stats, consumable items, temporary battle buffs and the
 * statistics that are summarised on the victory / defeat screens.
 *
 * <p><b>How it works:</b> this is a plain data holder. {@link Game} and {@link Battle} read and change the public
 * fields directly (HP, gold, buffs, counters); the only logic here is levelling up ({@link #addXp}) and the
 * clamped heal helpers.</p>
 */
class Player {
    // ---- core stats ----
    int level = 1, xp = 0;
    int hp = 60, maxHp = 60;      // hit points: reach 0 and the floor restarts
    int mp = 20, maxMp = 20;      // mana: Strategy costs 10, +1 regained every round
    int atk = 5;                  // base spell damage (Staff upgrades and levels raise it)
    int gold = 20;                // spent in the shop

    // ---- consumables ----
    int potions = 2, manaPots = 1, bombs = 1;

    /** Seconds added to every question timer. Each Hourglass from the shop adds 5 (see Game.MAX_TIME_BONUS). */
    int timeBonus = 0;

    // ---- per-battle buffs (reset when a battle starts) ----
    int streak = 0;                       // correct answers in a row
    boolean guard = false;                // Defend: the next enemy hit is reduced by 60%
    boolean focus = false;                // Strategy: the next Attack deals +50%

    // ---- run statistics ----
    int asked = 0, correct = 0, fast = 0; // questions asked / answered right / answered fast
    int goldEarned = 0;                   // total gold collected from monsters (starting gold excluded)
    int floors = 0;                       // floors cleared
    int deaths = 0;                       // how many times the wizard has fallen in this run

    /** XP required to go from the current level to the next one (grows by 30 each level). */
    int xpNeeded() { return 40 + 30 * (level - 1); }

    /**
     * Adds experience and handles level-ups. Each level gives +12 max HP, +4 max MP and +2 ATK, and a level-up
     * fully restores HP and MP. Several levels can be gained at once if the reward is large.
     *
     * @return the number of levels gained (0 if none)
     */
    int addXp(int amount) {
        xp += amount;
        int gained = 0;
        while (xp >= xpNeeded()) {
            xp -= xpNeeded();                 // spend the XP for the old level before levelling up
            level++; maxHp += 12; maxMp += 4; atk += 2; gained++;
        }
        if (gained > 0) { hp = maxHp; mp = maxMp; }
        return gained;
    }

    // Both helpers clamp at the maximum, so callers never need to check for overflow.

    /** Heals up to the maximum HP. */
    void heal(int n) { hp = Math.min(maxHp, hp + n); }

    /** Restores mana up to the maximum MP. */
    void restoreMp(int n) { mp = Math.min(maxMp, mp + n); }
}
