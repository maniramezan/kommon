package io.github.maniramezan.kommon.authsession

/** Provider-agnostic authenticated user shape. */
public data class AuthUser(
    val uid: String,
    val isAnonymous: Boolean,
    val displayName: String? = null,
    val email: String? = null,
)
