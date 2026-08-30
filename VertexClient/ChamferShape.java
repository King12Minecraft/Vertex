import java.awt.geom.GeneralPath;

/**
 * ChamferShape
 * ------------
 * Builds an octagon-like chamfered (angular-cut-corner) rectangle path
 * - the shape used throughout the reskin for buttons, the logo, and
 * now card art panels. Centralizes the geometry so it's defined once.
 */
public class ChamferShape
{
    private ChamferShape()
    {
        // Static utility class - never instantiated.
    }

    public static GeneralPath build(int x, int y, int w, int h, int cut)
    {
        GeneralPath path = new GeneralPath();
        path.moveTo(x + cut, y);
        path.lineTo(x + w - cut, y);
        path.lineTo(x + w, y + cut);
        path.lineTo(x + w, y + h - cut);
        path.lineTo(x + w - cut, y + h);
        path.lineTo(x + cut, y + h);
        path.lineTo(x, y + h - cut);
        path.lineTo(x, y + cut);
        path.closePath();
        return path;
    }
}
