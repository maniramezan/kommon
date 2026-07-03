package io.github.maniramezan.kommon.authsession

/** A small persisted hint about the last known session, so a cold-start UI can render optimistically. */
public data class AuthSessionHint(
    val hasAccount: Boolean,
)

/** Persists [AuthSessionHint] across app launches (typically backed by encrypted SharedPreferences). */
public interface AuthSessionStore {
    public fun record(hint: AuthSessionHint)

    public fun read(): AuthSessionHint?

    public fun clear()
}
