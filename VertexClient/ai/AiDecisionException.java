package ai;

/**
 * AiDecisionException
 * --------------------
 * Thrown only in the (should-never-happen-in-practice) case where BOTH
 * a game's primary AND its fallback AI strategy fail for the same
 * decision - see AiKernel.chooseMove(). A fallback strategy is meant
 * to be trivial enough that it essentially can't fail (e.g. "pick any
 * legal move at random"), so this exception existing at all is a
 * signal that a game's AI wiring is fundamentally broken, not a normal
 * operating condition to design around everywhere.
 */
public class AiDecisionException extends RuntimeException
{
    public AiDecisionException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
