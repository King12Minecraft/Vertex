package games;
import net.MessageType;
import net.Message;
import economy.EconomyManager;
import net.ClientHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * TelephoneMatch
 * ---------------
 * Gartic-Phone-style draw/guess chain, 4-8 players, purely for fun - no
 * scoring, no winner. N players means N parallel chains (chain k started by
 * player k's own opening phrase) and exactly N rounds: round 0 is every
 * player writing their chain's starting phrase; every odd round is
 * "draw the phrase you were just handed"; every even round after 0 is
 * "guess the drawing you were just handed" in one short phrase. Player p's
 * assignment in round r is chain (p - r) mod N - the standard rotation that
 * makes every chain get visited by every player exactly once, then the
 * match ends and every chain gets replayed start to finish for everyone
 * (the actual point of the game - watching a phrase mutate through N
 * rounds of drawing and guessing).
 *
 * Same "server broadcasts state, clients submit discrete actions, a
 * server-side Timer auto-advances regardless of who's answered" shape as
 * TriviaMatch - one slow or disconnected player gets an auto-filled blank
 * entry instead of stalling everyone else's chain.
 */
public class TelephoneMatch
{
    public static final long DRAW_ROUND_MS = 75_000;
    public static final long TEXT_ROUND_MS = 45_000;
    private static final long BETWEEN_ROUND_PAUSE_MS = 3_000;
    private static final String GAME_ID = "telephone";

    private static class ChainEntry
    {
        final String authorUsername;
        final boolean isDrawing;
        final String text;
        final byte[] image;

        ChainEntry(String authorUsername, boolean isDrawing, String text, byte[] image)
        {
            this.authorUsername = authorUsername;
            this.isDrawing = isDrawing;
            this.text = text;
            this.image = image;
        }
    }

    private final String matchId;
    private final List<ClientHandler> players;
    private final int playerCount;
    private final TelephoneMatchManager matchManager;
    private final EconomyManager economyManager;

    /** chains.get(k) is chain k's entries in order, appended to as rounds finish. */
    private final List<List<ChainEntry>> chains = new ArrayList<List<ChainEntry>>();
    private final Set<ClientHandler> submittedThisRound = new HashSet<ClientHandler>();
    private final String[] submittedText;
    private final byte[][] submittedImage;
    private final Set<ClientHandler> disconnected = new HashSet<ClientHandler>();

    private int currentRound = 0;
    private boolean over = false;
    private Timer roundTimer;

    public TelephoneMatch(String matchId, List<ClientHandler> players, TelephoneMatchManager matchManager,
                           EconomyManager economyManager)
    {
        this.matchId = matchId;
        this.players = players;
        this.playerCount = players.size();
        this.matchManager = matchManager;
        this.economyManager = economyManager;
        this.submittedText = new String[playerCount];
        this.submittedImage = new byte[playerCount][];

        for (int i = 0; i < playerCount; i++)
        {
            chains.add(new ArrayList<ChainEntry>());
        }
    }

    public void start()
    {
        startRound();
    }

    private static boolean isDrawingRound(int round)
    {
        return round > 0 && round % 2 == 1;
    }

    /** Player index p's chain assignment in round r - the standard rotation, see the class comment. */
    private int chainFor(int playerIndex, int round)
    {
        return ((playerIndex - round) % playerCount + playerCount) % playerCount;
    }

    private synchronized void startRound()
    {
        submittedThisRound.clear();
        for (int i = 0; i < playerCount; i++)
        {
            submittedText[i] = null;
            submittedImage[i] = null;
        }

        boolean drawing = isDrawingRound(currentRound);

        for (int i = 0; i < playerCount; i++)
        {
            ClientHandler player = players.get(i);
            if (disconnected.contains(player))
            {
                continue;
            }

            int chainIndex = chainFor(i, currentRound);
            List<ChainEntry> chain = chains.get(chainIndex);

            Message msg = new Message();
            msg.setType(MessageType.TELEPHONE_ROUND_START);
            msg.setMatchId(matchId);
            msg.setTelephoneRound(currentRound + 1);
            msg.setTelephoneTotalRounds(playerCount);
            msg.setTelephoneIsDrawingRound(drawing);

            if (!chain.isEmpty())
            {
                ChainEntry previous = chain.get(chain.size() - 1);
                if (drawing)
                {
                    // Drawing round: illustrate the phrase the previous player wrote.
                    msg.setTelephoneEntryText(previous.text);
                }
                else
                {
                    // Guessing round (round > 0): guess the drawing the previous player made.
                    msg.setFileData(previous.image);
                    msg.setFileName("telephone.png");
                }
            }
            // Round 0 (drawing == false, chain empty): no prior content at all -
            // this player just writes their chain's opening phrase from scratch.

            player.sendMessage(msg);
        }

        long duration = drawing ? DRAW_ROUND_MS : TEXT_ROUND_MS;
        roundTimer = new Timer(true);
        final int roundAtSchedule = currentRound;
        roundTimer.schedule(new TimerTask()
        {
            public void run() { finishRound(roundAtSchedule); }
        }, duration);
    }

    /** Idempotent - a second submission in the same round, or one after the round already closed, is ignored. */
    public synchronized void submitEntry(ClientHandler requester, String text, byte[] image)
    {
        if (over) return;
        int index = players.indexOf(requester);
        if (index < 0 || submittedThisRound.contains(requester)) return;

        submittedThisRound.add(requester);
        submittedText[index] = text;
        submittedImage[index] = image;
    }

    private synchronized void finishRound(int roundThatEnded)
    {
        if (over || roundThatEnded != currentRound) return;

        boolean drawing = isDrawingRound(currentRound);

        for (int i = 0; i < playerCount; i++)
        {
            ClientHandler player = players.get(i);
            int chainIndex = chainFor(i, currentRound);

            String text = submittedText[i];
            byte[] image = submittedImage[i];
            boolean missing = disconnected.contains(player) || (drawing ? image == null : (text == null || text.trim().isEmpty()));

            if (missing)
            {
                text = drawing ? null : "(no answer)";
                image = drawing ? blankImage() : null;
            }

            chains.get(chainIndex).add(new ChainEntry(player.getLoggedInUsername(), drawing, text, image));
        }

        currentRound++;
        if (currentRound >= playerCount)
        {
            finishMatch();
        }
        else
        {
            Timer delay = new Timer(true);
            delay.schedule(new TimerTask()
            {
                public void run() { startRound(); }
            }, BETWEEN_ROUND_PAUSE_MS);
        }
    }

    /** A small blank white PNG, used so a missed drawing round still leaves something real to display in the chain rather than a null image crashing the reveal viewer. */
    private byte[] blankImage()
    {
        try
        {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(320, 320, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2 = img.createGraphics();
            g2.setColor(java.awt.Color.WHITE);
            g2.fillRect(0, 0, 320, 320);
            g2.dispose();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", out);
            return out.toByteArray();
        }
        catch (java.io.IOException e)
        {
            return new byte[0];
        }
    }

    private synchronized void finishMatch()
    {
        if (over) return;
        over = true;
        if (roundTimer != null) roundTimer.cancel();

        for (int i = 0; i < playerCount; i++)
        {
            ClientHandler player = players.get(i);
            if (disconnected.contains(player)) continue;

            for (int chainIndex = 0; chainIndex < playerCount; chainIndex++)
            {
                List<ChainEntry> chain = chains.get(chainIndex);
                for (int entryIndex = 0; entryIndex < chain.size(); entryIndex++)
                {
                    ChainEntry entry = chain.get(entryIndex);
                    Message msg = new Message();
                    msg.setType(MessageType.TELEPHONE_REVEAL_ENTRY);
                    msg.setMatchId(matchId);
                    msg.setTelephoneChainIndex(chainIndex);
                    msg.setTelephoneChainCount(playerCount);
                    msg.setTelephoneEntryIndex(entryIndex);
                    msg.setTelephoneEntryCount(chain.size());
                    msg.setTelephoneEntryAuthor(entry.authorUsername);
                    msg.setTelephoneEntryIsDrawing(entry.isDrawing);
                    if (entry.isDrawing)
                    {
                        msg.setFileData(entry.image);
                        msg.setFileName("telephone.png");
                    }
                    else
                    {
                        msg.setTelephoneEntryText(entry.text);
                    }
                    boolean lastEntryOverall = chainIndex == playerCount - 1 && entryIndex == chain.size() - 1;
                    msg.setTelephoneRevealDone(lastEntryOverall);
                    player.sendMessage(msg);
                }
            }

            economyManager.awardWin(player, GAME_ID);
        }

        matchManager.endMatch(matchId);
    }

    /** Same shape as every other game's disconnect handling here - see AmongUsMatch.handleDisconnect. A mid-match departure never stalls the other players' chains: their still-pending round entry is just auto-filled at the next finishRound() like a timeout would be. */
    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over) return;
        disconnected.add(who);

        if (disconnected.size() >= playerCount)
        {
            over = true;
            if (roundTimer != null) roundTimer.cancel();
            matchManager.endMatch(matchId);
        }
    }
}
