import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.IntConsumer;

/**
 * Keyboard input: typed lines with time limits, and arrow-key menus.
 *
 * <p><b>How it works.</b> The game talks to the keyboard through JLine (org.jline:jline), a terminal library that works
 * on Windows (PowerShell, cmd, Windows Terminal, VS Code), Linux, macOS and Termux alike. JLine is looked up with
 * reflection, so the game still <i>compiles</i> with a plain {@code javac *.java} even when the JLine jar is not
 * there; the jar only has to be on the classpath when the game runs (see {@code run.sh}, {@code run.bat} and
 * {@code lib/README.txt}).</p>
 *
 * <ul>
 *   <li><b>JLine mode</b> (jar present and a real terminal): the terminal is put into raw mode once at start-up and
 *       restored on exit (also on Ctrl+C). Every key arrives one at a time, so {@link #choose} can react to the
 *       arrow keys and {@link #line} / {@link #timed} read typed answers themselves (echo, backspace, Enter).</li>
 *   <li><b>Fallback mode</b> (no jar, or input is piped): a background thread reads plain lines from
 *       {@code System.in}. {@link #rawSupported()} is then false, so {@code Display.menu} shows numbered menus and the
 *       player types the number. Everything stays playable.</li>
 * </ul>
 *
 * <p>When input ends (Ctrl+D, closed stdin) or the waiting thread is interrupted, a {@link Quit} exception is
 * thrown so {@link Game} can shut down cleanly instead of the program calling {@code System.exit}.</p>
 */
class Input {
    /** Thrown when input ends or the waiting thread is interrupted. Caught once, in {@link Game#main}. */
    static class Quit extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    // ------------------------------------------------------------------ JLine state (all null in fallback mode)

    private static boolean started;
    /** The JLine Terminal object (org.jline.terminal.Terminal), or null when JLine is not in use. */
    private static Object term;
    /** The terminal's NonBlockingReader and its {@code read(long timeoutMs)} method. */
    private static Object reader;
    private static Method readM, setAttrsM, closeM, widthM;
    /** Terminal attributes from before raw mode; put back on exit. */
    private static Object savedAttrs;
    private static OutputStream termOut;
    private static Charset termCharset = StandardCharsets.UTF_8;
    private static boolean shutDown;

    // ------------------------------------------------------------------ fallback state (plain System.in lines)

    /** Sentinel put in the line queue when stdin closes; compared by identity, never equal to typed text. */
    private static final String EOF = new String("<eof>");
    private static final BlockingQueue<String> Q = new LinkedBlockingQueue<>();
    private static volatile boolean closed;

    // ------------------------------------------------------------------ start-up and shut-down

    /** Chooses the input mode once: JLine if possible, otherwise the System.in reader thread. Safe to call repeatedly. */
    static synchronized void start() {
        if (started) return;
        started = true;
        if (!startJLine()) startStdin();
    }

    /**
     * Tries to open a JLine system terminal in raw mode.
     *
     * @return true if it worked; false when the jar is missing, there is no real terminal (piped input) or anything fails
     */
    private static boolean startJLine() {
        Object t = null, saved = null;
        Method setAttrs = null;
        try {
            // "mw.jline.pkg" only exists so the code can be tested against another copy of JLine; normally it is org.jline
            String pkg = System.getProperty("mw.jline.pkg", "org.jline");
            Class<?> builderC = Class.forName(pkg + ".terminal.TerminalBuilder");
            Class<?> termC = Class.forName(pkg + ".terminal.Terminal");
            Class<?> attrsC = Class.forName(pkg + ".terminal.Attributes");
            Class<?> readerC = Class.forName(pkg + ".utils.NonBlockingReader");
            Object b = builderC.getMethod("builder").invoke(null);
            b = builderC.getMethod("system", boolean.class).invoke(b, true);   // the real console, not a fake one
            b = builderC.getMethod("dumb", boolean.class).invoke(b, false);    // no real terminal -> fail (we use the fallback)
            t = builderC.getMethod("build").invoke(b);
            if (String.valueOf(termC.getMethod("getType").invoke(t)).startsWith("dumb")) throw new IllegalStateException("dumb");
            setAttrs = termC.getMethod("setAttributes", attrsC);
            saved = termC.getMethod("enterRawMode").invoke(t);                // no line buffering, no echo
            // Preserve normal newline translation for the game's System.out.println() calls.
            // Raw mode often disables OPOST; without OPOST + ONLCR, lines can drift diagonally.
            try {
                Object cur = termC.getMethod("getAttributes").invoke(t);
                Class<?> outputFlagC = Class.forName(pkg + ".terminal.Attributes$OutputFlag");
                Method setOutputFlag = attrsC.getMethod("setOutputFlag", outputFlagC, boolean.class);
                for (Object flag : outputFlagC.getEnumConstants()) {
                    String name = flag.toString();
                    if (name.equals("OPOST") || name.equals("ONLCR")) setOutputFlag.invoke(cur, flag, true);
                }
                setAttrs.invoke(t, cur);
            } catch (Throwable ignored) { }
            // JLine swallows Ctrl+C by default. Turn the terminal's signal handling off so Ctrl+C simply arrives as a
            // key (code 3) and the game can quit and restore the terminal. Not fatal if this step fails.
            try {
                Object cur = termC.getMethod("getAttributes").invoke(t);
                Class<?> flagC = Class.forName(pkg + ".terminal.Attributes$LocalFlag");
                Object isig = null;
                for (Object c : flagC.getEnumConstants()) if (c.toString().equals("ISIG")) isig = c;
                attrsC.getMethod("setLocalFlag", flagC, boolean.class).invoke(cur, isig, false);
                setAttrs.invoke(t, cur);
            } catch (Throwable ignored) { }
            reader = termC.getMethod("reader").invoke(t);
            readM = readerC.getMethod("read", long.class);                    // read(timeoutMs): -1 end of input, -2 timeout
            widthM = termC.getMethod("getWidth");
            closeM = termC.getMethod("close");
            termOut = (OutputStream) termC.getMethod("output").invoke(t);
            termCharset = (Charset) termC.getMethod("encoding").invoke(t);
            setAttrsM = setAttrs;
            savedAttrs = saved;
            term = t;
            Runtime.getRuntime().addShutdownHook(new Thread(Input::shutdown)); // restores the terminal even on Ctrl+C
            return true;
        } catch (Throwable e) {                                               // missing jar, no tty, unsupported terminal ...
            if (Boolean.getBoolean("mw.debugJLine")) {
                System.err.println("JLine terminal initialization failed: " + e);
                e.printStackTrace(System.err);
            }
            try { if (t != null && saved != null && setAttrs != null) setAttrs.invoke(t, saved); } catch (Throwable ignored) { }
            try { if (t != null) t.getClass().getMethod("close").invoke(t); } catch (Throwable ignored) { }
            term = null; reader = null;
            return false;
        }
    }

    /** Fallback: a daemon thread turns System.in into a queue of finished lines. */
    private static void startStdin() {
        Thread t = new Thread(() -> {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();           // bytes of the line being typed
            boolean lastCR = false;                                            // so "\r\n" counts as ONE line end
            try {
                int b;
                while ((b = System.in.read()) != -1) {
                    if (b == '\n' && lastCR) { lastCR = false; continue; }
                    lastCR = b == '\r';
                    if (b == '\n' || b == '\r') { Q.add(buf.toString(StandardCharsets.UTF_8)); buf.reset(); }
                    else buf.write(b);
                }
            } catch (IOException ignored) { }
            closed = true;
            Q.add(EOF);                                                        // wakes up whoever is waiting
        });
        t.setDaemon(true);                                                     // never keeps the JVM alive
        t.start();
    }

    /** Puts the terminal back as it was (cursor visible, normal line mode). Called on exit and by the shutdown hook. */
    static synchronized void shutdown() {
        if (term == null || shutDown) return;
        shutDown = true;
        try { System.out.print("\033[?25h"); System.out.flush(); } catch (Throwable ignored) { }   // never leave the cursor hidden
        try { setAttrsM.invoke(term, savedAttrs); } catch (Throwable ignored) { }
        try { closeM.invoke(term); } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ what the rest of the game asks about the terminal

    /** True when arrow-key menus are possible (JLine terminal in raw mode). */
    static boolean rawSupported() { start(); return term != null; }

    /** True when JLine gave us a real terminal that understands ANSI escape codes (also on Windows consoles). */
    static boolean ansiCapable() { start(); return term != null; }

    /** The terminal's output stream (JLine handles ANSI and Unicode on Windows consoles), or null in fallback mode. */
    static OutputStream output() { start(); return termOut; }

    /** The character set the terminal's output stream expects. */
    static Charset charset() { return termCharset; }

    /** Terminal width in columns, or 0 if unknown. */
    static int columns() {
        start();
        try {
            if (term != null) { int w = (Integer) widthM.invoke(term); if (w > 0) return w; }
        } catch (Throwable ignored) { }
        try { return Math.max(0, Integer.parseInt(System.getenv("COLUMNS"))); } catch (Exception e) { return 0; }
    }

    // ------------------------------------------------------------------ typed lines

    /**
     * Waits for one line with no time limit.
     *
     * @return the trimmed line the player typed
     * @throws Quit if input has ended
     */
    static String line() {
        start();
        if (term != null) return readLine(-1);
        prepStdin();
        try { return check(Q.take()); } catch (InterruptedException e) { throw interrupted(); }
    }

    /**
     * Waits for one line, but only for a limited time.
     *
     * @param ms how long to wait, in milliseconds
     * @return the trimmed line, or {@code null} if time ran out first
     * @throws Quit if input has ended
     */
    static String timed(long ms) {
        start();
        if (term != null) return readLine(ms);
        prepStdin();
        try {
            String s = Q.poll(ms, TimeUnit.MILLISECONDS);
            return s == null ? null : check(s);
        } catch (InterruptedException e) { throw interrupted(); }
    }

    /**
     * JLine mode: reads a line key by key and does the echo itself (the terminal is in raw mode).
     * Enter finishes, Backspace deletes, Ctrl+D on an empty line quits, escape sequences (arrow keys) are ignored.
     *
     * @param ms time limit in milliseconds, or a negative number for none
     * @return the trimmed line, or null on timeout
     */
    private static String readLine(long ms) {
        drain();                                                              // forget keys pressed before the prompt
        boolean forever = ms < 0;
        long end = forever ? 0 : System.currentTimeMillis() + ms;
        StringBuilder sb = new StringBuilder();
        while (true) {
            long left = forever ? 250 : Math.min(250, end - System.currentTimeMillis());
            if (!forever && left <= 0) return null;                           // time is up
            int k = key(left);
            if (k == -2) continue;                                            // nothing typed yet: keep waiting
            if (k == -1 || k == 3 || (k == 4 && sb.length() == 0)) throw new Quit();   // end of input, Ctrl+C, Ctrl+D
            if (k == '\r' || k == '\n') { System.out.println(); return sb.toString().trim(); }
            if (k == 127 || k == 8) {                                         // Backspace / Delete key
                if (sb.length() > 0) { sb.setLength(sb.length() - 1); System.out.print("\b \b"); }
                continue;
            }
            if (k == 27) { escape(); continue; }                              // arrow keys etc. are not text
            if (k >= 32) { sb.append((char) k); System.out.print((char) k); } // printable character: store and echo
        }
    }

    /** Fallback mode: throws away anything typed ahead of the prompt. */
    private static void prepStdin() { Q.clear(); if (closed) throw new Quit(); }

    /** Fallback mode: converts the EOF sentinel into a Quit, otherwise returns the trimmed text. */
    private static String check(String s) { if (s == EOF) throw new Quit(); return s.trim(); }

    /** Restores the interrupt flag and returns the exception to throw. */
    private static Quit interrupted() { Thread.currentThread().interrupt(); return new Quit(); }

    // ------------------------------------------------------------------ arrow-key menu

    /**
     * Lets the player pick one of {@code n} choices laid out in a grid with {@code cols} columns
     * (cols = 1 is a vertical list, cols = 2 is the 2x2 answer grid: cells 0 1 / 2 3).
     * Arrow keys move the selection (the caller's {@code draw} callback repaints the highlight immediately),
     * the digits 1-9 jump to a cell, and Enter confirms. The first cell is selected at the start.
     * Only call this when {@link #rawSupported()} is true.
     *
     * @param n    number of choices
     * @param cols number of columns in the layout (1 or 2)
     * @param ms   time limit in milliseconds (use a huge value for no limit)
     * @param draw called with the newly selected index whenever the selection changes
     * @return the confirmed index, or -1 if time ran out
     * @throws Quit if input has ended
     */
    static int choose(int n, int cols, long ms, IntConsumer draw) {
        start();
        if (term == null) throw new IllegalStateException("choose() needs a raw terminal; check rawSupported() first");
        drain();                                                              // ignore keys pressed before the menu appeared
        long end = System.currentTimeMillis() + ms;
        int sel = 0;                                                          // the first entry is selected at the start
        while (true) {
            long left = Math.min(250, end - System.currentTimeMillis());
            if (left <= 0) return -1;                                         // timed out
            int k = key(left);
            if (k == -2) continue;
            if (k == -1 || k == 3 || k == 4) throw new Quit();                // end of input, Ctrl+C or Ctrl+D quits from a menu
            if (k == '\n' || k == '\r') return sel;                           // Enter confirms immediately
            int next = sel;
            if (k == 27) next = move(sel, n, cols, escape());                 // arrow keys arrive as ESC [ A/B/C/D (or ESC O ...)
            else if (k >= '1' && k <= '9' && k - '1' < n) next = k - '1';     // number keys jump to an entry
            if (next != sel) { sel = next; draw.accept(sel); }                // repaint the highlight right away
        }
    }

    /** New selection after an arrow key ('A' up, 'B' down, 'C' right, 'D' left); unchanged at the edges. */
    private static int move(int sel, int n, int cols, int arrow) {
        if (arrow == 'A' && sel >= cols) return sel - cols;
        if (arrow == 'B' && sel + cols < n) return sel + cols;
        if (arrow == 'C' && sel % cols < cols - 1 && sel + 1 < n) return sel + 1;
        if (arrow == 'D' && sel % cols > 0) return sel - 1;
        return sel;
    }

    /**
     * Reads the rest of an escape sequence after ESC and returns its final letter ('A'-'D' for the arrow keys), or 0
     * for a lone ESC or an unknown sequence. Handles "ESC [ A", "ESC O A" and ones with modifiers like "ESC [ 1 ; 5 A".
     */
    private static int escape() {
        int a = key(50);
        if (a != '[' && a != 'O') return 0;
        for (int i = 0; i < 8; i++) {                                         // parameters such as "1;5" come before the letter
            int c = key(50);
            if (c < 0) return 0;
            if (c >= '@' && c <= '~') return c;                               // the final character of the sequence
        }
        return 0;
    }

    // ------------------------------------------------------------------ raw keys (JLine mode)

    /**
     * Next key from the JLine reader.
     *
     * @return the character code, -1 if input has ended, or -2 if nothing arrived within {@code ms}
     */
    private static int key(long ms) {
        if (ms <= 0) return -2;                                               // JLine treats a timeout of 0 as "wait forever"
        try {
            return (Integer) readM.invoke(reader, ms);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof InterruptedIOException) throw interrupted();
            return -1;                                                        // any other I/O problem: treat as end of input
        } catch (IllegalAccessException e) {
            return -1;
        }
    }

    /** Discards keys that were typed ahead (so an old key press can never answer a new question). */
    private static void drain() {
        while (true) {
            int k = key(1);
            if (k == -2) return;
            if (k == -1 || k == 3) throw new Quit();                          // a Ctrl+C pressed during a pause still counts
        }
    }
}
