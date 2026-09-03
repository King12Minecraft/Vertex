import javax.imageio.ImageIO;
import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * GameLogo
 * --------
 * The Vertex mark - an original faceted-crystal icon (an 8-point star
 * built from triangular facets meeting at a bright center point,
 * directly representing "vertex" as a geometric corner point), loaded
 * from vertex_logo.png rather than drawn with Graphics2D shapes. Using
 * an external image file is a deliberate departure from the project's
 * earlier programmatic-icon approach - still fully compliant with the
 * "no external libraries" rule, since loading a PNG only needs
 * javax.imageio (part of the JDK itself, not a third-party library).
 *
 * Used two ways:
 *   1. As a live Swing component (shown in the sidebar) - loads once,
 *      caches the scaled result per size requested.
 *   2. Via the static renderIcon(size) method, for the window/taskbar
 *      icon - same source image, resized to whatever size is asked
 *      for.
 */
public class GameLogo extends JComponent
{
    private static final String LOGO_FILE = "vertex_logo.png";
    private static BufferedImage sourceImage;
    private static boolean loadAttempted = false;

    private final int size;
    private BufferedImage scaledCache;

    public GameLogo(int size)
    {
        this.size = size;
        setPreferredSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setOpaque(false);
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.drawImage(scaledToSize(), 0, 0, null);
        g2.dispose();
    }

    /** Scales once per instance and reuses the result on every subsequent paint - drawing at a mismatched size every frame (this component sits in the Sidebar, which repaints on every tick of its ~12ms expand/collapse animation) would mean re-running a full bilinear resample many times for a result that never actually changes. */
    private BufferedImage scaledToSize()
    {
        if (scaledCache == null)
        {
            BufferedImage source = loadSource();
            scaledCache = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = scaledCache.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (source != null)
            {
                g2.drawImage(source, 0, 0, size, size, null);
            }
            g2.dispose();
        }
        return scaledCache;
    }

    /** Renders the mark into a standalone image for use as a window/taskbar icon. */
    public static BufferedImage renderIcon(int size)
    {
        BufferedImage source = loadSource();
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (source != null)
        {
            g2.drawImage(source, 0, 0, size, size, null);
        }
        g2.dispose();
        return image;
    }

    /** Loads vertex_logo.png once - from the working directory first (the BlueJ/source layout), falling back to the classpath (a packaged runnable jar, where the png is bundled alongside the .class files) - and caches it; every call after the first just reuses the cached image, resized on demand at draw time. */
    private static synchronized BufferedImage loadSource()
    {
        if (loadAttempted)
        {
            return sourceImage;
        }
        loadAttempted = true;
        try
        {
            File file = new File(LOGO_FILE);
            if (file.exists())
            {
                sourceImage = ImageIO.read(file);
            }
            else
            {
                java.io.InputStream in = GameLogo.class.getResourceAsStream("/" + LOGO_FILE);
                if (in != null)
                {
                    try
                    {
                        sourceImage = ImageIO.read(in);
                    }
                    finally
                    {
                        in.close();
                    }
                }
            }
        }
        catch (IOException e)
        {
            System.err.println("Could not load " + LOGO_FILE + ": " + e.getMessage());
        }
        return sourceImage;
    }
}
