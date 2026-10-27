import java.util.*;
import java.util.function.Supplier;

/**
 * Builds every math question in the game.
 *
 * <p><b>How it works:</b> each category and difficulty has a list of templates. A template is a small lambda
 * that builds the question text and computes the answer from the same numbers, so the answer can never disagree
 * with the question and no expression parser is needed. {@link #make} picks a random template and re-rolls
 * (a) templates that came out invalid (negative or oversized answers, brackets that grew too big) and
 * (b) questions that were asked in the last {@link #MEMORY} turns, so patterns do not repeat.</p>
 *
 * <p>Categories: {@link Type#ATTACK} = PEMDAS expressions and fantasy word problems,
 * {@link Type#DEFEND} = fractions, {@link Type#STRATEGY} = percentages. Difficulty 1/2/3 = easy/medium/hard.
 * <b>Division (the &divide; sign, "Simplify", "fraction of a number", "is what %") is locked to floor 3 and above.</b>
 * Squares only use bases up to 10 and anything inside brackets that is squared stays at 10 or less.</p>
 */
class QuestionGenerator {
    /** The three question categories (one per combat action). */
    enum Type { ATTACK, DEFEND, STRATEGY }

    /**
     * One generated question.
     *
     * @param type   category
     * @param text   what the player sees
     * @param answer the correct answer in lowest terms ("12", "3/4")
     * @param hint   short reminder of the expected answer format (for typed answers)
     */
    record Question(Type type, String text, String answer, String hint) { }

    private static final String FMT = "fraction, e.g. 3/4";
    /** Superscripts, multiplication and division signs, written as escapes so the source stays ASCII-safe. */
    private static final String SQ = "\u00B2", CU = "\u00B3", MUL = " \u00D7 ", DIV = " \u00F7 ";
    /** How many recent question texts are remembered to avoid repeats. */
    private static final int MEMORY = 10;
    private static final Deque<String> RECENT = new ArrayDeque<>();

    /** Random int in [lo, hi] (inclusive). */
    private static int r(int lo, int hi) { return lo + Game.R.nextInt(hi - lo + 1); }

    /** Mostly one-digit operands; a two-digit one (10-14) is rare (about 1 in 20). */
    private static int num() { return r(0, 99) < 5 ? r(10, 14) : r(2, 9); }

    /** Base of a square: 2-9, and 10 only rarely (anything above 10 is never used). */
    private static int sq() { return r(0, 99) < 6 ? 10 : r(2, 9); }

    /** Greatest common divisor (Euclid). */
    private static int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }

    /** Shortcut for a question whose answer is a whole number. */
    private static Question q(Type t, String text, int ans) { return new Question(t, text, String.valueOf(ans), "whole number"); }

    /** Shortcut for a question whose answer is a fraction (or a whole number once reduced). */
    private static Question f(String text, int n, int d) { return new Question(Type.DEFEND, text, fr(n, d), FMT); }

    /** Picks a random template from the list and builds the question (may return null if the numbers were invalid). */
    private static Question pick(List<Supplier<Question>> l) { return l.get(Game.R.nextInt(l.size())).get(); }

    /**
     * Creates a question.
     *
     * @param t     category
     * @param diff  difficulty 1-3
     * @param floor current floor; division templates are only allowed when floor &gt;= 3
     */
    static Question make(Type t, int diff, int floor) {
        boolean div = floor >= 3;                       // the division lock: floors 1-2 never get division templates
        Question best = null;
        for (int tries = 0; tries < 80; tries++) {
            Question c = switch (t) {
                // about 30% of Attack questions are short fantasy word problems
                case ATTACK -> Game.R.nextInt(100) < 30 ? word(diff, div) : pemdas(diff, div);
                case DEFEND -> fraction(diff, div);
                case STRATEGY -> percent(diff, div);
            };
            if (c == null || !usable(c)) continue;      // invalid numbers: roll again
            best = c;
            if (!RECENT.contains(c.text())) break;      // fresh question: use it
        }
        if (best == null) best = q(Type.ATTACK, "2 + 2", 4);   // safety net, practically never reached
        RECENT.addLast(best.text());
        if (RECENT.size() > MEMORY) RECENT.removeFirst();
        return best;
    }

    /** A question is usable when its answer is a positive fraction or a whole number from 0 to 999. */
    private static boolean usable(Question c) {
        String a = c.answer();
        if (c.type() == Type.DEFEND && a.equals("0")) return false;   // "0" is a dull fraction answer
        if (a.contains("/")) return !a.startsWith("-");
        return a.matches("\\d{1,3}");
    }

    // ------------------------------------------------------------------ Attack: PEMDAS

    /**
     * PEMDAS expressions. Easy: one or two simple operations. Medium: precedence and brackets.
     * Hard: squares, cubes and brackets together. Division templates are only added when {@code div} is true.
     */
    private static Question pemdas(int d, boolean div) {
        Type t = Type.ATTACK;
        int a = num(), b = num(), c = num();        // general operands (two digits only rarely)
        int e = r(2, 9), g = r(2, 9), h = r(2, 9);  // always one digit (used in products)
        int s = sq(), s2 = r(2, 6);                 // square bases: never above 10
        int k = r(2, 5), m = r(2, 6);               // divisor and quotient for the division templates
        int total = k * m, x1 = r(1, total - 1);    // total is a multiple of k, so (x1 + (total - x1)) / k is always whole
        List<Supplier<Question>> l = new ArrayList<>();
        if (d == 1) {
            l.add(() -> q(t, a + " + " + b, a + b));
            l.add(() -> q(t, Math.max(a, b) + " - " + Math.min(a, b), Math.abs(a - b)));
            l.add(() -> q(t, e + MUL + g, e * g));
            l.add(() -> q(t, s + SQ, s * s));
            l.add(() -> q(t, a + " + " + b + " + " + c, a + b + c));
            l.add(() -> q(t, a + " + " + b + " - " + c, a + b - c));
            l.add(() -> q(t, k + MUL + e + " + " + h, k * e + h));
        } else if (d == 2) {
            l.add(() -> q(t, a + " + " + b + MUL + c, a + b * c));
            l.add(() -> q(t, "(" + a + " + " + b + ")" + MUL + c, (a + b) * c));
            l.add(() -> q(t, s + SQ + " + " + b, s * s + b));
            l.add(() -> q(t, e + MUL + g + " - " + c, e * g - c));
            l.add(() -> q(t, e + MUL + g + " + " + h + MUL + k, e * g + h * k));
            l.add(() -> q(t, "(" + a + " + " + b + " - " + c + ")" + MUL + e, (a + b - c) * e));
            l.add(() -> q(t, e + MUL + "(" + g + " - " + h + ") + " + c, e * (g - h) + c));
            l.add(() -> q(t, a + " + " + b + " + " + c + MUL + e, a + b + c * e));
            l.add(() -> q(t, s + SQ + " - " + b, s * s - b));
            l.add(() -> q(t, s2 + SQ + " + " + (m) + SQ, s2 * s2 + m * m));
            if (div) {
                l.add(() -> q(t, (k * e) + DIV + k + " + " + b, e + b));
                l.add(() -> q(t, (k * e) + DIV + k + " - " + c, e - c));
                l.add(() -> q(t, "(" + x1 + " + " + (total - x1) + ")" + DIV + k, m));
            }
        } else {
            int lo = r(1, 5), hi = lo + r(1, 4), n = r(2, 4), p = r(2, 6), q2 = r(1, 10 - p);
            int a2 = r(2, 9), b2 = r(1, 9), c2 = r(1, 9), inner = a2 - b2 + c2;
            l.add(() -> q(t, a + " + " + b + MUL + "(" + hi + " - " + lo + ")" + SQ, a + b * (hi - lo) * (hi - lo)));
            l.add(() -> q(t, "(" + p + " + " + q2 + ")" + SQ + " - " + e + MUL + "2", (p + q2) * (p + q2) - e * 2));
            l.add(() -> q(t, n + CU + " + " + e + MUL + g, n * n * n + e * g));
            l.add(() -> q(t, e + MUL + "(" + g + " + " + h + ") - " + n + SQ, e * (g + h) - n * n));
            l.add(() -> q(t, "(" + e + " + " + g + ")" + MUL + "(" + h + " - 1)", (e + g) * (h - 1)));
            l.add(() -> q(t, s2 + SQ + " + " + m + SQ + " - " + e, s2 * s2 + m * m - e));
            l.add(() -> inner < 1 || inner > 10 ? null                      // bracket content must stay 1-10
                    : q(t, "(" + a2 + " - " + b2 + " + " + c2 + ")" + SQ, inner * inner));
            l.add(() -> q(t, e + MUL + g + " - " + h + MUL + n, e * g - h * n));
            l.add(() -> q(t, "(" + p + " + " + q2 + ")" + MUL + "(" + e + " - 1) - " + n + SQ, (p + q2) * (e - 1) - n * n));
            l.add(() -> q(t, n + CU + " - " + n + MUL + g, n * n * n - n * g));
            if (div) {
                int cc = r(2, 5), bb = r(1, 10 - cc);                     // (cc + bb) stays at 10 or less
                l.add(() -> q(t, "(" + (k * cc) + DIV + k + " + " + bb + ")" + SQ, (cc + bb) * (cc + bb)));
                l.add(() -> q(t, "(" + x1 + " + " + (total - x1) + ")" + DIV + k + MUL + e, m * e));
                l.add(() -> q(t, total + DIV + k + " + " + e + MUL + g, m + e * g));
            }
        }
        return pick(l);
    }

    // ------------------------------------------------------------------ Attack: word problems

    /**
     * Short fantasy word problems using basic arithmetic only.
     * Easy = one step, medium = two steps, hard = three steps. The division problems need floor 3+.
     * Every text is at most two screen lines (Battle wraps it).
     */
    private static Question word(int d, boolean div) {
        Type t = Type.ATTACK;
        int a = r(2, 8), b = r(2, 7), c = r(2, 9), e = r(2, 6);
        int x = r(30, 70);                           // starting gold
        int k = r(2, 6), ww = r(2, 5), cst = r(1, k - 1);   // for division: ww wizards share k each; cst = charges cast
        List<Supplier<Question>> l = new ArrayList<>();
        if (d == 1) {
            l.add(() -> q(t, "You hold " + a + " potions and find " + c + " more. How many now?", a + c));
            l.add(() -> q(t, x + " gold, a goblin steals " + c + ". How much is left?", x - c));
            l.add(() -> q(t, a + " chests hold " + b + " gold each. How much gold in total?", a * b));
            l.add(() -> q(t, "A wizard owns " + a + " torches and buys " + b + " more. Total?", a + b));
            l.add(() -> q(t, "Each of " + a + " quivers holds " + b + " arrows. How many arrows?", a * b));
            l.add(() -> q(t, x + " coins, you lose " + c + " in a trap. How many are left?", x - c));
            if (div) l.add(() -> q(t, (ww * k) + " charges split into " + ww + " scrolls. Each?", k));
        } else if (d == 2) {
            l.add(() -> q(t, a + " chests hold " + b + " gold each. You spend " + (a * b / 2) + ". Gold left?", a * b - a * b / 2));
            l.add(() -> q(t, b + " bags hold " + a + " gems each, plus " + c + " loose gems. Total gems?", a * b + c));
            l.add(() -> q(t, "You have " + (x + a * c) + " gold and buy " + a + " potions at " + c + " each. Gold left?", x));
            l.add(() -> q(t, "You have " + x + " gold and win " + a + " duels worth " + b + " each. Total gold?", x + a * b));
            l.add(() -> q(t, a + " goblins carry " + b + " gold each. You take all but " + e + ". Gold taken?", a * b - e));
            l.add(() -> q(t, (x + a * b) + " arrows, you fire " + a + " volleys of " + b + ". Arrows left?", x));
            l.add(() -> q(t, a + " towers have " + b + " torches each, and " + e + " go out. How many still burn?", a * b - e));
            if (div) {
                l.add(() -> q(t, (ww * k) + " charges sit in " + ww + " scrolls. You cast " + cst + " from one. Left in it?", k - cst));
                l.add(() -> q(t, (ww * k) + " gems are shared by " + ww + " wizards, then you add " + c + ". Total for you?", k + c));
            }
        } else {
            int y = r(2, 6);
            l.add(() -> q(t, "You have " + x + " gold, loot " + a + " chests of " + b + ", then pay " + y + " guards " + e
                    + " each. Gold left?", x + a * b - y * e));
            l.add(() -> q(t, a + " potions cost " + b + " gold each and " + e + " scrolls cost " + c + " each. Total gold?", a * b + e * c));
            l.add(() -> q(t, "You have " + x + " gold, buy " + a + " potions at " + b + ", sell " + e + " gems at " + c + ". Gold now?",
                    x - a * b + e * c));
            l.add(() -> q(t, a + " bags hold " + b + " gems. You lose " + c + " and find " + e + " more. How many gems?", a * b - c + e));
            l.add(() -> q(t, a + " towers have " + b + " floors, each with " + e + " torches. " + c + " go out. Torches lit?", a * b * e - c));
            l.add(() -> q(t, "A dragon has " + x + " gold. A knight takes " + a * b + ", then " + e + " thieves take " + c + " each. Left?",
                    x - a * b - e * c));
            if (div) {
                l.add(() -> q(t, (ww * k + c) + " gems: you lose " + c + ", then share the rest among " + ww + " wizards. Each gets?", k));
                l.add(() -> q(t, (ww * (k + e)) + " gold is split by " + ww + " goblins, each spends " + e + ", then finds " + c + ". Gold each?", k + c));
            }
        }
        return pick(l);
    }

    // ------------------------------------------------------------------ Defend: fractions

    /** Reduces n/d to lowest terms; returns just "n" when the fraction is a whole number ("-..." if negative). */
    private static String fr(int n, int d) {
        int g = gcd(Math.abs(n), d);
        n /= g; d /= g;
        return d == 1 ? String.valueOf(n) : n + "/" + d;
    }

    /**
     * Fraction questions. Every question has ONE clear target: a single operation on one or two fractions
     * (or "a fraction of a number"), never two separate calculations stuck together.
     * Easy: same denominators. Medium: different denominators, multiplication, a whole number times a fraction.
     * Hard (typed): bigger denominators and, from floor 3, division. Negative results are filtered by {@link #usable}.
     */
    private static Question fraction(int dif, boolean div) {
        Type t = Type.DEFEND;
        List<Supplier<Question>> l = new ArrayList<>();
        int d = r(4, 9), x = r(1, d - 1), y = r(1, d - 1), kk = r(2, 4), mm = r(2, 6);
        if (dif == 1) {
            l.add(() -> f(x + "/" + d + " + " + y + "/" + d, x + y, d));
            l.add(() -> f(x + "/" + d + " - " + y + "/" + d, x - y, d));
            l.add(() -> f("1 - " + x + "/" + d, d - x, d));
            if (div) {
                l.add(() -> f("Simplify " + (x * kk) + "/" + (d * kk), x, d));       // simplifying is a division: floor 3+ only
                l.add(() -> q(t, x + "/" + d + " of " + (d * mm), x * mm));          // a fraction of a number
            }
            return pick(l);
        }
        int lo = dif == 2 ? 2 : 3, hi = dif == 2 ? 6 : 8;
        int b1 = r(lo, hi), b2 = r(lo, hi), a1 = r(1, b1 - 1), a2 = r(1, b2 - 1);
        String f1 = a1 + "/" + b1, f2 = a2 + "/" + b2;
        l.add(() -> f(f1 + " + " + f2, a1 * b2 + a2 * b1, b1 * b2));
        l.add(() -> f(f1 + " \u00D7 " + f2, a1 * a2, b1 * b2));
        l.add(() -> f(f1 + " - " + f2, a1 * b2 - a2 * b1, b1 * b2));                 // negative -> re-rolled
        if (dif == 2) {
            l.add(() -> f(kk + " \u00D7 " + f1, kk * a1, b1));
            l.add(() -> f(a1 + "/" + b1 + " + " + a2 + "/" + (b1 * 2), 2 * a1 + a2, b1 * 2));   // related denominators
        }
        if (div) {
            l.add(() -> q(t, a1 + "/" + b1 + " of " + (b1 * mm), a1 * mm));                  // a fraction of a number
            if (dif == 2) l.add(() -> f("(" + f1 + ")" + DIV + "2", a1, b1 * 2));
            else l.add(() -> f("(" + f1 + ")" + DIV + "(" + f2 + ")", a1 * b2, b1 * a2));
        }
        return pick(l);
    }

    // ------------------------------------------------------------------ Strategy: percentages

    /**
     * Percentage questions. Every question has ONE clear target (one percentage of one number, or one price/total
     * after one change). Every base is a multiple of 20 so all answers are whole numbers.
     * Easy: "p% of x" and simple tips/savings. Medium: increase/decrease, discount, tip total.
     * Hard (typed): the same ideas with friendlier-to-type numbers (10, 20, 25, 30, 40, 50%) and bases up to 160;
     * "is what %" is a division, so floor 3+ only.
     */
    private static Question percent(int dif, boolean div) {
        Type t = Type.STRATEGY;
        int base = 20 * r(1, 10);
        int[] easy = {10, 20, 25, 50};
        int p = easy[r(0, 3)];
        List<Supplier<Question>> l = new ArrayList<>();
        if (dif == 1) {
            l.add(() -> q(t, p + "% of " + base, p * base / 100));
            l.add(() -> q(t, "What is " + p + "% of " + base + "?", p * base / 100));
            l.add(() -> q(t, "Bill " + base + ", tip 10%. How much is the tip?", base / 10));
            l.add(() -> q(t, "A sale takes " + p + "% off " + base + " gold. How much is saved?", p * base / 100));
        } else if (dif == 2) {
            l.add(() -> q(t, base + " increased by " + p + "%", base + p * base / 100));
            l.add(() -> q(t, base + " decreased by " + p + "%", base - p * base / 100));
            l.add(() -> q(t, "Price " + base + ", " + p + "% off. New price?", base - p * base / 100));
            l.add(() -> q(t, "Bill " + base + ", tip 10%. Total to pay?", base + base / 10));
            l.add(() -> q(t, "You have " + base + " gold and spend " + p + "%. Gold left?", base - p * base / 100));
        } else {
            int bb = 20 * r(1, 8), h = new int[]{10, 20, 25, 30, 40, 50}[r(0, 5)];   // friendlier numbers for typed answers
            l.add(() -> q(t, "Price " + bb + ", " + h + "% off. New price?", bb - h * bb / 100));
            l.add(() -> q(t, "Price " + bb + ", " + h + "% more. New price?", bb + h * bb / 100));
            l.add(() -> q(t, bb + " gold, spend " + h + "%. Gold left?", bb - h * bb / 100));
            l.add(() -> q(t, "Bill " + bb + ", " + h + "% tip. Total to pay?", bb + h * bb / 100));
            if (div) l.add(() -> q(t, (h * bb / 100) + " is what % of " + bb + "?", h));
        }
        return pick(l);
    }

    // ------------------------------------------------------------------ answer checking

    /** Parses "7", "3/4" or "75%" into {numerator, denominator}; returns null if it is not a number or fraction. */
    private static long[] parse(String s) {
        try {
            String[] p = s.replace(" ", "").replace("%", "").split("/");
            if (p.length == 1) return new long[]{Long.parseLong(p[0]), 1};
            if (p.length == 2 && Long.parseLong(p[1]) != 0) return new long[]{Long.parseLong(p[0]), Long.parseLong(p[1])};
        } catch (NumberFormatException ignored) { }
        return null;
    }

    /** True if the typed text looks like a number or fraction (so a typo can be re-entered, not marked wrong). */
    static boolean valid(String in) { return parse(in) != null; }

    /** True if the typed answer equals the correct one in value, so 2/4 and 3/6 both match 1/2. */
    static boolean matches(String in, String answer) {
        long[] a = parse(in), b = parse(answer);
        return a != null && b != null && a[0] * b[1] == a[1] * b[0];   // cross-multiplication
    }

    // ------------------------------------------------------------------ multiple-choice distractors

    /** Up to four shuffled answer options (one correct), never equal in value to the answer. */
    static List<String> choices(Question q) {
        Set<String> set = new LinkedHashSet<>();     // a set drops duplicate distractors automatically
        set.add(q.answer());
        for (int tries = 0; set.size() < 4 && tries < 100; tries++)
            set.add(q.answer().contains("/") ? nearFraction(q.answer()) : nearInt(Integer.parseInt(q.answer())));
        List<String> l = new ArrayList<>(set);
        Collections.shuffle(l, Game.R);
        return l;
    }

    /** A believable wrong whole number close to v. */
    private static String nearInt(int v) {
        int off = r(1, Math.max(3, Math.abs(v) / 5));
        return String.valueOf(Game.R.nextBoolean() ? v + off : Math.max(0, v - off));
    }

    /** A believable wrong fraction: tweak the numerator or denominator, keep it in lowest terms. */
    private static String nearFraction(String s) {
        String[] p = s.split("/");
        int n = Integer.parseInt(p[0]), d = Integer.parseInt(p[1]);
        int cn = Game.R.nextBoolean() ? Math.max(1, n + r(-2, 2)) : n;
        int cd = cn == n ? Math.max(2, d + r(-2, 2)) : d;
        return cn * d == n * cd || gcd(cn, cd) > 1 ? s : cn + "/" + cd;   // equal value or not lowest terms -> duplicate, dropped
    }
}
