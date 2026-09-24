package android.content

abstract class Context {
    abstract fun getSharedPreferences(name: String, mode: Int): SharedPreferences

    companion object {
        const val MODE_PRIVATE = 0
    }
}

interface SharedPreferences {
    fun contains(key: String): Boolean

    fun getBoolean(key: String, def: Boolean): Boolean

    fun getString(key: String, def: String?): String?

    fun getInt(key: String, def: Int): Int

    fun getLong(key: String, def: Long): Long

    fun edit(): Editor

    interface Editor {
        fun putBoolean(k: String, v: Boolean): Editor

        fun putString(k: String, v: String?): Editor

        fun putInt(k: String, v: Int): Editor

        fun putLong(k: String, v: Long): Editor

        fun remove(k: String): Editor

        fun clear(): Editor

        fun apply()
    }
}
