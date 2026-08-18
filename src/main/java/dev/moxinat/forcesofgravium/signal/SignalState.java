package dev.moxinat.forcesofgravium.signal;

public enum SignalState {
    PUSH,
    PULL,
    OFF;

    public SignalState inverted() {
        return switch (this) {
            case PUSH -> PULL;
            case PULL -> PUSH;
            case OFF -> OFF;
        };
    }
}
