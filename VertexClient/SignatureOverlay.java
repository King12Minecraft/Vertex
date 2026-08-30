import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.BoxLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * SignatureOverlay
 * ----------------
 * A small "By Benjamin Bipin George" / "Made by Claude" mark, bottom-
 * right on every window. Attaches to the frame's layered pane (floats
 * above the normal content, doesn't take a layout slot from anything
 * else), and repositions itself on resize. Idempotent - safe to call
 * more than once on the same frame (several windows call pack() again
 * during mode/state transitions) - a client property on the root pane
 * marks a frame as already signed, so a second call is a no-op rather
 * than stacking a duplicate label.
 */
public class SignatureOverlay
{
    private static final String ATTACHED_KEY = "signatureOverlay.attached";

    private SignatureOverlay()
    {
        // Static utility class - never instantiated.
    }

    public static void attach(final JFrame frame)
    {
        if (Boolean.TRUE.equals(frame.getRootPane().getClientProperty(ATTACHED_KEY)))
        {
            return;
        }
        frame.getRootPane().putClientProperty(ATTACHED_KEY, Boolean.TRUE);

        final JPanel sigPanel = new JPanel();
        sigPanel.setOpaque(false);
        sigPanel.setLayout(new BoxLayout(sigPanel, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel("By Benjamin Bipin George");
        nameLabel.setFont(UITheme.FONT_SMALL.deriveFont(9f));
        nameLabel.setForeground(new java.awt.Color(255, 255, 255, 110));
        nameLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel madeByLabel = new JLabel("Made by Claude");
        madeByLabel.setFont(UITheme.FONT_SMALL.deriveFont(Font.ITALIC, 8f));
        madeByLabel.setForeground(new java.awt.Color(255, 255, 255, 90));
        madeByLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        sigPanel.add(nameLabel);
        sigPanel.add(madeByLabel);

        final JLayeredPane layeredPane = frame.getLayeredPane();
        layeredPane.add(sigPanel, JLayeredPane.PALETTE_LAYER);

        final Runnable reposition = new Runnable()
        {
            public void run()
            {
                Dimension size = sigPanel.getPreferredSize();
                Dimension paneSize = layeredPane.getSize();
                sigPanel.setBounds(Math.max(0, paneSize.width - size.width - 10),
                    Math.max(0, paneSize.height - size.height - 6), size.width, size.height);
            }
        };

        // Deferred via invokeLater rather than run immediately: at the point attach()
        // is called, the caller's own layout (sidebar/content/etc.) may not have been
        // built or validated yet, so getSize() here could still report (0,0) - which
        // would pin this at the top-left corner, on top of everything else, instead of
        // bottom-right. Queuing the first positioning to run after the current
        // construction/layout pass finishes avoids that regardless of exactly where in
        // a window's constructor attach() happens to be called.
        javax.swing.SwingUtilities.invokeLater(reposition);

        frame.addComponentListener(new ComponentAdapter()
        {
            public void componentResized(ComponentEvent e) { reposition.run(); }
        });
    }
}
