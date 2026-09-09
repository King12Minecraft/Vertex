package economy;
import net.NetworkManager;
import net.MessageType;
import net.Message;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Timer;

/**
 * AvatarFrameRegistry
 * --------------------
 * Client-side cache of the shop's FRAME items (same fetch/cache
 * pattern as PlayerColorRegistry, sharing the same ShopItemInfo list -
 * a frame just uses its colorHex as the ring's base color instead of
 * a username color). Also owns a single shared ~20fps Timer that
 * every avatar-with-a-frame component subscribes to for repaint
 * calls, so each frame's animation doesn't need its own timer.
 */
public class AvatarFrameRegistry
{
    private static List<ShopItemInfo> items = new ArrayList<ShopItemInfo>();
    private static final List<Runnable> animationListeners = new ArrayList<Runnable>();
    private static Timer animationTimer;

    private AvatarFrameRegistry()
    {
        // Static utility class - never instantiated.
    }

    public static void setItems(List<ShopItemInfo> newItems)
    {
        if (newItems != null)
        {
            items = newItems;
        }
    }

    /** Null for "no frame"/unknown - components should just skip drawing anything in that case. */
    public static String resolveColorHex(String frameId)
    {
        if (frameId == null || frameId.isEmpty())
        {
            return null;
        }
        for (int i = 0; i < items.size(); i++)
        {
            if (items.get(i).getId().equals(frameId) && "FRAME".equals(items.get(i).getType()))
            {
                return items.get(i).getColorHex();
            }
        }
        return null;
    }

    /** Call once per component that draws an animated frame - adds it to the shared repaint tick, starting the tick itself on first use. */
    public static void addAnimationListener(Runnable listener)
    {
        animationListeners.add(listener);
        if (animationTimer == null)
        {
            animationTimer = new Timer(50, new java.awt.event.ActionListener()
            {
                public void actionPerformed(java.awt.event.ActionEvent e)
                {
                    for (int i = 0; i < animationListeners.size(); i++)
                    {
                        animationListeners.get(i).run();
                    }
                }
            });
            animationTimer.start();
        }
    }

    public static void removeAnimationListener(Runnable listener)
    {
        animationListeners.remove(listener);
    }

    /** Draws the animated ring around a size x size avatar area at (x, y), or nothing if frameId doesn't resolve to a known frame. "pulse" frames breathe in opacity; "rainbow" cycles hue; anything else (a future static frame type) just draws a solid ring in its color. */
    public static void paintFrame(Graphics2D g2, int x, int y, int size, String frameId)
    {
        String hex = resolveColorHex(frameId);
        if (hex == null)
        {
            return;
        }

        long now = System.currentTimeMillis();
        Color ringColor;
        float strokeWidth;

        if ("frame-rainbow".equals(frameId))
        {
            float hue = (now % 3000) / 3000f;
            ringColor = Color.getHSBColor(hue, 0.75f, 1f);
            strokeWidth = 3f;
        }
        else
        {
            Color base;
            try
            {
                base = Color.decode(hex);
            }
            catch (NumberFormatException e)
            {
                return;
            }
            double phase = (Math.sin(now / 400.0) + 1) / 2.0; // 0..1 breathing pulse
            int alpha = (int) (140 + phase * 115);
            ringColor = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
            strokeWidth = (float) (2.5 + phase * 1.5);
        }

        Graphics2D ring = (Graphics2D) g2.create();
        ring.setColor(ringColor);
        ring.setStroke(new BasicStroke(strokeWidth));
        int pad = 2;
        ring.drawOval(x - pad, y - pad, size + pad * 2, size + pad * 2);
        ring.dispose();
    }

    public static void fetchInBackground()
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.SHOP_ITEMS_REQUEST);
                Message response = NetworkManager.send(request);
                if (response != null && response.isSuccess())
                {
                    setItems(response.getShopItems());
                }
            }
        });
        worker.start();
    }
}
