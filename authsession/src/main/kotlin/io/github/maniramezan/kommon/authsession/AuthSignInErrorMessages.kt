package io.github.maniramezan.kommon.authsession

/**
 * Maps a sign-in failure to a short, user-facing message.
 *
 * Detects the common "package certificate hash mismatch" failure mode (Google/Firebase Auth
 * SHA-1 fingerprint misconfiguration) and surfaces a distinct, actionable message for it instead
 * of a raw, developer-facing exception string.
 */
public fun Throwable.authSignInErrorMessage(
    providerName: String,
    genericFailureMessage: String = "Sign in failed. Please try again.",
): String {
    val detail = message.orEmpty()
    return if (
        detail.contains("package certificate hash", ignoreCase = true) ||
        detail.contains("INVALID_CERT_HASH", ignoreCase = true)
    ) {
        "$providerName sign-in is unavailable right now. Please try again later."
    } else {
        message ?: genericFailureMessage
    }
}
