package io.github.maniramezan.kommon.authsession

/**
 * Provider-agnostic authentication repository combining [AuthStateProvider], [AnonymousAuth],
 * [EmailPasswordAuth], and [SocialAuth]. Bridge this to Firebase Auth, Supabase Auth, or
 * any other auth backend.
 *
 * For consumers that only need a subset of capabilities (e.g. only session observation or only
 * email authentication), depend on the narrower interfaces directly.
 */
public interface AuthRepository :
    AuthStateProvider,
    AnonymousAuth,
    EmailPasswordAuth,
    SocialAuth
