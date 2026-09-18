package games;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * KlondikeGame
 * -------------
 * An original implementation of Klondike - the traditional, public-
 * domain "patience"/solitaire card game people have played with a
 * physical deck for well over a century, long before any software
 * existed. Standard rules: 7 tableau columns, 4 suit foundations built
 * up Ace-to-King, draw-one from the stock, build tableau runs downward
 * in alternating colors, move a face-up run (or single card) onto a
 * tableau card one rank higher of the opposite color, or onto an empty
 * column only with a King. No relation to any specific existing
 * software's code, card art, or table layout.
 *
 * Turn-based like Lights Out/Peg Solitaire - no Swing Timer, the window
 * just repaints after each click. Selection is two-step: click a
 * face-up tableau card (or the waste pile's top card) to pick it up as
 * the source - a clicked tableau card brings every face-up card below
 * it in that column along with it, since any contiguous face-up tail
 * is guaranteed to already be validly sequenced (each card was checked
 * against the one below it at the moment it was placed) - then click a
 * destination pile.
 */
public class KlondikeGame
{
    public static final int SUITS = 4;
    public static final int CLUBS = 0, DIAMONDS = 1, HEARTS = 2, SPADES = 3;
    public static final int COLUMNS = 7;

    public static class Card
    {
        public final int rank; // 1 (Ace) .. 13 (King)
        public final int suit;
        public boolean faceUp;

        Card(int rank, int suit)
        {
            this.rank = rank;
            this.suit = suit;
        }

        public boolean isRed() { return suit == DIAMONDS || suit == HEARTS; }
    }

    public enum SourceType { NONE, WASTE, TABLEAU }

    private final List<Card> stock = new ArrayList<Card>();
    private final List<Card> waste = new ArrayList<Card>();
    private final List<List<Card>> foundations = new ArrayList<List<Card>>();
    private final List<List<Card>> tableau = new ArrayList<List<Card>>();

    private SourceType selectedType = SourceType.NONE;
    private int selectedCol = -1;
    private int selectedIndex = -1;

    private boolean won;

    public KlondikeGame()
    {
        List<Card> deck = new ArrayList<Card>();
        for (int suit = 0; suit < SUITS; suit++)
        {
            for (int rank = 1; rank <= 13; rank++)
            {
                deck.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(deck);

        for (int s = 0; s < SUITS; s++) foundations.add(new ArrayList<Card>());

        int pos = 0;
        for (int col = 0; col < COLUMNS; col++)
        {
            List<Card> column = new ArrayList<Card>();
            for (int i = 0; i <= col; i++)
            {
                Card c = deck.get(pos++);
                c.faceUp = (i == col);
                column.add(c);
            }
            tableau.add(column);
        }
        while (pos < deck.size())
        {
            Card c = deck.get(pos++);
            c.faceUp = false;
            stock.add(c);
        }
    }

    public List<Card> getStock() { return stock; }
    public List<Card> getWaste() { return waste; }
    public List<Card> getFoundation(int suit) { return foundations.get(suit); }
    public List<Card> getTableauColumn(int col) { return tableau.get(col); }
    public boolean isWon() { return won; }
    public boolean isOver() { return won; }

    public SourceType getSelectedType() { return selectedType; }
    public int getSelectedCol() { return selectedCol; }
    public int getSelectedIndex() { return selectedIndex; }

    public void clickStock()
    {
        if (won) return;
        clearSelection();
        if (stock.isEmpty())
        {
            // Recycle: waste goes back into the stock, face down, in reverse so the draw order repeats.
            for (int i = waste.size() - 1; i >= 0; i--)
            {
                Card c = waste.get(i);
                c.faceUp = false;
                stock.add(c);
            }
            waste.clear();
        }
        else
        {
            Card c = stock.remove(stock.size() - 1);
            c.faceUp = true;
            waste.add(c);
        }
    }

    public void clickWaste()
    {
        if (won) return;
        if (selectedType != SourceType.NONE)
        {
            clearSelection();
            return;
        }
        if (!waste.isEmpty())
        {
            selectedType = SourceType.WASTE;
            selectedCol = -1;
            selectedIndex = waste.size() - 1;
        }
    }

    public void clickFoundation(int suit)
    {
        if (won) return;
        if (selectedType == SourceType.NONE)
        {
            return;
        }

        List<Card> run = currentRun();
        if (run.size() != 1)
        {
            clearSelection();
            return;
        }

        Card card = run.get(0);
        List<Card> foundation = foundations.get(suit);
        boolean legal = (card.suit == suit)
            && ((foundation.isEmpty() && card.rank == 1)
                || (!foundation.isEmpty() && foundation.get(foundation.size() - 1).rank == card.rank - 1));

        if (legal)
        {
            removeRunFromSource(1);
            foundation.add(card);
            checkWin();
        }
        clearSelection();
    }

    public void clickTableau(int col, int cardIndex)
    {
        if (won) return;

        if (selectedType == SourceType.NONE)
        {
            List<Card> column = tableau.get(col);
            if (cardIndex < 0 || cardIndex >= column.size()) return;
            if (!column.get(cardIndex).faceUp) return;

            selectedType = SourceType.TABLEAU;
            selectedCol = col;
            selectedIndex = cardIndex;
            return;
        }

        // Clicking the same column/position again just cancels.
        if (selectedType == SourceType.TABLEAU && selectedCol == col)
        {
            clearSelection();
            return;
        }

        List<Card> run = currentRun();
        Card top = run.get(0);
        List<Card> destColumn = tableau.get(col);

        boolean legal;
        if (destColumn.isEmpty())
        {
            legal = top.rank == 13; // only a King may start an empty column
        }
        else
        {
            Card destTop = destColumn.get(destColumn.size() - 1);
            legal = destTop.faceUp && destTop.rank == top.rank + 1 && destTop.isRed() != top.isRed();
        }

        if (legal)
        {
            removeRunFromSource(run.size());
            destColumn.addAll(run);
        }
        clearSelection();
    }

    /** The clicked card plus every face-up card after it in the same column (for a tableau source), or just the single top card (for a waste source). */
    private List<Card> currentRun()
    {
        List<Card> run = new ArrayList<Card>();
        if (selectedType == SourceType.WASTE)
        {
            run.add(waste.get(waste.size() - 1));
        }
        else if (selectedType == SourceType.TABLEAU)
        {
            List<Card> column = tableau.get(selectedCol);
            for (int i = selectedIndex; i < column.size(); i++)
            {
                run.add(column.get(i));
            }
        }
        return run;
    }

    private void removeRunFromSource(int count)
    {
        if (selectedType == SourceType.WASTE)
        {
            waste.remove(waste.size() - 1);
        }
        else if (selectedType == SourceType.TABLEAU)
        {
            List<Card> column = tableau.get(selectedCol);
            for (int i = 0; i < count; i++)
            {
                column.remove(column.size() - 1);
            }
            if (!column.isEmpty())
            {
                column.get(column.size() - 1).faceUp = true;
            }
        }
    }

    private void clearSelection()
    {
        selectedType = SourceType.NONE;
        selectedCol = -1;
        selectedIndex = -1;
    }

    private void checkWin()
    {
        for (List<Card> f : foundations)
        {
            if (f.size() != 13) return;
        }
        won = true;
    }

    public int getFoundationCardCount()
    {
        int total = 0;
        for (List<Card> f : foundations) total += f.size();
        return total;
    }

    /** 10 points per card safely on a foundation, plus a solid completion bonus. */
    public int getScore()
    {
        int score = getFoundationCardCount() * 10;
        if (won) score += 200;
        return score;
    }
}
