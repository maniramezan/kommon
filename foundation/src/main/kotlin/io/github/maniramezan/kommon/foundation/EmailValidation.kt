package io.github.maniramezan.kommon.foundation

import android.util.Patterns

/** True when the trimmed string parses as a syntactically valid email address. */
public fun String.isValidEmail(): Boolean = Patterns.EMAIL_ADDRESS.matcher(this.trim()).matches()
