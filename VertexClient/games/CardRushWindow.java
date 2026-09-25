package games;

import net.Message;
import net.MessageType;
import net.NetworkManager;
import pages.MainMenu;
import theme.ThemeColor;
import theme.ThemeManager;
import theme.UITheme;
import ui.RoundedPanel;
import ui.ThemedButton;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * CardRushWindow
 * --------------
 * Online ranked Card Rush, 1v1 - no turns, click a card in your hand
 * to select it, then click either center pile to try playing it
 * there. CardRushMatch is the sole authority on legality; this window
 * just sends the attempt and redraws whatever state comes back
 * (which for the opponent's hand is only a card COUNT, never their
 * actual cards - see CardRushMatch.stateString()).
 */
public class CardRushWindow extends JPanel implements NetworkManager.PushListener, EmbeddedGamePanel
{
    private static final String MODE_SELECT = "MODE_SELECT";
    private static final String SEARCHING = "SEARCHING";
    private static final String BOARD = "BOARD";
    private static final String[] RANK_NAMES = {
        "", "A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"
    };
    private static final String[] SUIT_SYMBOLS = { "\u2660", "\u2665", "\u2666", "\u2663" };

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private JLabel searchingLabel;
    private JPanel handPanel;
    private PileButton pile1Button, pile2Button;
    private JLabel statusLabel;
    private JLabel countsLabel;

    private String matchId;
    private String mySymbol;
    private String opponentUsername;
    private int centerPile1, centerPile2;
    private List<Integer> myHand = new ArrayList<Integer>();
    private int opponentHandCount, myStockCount, opponentStockCount;
    private Integer selectedCard;
    private boolean gameOver;

    public CardRushWindow()
    {
        setLayout(new BorderLayout());

        cards.add(createModeSelectScreen(), MODE_SELECT);
        cards.add(createSearchingScreen(), SEARCHING);
        cards.add(createBoardScreen(), BOARD);

        add(cards, BorderLayout.CENTER);
        cardLayout.show(cards, MODE_SELECT);

        NetworkManager.addPushListener(this);
    }

    @Override
    public boolean requestLeave()
    {
        leaveMatch();
        NetworkManager.removePushListener(this);
        return true;
    }

    private JPanel createModeSelectScreen()
    {
        RoundedPanel wrapper = new RoundedPanel(ThemeColor.BG_APP, 0);
        wrapper.setLayout(new GridBagLayout());

        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(50, 60, 50, 60));
        panel.setPreferredSize(new Dimension(420, 240));
        wrapper.add(panel, new GridBagConstraints());

        JLabel title = new JLabel("Card Rush");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("Ranked 1v1 - no turns, play adjacent ranks as fast as you can.");
        subtitle.setFont(UITheme.FONT_SUBHEAD);
        subtitle.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 24, 0));
        panel.add(subtitle);

        ThemedButton playOnline = new ThemedButton("Find Match", true);
        playOnline.setAlignmentX(Component.LEFT_ALIGNMENT);
        playOnline.setMaximumSize(new Dimension(2000, 42));
        playOnline.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                cardLayout.show(cards, SEARCHING);
                findMatch();
            }
        });
        panel.add(playOnline);

        return wrapper;
    }

    private JPanel createSearchingScreen()
    {
        RoundedPanel wrapper = new RoundedPanel(ThemeColor.BG_APP, 0);
        wrapper.setLayout(new GridBagLayout());

        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(60, 60, 60, 60));
        panel.setPreferredSize(new Dimension(360, 220));
        wrapper.add(panel, new GridBagConstraints());

        JLabel title = new JLabel("Card Rush");
        title.setFont(UITheme.FONT_HEADING);
        title.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        searchingLabel = new JLabel("Looking for an opponent...");
        searchingLabel.setFont(UITheme.FONT_SUBHEAD);
        searchingLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_SECONDARY));
        searchingLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchingLabel.setBorder(new EmptyBorder(10, 0, 30, 0));
        panel.add(searchingLabel);

        ThemedButton cancel = new ThemedButton("Cancel", false);
        cancel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cancel.setPreferredSize(new Dimension(120, 38));
        cancel.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                leaveMatch();
                MainMenu.getInstance().returnToGames();
            }
        });
        panel.add(cancel);

        return wrapper;
    }

    private JPanel createBoardScreen()
    {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(ThemeManager.getColor(ThemeColor.BG_APP));
        wrap.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        statusLabel = new JLabel("Waiting...");
        statusLabel.setFont(UITheme.FONT_NAV_BOLD);
        statusLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_PRIMARY));
        top.add(statusLabel);

        countsLabel = new JLabel("You: 0 left    Opponent: 0 left");
        countsLabel.setFont(UITheme.FONT_SMALL);
        countsLabel.setForeground(ThemeManager.getColor(ThemeColor.TEXT_MUTED));
        countsLabel.setBorder(new EmptyBorder(2, 0, 16, 0));
        top.add(countsLabel);
        wrap.add(top, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JPanel pilesRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        pilesRow.setOpaque(false);
        pile1Button = new PileButton();
        pile1Button.addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e) { tryPlaySelected(1); }
        });
        pile2Button = new PileButton();
        pile2Button.addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e) { tryPlaySelected(2); }
        });
        pilesRow.add(pile1Button);
        pilesRow.add(pile2Button);
        center.add(pilesRow);
        center.add(javax.swing.Box.createVerticalStrut(20));

        handPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        handPanel.setOpaque(false);
        center.add(handPanel);

        JPanel centerCenterer = new JPanel(new GridBagLayout());
        centerCenterer.setOpaque(false);
        centerCenterer.add(center, new GridBagConstraints());
        wrap.add(centerCenterer, BorderLayout.CENTER);
        return wrap;
    }

    private void findMatch()
    {
        Message request = new Message();
        request.setType(MessageType.CARDRUSH_FIND_MATCH_REQUEST);
        boolean sent = NetworkManager.sendAsync(request);
        if (!sent)
        {
            searchingLabel.setText("Can't reach the server - is it running?");
        }
    }

    private void leaveMatch()
    {
        Message request = new Message();
        request.setType(MessageType.CARDRUSH_LEAVE_QUEUE_REQUEST);
        request.setMatchId(matchId);
        NetworkManager.sendAsync(request);
    }

    private void selectCard(int cardId)
    {
        if (gameOver) return;
        selectedCard = cardId;
        rebuildHand();
    }

    private void tryPlaySelected(int pileNumber)
    {
        if (gameOver || selectedCard == null) return;
        Message request = new Message();
        request.setType(MessageType.CARDRUSH_PLAY_REQUEST);
        request.setMatchId(matchId);
        request.setCellIndex(selectedCard);
        request.setMuteDurationMinutes(pileNumber);
        NetworkManager.sendAsync(request);
        selectedCard = null;
    }

    @Override
    public void onPush(final Message message)
    {
        MessageType type = message.getType();
        boolean isType = type == MessageType.CARDRUSH_MATCH_FOUND || type == MessageType.CARDRUSH_UPDATE
            || type == MessageType.CARDRUSH_RESULT;
        if (!isType)
        {
            return;
        }
        if (matchId != null && message.getMatchId() != null && !message.getMatchId().equals(matchId))
        {
            return;
        }

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run() { handleServerMessage(message); }
        });
    }

    private void handleServerMessage(Message message)
    {
        MessageType type = message.getType();

        if (type == MessageType.CARDRUSH_MATCH_FOUND)
        {
            matchId = message.getMatchId();
            mySymbol = message.getSymbol();
            opponentUsername = message.getOpponentUsername();
            applyState(message.getBoardState());
            statusLabel.setText("vs " + opponentUsername + " - no turns, play anytime!");
            cardLayout.show(cards, BOARD);
        }
        else if (type == MessageType.CARDRUSH_UPDATE)
        {
            applyState(message.getBoardState());
        }
        else if (type == MessageType.CARDRUSH_RESULT)
        {
            applyState(message.getBoardState());
            gameOver = true;
            String result = message.getMatchResult();
            String text = "WIN".equals(result) ? "You won!"
                : "LOSE".equals(result) ? "You lost."
                : "DRAW".equals(result) ? "It's a draw."
                : "OPPONENT_LEFT".equals(result) ? "Your opponent left - you win!"
                : "Game over.";
            statusLabel.setText(text);

            String shareText = ("WIN".equals(result) || "OPPONENT_LEFT".equals(result))
                ? "I won a Card Rush match on Vertex!" : null;
            SnakeGameOverDialog.show(this, 0, text, shareText, new SnakeGameOverDialog.Choice()
            {
                public void onPlayAgain()
                {
                    gameOver = false;
                    matchId = null;
                    cardLayout.show(cards, SEARCHING);
                    findMatch();
                }
                public void onClose() { MainMenu.getInstance().returnToGames(); }
            });
        }
    }

    /** "centerPile1,centerPile2|myHand-or-theirs|theirHand-or-mine|stockA,stockB" - see CardRushMatch.stateString(); this window picks out its own hand by mySymbol and only cares about the opponent's hand COUNT. */
    private void applyState(String state)
    {
        if (state == null) return;
        String[] parts = state.split("\\|", -1);
        if (parts.length < 4) return;

        String[] pileParts = parts[0].split(",");
        centerPile1 = Integer.parseInt(pileParts[0]);
        centerPile2 = Integer.parseInt(pileParts[1]);

        String handAText = parts[1], handBText = parts[2];
        String myHandText = "A".equals(mySymbol) ? handAText : handBText;
        String opponentHandText = "A".equals(mySymbol) ? handBText : handAText;

        myHand = parseCardList(myHandText);
        opponentHandCount = opponentHandText.isEmpty() ? 0 : opponentHandText.split(",").length;

        String[] stockParts = parts[3].split(",");
        int stockA = Integer.parseInt(stockParts[0]), stockB = Integer.parseInt(stockParts[1]);
        myStockCount = "A".equals(mySymbol) ? stockA : stockB;
        opponentStockCount = "A".equals(mySymbol) ? stockB : stockA;

        countsLabel.setText("You: " + (myHand.size() + myStockCount) + " left    Opponent: "
            + (opponentHandCount + opponentStockCount) + " left");

        pile1Button.setCardId(centerPile1);
        pile2Button.setCardId(centerPile2);
        rebuildHand();
    }

    private List<Integer> parseCardList(String text)
    {
        List<Integer> result = new ArrayList<Integer>();
        if (text == null || text.isEmpty()) return result;
        for (String part : text.split(","))
        {
            result.add(Integer.parseInt(part));
        }
        return result;
    }

    private void rebuildHand()
    {
        handPanel.removeAll();
        for (final int cardId : myHand)
        {
            CardTile tile = new CardTile(cardId, cardId == (selectedCard == null ? -1 : selectedCard));
            tile.addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent e) { selectCard(cardId); }
            });
            handPanel.add(tile);
        }
        handPanel.revalidate();
        handPanel.repaint();
    }

    private static String rankText(int cardId) { return RANK_NAMES[cardId / 10]; }
    private static String suitSymbol(int cardId) { return SUIT_SYMBOLS[cardId % 10]; }
    private static boolean isRed(int cardId) { return cardId % 10 == 1 || cardId % 10 == 2; }

    private static class CardTile extends RoundedPanel
    {
        private final int cardId;
        private final boolean selected;

        CardTile(int cardId, boolean selected)
        {
            super(ThemeColor.BG_PANEL, 8);
            this.cardId = cardId;
            this.selected = selected;
            setPreferredSize(new Dimension(52, 72));
        }

        @Override
        protected void paintComponent(java.awt.Graphics g)
        {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);
            g2.setColor(selected ? new Color(90, 130, 200) : Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g2.setColor(isRed(cardId) ? new Color(200, 50, 50) : Color.BLACK);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            g2.drawString(rankText(cardId), 6, 20);
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
            g2.drawString(suitSymbol(cardId), 6, getHeight() - 10);
            g2.dispose();
        }
    }

    private static class PileButton extends RoundedPanel
    {
        private int cardId = -1;

        PileButton()
        {
            super(ThemeColor.BG_PANEL, 8);
            setPreferredSize(new Dimension(70, 96));
        }

        void setCardId(int cardId) { this.cardId = cardId; repaint(); }

        @Override
        protected void paintComponent(java.awt.Graphics g)
        {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            UITheme.applyAntialiasing(g2);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            if (cardId >= 0)
            {
                g2.setColor(isRed(cardId) ? new Color(200, 50, 50) : Color.BLACK);
                g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
                String rank = rankText(cardId);
                int textWidth = g2.getFontMetrics().stringWidth(rank);
                g2.drawString(rank, (getWidth() - textWidth) / 2, 34);
                g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
                String suit = suitSymbol(cardId);
                int suitWidth = g2.getFontMetrics().stringWidth(suit);
                g2.drawString(suit, (getWidth() - suitWidth) / 2, getHeight() - 16);
            }
            g2.dispose();
        }
    }
}
