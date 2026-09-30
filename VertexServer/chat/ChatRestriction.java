package chat;

/**
 * ChatRestriction
 * ----------------
 * Whether players in a match chat room may currently send messages. Enforced by
 * the server in MatchChatRoom.post(...) - the client only mirrors it (a locked dock
 * disables its input), so a modified client can't bypass a restriction.
 */
public enum ChatRestriction
{
    /** Everyone in the room can send. */
    OPEN,
    /** Nobody can send - e.g. Telephone, where free chat would let players just say the answer out loud. The game lifts it (room.unlock()) once that stops mattering. */
    LOCKED
}
