/**
 * A monster in battle, plus the monster tables for every floor.
 *
 * <p>Monsters are pure data: adding one means adding a row to {@link #POOL} and an {@code art/<art>.txt} file.
 * All monsters share the same battle code; only their numbers and their {@code special} behaviour differ.</p>
 *
 * <p><b>How it works:</b> a {@link Monster} is a fixed template. When a fight starts, {@link #spawn} (random regular
 * monster) or {@link #guardian} (the floor boss) turns a template into a live {@code Enemy} with its HP and damage
 * scaled to the player's level and the floor. {@code diff} decides the question style: 1-2 = multiple choice,
 * 3 = typed answer. Mix per floor: floor 3 has one typed monster (the guardian), floor 4 two (Golem, Stone Colossus),
 * floor 5 all of them.</p>
 */
class Enemy {
    /**
     * Static template for a monster.
     *
     * @param name    display name
     * @param art     art file name (without .txt) in the art folder
     * @param hp      base hit points
     * @param dmg     base damage per hit
     * @param diff    question difficulty it asks: 1 easy, 2 medium, 3 hard (3 = typed answers)
     * @param special 0 none, 1 double strike, 2 heals itself, 3 charges then hits hard
     */
    record Monster(String name, String art, int hp, int dmg, int diff, int special) { }

    /** Background art per floor (first and last line are used as the battle frame's ceiling and ground). */
    static final String[] BG = {"cave", "crypt", "swamp", "lava", "castle"};
    static final String[] FLOOR_NAMES = {"The Cave", "The Crypt", "The Swamp", "The Lava Pit", "The Castle"};

    /** Regular monsters per floor (index 0 = floor 1). Each floor has 2-3 kinds that spawn at random. */
    static final Monster[][] POOL = {
        {new Monster("Slime", "slime", 30, 5, 1, 2), new Monster("Bat", "bat", 25, 6, 1, 1),
         new Monster("Goblin", "goblin", 35, 6, 1, 0)},
        {new Monster("Skeleton", "skeleton", 45, 9, 2, 1), new Monster("Ghost", "ghost", 40, 8, 2, 2)},
        {new Monster("Orc", "orc", 65, 12, 2, 3), new Monster("Troll", "troll", 75, 11, 2, 2)},
        {new Monster("Fire Imp", "fire_imp", 60, 14, 2, 1), new Monster("Golem", "golem", 95, 13, 3, 3)},
        {new Monster("Dark Mage", "dark_mage", 85, 16, 3, 2), new Monster("Knight", "knight", 105, 15, 3, 1)}
    };

    /** The guardian that ends each floor: reuses a regular monster's art with boosted stats. */
    static final Monster[] GUARDIANS = {
        new Monster("Goblin Chief", "goblin", 80, 9, 2, 1),
        new Monster("Skeleton King", "skeleton", 110, 12, 3, 1),
        new Monster("Troll Warlord", "troll", 150, 15, 3, 2),
        new Monster("Stone Colossus", "golem", 180, 17, 3, 3),
        new Monster("Dragon", "dragon", 280, 20, 3, 3)
    };

    // ---- live battle state ----
    final String name, art;
    final int diff, special, dmg, maxHp;
    int hp;
    boolean charging;     // special 3: winding up a big hit for next turn
    boolean weakened;     // set by the player's Strategy: deals 25% less damage
    final boolean boss;   // true for floor guardians (3x XP and gold)

    /** Builds a scaled enemy from a template (use {@link #spawn} or {@link #guardian}). */
    private Enemy(Monster m, int floor, int playerLevel, boolean boss) {
        // Enemies grow 5% per player level, and floors 4-5 get an extra +12% per floor so the challenge keeps up.
        double s = (1 + 0.05 * (playerLevel - 1)) * (1 + 0.12 * Math.max(0, floor - 3));
        name = m.name(); art = m.art(); diff = m.diff(); special = m.special();   // copied from the template
        // HP and damage are the template values multiplied by the scale factor, rounded down
        maxHp = (int) (m.hp() * s); dmg = (int) (m.dmg() * s);
        hp = maxHp; this.boss = boss;
    }

    /**
     * Picks a random regular monster for the floor.
     *
     * @param last name of the previous monster; it is avoided so the same monster never appears twice in a row
     */
    static Enemy spawn(int floor, int level, String last) {
        Monster[] pool = POOL[floor - 1];
        Monster m;
        // keep drawing until it differs from the previous monster (only possible when the floor has 2+ kinds)
        do { m = pool[Game.R.nextInt(pool.length)]; } while (m.name().equals(last) && pool.length > 1);
        return new Enemy(m, floor, level, false);
    }

    /** The guardian that waits at the end of the floor. */
    static Enemy guardian(int floor, int level) { return new Enemy(GUARDIANS[floor - 1], floor, level, true); }

    /** XP reward: grows with floor and difficulty, tripled for guardians. */
    int xpReward(int floor) { return (12 + 6 * floor + 4 * diff) * (boss ? 3 : 1); }

    /** Gold reward: grows with floor plus a little luck, tripled for guardians. */
    int goldReward(int floor) { return (8 + 4 * floor + Game.R.nextInt(6)) * (boss ? 3 : 1); }
}
