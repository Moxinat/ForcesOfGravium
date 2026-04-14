package dev.moxinat.forcesofgravium.logic.network;

public enum SignalMode {
    PUSH,
    PULL;

    public SignalMode inverted() {
        return this == PUSH ? PULL : PUSH;
    }
}
