package ui;

import theme.ThemeManager;
import ui.CursorArtwork.CursorSet;
import ui.CursorArtwork.Role;

import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.prefs.Preferences;

/**
 * CursorManager
 * -------------
 * The mouse cursor the whole app shows, chosen in Settings (per computer, stored like the other
 * display settings). Swing components choose their own cursors (buttons ask for the hand, text
 * fields for the I-beam), so this can't just set one on the window: it watches mouse movement
 * and, for whatever is under the pointer, swaps the three cursors Swing uses - default, hand,
 * text - for the chosen set's versions, remembering each original so "System default" (or
 * another set) puts things back exactly. Anything else a component asks for (the resize
 * arrows, a game's crosshair, a hidden cursor, the wait cursor) is left alone. Covers dialogs
 * and the screen-break overlay too, since it listens at the AWT level rather than per window.
 *
 * Cursors are created at the platform's best size (usually 32x32; scaled art, never
 * stretched bitmaps). A platform that supports no custom cursors simply keeps its own.
 */
public final class CursorManager
{
    private static final Preferences PREFS = Preferences.userNodeForPackage(CursorManager.class);
    private static final String KEY = "cursorSet";
    private static final String NAME_PREFIX = "vertex-cursor:";

    private static CursorSet current = load();
    private static boolean installed = false;
    private static final Map<Role, Cursor> cache = new EnumMap<Role, Cursor>(Role.class);

    /** Components whose own cursor we replaced, with what they had before (a Cursor, or null for "inherit"). Weak keys: closed windows drop out by themselves. */
    private static final Map<Component, Object> originals = new WeakHashMap<Component, Object>();
    private static final Object INHERITED = new Object();

    private CursorManager() { }

    private static CursorSet load()
    {
        try
        {
            String saved = PREFS.get(KEY, CursorSet.SYSTEM.name());
            if ("MONO".equals(saved))
            {
                saved = "CLASSIC";   // renamed when each set became its own design
            }
            return CursorSet.valueOf(saved);
        }
        catch (IllegalArgumentException e)
        {
            return CursorSet.SYSTEM;
        }
    }

    public static CursorSet getSet() { return current; }

    private static final String GLOW_KEY = "cursorGlow";
    private static boolean glow = PREFS.getBoolean(GLOW_KEY, false);

    /** Whether the cursors get a soft coloured glow (off by default). */
    public static boolean isGlow() { return glow; }

    public static void setGlow(boolean on)
    {
        glow = on;
        PREFS.putBoolean(GLOW_KEY, on);
        rebuild();
    }

    /** Call once at startup: begins watching the mouse, and follows theme changes for the "Match my theme" set. */
    public static synchronized void install()
    {
        if (installed)
        {
            return;
        }
        installed = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener()
        {
            public void eventDispatched(AWTEvent event)
            {
                if (current == CursorSet.SYSTEM || !(event instanceof MouseEvent))
                {
                    return;
                }
                int id = event.getID();
                if (id == MouseEvent.MOUSE_MOVED || id == MouseEvent.MOUSE_ENTERED
                    || id == MouseEvent.MOUSE_DRAGGED || id == MouseEvent.MOUSE_PRESSED)
                {
                    patchUnderPointer((MouseEvent) event);
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);

        ThemeManager.addListener(new Runnable()
        {
            public void run()
            {
                if (current == CursorSet.APP)
                {
                    rebuild();
                }
            }
        });
    }

    public static void setSet(CursorSet set)
    {
        current = set;
        PREFS.put(KEY, set.name());
        rebuild();
    }

    /** Re-creates the cursors and re-applies them to everything already patched (and restores everything for SYSTEM). */
    private static void rebuild()
    {
        cache.clear();
        for (Map.Entry<Component, Object> entry : new java.util.ArrayList<Map.Entry<Component, Object>>(originals.entrySet()))
        {
            Component c = entry.getKey();
            if (c == null) continue;
            Object original = entry.getValue();
            Cursor restoredFrom = original == INHERITED ? null : (Cursor) original;
            Role role = roleOf(restoredFrom);
            if (current == CursorSet.SYSTEM || role == null)
            {
                c.setCursor(restoredFrom);
            }
            else
            {
                c.setCursor(cursorFor(role, restoredFrom));
            }
        }
        if (current == CursorSet.SYSTEM)
        {
            originals.clear();
        }
        for (Window w : Window.getWindows())
        {
            if (current == CursorSet.SYSTEM)
            {
                w.setCursor(null);
            }
            else if (w.isDisplayable())
            {
                w.setCursor(cursorFor(Role.ARROW, null));
            }
            w.repaint();
        }
    }

    /** The custom cursor for a role, or the fallback (the component's own) if the platform can't make one. */
    private static Cursor cursorFor(Role role, Cursor fallback)
    {
        Cursor cached = cache.get(role);
        if (cached == null)
        {
            cached = create(role);
            if (cached != null)
            {
                cache.put(role, cached);
            }
        }
        return cached != null ? cached : fallback;
    }

    private static Cursor create(Role role)
    {
        try
        {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            Dimension best = toolkit.getBestCursorSize(32, 32);
            int size = best.width > 0 ? Math.min(best.width, best.height) : 0;
            if (size <= 0)
            {
                return null;
            }
            BufferedImage image = CursorArtwork.draw(current, role, size);
            Point spot = CursorArtwork.hotspot(current, role);
            double scale = size / 32.0;
            Point scaled = new Point((int) Math.round(spot.x * scale), (int) Math.round(spot.y * scale));
            return toolkit.createCustomCursor(image, scaled, NAME_PREFIX + current.name() + (glow ? "+glow" : "") + ":" + role.name());
        }
        catch (Exception e)
        {
            return null;   // headless or unsupported: keep the platform's cursors
        }
    }

    /** ARROW/LINK/TEXT for the three cursors Swing components commonly set; null for anything else (left alone). */
    private static Role roleOf(Cursor cursor)
    {
        if (cursor == null)
        {
            return Role.ARROW;
        }
        if (cursor.getName() != null && cursor.getName().startsWith(NAME_PREFIX))
        {
            return null;
        }
        switch (cursor.getType())
        {
            case Cursor.DEFAULT_CURSOR: return Role.ARROW;
            case Cursor.HAND_CURSOR: return Role.LINK;
            case Cursor.TEXT_CURSOR: return Role.TEXT;
            default: return null;
        }
    }

    private static void patchUnderPointer(MouseEvent e)
    {
        Component source = e.getComponent();
        if (source == null)
        {
            return;
        }
        Component under = SwingUtilities.getDeepestComponentAt(source, e.getX(), e.getY());
        if (under == null)
        {
            under = source;
        }

        // The cursor in effect is the nearest explicitly-set one up the chain.
        for (Component c = under; c != null; c = c.getParent())
        {
            if (!c.isCursorSet())
            {
                continue;
            }
            Cursor own = c.getCursor();
            Role role = roleOf(own);
            if (role != null && own.getType() != Cursor.CUSTOM_CURSOR)
            {
                Cursor custom = cursorFor(role, own);
                if (custom != own)
                {
                    originals.put(c, own);
                    c.setCursor(custom);
                }
            }
            return;
        }

        // Nothing sets a cursor anywhere above: the window's cursor is what shows.
        Window window = SwingUtilities.getWindowAncestor(under);
        if (window == null && under instanceof Window)
        {
            window = (Window) under;
        }
        if (window != null && !window.isCursorSet())
        {
            originals.put(window, INHERITED);
            window.setCursor(cursorFor(Role.ARROW, null));
        }
    }
}
