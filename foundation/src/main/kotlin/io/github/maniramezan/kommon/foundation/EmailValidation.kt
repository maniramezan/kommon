package io.github.maniramezan.kommon.foundation

/**
 * Same grammar as `android.util.Patterns.EMAIL_ADDRESS`, reimplemented in pure Kotlin so the check
 * works in plain JVM unit tests (where `Patterns` is an unmocked stub) and on non-Android targets.
 */
private val EMAIL_ADDRESS =
    Regex(
        "[a-zA-Z0-9+._%\\-]{1,256}" +
            "@" +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
            "(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+",
    )

/** True when the trimmed string parses as a syntactically valid email address. */
public fun String.isValidEmail(): Boolean = EMAIL_ADDRESS.matches(trim())
