import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * FightArenaPanel
 * ---------------
 * Pure renderer for FightMatch's tick snapshots - no local physics of
 * its own. Every repaint just draws whatever the most recent
 * FIGHT_TICK_UPDATE said. Key handling tracks held-key state and sends
 * an update to the server only when that combined state actually
 * changes (not every frame), matching the "input changes, not
 * continuous polling" approach used for Pong's paddle.
 */
public class FightArenaPanel extends JPanel
{
    private static final int WIDTH = 760;
    private static final int HEIGHT = 320;
    private static final double ARENA_WIDTH = 800.0;
    private static final int PLAYER_SIZE = 40;

    private static final Color[] TEAM_COLORS = {
        new Color(90, 160, 240),
        new Color(240, 100, 100),
        new Color(120, 210, 120),
        new Color(230, 190, 70),
        new Color(200, 120, 230),
        new Color(240, 150, 90),
        new Color(100, 220, 210),
        new Color(220, 100, 180)
    };

    private final java.util.Map<String, Integer> teamByUsername = new java.util.HashMap<String, Integer>();
    private List<String[]> latestTickRows = new ArrayList<String[]>();
    private String myUsername;

    private boolean movingLeft;
    private boolean movingRight;
    private boolean attacking;
    private final InputListener inputListener;

    public interface InputListener
    {
        void onInputChanged(boolean left, boolean right, boolean attack);
    }

    public FightArenaPanel(InputListener inputListener)
    {
        this.inputListener = inputListener;
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);

        addKeyListener(new KeyAdapter()
        {
            public void keyPressed(KeyEvent e) { handleKey(e.getKeyCode(), true); }
            public void keyReleased(KeyEvent e) { handleKey(e.getKeyCode(), false); }
        });

        ThemeManager.addListener(new Runnable()
        {
            public void run() { repaint(); }
        });
    }

    private void handleKey(int keyCode, boolean pressed)
    {
        boolean changed = false;
        if (keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_A)
        {
            if (movingLeft != pressed) { movingLeft = pressed; changed = true; }
        }
        else if (keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_D)
        {
            if (movingRight != pressed) { movingRight = pressed; changed = true; }
        }
        else if (keyCode == KeyEvent.VK_SPACE)
        {
            if (attacking != pressed) { attacking = pressed; changed = true; }
        }

        if (changed)
        {
            inputListener.onInputChanged(movingLeft, movingRight, attacking);
        }
    }

    public void setMyUsername(String username)
    {
        this.myUsername = username;
    }

    public void setTeamAssignments(List<String> assignments)
    {
        teamByUsername.clear();
        if (assignments == null) return;
        for (int i = 0; i < assignments.size(); i++)
        {
            String[] parts = assignments.get(i).split("\\|", 2);
            if (parts.length == 2)
            {
                try
                {
                    teamByUsername.put(parts[0], Integer.parseInt(parts[1]));
                }
                catch (NumberFormatException ignored) { }
            }
        }
    }

    public void applyTick(List<String> tickData)
    {
        List<String[]> rows = new ArrayList<String[]>();
        if (tickData != null)
        {
            for (int i = 0; i < tickData.size(); i++)
            {
                rows.add(tickData.get(i).split("\\|"));
            }
        }
        latestTickRows = rows;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        UITheme.applyAntialiasing(g2);

        int w = getWidth();
        int h = getHeight();
        double scaleX = w / ARENA_WIDTH;

        g2.setColor(ThemeManager.getColor(ThemeColor.BG_SIDEBAR));
        g2.fillRect(0, 0, w, h);

        int groundY = h - 40;
        g2.setColor(ThemeManager.getColor(ThemeColor.BORDER));
        g2.drawLine(0, groundY, w, groundY);

        for (int i = 0; i < latestTickRows.size(); i++)
        {
            String[] row = latestTickRows.get(i);
            if (row.length < 6) continue;

            String username = row[0];
            double x = parseDouble(row[1]);
            int health = (int) parseDouble(row[2]);
            boolean facingRight = "1".equals(row[3]);
            boolean attackFlash = "1".equals(row[4]);
            boolean alive = "1".equals(row[5]);

            if (!alive) continue;

            int screenX = (int) (x * scaleX);
            Integer team = teamByUsername.get(username);
            Color color = TEAM_COLORS[team != null ? team % TEAM_COLORS.length : 0];

            g2.setColor(color);
            g2.fillRoundRect(screenX - PLAYER_SIZE / 2, groundY - PLAYER_SIZE, PLAYER_SIZE, PLAYER_SIZE, 8, 8);

            if (attackFlash)
            {
                g2.setColor(Color.WHITE);
                int flashX = facingRight ? screenX + PLAYER_SIZE / 2 : screenX - PLAYER_SIZE / 2 - 16;
                g2.fillOval(flashX, groundY - PLAYER_SIZE / 2 - 6, 16, 12);
            }

            g2.setColor(new Color(60, 60, 60));
            g2.fillRect(screenX - 22, groundY - PLAYER_SIZE - 14, 44, 6);
            g2.setColor(health > 40 ? new Color(90, 210, 120) : new Color(230, 90, 90));
            g2.fillRect(screenX - 22, groundY - PLAYER_SIZE - 14, (int) (44 * (health / 100.0)), 6);

            g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
            g2.setFont(UITheme.FONT_SMALL);
            boolean isMe = username.equals(myUsername);
            g2.drawString(isMe ? username + " (you)" : username, screenX - 30, groundY - PLAYER_SIZE - 18);
        }

        g2.dispose();
    }

    private double parseDouble(String s)
    {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0; }
    }
}
