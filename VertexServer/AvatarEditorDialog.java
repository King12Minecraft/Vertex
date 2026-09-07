import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * AvatarEditorDialog
 * -------------------
 * Two ways to set your avatar, one dialog: pick an image file, or
 * paint one by hand directly in the app. Either way the result gets
 * flattened to a 128x128 PNG before it's ever sent to the server -
 * AvatarStore only ever stores that one canonical size, it has no
 * idea (or need to know) which tab produced it.
 */
public class AvatarEditorDialog
{
    private static final int AVATAR_SIZE = 128;
    private static final int CANVAS_SIZE = 320;

    public interface SaveListener
    {
        void onSaved();
    }

    public static void show(Component anchor, final SaveListener onSaved)
    {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(anchor);
        final JDialog dialog = new JDialog(owner, "Edit Avatar", true);
        dialog.setResizable(false);

        RoundedPanel root = new RoundedPanel(ThemeColor.BG_PANEL, 16);
        root.setLayout(new BorderLayout());
        root.setBorder(new EmptyBorder(20, 20, 20, 20));
        dialog.setContentPane(root);

        final JTabbedPane tabs = new JTabbedPane();
        final UploadTab uploadTab = new UploadTab();
        final PaintTab paintTab = new PaintTab();
        tabs.addTab("Upload Image", uploadTab);
        tabs.addTab("Paint", paintTab);
        root.add(tabs, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(new EmptyBorder(14, 0, 0, 0));

        final JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(UITheme.FONT_SMALL);
        statusLabel.setForeground(new Color(240, 100, 100));

        ThemedButton cancel = new ThemedButton("Cancel", false);
        cancel.setPreferredSize(new Dimension(100, 38));
        cancel.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { dialog.dispose(); }
        });

        final ThemedButton save = new ThemedButton("Save Avatar", true);
        save.setPreferredSize(new Dimension(140, 38));
        save.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                BufferedImage image = tabs.getSelectedComponent() == uploadTab
                    ? uploadTab.getImage() : paintTab.getImage();
                if (image == null)
                {
                    statusLabel.setText("Choose an image or paint something first.");
                    return;
                }
                uploadAvatar(dialog, save, statusLabel, image, onSaved);
            }
        });

        buttonRow.add(statusLabel);
        buttonRow.add(cancel);
        buttonRow.add(save);
        root.add(buttonRow, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(anchor);
        dialog.setVisible(true);
    }

    private static void uploadAvatar(final JDialog dialog, final ThemedButton save, final JLabel statusLabel,
                                      BufferedImage image, final SaveListener onSaved)
    {
        final byte[] pngBytes = toPng(resizeTo(image, AVATAR_SIZE, AVATAR_SIZE));
        if (pngBytes == null)
        {
            statusLabel.setText("Could not process that image.");
            return;
        }

        save.setEnabled(false);
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.AVATAR_UPLOAD_REQUEST);
                request.setFileData(pngBytes);
                final Message response = NetworkManager.send(request);

                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        save.setEnabled(true);
                        if (response == null || !response.isSuccess())
                        {
                            statusLabel.setText(response != null && response.getErrorText() != null
                                ? response.getErrorText() : "Could not reach the server.");
                            return;
                        }
                        if (Session.isLoggedIn())
                        {
                            AvatarCache.invalidate(Session.getCurrentAccount().getUsername());
                        }
                        dialog.dispose();
                        if (onSaved != null) onSaved.onSaved();
                    }
                });
            }
        });
        worker.start();
    }

    private static BufferedImage resizeTo(BufferedImage source, int w, int h)
    {
        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resized.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(source, 0, 0, w, h, null);
        g2.dispose();
        return resized;
    }

    private static byte[] toPng(BufferedImage image)
    {
        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
        catch (IOException e)
        {
            return null;
        }
    }

    // ==================== Upload tab ====================

    private static class UploadTab extends JPanel
    {
        private BufferedImage chosenImage;
        private final PreviewPanel preview = new PreviewPanel();

        UploadTab()
        {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(16, 16, 16, 16));
            setOpaque(false);

            preview.setAlignmentX(Component.CENTER_ALIGNMENT);
            add(preview);
            add(Box.createVerticalStrut(14));

            ThemedButton choose = new ThemedButton("Choose Image...", false);
            choose.setAlignmentX(Component.CENTER_ALIGNMENT);
            choose.setPreferredSize(new Dimension(180, 38));
            choose.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e) { pickFile(); }
            });
            add(choose);
        }

        private void pickFile()
        {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Images", "png", "jpg", "jpeg", "gif", "bmp"));
            int result = chooser.showOpenDialog(this);
            if (result != JFileChooser.APPROVE_OPTION)
            {
                return;
            }
            File file = chooser.getSelectedFile();
            try
            {
                BufferedImage loaded = ImageIO.read(file);
                if (loaded != null)
                {
                    chosenImage = loaded;
                    preview.setImage(loaded);
                }
            }
            catch (IOException e)
            {
                GameHubDialog.show(this, "Avatar", "Could not open that file.");
            }
        }

        BufferedImage getImage() { return chosenImage; }
    }

    private static class PreviewPanel extends JPanel
    {
        private Image image;

        PreviewPanel()
        {
            setPreferredSize(new Dimension(AVATAR_SIZE * 2, AVATAR_SIZE * 2));
            setOpaque(false);
        }

        void setImage(Image image)
        {
            this.image = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(ThemeManager.getColor(ThemeColor.BG_APP));
            g2.fillOval(0, 0, getWidth(), getHeight());
            if (image != null)
            {
                g2.setClip(new java.awt.geom.Ellipse2D.Float(0, 0, getWidth(), getHeight()));
                g2.drawImage(image, 0, 0, getWidth(), getHeight(), null);
            }
            else
            {
                g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
                g2.setFont(UITheme.FONT_SMALL);
                g2.drawString("No image", getWidth() / 2 - 28, getHeight() / 2);
            }
            g2.setColor(ThemeManager.getColor(ThemeColor.BORDER));
            g2.setStroke(new BasicStroke(2));
            g2.drawOval(1, 1, getWidth() - 3, getHeight() - 3);
            g2.dispose();
        }
    }

    // ==================== Paint tab ====================

    private static class PaintTab extends JPanel
    {
        private final BufferedImage canvasImage = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
        private Color currentColor = Color.BLACK;
        private int brushSize = 8;
        private boolean hasPainted = false;

        PaintTab()
        {
            setLayout(new BorderLayout(12, 0));
            setBorder(new EmptyBorder(16, 16, 16, 16));
            setOpaque(false);
            clearCanvas();

            final CanvasPanel canvas = new CanvasPanel();
            canvas.setPreferredSize(new Dimension(CANVAS_SIZE, CANVAS_SIZE));
            add(canvas, BorderLayout.CENTER);
            add(buildToolPanel(canvas), BorderLayout.EAST);
        }

        private void clearCanvas()
        {
            Graphics2D g2 = canvasImage.createGraphics();
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
            g2.dispose();
            hasPainted = false;
        }

        private JPanel buildToolPanel(final CanvasPanel canvas)
        {
            JPanel panel = new JPanel();
            panel.setOpaque(false);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setPreferredSize(new Dimension(130, CANVAS_SIZE));

            JLabel colorsLabel = new JLabel("Colors");
            colorsLabel.setFont(UITheme.FONT_SMALL);
            colorsLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            colorsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(colorsLabel);
            panel.add(Box.createVerticalStrut(6));

            Color[] swatches = {
                Color.BLACK, Color.WHITE, new Color(230, 90, 90), new Color(90, 170, 230),
                new Color(90, 210, 120), new Color(230, 200, 60), new Color(200, 90, 200),
                new Color(230, 150, 60), new Color(120, 90, 60), new Color(150, 150, 150)
            };
            JPanel swatchGrid = new JPanel(new GridLayout(2, 5, 4, 4));
            swatchGrid.setOpaque(false);
            swatchGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
            swatchGrid.setMaximumSize(new Dimension(130, 50));
            for (final Color color : swatches)
            {
                JPanel swatch = new JPanel();
                swatch.setBackground(color);
                swatch.setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER)));
                swatch.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                swatch.addMouseListener(new MouseAdapter()
                {
                    public void mouseClicked(MouseEvent e) { currentColor = color; }
                });
                swatchGrid.add(swatch);
            }
            panel.add(swatchGrid);
            panel.add(Box.createVerticalStrut(10));

            ThemedButton customColor = new ThemedButton("Custom...", false);
            customColor.setAlignmentX(Component.LEFT_ALIGNMENT);
            customColor.setMaximumSize(new Dimension(130, 32));
            customColor.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    Color picked = JColorChooser.showDialog(panel, "Pick a color", currentColor);
                    if (picked != null) currentColor = picked;
                }
            });
            panel.add(customColor);
            panel.add(Box.createVerticalStrut(16));

            JLabel brushLabel = new JLabel("Brush Size");
            brushLabel.setFont(UITheme.FONT_SMALL);
            brushLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
            brushLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(brushLabel);

            final javax.swing.JSlider slider = new javax.swing.JSlider(2, 24, brushSize);
            slider.setOpaque(false);
            slider.setAlignmentX(Component.LEFT_ALIGNMENT);
            slider.setMaximumSize(new Dimension(130, 40));
            slider.addChangeListener(new javax.swing.event.ChangeListener()
            {
                public void stateChanged(javax.swing.event.ChangeEvent e) { brushSize = slider.getValue(); }
            });
            panel.add(slider);
            panel.add(Box.createVerticalStrut(16));

            ThemedButton clear = new ThemedButton("Clear", false);
            clear.setAlignmentX(Component.LEFT_ALIGNMENT);
            clear.setMaximumSize(new Dimension(130, 32));
            clear.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    clearCanvas();
                    canvas.repaint();
                }
            });
            panel.add(clear);

            return panel;
        }

        BufferedImage getImage() { return hasPainted ? canvasImage : null; }

        private class CanvasPanel extends JPanel
        {
            private int lastX = -1, lastY = -1;

            CanvasPanel()
            {
                setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.CROSSHAIR_CURSOR));
                setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER)));

                MouseAdapter drag = new MouseAdapter()
                {
                    public void mousePressed(MouseEvent e) { paintAt(e.getX(), e.getY()); }
                    public void mouseReleased(MouseEvent e) { lastX = -1; lastY = -1; }
                };
                addMouseListener(drag);
                addMouseMotionListener(new MouseMotionAdapter()
                {
                    public void mouseDragged(MouseEvent e) { paintAt(e.getX(), e.getY()); }
                });
            }

            private void paintAt(int x, int y)
            {
                Graphics2D g2 = canvasImage.createGraphics();
                g2.setColor(currentColor);
                g2.setStroke(new BasicStroke(brushSize, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (lastX >= 0)
                {
                    g2.drawLine(lastX, lastY, x, y);
                }
                else
                {
                    g2.fillOval(x - brushSize / 2, y - brushSize / 2, brushSize, brushSize);
                }
                g2.dispose();
                lastX = x;
                lastY = y;
                hasPainted = true;
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                g.drawImage(canvasImage, 0, 0, null);
            }
        }
    }
}
