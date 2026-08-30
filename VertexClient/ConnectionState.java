/**
 * ConnectionState
 * ---------------
 * The states shown by ConnectionIndicator. Phase 5 (Networking) wires
 * NetworkManager to actually call ConnectionIndicator.setState(...) as
 * the real connection changes - for now everything defaults to OFFLINE
 * since there's no server yet.
 */
public enum ConnectionState
{
    CONNECTING,
    ONLINE,
    OFFLINE,
    RECONNECTING
}
