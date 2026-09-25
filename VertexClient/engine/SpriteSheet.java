package engine;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * SpriteSheet
 * -----------
 * Slices a single image into a grid of equal-sized Sprite frames -
 * the standard "one PNG holds a whole walk cycle" layout. Loads from
 * the classpath (Class.getResourceAsStream), the portable-jar-friendly
 * way to bundle art alongside .class files, the same approach
 * GameLogo.loadSource() uses for vertex_logo.png (minus the
 * working-directory fallback GameLogo needs for BlueJ - a sheet is
 * expected to always ship inside the packaged jar).
 *
 * Also buildable directly from an already-loaded BufferedImage, for a
 * sprite sheet rendered procedurally at startup rather than shipped as
 * a PNG asset - consistent with this codebase's general preference for
 * hand-drawn vector art over external image files.
 */
public class SpriteSheet
{
    private final BufferedImage sheet;
    private final int frameWidth;
    private final int frameHeight;

    public SpriteSheet(BufferedImage sheet, int frameWidth, int frameHeight)
    {
        this.sheet = sheet;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
    }

    /** Loads resourcePath from the classpath (relative to loader's package unless it starts with '/') and slices it into frameWidth x frameHeight frames. */
    public static SpriteSheet load(Class<?> loader, String resourcePath, int frameWidth, int frameHeight) throws IOException
    {
        try (InputStream in = loader.getResourceAsStream(resourcePath))
        {
            if (in == null)
            {
                throw new IOException("Sprite sheet resource not found: " + resourcePath);
            }
            BufferedImage image = ImageIO.read(in);
            return new SpriteSheet(image, frameWidth, frameHeight);
        }
    }

    public int columns()
    {
        return sheet.getWidth() / frameWidth;
    }

    public int rows()
    {
        return sheet.getHeight() / frameHeight;
    }

    /** The single frame at (row, col), 0-indexed from the sheet's top-left. */
    public Sprite frame(int row, int col)
    {
        BufferedImage sub = sheet.getSubimage(col * frameWidth, row * frameHeight, frameWidth, frameHeight);
        return new Sprite(sub);
    }

    /** An entire row's frames left-to-right - the common "one animation per row" sheet layout. */
    public Sprite[] row(int row)
    {
        int cols = columns();
        Sprite[] frames = new Sprite[cols];
        for (int col = 0; col < cols; col++)
        {
            frames[col] = frame(row, col);
        }
        return frames;
    }
}
