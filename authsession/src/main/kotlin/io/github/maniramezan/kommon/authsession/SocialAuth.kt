package io.github.maniramezan.kommon.authsession

import android.app.Activity

/**
 * Federated / third-party identity provider sign-in operations.
 */
public interface SocialAuth {
    /** Signs in using a Google ID token. */
    public suspend fun signInWithGoogle(idToken: String): Result<AuthUser>

    /** Signs in using Apple authentication on [activity]. */
    public suspend fun signInWithApple(activity: Activity): Result<AuthUser>
}
