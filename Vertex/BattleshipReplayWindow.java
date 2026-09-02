import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * BattleshipReplayWindow
 * -----------------------
 * Steps through a finished Battleship match's shot history. Unlike
 * Chess (one board snapshot per move already computed server-side),
 * Battleship's replay data is two static fleet layouts plus a shot
 * log ("shooter:cellIndex:result" per entry) - this viewer
 * reconstructs each step by resetting both grids to their base fleet
 * layout and replaying every shot from 0 up to the current step. That
 * full-rebuild-per-step approach is cheap here (a match is at most a
 * few dozen shots) and avoids needing incremental undo logic for
 * stepping backward.
 *
 * The data doesn't label which fleet belongs to which named player -
 * only the shot log's shooter names are real usernames - so the two
 * fleets are shown as generic "Fleet 1"/"Fleet 2", matching the same
 * neutral-labeling approach RPSReplayWindow uses.
 */
public class BattleshipReplayWindow extends JFrame
{
    private static final int SIZE = 10;

    private final JButton[] fleet1Cells = new JButton[SIZE * SIZE];
    private final JButton[] fleet2Cells = new JButton[SIZE * SIZE];
    private final JLabel stepLabel;

    private final String fleet1Layout;
    private final String fleet2Layout;
    private final List<String[]> shots = new ArrayList<String[]>();
    private String player1Name;
    private String player2Name;
    private int currentIndex;

    public BattleshipReplayWindow(List<String> data)
    {
        super("Vertex - Replay: Battleship");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        fleet1Layout = data.size() > 0 ? data.get(0) : "";
        fleet2Layout = data.size() > 1 ? data.get(1) : "";
        for (int i = 2; i < data.size(); i++)
        {
            String[] parts = data.get(i).split(":", -1);
            if (parts.length >= 3)
            {
                shots.add(parts);
                if (player1Name == null)
                {
                    player1Name = parts[0];
                }
                else if (player2Name == null && !parts[0].equals(player1Name))
                {
                    player2Name = parts[0];
                }
            }
        }
        currentIndex = shots.size();

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_APP, 0);
        root.setLayout(new BorderLayout());
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        stepLabel = new JLabel();
        stepLabel.setFont(UITheme.FONT_NAV_BOLD);
        stepLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        stepLabel.setBorder(new EmptyBorder(0, 4, 12, 4));
        root.add(stepLabel, BorderLayout.NORTH);

        JPanel gridsRow = new JPanel(new GridLayout(1, 2, 20, 0));
        gridsRow.setOpaque(false);
        gridsRow.add(buildGridColumn("Fleet 1", fleet1Cells));
        gridsRow.add(buildGridColumn("Fleet 2", fleet2Cells));
        root.add(gridsRow, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
        controls.setOpaque(false);

        ThemedButton prev = new ThemedButton("< Prev", false);
        prev.setPreferredSize(new Dimension(90, 36));
        prev.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { step(-1); }
        });
        controls.add(prev);

        ThemedButton next = new ThemedButton("Next >", true);
        next.setPreferredSize(new Dimension(90, 36));
        next.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { step(1); }
        });
        controls.add(next);

        root.add(controls, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setLocationRelativeTo(null);
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);

        renderCurrent();
    }

    private JPanel buildGridColumn(String title, JButton[] cells)
    {
        JPanel column = new JPanel(new BorderLayout());
        column.setOpaque(false);

        JLabel label = new JLabel(title);
        label.setFont(UITheme.FONT_NAV_BOLD);
        label.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        label.setBorder(new EmptyBorder(0, 0, 8, 0));
        column.add(label, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(SIZE, SIZE, 1, 1));
        grid.setPreferredSize(new Dimension(280, 280));
        for (int i = 0; i < SIZE * SIZE; i++)
        {
            JButton cell = new JButton();
            cell.setFont(new Font("SansSerif", Font.BOLD, 11));
            cell.setFocusPainted(false);
            cell.setBorderPainted(false);
            cell.setEnabled(false);
            cells[i] = cell;
            grid.add(cell);
        }
        column.add(grid, BorderLayout.CENTER);
        return column;
    }

    private void step(int delta)
    {
        int next = currentIndex + delta;
        if (next < 0 || next > shots.size())
        {
            return;
        }
        currentIndex = next;
        renderCurrent();
    }

    private void renderCurrent()
    {
        resetGrid(fleet1Cells, fleet1Layout);
        resetGrid(fleet2Cells, fleet2Layout);

        for (int i = 0; i < currentIndex; i++)
        {
            String[] shot = shots.get(i);
            String shooter = shot[0];
            int cellIndex = Integer.parseInt(shot[1]);
            String result = shot[2];
            // A shot always lands on the fleet opposite whoever fired.
            JButton[] targetGrid = shooter.equals(player1Name) ? fleet2Cells : fleet1Cells;
            markCell(targetGrid[cellIndex], result);
        }

        if (currentIndex == 0)
        {
            stepLabel.setText("Start of match (" + shots.size() + " shots total)");
        }
        else
        {
            String[] last = shots.get(currentIndex - 1);
            stepLabel.setText("Shot " + currentIndex + " of " + shots.size() + ":  "
                + last[0] + " - " + last[2]);
        }
    }

    private void resetGrid(JButton[] cells, String layout)
    {
        for (int i = 0; i < SIZE * SIZE; i++)
        {
            cells[i].setText("");
            char c = i < layout.length() ? layout.charAt(i) : '.';
            cells[i].setBackground(c == '.' ? new Color(60, 100, 150) : new Color(150, 150, 150));
        }
    }

    private void markCell(JButton cell, String result)
    {
        if ("MISS".equals(result))
        {
            cell.setBackground(new Color(40, 70, 110));
            cell.setForeground(Color.WHITE);
            cell.setText("\u2022");
        }
        else
        {
            cell.setBackground(new Color(210, 80, 70));
            cell.setForeground(Color.WHITE);
            cell.setText("X");
        }
    }
}
