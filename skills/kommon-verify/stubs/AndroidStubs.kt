// Compile-only stand-ins for the handful of Android framework types kommon's main sources touch.
// Used exclusively by jvm-verify.sh when Google Maven (AGP / android.jar) is unreachable. Never
// shipped; never used to run Android-dependent tests (those still need Robolectric in real CI).
@file:Suppress("unused")

package android.app

open class Activity
