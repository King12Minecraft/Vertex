import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;

/**
 * NavIcons
 * --------
 * Simple flat glyph icons for the sidebar's nav entries - drawn
 * entirely with Graphics2D shapes, same "no image files" principle as
 * GameLogo and GameCardArt. Kept deliberately simple (a handful of
 * strokes/fills each) so they stay legible at the small size the
 * collapsed sidebar uses.
 */
public class NavIcons
{
    private NavIcons()
    {
        // Static utility class - never instantiated.
    }

    public static void draw(Graphics2D g2, String pageKey, int size, Color color)
    {
        Graphics2D g = (Graphics2D) g2.create();
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(1.4f, size * 0.09f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        if (Pages.GAMES.equals(pageKey))
        {
            drawGamepad(g, size);
        }
        else if (Pages.QUESTS.equals(pageKey))
        {
            drawQuest(g, size);
        }
        else if (Pages.FRIENDS.equals(pageKey))
        {
            drawFriends(g, size);
        }
        else if (Pages.CHAT.equals(pageKey))
        {
            drawChat(g, size);
        }
        else if (Pages.SHOP.equals(pageKey))
        {
            drawShop(g, size);
        }
        else if (Pages.PROFILE.equals(pageKey))
        {
            drawProfile(g, size);
        }
        else if (Pages.SETTINGS.equals(pageKey))
        {
            drawSettings(g, size);
        }
        else if (Pages.MODERATION.equals(pageKey))
        {
            drawShield(g, size);
        }
        else if (Pages.ALL_GAMES.equals(pageKey))
        {
            drawGrid(g, size);
        }
        else if (Pages.LEADERBOARDS.equals(pageKey))
        {
            drawTrophy(g, size);
        }
        else if (Pages.ACHIEVEMENTS.equals(pageKey))
        {
            drawStar(g, size);
        }
        else if (Pages.TOURNAMENTS.equals(pageKey))
        {
            drawBracket(g, size);
        }
        else
        {
            drawGamepad(g, size);
        }

        g.dispose();
    }

    /** A simple 2x2 grid of squares - distinct from the single gamepad glyph used for the curated "Games" home entry. */
    private static void drawGrid(Graphics2D g, int s)
    {
        float cell = s * 0.32f;
        float gap = s * 0.10f;
        float totalW = cell * 2 + gap;
        float x0 = (s - totalW) / 2f;
        float y0 = (s - totalW) / 2f;

        g.draw(new RoundRectangle2D.Float(x0, y0, cell, cell, cell * 0.25f, cell * 0.25f));
        g.draw(new RoundRectangle2D.Float(x0 + cell + gap, y0, cell, cell, cell * 0.25f, cell * 0.25f));
        g.draw(new RoundRectangle2D.Float(x0, y0 + cell + gap, cell, cell, cell * 0.25f, cell * 0.25f));
        g.draw(new RoundRectangle2D.Float(x0 + cell + gap, y0 + cell + gap, cell, cell, cell * 0.25f, cell * 0.25f));
    }

    /** A simple cup shape - bowl, two side handles, stem, and base - built entirely from basic arcs/rectangles, no external icon assets. */
    private static void drawTrophy(Graphics2D g, int s)
    {
        float cupW = s * 0.42f;
        float cupH = s * 0.32f;
        float cx = s / 2f;
        float cupTop = s * 0.20f;

        g.draw(new java.awt.geom.Arc2D.Float(cx - cupW / 2, cupTop, cupW, cupH, 180, 180, java.awt.geom.Arc2D.OPEN));
        g.draw(new java.awt.geom.Line2D.Float(cx - cupW / 2, cupTop, cx - cupW / 2, cupTop + cupH / 2));
        g.draw(new java.awt.geom.Line2D.Float(cx + cupW / 2, cupTop, cx + cupW / 2, cupTop + cupH / 2));

        float handleR = cupH * 0.28f;
        g.draw(new java.awt.geom.Arc2D.Float(cx - cupW / 2 - handleR, cupTop + cupH * 0.08f, handleR, handleR * 1.6f, 90, 180, java.awt.geom.Arc2D.OPEN));
        g.draw(new java.awt.geom.Arc2D.Float(cx + cupW / 2 - handleR * 0.15f, cupTop + cupH * 0.08f, handleR, handleR * 1.6f, -90, 180, java.awt.geom.Arc2D.OPEN));

        float stemBottom = cupTop + cupH / 2 + s * 0.14f;
        g.draw(new java.awt.geom.Line2D.Float(cx, cupTop + cupH / 2, cx, stemBottom));

        float baseW = s * 0.26f;
        g.draw(new RoundRectangle2D.Float(cx - baseW / 2, stemBottom, baseW, s * 0.09f, s * 0.03f, s * 0.03f));
    }

    /** A simple 5-point star, built from polar coordinates alternating outer/inner radius - same construction technique as the game logo's faceted-crystal mark. */
    private static void drawStar(Graphics2D g, int s)
    {
        float cx = s / 2f;
        float cy = s / 2f;
        float outerR = s * 0.42f;
        float innerR = outerR * 0.42f;
        int points = 5;

        java.awt.geom.GeneralPath star = new java.awt.geom.GeneralPath();
        for (int i = 0; i < points * 2; i++)
        {
            double angle = Math.toRadians(360.0 / (points * 2) * i - 90);
            float r = (i % 2 == 0) ? outerR : innerR;
            float x = (float) (cx + Math.cos(angle) * r);
            float y = (float) (cy + Math.sin(angle) * r);
            if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
        }
        star.closePath();
        g.draw(star);
    }

    /** A simple 4-to-1 bracket tree - two pairs of lines converging into one, representing a single-elimination bracket. */
    private static void drawBracket(Graphics2D g, int s)
    {
        float leftX = s * 0.18f;
        float midX = s * 0.5f;
        float rightX = s * 0.82f;

        float topY = s * 0.22f;
        float upperMidY = s * 0.38f;
        float lowerMidY = s * 0.62f;
        float bottomY = s * 0.78f;
        float centerY = s * 0.5f;

        g.draw(new Line2D.Float(leftX, topY, midX, topY));
        g.draw(new Line2D.Float(leftX, upperMidY, midX, upperMidY));
        g.draw(new Line2D.Float(midX, topY, midX, upperMidY));

        g.draw(new Line2D.Float(leftX, lowerMidY, midX, lowerMidY));
        g.draw(new Line2D.Float(leftX, bottomY, midX, bottomY));
        g.draw(new Line2D.Float(midX, lowerMidY, midX, bottomY));

        float midConvergeX = (midX + rightX) / 2f;
        g.draw(new Line2D.Float(midX, (topY + upperMidY) / 2f, midConvergeX, centerY));
        g.draw(new Line2D.Float(midX, (lowerMidY + bottomY) / 2f, midConvergeX, centerY));
        g.draw(new Line2D.Float(midConvergeX, centerY, rightX, centerY));
    }

    private static void drawGamepad(Graphics2D g, int s)
    {
        float w = s * 0.9f, h = s * 0.55f;
        float x = (s - w) / 2, y = (s - h) / 2;
        RoundRectangle2D body = new RoundRectangle2D.Float(x, y, w, h, h * 0.8f, h * 0.8f);
        g.draw(body);

        float dpadCx = x + w * 0.28f, dpadCy = y + h * 0.5f;
        float arm = h * 0.28f;
        g.draw(new Line2D.Float(dpadCx - arm, dpadCy, dpadCx + arm, dpadCy));
        g.draw(new Line2D.Float(dpadCx, dpadCy - arm, dpadCx, dpadCy + arm));

        float btnR = h * 0.14f;
        float bx = x + w * 0.72f, by = y + h * 0.5f;
        g.fill(new Ellipse2D.Float(bx - btnR, by - btnR - h * 0.16f, btnR * 2, btnR * 2));
        g.fill(new Ellipse2D.Float(bx - btnR - h * 0.16f, by - btnR + h * 0.05f, btnR * 2, btnR * 2));
    }

    private static void drawQuest(Graphics2D g, int s)
    {
        float poleX = s * 0.32f;
        g.draw(new Line2D.Float(poleX, s * 0.15f, poleX, s * 0.88f));

        GeneralPath flag = new GeneralPath();
        flag.moveTo(poleX, s * 0.18f);
        flag.lineTo(s * 0.78f, s * 0.30f);
        flag.lineTo(poleX, s * 0.46f);
        flag.closePath();
        g.fill(flag);
    }

    private static void drawFriends(Graphics2D g, int s)
    {
        float headR = s * 0.15f;

        float leftCx = s * 0.36f, leftHeadCy = s * 0.32f;
        g.draw(new Ellipse2D.Float(leftCx - headR, leftHeadCy - headR, headR * 2, headR * 2));
        g.draw(new Arc2D.Float(s * 0.14f, s * 0.5f, s * 0.44f, s * 0.5f, 20, 140, Arc2D.OPEN));

        float rightCx = s * 0.68f, rightHeadCy = s * 0.4f;
        float rightHeadR = headR * 0.85f;
        g.draw(new Ellipse2D.Float(rightCx - rightHeadR, rightHeadCy - rightHeadR, rightHeadR * 2, rightHeadR * 2));
        g.draw(new Arc2D.Float(s * 0.5f, s * 0.56f, s * 0.42f, s * 0.42f, 20, 140, Arc2D.OPEN));
    }

    private static void drawChat(Graphics2D g, int s)
    {
        float w = s * 0.82f, h = s * 0.6f;
        float x = (s - w) / 2, y = s * 0.16f;
        RoundRectangle2D bubble = new RoundRectangle2D.Float(x, y, w, h, h * 0.5f, h * 0.5f);
        g.draw(bubble);

        GeneralPath tail = new GeneralPath();
        tail.moveTo(x + w * 0.25f, y + h);
        tail.lineTo(x + w * 0.18f, y + h + s * 0.16f);
        tail.lineTo(x + w * 0.45f, y + h);
        tail.closePath();
        g.fill(tail);
    }

    private static void drawShop(Graphics2D g, int s)
    {
        float w = s * 0.66f, h = s * 0.56f;
        float x = (s - w) / 2, y = s * 0.36f;
        g.draw(new RoundRectangle2D.Float(x, y, w, h, s * 0.08f, s * 0.08f));

        float handleR = w * 0.28f;
        g.draw(new Arc2D.Float(x + w / 2 - handleR, y - handleR * 1.2f, handleR * 2, handleR * 2, 0, 180, Arc2D.OPEN));
    }

    private static void drawProfile(Graphics2D g, int s)
    {
        float headR = s * 0.19f;
        g.draw(new Ellipse2D.Float(s / 2f - headR, s * 0.16f, headR * 2, headR * 2));

        Arc2D shoulders = new Arc2D.Float(s * 0.16f, s * 0.5f, s * 0.68f, s * 0.6f, 20, 140, Arc2D.OPEN);
        g.draw(shoulders);
    }

    private static void drawSettings(Graphics2D g, int s)
    {
        float cx = s / 2f, cy = s / 2f;
        float r = s * 0.22f;
        g.draw(new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2));

        int teeth = 6;
        float toothLen = s * 0.14f;
        for (int i = 0; i < teeth; i++)
        {
            double angle = Math.toRadians(360.0 / teeth * i);
            float x1 = (float) (cx + Math.cos(angle) * r);
            float y1 = (float) (cy + Math.sin(angle) * r);
            float x2 = (float) (cx + Math.cos(angle) * (r + toothLen));
            float y2 = (float) (cy + Math.sin(angle) * (r + toothLen));
            g.draw(new Line2D.Float(x1, y1, x2, y2));
        }
    }

    private static void drawShield(Graphics2D g, int s)
    {
        GeneralPath shield = new GeneralPath();
        float x = s * 0.22f, top = s * 0.14f, w = s * 0.56f;
        shield.moveTo(x, top);
        shield.lineTo(x + w, top);
        shield.lineTo(x + w, s * 0.5f);
        shield.curveTo(x + w, s * 0.75f, x + w * 0.5f, s * 0.9f, x + w * 0.5f, s * 0.9f);
        shield.curveTo(x + w * 0.5f, s * 0.9f, x, s * 0.75f, x, s * 0.5f);
        shield.closePath();
        g.draw(shield);
    }
}
