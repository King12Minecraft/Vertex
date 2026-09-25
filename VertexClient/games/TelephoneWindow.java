package games;
import ui.GameHubDialog;
import ui.ThemedScrollBarUI;
import net.MessageType;
import ui.ThemedButton;
import ui.ThemedTextArea;
import theme.ThemeManager;
import theme.UITheme;
import theme.ThemeColor;
import ui.RoundedPanel;
import net.NetworkManager;
import pages.MainMenu;
import net.Message;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * TelephoneWindow
 * ----------------
 * Gartic-Phone-style draw/guess chain - see TelephoneMatch (server) for the
 * full round-rotation design. Four phases: waiting in queue, a round screen
 * that swaps between "write a phrase" and "draw this phrase" depending on
 * what the server says this round is, a short "waiting on other players"
 * screen between rounds, and the final reveal (step through every chain,
 * one entry at a time, with a Next button - the actual payoff of the game).
 * No scoring, no winner - purely for fun, same spirit as the game itself.
 */
public class TelephoneWindow extends JPanel implements NetworkManager.PushListener, EmbeddedGamePanel
{
    private static final String SEARCHING = "SEARCHING";
    private static final String TEXT_ROUND = "TEXT_ROUND";
    private static final String DRAW_ROUND = "DRAW_ROUND";
    private static final String WAITING = "WAITING";
    private static final String REVEAL = "REVEAL";

    private static final int CANVAS_SIZE = 380;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;

    private JLabel textRoundPromptLabel;
    private ThemedTextArea textRoundInput;
    private JLabel textRoundTimerLabel;
    private ThemedButton textRoundSubmit;

    private JLabel drawRoundPromptLabel;
    private DrawingCanvas drawingCanvas;
    private JLabel drawRoundTimerLabel;
    private ThemedButton drawRoundSubmit;

    private JLabel waitingLabel;

    private JLabel revealHeaderLabel;
    private JLabel revealAuthorLabel;
    private RevealDisplay revealDisplay;
    private ThemedButton revealNextButton;

    private String matchId;
    private boolean submittedThisRound = false;
    private Timer countdownTimer;
    private long roundDeadline;

    private final List<RevealEntry> revealEntries = new ArrayList<RevealEntry>();
    private int revealPosition = -1;

    private static class RevealEntry
    {
        int chainIndex, entryIndex, entryCount;
        String author;
        boolean isDrawing;
        String text;
        byte[] image;
    }

    public TelephoneWindow()
    {
        setLayout(new BorderLayout());

        cards.add(createSearchingScreen(), SEARCHING);
        cards.add(createTextRoundScreen(), TEXT_ROUND);
        cards.add(createDrawRoundScreen(), DRAW_ROUND);
        cards.add(createWaitingScreen(), WAITING);
        cards.add(createRevealScreen(), REVEAL);

        add(cards, BorderLayout.CENTER);
        cardLayout.show(cards, SEARCHING);

        NetworkManager.addPushListener(this);
        findMatch();
    }

    @Override
    public boolean requestLeave()
    {
        if (matchId == null)
        {
            leaveQueue();
        }
        stopCountdown();
        NetworkManager.removePushListener(this);
        return true;
    }

    // ==================== Searching ====================

    private JPanel createSearchingScreen()
    {
        RoundedPanel wrapper = new RoundedPanel(ThemeColor.BG_APP, 0);
        wrapper.setLayout(new GridBagLayout());

        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        panel.setPreferredSize(new Dimension(420, 240));
        wrapper.add(panel, new GridBagConstraints());

        JLabel title = new JLabel("Telephone");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        searchingLabel = new JLabel("Waiting for more players... (need 4 to start)");
        searchingLabel.setFont(UITheme.FONT_BODY);
        searchingLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        searchingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchingLabel.setBorder(new EmptyBorder(10, 0, 20, 0));
        panel.add(searchingLabel);

        ThemedButton cancel = new ThemedButton("Cancel", false);
        cancel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cancel.setPreferredSize(new Dimension(120, 36));
        cancel.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                leaveQueue();
                MainMenu.getInstance().returnToGames();
            }
        });
        panel.add(cancel);

        return wrapper;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.TELEPHONE_FIND_MATCH_REQUEST);
        NetworkManager.sendAsync(request);
        String connectionIssue = NetworkManager.describeIfNotReady();
        if (connectionIssue != null)
        {
            searchingLabel.setText(connectionIssue);
        }
    }

    private void leaveQueue()
    {
        Message request = new Message();
        request.setType(MessageType.TELEPHONE_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    // ==================== Text round (write a starting phrase, or guess a drawing) ====================

    private JPanel createTextRoundScreen()
    {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(24, 24, 24, 24));
        content.setPreferredSize(new Dimension(460, 620));

        JLabel title = new JLabel("Telephone");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        content.add(title);

        textRoundTimerLabel = new JLabel(" ");
        textRoundTimerLabel.setFont(UITheme.FONT_SMALL);
        textRoundTimerLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        textRoundTimerLabel.setBorder(new EmptyBorder(4, 0, 16, 0));
        content.add(textRoundTimerLabel);

        textRoundPromptLabel = new JLabel("Write a phrase to kick off your chain:");
        textRoundPromptLabel.setFont(UITheme.FONT_BODY);
        textRoundPromptLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        content.add(textRoundPromptLabel);
        content.add(Box.createVerticalStrut(10));

        textRoundInput = new ThemedTextArea("Type here...", 3);
        textRoundInput.setAlignmentX(Component.LEFT_ALIGNMENT);
        textRoundInput.setMaximumSize(new Dimension(2000, 100));
        content.add(textRoundInput);
        content.add(Box.createVerticalStrut(16));

        textRoundSubmit = new ThemedButton("Submit", true);
        textRoundSubmit.setAlignmentX(Component.LEFT_ALIGNMENT);
        textRoundSubmit.setMaximumSize(new Dimension(140, 38));
        textRoundSubmit.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                String text = textRoundInput.getValue();
                if (text.isEmpty())
                {
                    GameHubDialog.show(TelephoneWindow.this, "Telephone", "Write something first.");
                    return;
                }
                submitText(text);
                textRoundSubmit.setEnabled(false);
                showWaiting();
            }
        });
        content.add(textRoundSubmit);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ThemedScrollBarUI.apply(scroll);

        RoundedPanel wrapper = new RoundedPanel(ThemeColor.BG_APP, 0);
        wrapper.setLayout(new GridBagLayout());
        wrapper.add(scroll, new GridBagConstraints());
        return wrapper;
    }

    // ==================== Draw round ====================

    private JPanel createDrawRoundScreen()
    {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(24, 24, 24, 24));

        JLabel title = new JLabel("Telephone");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        content.add(title);

        drawRoundTimerLabel = new JLabel(" ");
        drawRoundTimerLabel.setFont(UITheme.FONT_SMALL);
        drawRoundTimerLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        drawRoundTimerLabel.setBorder(new EmptyBorder(4, 0, 10, 0));
        content.add(drawRoundTimerLabel);

        drawRoundPromptLabel = new JLabel("Draw: something");
        drawRoundPromptLabel.setFont(UITheme.FONT_NAV_BOLD);
        drawRoundPromptLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        drawRoundPromptLabel.setBorder(new EmptyBorder(0, 0, 14, 0));
        content.add(drawRoundPromptLabel);

        JPanel canvasRow = new JPanel();
        canvasRow.setLayout(new BoxLayout(canvasRow, BoxLayout.X_AXIS));
        canvasRow.setOpaque(false);
        canvasRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        // BoxLayout respects a fixed max size (unlike BorderLayout.CENTER, which would
        // stretch the canvas to fill all remaining space and expose Swing's default
        // grey background in whatever the canvas doesn't explicitly paint).
        canvasRow.setMaximumSize(new Dimension(CANVAS_SIZE + 132, CANVAS_SIZE));

        drawingCanvas = new DrawingCanvas();
        drawingCanvas.setPreferredSize(new Dimension(CANVAS_SIZE, CANVAS_SIZE));
        drawingCanvas.setMaximumSize(new Dimension(CANVAS_SIZE, CANVAS_SIZE));
        canvasRow.add(drawingCanvas);
        canvasRow.add(Box.createHorizontalStrut(12));
        canvasRow.add(buildDrawTools());
        content.add(canvasRow);
        content.add(Box.createVerticalStrut(16));

        drawRoundSubmit = new ThemedButton("Submit Drawing", true);
        drawRoundSubmit.setAlignmentX(Component.LEFT_ALIGNMENT);
        drawRoundSubmit.setMaximumSize(new Dimension(180, 38));
        drawRoundSubmit.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                byte[] png = drawingCanvas.toPng();
                if (png == null)
                {
                    GameHubDialog.show(TelephoneWindow.this, "Telephone", "Draw something first.");
                    return;
                }
                submitDrawing(png);
                drawRoundSubmit.setEnabled(false);
                showWaiting();
            }
        });
        content.add(drawRoundSubmit);

        RoundedPanel wrapper = new RoundedPanel(ThemeColor.BG_APP, 0);
        wrapper.setLayout(new GridBagLayout());
        wrapper.add(content, new GridBagConstraints());
        return wrapper;
    }

    private JPanel buildDrawTools()
    {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setPreferredSize(new Dimension(120, CANVAS_SIZE));

        Color[] swatches = {
            Color.BLACK, Color.WHITE, new Color(230, 90, 90), new Color(90, 170, 230),
            new Color(90, 210, 120), new Color(230, 200, 60), new Color(200, 90, 200),
            new Color(230, 150, 60)
        };
        JPanel swatchGrid = new JPanel(new GridLayout(2, 4, 4, 4));
        swatchGrid.setOpaque(false);
        swatchGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        swatchGrid.setMaximumSize(new Dimension(120, 44));
        for (final Color color : swatches)
        {
            JPanel swatch = new JPanel();
            swatch.setBackground(color);
            swatch.setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER)));
            swatch.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            swatch.addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e) { drawingCanvas.setColor(color); }
            });
            swatchGrid.add(swatch);
        }
        panel.add(swatchGrid);
        panel.add(Box.createVerticalStrut(12));

        JLabel brushLabel = new JLabel("Brush Size");
        brushLabel.setFont(UITheme.FONT_SMALL);
        brushLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        brushLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(brushLabel);

        final javax.swing.JSlider slider = new javax.swing.JSlider(2, 24, 6);
        slider.setOpaque(false);
        slider.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setMaximumSize(new Dimension(120, 40));
        slider.addChangeListener(new javax.swing.event.ChangeListener()
        {
            public void stateChanged(javax.swing.event.ChangeEvent e) { drawingCanvas.setBrushSize(slider.getValue()); }
        });
        panel.add(slider);
        panel.add(Box.createVerticalStrut(12));

        ThemedButton clear = new ThemedButton("Clear", false);
        clear.setAlignmentX(Component.LEFT_ALIGNMENT);
        clear.setMaximumSize(new Dimension(120, 32));
        clear.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { drawingCanvas.clear(); }
        });
        panel.add(clear);

        return panel;
    }

    // ==================== Waiting between rounds ====================

    private JPanel createWaitingScreen()
    {
        RoundedPanel wrapper = new RoundedPanel(ThemeColor.BG_APP, 0);
        wrapper.setLayout(new GridBagLayout());

        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        wrapper.add(panel, new GridBagConstraints());

        waitingLabel = new JLabel("Waiting on the rest of the table...");
        waitingLabel.setFont(UITheme.FONT_NAV_BOLD);
        waitingLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        panel.add(waitingLabel);

        return wrapper;
    }

    private void showWaiting()
    {
        stopCountdown();
        cardLayout.show(cards, WAITING);
    }

    // ==================== Reveal ====================

    private JPanel createRevealScreen()
    {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(24, 24, 24, 24));
        content.setPreferredSize(new Dimension(460, 560));

        JLabel title = new JLabel("Telephone - Reveal");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        content.add(title);

        revealHeaderLabel = new JLabel(" ");
        revealHeaderLabel.setFont(UITheme.FONT_SMALL);
        revealHeaderLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        revealHeaderLabel.setBorder(new EmptyBorder(6, 0, 4, 0));
        content.add(revealHeaderLabel);

        revealAuthorLabel = new JLabel(" ");
        revealAuthorLabel.setFont(UITheme.FONT_BODY);
        revealAuthorLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        revealAuthorLabel.setBorder(new EmptyBorder(0, 0, 14, 0));
        content.add(revealAuthorLabel);

        revealDisplay = new RevealDisplay();
        revealDisplay.setAlignmentX(Component.LEFT_ALIGNMENT);
        revealDisplay.setPreferredSize(new Dimension(CANVAS_SIZE, CANVAS_SIZE));
        revealDisplay.setMaximumSize(new Dimension(CANVAS_SIZE, CANVAS_SIZE));
        content.add(revealDisplay);
        content.add(Box.createVerticalStrut(16));

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        revealNextButton = new ThemedButton("Next", true);
        revealNextButton.setPreferredSize(new Dimension(120, 38));
        revealNextButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { advanceReveal(); }
        });
        buttonRow.add(revealNextButton);

        ThemedButton done = new ThemedButton("Back to Games", false);
        done.setPreferredSize(new Dimension(160, 38));
        done.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { MainMenu.getInstance().returnToGames(); }
        });
        buttonRow.add(done);

        content.add(buttonRow);

        RoundedPanel wrapper = new RoundedPanel(ThemeColor.BG_APP, 0);
        wrapper.setLayout(new GridBagLayout());
        wrapper.add(content, new GridBagConstraints());
        return wrapper;
    }

    private void advanceReveal()
    {
        revealPosition++;
        if (revealPosition >= revealEntries.size())
        {
            revealHeaderLabel.setText("That's every chain - thanks for playing!");
            revealAuthorLabel.setText(" ");
            revealDisplay.clear();
            revealNextButton.setEnabled(false);
            return;
        }

        RevealEntry entry = revealEntries.get(revealPosition);
        revealHeaderLabel.setText("Chain " + (entry.chainIndex + 1) + " - step " + (entry.entryIndex + 1)
            + " of " + entry.entryCount);
        revealAuthorLabel.setText((entry.isDrawing ? "Drawn by " : "Written by ") + entry.author);
        if (entry.isDrawing)
        {
            revealDisplay.showImage(entry.image);
        }
        else
        {
            revealDisplay.showText(entry.text);
        }
    }

    // ==================== Networking ====================

    private void submitText(String text)
    {
        Message request = new Message();
        request.setType(MessageType.TELEPHONE_SUBMIT_REQUEST);
        request.setMatchId(matchId);
        request.setTelephoneEntryText(text);
        NetworkManager.sendAsync(request);
        submittedThisRound = true;
    }

    private void submitDrawing(byte[] png)
    {
        Message request = new Message();
        request.setType(MessageType.TELEPHONE_SUBMIT_REQUEST);
        request.setMatchId(matchId);
        request.setFileData(png);
        NetworkManager.sendAsync(request);
        submittedThisRound = true;
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isTelephoneType = type == MessageType.TELEPHONE_ROUND_START
            || type == MessageType.TELEPHONE_REVEAL_ENTRY || type == MessageType.QUEUE_UPDATE;
        if (!isTelephoneType)
        {
            return;
        }
        if (type == MessageType.QUEUE_UPDATE && !"telephone".equals(message.getQueueGameId()))
        {
            return;
        }
        if (matchId != null && message.getMatchId() != null && !message.getMatchId().equals(matchId)
            && type != MessageType.QUEUE_UPDATE)
        {
            return;
        }

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { handleTelephoneMessage(message); }
        });
    }

    private void handleTelephoneMessage(Message message)
    {
        if (message.getType() == MessageType.QUEUE_UPDATE)
        {
            if (matchId == null)
            {
                searchingLabel.setText("Waiting for more players... " + message.getQueueCount()
                    + " in queue (need 4 to start)");
            }
        }
        else if (message.getType() == MessageType.TELEPHONE_ROUND_START)
        {
            if (matchId == null)
            {
                matchId = message.getMatchId();
            }
            submittedThisRound = false;
            startRound(message);
        }
        else if (message.getType() == MessageType.TELEPHONE_REVEAL_ENTRY)
        {
            addRevealEntry(message);
        }
    }

    private void startRound(Message message)
    {
        String header = "Round " + message.getTelephoneRound() + " of " + message.getTelephoneTotalRounds();
        boolean drawing = message.isTelephoneIsDrawingRound();
        long duration = drawing ? TelephoneMatch.DRAW_ROUND_MS : TelephoneMatch.TEXT_ROUND_MS;
        roundDeadline = System.currentTimeMillis() + duration;

        if (drawing)
        {
            drawRoundPromptLabel.setText("<html>" + header + " - Draw: <b>"
                + escapeHtml(message.getTelephoneEntryText()) + "</b></html>");
            drawingCanvas.clear();
            drawRoundSubmit.setEnabled(true);
            cardLayout.show(cards, DRAW_ROUND);
            startCountdown(drawRoundTimerLabel);
        }
        else
        {
            byte[] toGuess = message.getFileData();
            textRoundInput.clear();
            textRoundSubmit.setEnabled(true);
            if (toGuess != null)
            {
                textRoundPromptLabel.setText(header + " - what do you think this drawing shows?");
                showGuessImage(toGuess);
            }
            else
            {
                textRoundPromptLabel.setText(header + " - write a phrase to kick off your chain:");
            }
            cardLayout.show(cards, TEXT_ROUND);
            startCountdown(textRoundTimerLabel);
        }
    }

    private JLabel guessImagePreview;

    private void showGuessImage(byte[] png)
    {
        // Lazily inserted right above the text input the first time a guess round needs it -
        // round 0 never needs this (nothing to guess yet), so most matches never build it.
        if (guessImagePreview == null)
        {
            guessImagePreview = new JLabel();
            guessImagePreview.setAlignmentX(Component.LEFT_ALIGNMENT);
            guessImagePreview.setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER)));
            textRoundInput.getParent().add(guessImagePreview, indexOfInputInParent());
        }
        BufferedImage img = decodePng(png);
        if (img != null)
        {
            Image scaled = img.getScaledInstance(280, 280, Image.SCALE_SMOOTH);
            guessImagePreview.setIcon(new ImageIcon(scaled));
        }
        guessImagePreview.setVisible(true);
    }

    private int indexOfInputInParent()
    {
        java.awt.Container parent = textRoundInput.getParent();
        for (int i = 0; i < parent.getComponentCount(); i++)
        {
            if (parent.getComponent(i) == textRoundInput) return i;
        }
        return -1;
    }

    private void startCountdown(final JLabel label)
    {
        stopCountdown();
        countdownTimer = new Timer(1000, new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                long remaining = Math.max(0, roundDeadline - System.currentTimeMillis());
                label.setText((remaining / 1000) + "s left");
            }
        });
        countdownTimer.start();
        label.setText("...");
    }

    private void stopCountdown()
    {
        if (countdownTimer != null)
        {
            countdownTimer.stop();
            countdownTimer = null;
        }
    }

    private void addRevealEntry(Message message)
    {
        RevealEntry entry = new RevealEntry();
        entry.chainIndex = message.getTelephoneChainIndex();
        entry.entryIndex = message.getTelephoneEntryIndex();
        entry.entryCount = message.getTelephoneEntryCount();
        entry.author = message.getTelephoneEntryAuthor();
        entry.isDrawing = message.isTelephoneEntryIsDrawing();
        entry.text = message.getTelephoneEntryText();
        entry.image = message.getFileData();
        revealEntries.add(entry);

        if (revealPosition < 0)
        {
            stopCountdown();
            cardLayout.show(cards, REVEAL);
            advanceReveal();
        }
    }

    private static String escapeHtml(String text)
    {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static BufferedImage decodePng(byte[] png)
    {
        try
        {
            return ImageIO.read(new java.io.ByteArrayInputStream(png));
        }
        catch (IOException e)
        {
            return null;
        }
    }

    // ==================== Small reusable pieces ====================

    /** Freehand paint canvas, same mouse-drag-onto-a-BufferedImage technique as AvatarEditorDialog's paint tab. */
    private static class DrawingCanvas extends JPanel
    {
        private final BufferedImage image = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_RGB);
        private Color currentColor = Color.BLACK;
        private int brushSize = 6;
        private boolean hasPainted = false;
        private int lastX = -1, lastY = -1;

        DrawingCanvas()
        {
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.CROSSHAIR_CURSOR));
            setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER)));
            clear();

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

        void setColor(Color color) { currentColor = color; }
        void setBrushSize(int size) { brushSize = size; }

        void clear()
        {
            Graphics2D g2 = image.createGraphics();
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
            g2.dispose();
            hasPainted = false;
            repaint();
        }

        private void paintAt(int x, int y)
        {
            Graphics2D g2 = image.createGraphics();
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

        byte[] toPng()
        {
            if (!hasPainted) return null;
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

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            g.drawImage(image, 0, 0, null);
        }
    }

    /** Reveal viewer - shows either a big text phrase or a drawing, whichever the current step is. */
    private static class RevealDisplay extends JPanel
    {
        private Image image;
        private String text;

        RevealDisplay()
        {
            setBorder(BorderFactory.createLineBorder(ThemeManager.getColor(ThemeColor.BORDER)));
        }

        void showImage(byte[] png)
        {
            text = null;
            image = png == null ? null : decodePng(png);
            repaint();
        }

        void showText(String value)
        {
            image = null;
            text = value;
            repaint();
        }

        void clear()
        {
            image = null;
            text = null;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(ThemeManager.getColor(ThemeColor.BG_PANEL));
            g2.fillRect(0, 0, getWidth(), getHeight());

            if (image != null)
            {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(image, 0, 0, getWidth(), getHeight(), null);
            }
            else if (text != null)
            {
                g2.setColor(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
                g2.setFont(UITheme.FONT_HEADING);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                List<String> lines = wrapText(text, fm, getWidth() - 40);
                int totalHeight = lines.size() * fm.getHeight();
                int y = (getHeight() - totalHeight) / 2 + fm.getAscent();
                for (String line : lines)
                {
                    int x = (getWidth() - fm.stringWidth(line)) / 2;
                    g2.drawString(line, x, y);
                    y += fm.getHeight();
                }
            }
            g2.dispose();
        }

        private static List<String> wrapText(String text, java.awt.FontMetrics fm, int maxWidth)
        {
            List<String> lines = new ArrayList<String>();
            String[] words = text.split("\\s+");
            StringBuilder current = new StringBuilder();
            for (String word : words)
            {
                String candidate = current.length() == 0 ? word : current + " " + word;
                if (fm.stringWidth(candidate) > maxWidth && current.length() > 0)
                {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                }
                else
                {
                    current = new StringBuilder(candidate);
                }
            }
            if (current.length() > 0) lines.add(current.toString());
            return lines;
        }
    }
}
