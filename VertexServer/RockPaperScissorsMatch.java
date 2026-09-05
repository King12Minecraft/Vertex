/**
 * RockPaperScissorsMatch
 * -----------------------
 * 1v1 Rock Paper Scissors, best-of-5 (first to 3 round wins). Unlike
 * Chess/Tic-Tac-Toe, moves are simultaneous, not turn-based: each round
 * both players submit blind, and the server only reveals/resolves once
 * both have come in.
 */
public class RockPaperScissorsMatch
{
    private static final int WINS_NEEDED = 3;

    private final String matchId;
    private final ClientHandler playerA;
    private final ClientHandler playerB;
    private final RockPaperScissorsMatchManager matchManager;
    private final LeaderboardManager leaderboardManager;
    private final ReplayManager replayManager;
    private final EconomyManager economyManager;
    private final java.util.List<ClientHandler> spectators = new java.util.ArrayList<ClientHandler>();
    private final java.util.List<String> roundLog = new java.util.ArrayList<String>();

    private String moveA;
    private String moveB;
    private int scoreA = 0;
    private int scoreB = 0;
    private boolean over = false;
    private TournamentMatchListener tournamentListener;

    public RockPaperScissorsMatch(String matchId, ClientHandler playerA, ClientHandler playerB, RockPaperScissorsMatchManager matchManager, LeaderboardManager leaderboardManager, ReplayManager replayManager, EconomyManager economyManager)
    {
        this.matchId = matchId;
        this.playerA = playerA;
        this.playerB = playerB;
        this.matchManager = matchManager;
        this.leaderboardManager = leaderboardManager;
        this.replayManager = replayManager;
        this.economyManager = economyManager;
    }

    public String getPlayerAUsername() { return playerA.getLoggedInUsername(); }
    public String getPlayerBUsername() { return playerB.getLoggedInUsername(); }

    /** Nothing to send immediately (unlike Chess's board snapshot) - a spectator joining mid-match just sees the next round resolve like everyone else, since there's no meaningful "current state" between rounds worth catching up on. */
    public synchronized void addSpectator(ClientHandler spectator)
    {
        spectators.add(spectator);
    }

    public void setTournamentListener(TournamentMatchListener listener)
    {
        this.tournamentListener = listener;
    }

    public void start()
    {
        sendMatchFound(playerA, playerB.getLoggedInUsername());
        sendMatchFound(playerB, playerA.getLoggedInUsername());
    }

    private void sendMatchFound(ClientHandler to, String opponentUsername)
    {
        Message msg = new Message();
        msg.setType(MessageType.RPS_MATCH_FOUND);
        msg.setMatchId(matchId);
        msg.setOpponentUsername(opponentUsername);
        to.sendMessage(msg);
    }

    public synchronized void submitMove(ClientHandler requester, String move)
    {
        if (over || move == null)
        {
            return;
        }

        if (requester == playerA)
        {
            moveA = move;
        }
        else if (requester == playerB)
        {
            moveB = move;
        }
        else
        {
            return;
        }

        if (moveA != null && moveB != null)
        {
            resolveRound();
        }
    }

    private void resolveRound()
    {
        int outcome = resolve(moveA, moveB);
        if (outcome > 0)
        {
            scoreA++;
        }
        else if (outcome < 0)
        {
            scoreB++;
        }

        sendRoundResult(playerA, moveA, moveB, outcome > 0 ? "WIN" : outcome < 0 ? "LOSE" : "DRAW");
        sendRoundResult(playerB, moveB, moveA, outcome < 0 ? "WIN" : outcome > 0 ? "LOSE" : "DRAW");
        notifySpectatorsRound(moveA, moveB, scoreA, scoreB);
        roundLog.add(moveA + ":" + moveB + ":" + scoreA + ":" + scoreB);

        moveA = null;
        moveB = null;

        if (scoreA >= WINS_NEEDED || scoreB >= WINS_NEEDED)
        {
            over = true;
            matchManager.endMatch(matchId);
            recordRating();
            awardWinner();
            saveReplay();
            sendMatchOver(playerA, scoreA > scoreB ? "WIN" : "LOSE");
            sendMatchOver(playerB, scoreB > scoreA ? "WIN" : "LOSE");
            notifySpectatorsEnded();

            if (tournamentListener != null)
            {
                ClientHandler winner = scoreA > scoreB ? playerA : playerB;
                ClientHandler loser = scoreA > scoreB ? playerB : playerA;
                tournamentListener.onMatchComplete(winner, loser);
            }
        }
    }

    /** A neutral view for spectators - actual picks from both players and the running score, not the "my move / opponent move" framing sendRoundResult uses for participants. Reuses RPS_ROUND_RESULT with playerA/playerB's real usernames carried via getRpsMove/getRpsOpponentMove and the score fields, distinguished from a normal participant update only by matchId not matching any match the client itself is playing (the client tells these apart the same way ChessWindow's spectate mode does). */
    private void notifySpectatorsRound(String pickA, String pickB, int newScoreA, int newScoreB)
    {
        for (int i = 0; i < spectators.size(); i++)
        {
            Message msg = new Message();
            msg.setType(MessageType.RPS_ROUND_RESULT);
            msg.setMatchId(matchId);
            msg.setRpsMove(pickA);
            msg.setRpsOpponentMove(pickB);
            msg.setRpsMyScore(newScoreA);
            msg.setRpsOpponentScore(newScoreB);
            spectators.get(i).sendMessage(msg);
        }
    }

    private void notifySpectatorsEnded()
    {
        for (int i = 0; i < spectators.size(); i++)
        {
            Message ended = new Message();
            ended.setType(MessageType.SPECTATE_ENDED);
            ended.setMatchId(matchId);
            spectators.get(i).sendMessage(ended);
        }
    }

    private void saveReplay()
    {
        if (replayManager == null)
        {
            return;
        }
        String result = scoreA > scoreB ? "PLAYER_A" : "PLAYER_B";
        replayManager.save("rock-paper-scissors", playerA.getLoggedInUsername(), playerB.getLoggedInUsername(), result, roundLog);
    }

    private void recordRating()
    {
        if (leaderboardManager == null || playerA.getAccountId() == null || playerB.getAccountId() == null)
        {
            return;
        }
        double outcomeForA = scoreA > scoreB ? 1.0 : 0.0;
        leaderboardManager.recordRatedMatch("rock-paper-scissors", playerA.getAccountId(), playerB.getAccountId(), outcomeForA);
    }

    private void awardWinner()
    {
        if (economyManager == null)
        {
            return;
        }
        economyManager.awardWin(scoreA > scoreB ? playerA : playerB, "rock-paper-scissors");
    }

    /** Returns 1 if moveA beats moveB, -1 if moveB wins, 0 for a draw. */
    private int resolve(String moveA, String moveB)
    {
        if (moveA.equals(moveB))
        {
            return 0;
        }
        if (("Rock".equals(moveA) && "Scissors".equals(moveB))
            || ("Paper".equals(moveA) && "Rock".equals(moveB))
            || ("Scissors".equals(moveA) && "Paper".equals(moveB)))
        {
            return 1;
        }
        return -1;
    }

    private void sendRoundResult(ClientHandler to, String myMove, String opponentMove, String result)
    {
        Message msg = new Message();
        msg.setType(MessageType.RPS_ROUND_RESULT);
        msg.setMatchId(matchId);
        msg.setRpsMove(myMove);
        msg.setRpsOpponentMove(opponentMove);
        msg.setMatchResult(result);
        msg.setRpsMyScore(to == playerA ? scoreA : scoreB);
        msg.setRpsOpponentScore(to == playerA ? scoreB : scoreA);
        to.sendMessage(msg);
    }

    private void sendMatchOver(ClientHandler to, String result)
    {
        Message msg = new Message();
        msg.setType(MessageType.RPS_MATCH_OVER);
        msg.setMatchId(matchId);
        msg.setMatchResult(result);
        msg.setRpsMyScore(to == playerA ? scoreA : scoreB);
        msg.setRpsOpponentScore(to == playerA ? scoreB : scoreA);
        to.sendMessage(msg);
    }

    public synchronized void handleDisconnect(ClientHandler who)
    {
        if (over)
        {
            return;
        }
        over = true;
        matchManager.endMatch(matchId);

        ClientHandler remaining = (who == playerA) ? playerB : playerA;
        Message msg = new Message();
        msg.setType(MessageType.RPS_MATCH_OVER);
        msg.setMatchId(matchId);
        msg.setMatchResult("OPPONENT_LEFT");
        remaining.sendMessage(msg);
        notifySpectatorsEnded();
    }
}
