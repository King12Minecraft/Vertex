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
import java.util.List;

/**
 * ReplayWindow
 * ------------
 * Steps through a finished Chess match's saved board snapshots with
 * Prev/Next - purely local once the snapshot list arrives, no network
 * involvement during stepping. Reuses the same piece-glyph rendering
 * ChessWindow uses, just without any click-to-move interaction.
 */
public class ReplayWindow extends JFrame
{
    private final JButton[] cells = new JButton[64];
    private final JLabel stepLabel;
    private final List<String> snapshots;
    private int currentIndex = 0;

    public ReplayWindow(List<String> snapshots, String player1, String player2)
    {
        super("Vertex - Replay: " + player1 + " vs " + player2);
        this.snapshots = snapshots;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_APP, 0);
        root.setLayout(new BorderLayout());
        root.setBorder(new EmptyBorder(16, 16, 16, 16));

        stepLabel = new JLabel();
        stepLabel.setFont(UITheme.FONT_NAV_BOLD);
        stepLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        stepLabel.setBorder(new EmptyBorder(0, 4, 10, 4));
        root.add(stepLabel, BorderLayout.NORTH);

        JPanel board = new JPanel(new GridLayout(8, 8));
        board.setPreferredSize(new Dimension(400, 400));
        for (int row = 7; row >= 0; row--)
        {
            for (int col = 0; col < 8; col++)
            {
                int square = row * 8 + col;
                JButton cell = new JButton();
                cell.setFont(new Font("Serif", Font.PLAIN, 30));
                cell.setFocusPainted(false);
                cell.setBorderPainted(false);
                cell.setEnabled(false);
                cells[square] = cell;
                board.add(cell);
            }
        }
        root.add(board, BorderLayout.CENTER);

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

    private void step(int delta)
    {
        int next = currentIndex + delta;
        if (next < 0 || next >= snapshots.size())
        {
            return;
        }
        currentIndex = next;
        renderCurrent();
    }

    private void renderCurrent()
    {
        String board = snapshots.get(currentIndex);
        for (int square = 0; square < 64; square++)
        {
            int row = square / 8;
            int col = square % 8;
            char piece = board.charAt(square);
            cells[square].setText(piece == '.' ? "" : String.valueOf(glyphFor(piece)));
            cells[square].setForeground(Character.isUpperCase(piece) ? Color.WHITE : Color.BLACK);
            cells[square].setBackground(((row + col) % 2 == 0) ? new Color(90, 70, 60) : new Color(210, 195, 170));
        }
        stepLabel.setText("Move " + currentIndex + " of " + (snapshots.size() - 1));
    }

    private char glyphFor(char piece)
    {
        switch (Character.toUpperCase(piece))
        {
            case 'K': return Character.isUpperCase(piece) ? '\u2654' : '\u265A';
            case 'Q': return Character.isUpperCase(piece) ? '\u2655' : '\u265B';
            case 'R': return Character.isUpperCase(piece) ? '\u2656' : '\u265C';
            case 'B': return Character.isUpperCase(piece) ? '\u2657' : '\u265D';
            case 'N': return Character.isUpperCase(piece) ? '\u2658' : '\u265E';
            case 'P': return Character.isUpperCase(piece) ? '\u2659' : '\u265F';
            default: return ' ';
        }
    }
}
