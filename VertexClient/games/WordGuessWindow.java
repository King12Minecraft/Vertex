package games;

import net.Message;
import net.MessageType;
import net.NetworkManager;
import theme.GlitchEffectOverlay;
import theme.SignatureOverlay;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.ThemedButton;

import javax.swing.AbstractAction;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * WordGuessWindow
 * ----------------
 * Offline - WordGuessGame runs entirely client-side. Type A-Z, Backspace
 * to edit, Enter to submit a guess. Turn-based, so unlike the arcade
 * games there's no Swing Timer - the board just repaints after each key
 * press. Reports a score once on win or loss, same GAME_PLAYED_REQUEST
 * pattern as the other single-player games (0 if the word was never
 * solved).
 */
public class WordGuessWindow extends JFrame
{
    private static final Color COLOR_CORRECT = new Color(100, 180, 100);
    private static final Color COLOR_PRESENT = new Color(210, 180, 80);
    private static final Color COLOR_ABSENT = new Color(90, 90, 100);
    private static final Color COLOR_EMPTY = new Color(50, 50, 60);
    private static final Color COLOR_BORDER = new Color(120, 120, 135);

    private static final String[] KEYBOARD_ROWS = new String[]
    {
        "QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"
    };

    private WordGuessGame game;
    private BoardPanel boardPanel;
    private JLabel statusLabel;
    private boolean reported;

    public WordGuessWindow()
    {
        super("Vertex - Word Guess");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setIconImage(GameLogo.renderIcon(64));

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setBorder(new EmptyBorder(0, 0, 12, 0));

        statusLabel = new JLabel("Guess the 5-letter word. Type letters, Enter to submit.");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        topRow.add(statusLabel, BorderLayout.WEST);

        ThemedButton restart = new ThemedButton("New Word", false);
        restart.setPreferredSize(new Dimension(100, 34));
        restart.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e) { startNewGame(); }
        });
        JPanel restartWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        restartWrap.setOpaque(false);
        restartWrap.add(restart);
        topRow.add(restartWrap, BorderLayout.EAST);

        root.add(topRow, BorderLayout.NORTH);

        boardPanel = new BoardPanel();
        root.add(boardPanel, BorderLayout.CENTER);

        getContentPane().add(root);
        startNewGame();
        pack();
        setLocationRelativeTo(null);
        SignatureOverlay.attach(this);
        GlitchEffectOverlay.attach(this);
    }

    private void startNewGame()
    {
        game = new WordGuessGame();
        reported = false;
        updateStatus();
        boardPanel.requestFocusInWindow();
        boardPanel.repaint();
    }

    private void updateStatus()
    {
        if (game.isOver())
        {
            return;
        }
        if (game.getLastError() != null)
        {
            statusLabel.setText(game.getLastError());
        }
        else
        {
            statusLabel.setText("Guess " + (game.getGuessesUsed() + 1) + " of " + WordGuessGame.MAX_GUESSES
                + " - type letters, Enter to submit.");
        }
    }

    private void afterInput()
    {
        updateStatus();
        boardPanel.repaint();
        if (game.isOver())
        {
            finishGame();
        }
    }

    private void finishGame()
    {
        int finalScore = game.getScore();
        boolean won = game.isWon();
        statusLabel.setText((won ? "Solved it! " : "Out of guesses - it was " + game.getTarget() + ". ")
            + "Score: " + finalScore);
        if (!reported)
        {
            reported = true;
            reportScore(finalScore);
        }

        SnakeGameOverDialog.show(this, finalScore,
            won ? "Got it in " + game.getGuessesUsed() + " guesses." : "The word was " + game.getTarget() + ".",
            won && finalScore >= 100 ? "I solved the word in " + game.getGuessesUsed() + " guesses on Vertex!" : null,
            new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain() { startNewGame(); }
                public void onClose() { WordGuessWindow.this.dispose(); }
            });
    }

    private void reportScore(final int finalScore)
    {
        Thread worker = new Thread(new Runnable()
        {
            public void run()
            {
                Message request = new Message();
                request.setType(MessageType.GAME_PLAYED_REQUEST);
                request.setGameId("word-guess");
                request.setScore(finalScore);
                NetworkManager.sendAsync(request);
            }
        });
        worker.start();
    }

    private class BoardPanel extends JPanel
    {
        private static final int TILE = 52;
        private static final int TILE_GAP = 6;
        private static final int GRID_TOP = 10;
        private static final int KEY_TOP = GRID_TOP + WordGuessGame.MAX_GUESSES * (TILE + TILE_GAP) + 24;
        private static final int KEY_HEIGHT = 42;
        private static final int KEY_GAP = 6;

        BoardPanel()
        {
            int width = WordGuessGame.WORD_LENGTH * (TILE + TILE_GAP) + TILE_GAP;
            int height = KEY_TOP + KEYBOARD_ROWS.length * (KEY_HEIGHT + KEY_GAP) + 10;
            setPreferredSize(new Dimension(width, height));
            setFocusable(true);
            setBackground(new Color(24, 24, 32));
            bindKeys();
        }

        private void bindKeys()
        {
            for (char c = 'A'; c <= 'Z'; c++)
            {
                final char letter = c;
                String keyName = String.valueOf(c);
                getInputMap().put(KeyStroke.getKeyStroke(keyName), keyName + "_type");
                getActionMap().put(keyName + "_type", new AbstractAction()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        game.typeLetter(letter);
                        afterInput();
                    }
                });
            }
            getInputMap().put(KeyStroke.getKeyStroke("BACK_SPACE"), "backspace");
            getActionMap().put("backspace", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e)
                {
                    game.backspace();
                    afterInput();
                }
            });
            getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "submit");
            getActionMap().put("submit", new AbstractAction()
            {
                public void actionPerformed(ActionEvent e)
                {
                    game.submitGuess();
                    afterInput();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(UITheme.FONT_NAV_BOLD.deriveFont(Font.BOLD, 26f));

            paintGrid(g2);
            paintKeyboard(g2);

            g2.dispose();
        }

        private void paintGrid(Graphics2D g2)
        {
            List<String> guesses = game.getGuesses();
            List<int[]> feedback = game.getFeedback();
            String current = game.getCurrentGuess();

            for (int row = 0; row < WordGuessGame.MAX_GUESSES; row++)
            {
                String rowText;
                int[] rowResult = null;
                if (row < guesses.size())
                {
                    rowText = guesses.get(row);
                    rowResult = feedback.get(row);
                }
                else if (row == guesses.size())
                {
                    rowText = current;
                }
                else
                {
                    rowText = "";
                }

                for (int col = 0; col < WordGuessGame.WORD_LENGTH; col++)
                {
                    int x = TILE_GAP + col * (TILE + TILE_GAP);
                    int y = GRID_TOP + row * (TILE + TILE_GAP);

                    Color fill = COLOR_EMPTY;
                    if (rowResult != null)
                    {
                        int status = rowResult[col];
                        fill = status == WordGuessGame.CORRECT ? COLOR_CORRECT
                            : status == WordGuessGame.PRESENT ? COLOR_PRESENT : COLOR_ABSENT;
                    }
                    g2.setColor(fill);
                    g2.fillRoundRect(x, y, TILE, TILE, 6, 6);
                    g2.setColor(COLOR_BORDER);
                    g2.drawRoundRect(x, y, TILE, TILE, 6, 6);

                    if (col < rowText.length())
                    {
                        char c = rowText.charAt(col);
                        g2.setColor(Color.WHITE);
                        int textWidth = g2.getFontMetrics().stringWidth(String.valueOf(c));
                        g2.drawString(String.valueOf(c), x + (TILE - textWidth) / 2, y + TILE - 16);
                    }
                }
            }
        }

        private void paintKeyboard(Graphics2D g2)
        {
            g2.setFont(UITheme.FONT_NAV_BOLD.deriveFont(Font.BOLD, 14f));
            int boardWidth = getPreferredSize().width;

            for (int r = 0; r < KEYBOARD_ROWS.length; r++)
            {
                String rowLetters = KEYBOARD_ROWS[r];
                int keyWidth = 30;
                int rowWidth = rowLetters.length() * (keyWidth + KEY_GAP) - KEY_GAP;
                int startX = (boardWidth - rowWidth) / 2;
                int y = KEY_TOP + r * (KEY_HEIGHT + KEY_GAP);

                for (int i = 0; i < rowLetters.length(); i++)
                {
                    char c = rowLetters.charAt(i);
                    int x = startX + i * (keyWidth + KEY_GAP);
                    int status = game.bestStatusForLetter(c);
                    Color fill = status == WordGuessGame.CORRECT ? COLOR_CORRECT
                        : status == WordGuessGame.PRESENT ? COLOR_PRESENT
                        : status == WordGuessGame.ABSENT ? COLOR_ABSENT
                        : new Color(70, 70, 82);

                    g2.setColor(fill);
                    g2.fillRoundRect(x, y, keyWidth, KEY_HEIGHT, 5, 5);
                    g2.setColor(Color.WHITE);
                    int textWidth = g2.getFontMetrics().stringWidth(String.valueOf(c));
                    g2.drawString(String.valueOf(c), x + (keyWidth - textWidth) / 2, y + KEY_HEIGHT - 14);
                }
            }
        }
    }
}
