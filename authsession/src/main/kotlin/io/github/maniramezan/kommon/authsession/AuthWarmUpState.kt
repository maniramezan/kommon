package io.github.maniramezan.kommon.authsession

/** Progress of [AuthSessionInitializer]'s cold-start token warm-up. */
public enum class AuthWarmUpState {
    /** [AuthSessionInitializer.start] has not been called, or warm-up was stopped before it finished. */
    IDLE,

    /** A token is being minted or refreshed. */
    WARMING,

    /** Warm-up finished with an ID token; requests can be authenticated. */
    READY,

    /** Warm-up finished, but the provider returned no ID token. */
    NO_TOKEN,

    /** Warm-up failed; the error was logged and auth-state observation continues. */
    FAILED,
    ;

    /** `true` once warm-up has finished, whatever the outcome. */
    public val isFinished: Boolean
        get() = this == READY || this == NO_TOKEN || this == FAILED
}
