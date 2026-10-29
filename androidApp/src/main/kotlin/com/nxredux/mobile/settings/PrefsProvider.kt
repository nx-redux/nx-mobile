package com.nxredux.mobile.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.nxredux.mobile.storage.DataRoot

/**
 * The main process's settings, served to the game process (see [ProviderPreferences]). The provider has no `process`
 * attribute, so it always runs where the real [android.content.SharedPreferences] live. Not exported.
 */
class PrefsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        when (method) {
            METHOD_DATA_ROOT_SETTLED -> return encode(mapOf(RESULT_PATH to DataRoot.settled(context).path))   // blocks while a move runs: a binder thread
            METHOD_DATA_ROOT_USE -> {
                if (decode(extras ?: Bundle())[ARG_BEGIN] == true) DataRoot.useBegin(context) else DataRoot.useEnd(context)
                return encode(mapOf(RESULT_OK to true))
            }
        }
        val prefs = context.getSharedPreferences(arg ?: return null, Context.MODE_PRIVATE)
        return when (method) {
            METHOD_GET_ALL -> encode(prefs.all)
            METHOD_APPLY -> {
                val args = decode(extras ?: Bundle())
                val editor = prefs.edit()
                if (args[ARG_CLEAR] == true) editor.clear()
                @Suppress("UNCHECKED_CAST")
                (args[ARG_REMOVE] as? List<String>)?.forEach { editor.remove(it) }
                @Suppress("UNCHECKED_CAST")
                (args[ARG_PUT] as? Map<String, Any?>)?.forEach { (key, value) ->
                    @Suppress("UNCHECKED_CAST")
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                        is Set<*> -> editor.putStringSet(key, (value as Set<String>).toMutableSet())
                    }
                }
                encode(mapOf(RESULT_OK to editor.commit()))
            }
            else -> null
        }
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    companion object {
        const val METHOD_GET_ALL = "getAll"
        /** Extras [ARG_PUT] (key to value), [ARG_REMOVE] (keys), [ARG_CLEAR]; applied with commit. Result [RESULT_OK]. */
        const val METHOD_APPLY = "apply"
        const val ARG_PUT = "put"
        const val ARG_REMOVE = "remove"
        const val ARG_CLEAR = "clear"
        const val RESULT_OK = "ok"
        /** The data root as [DataRoot.settled] answers in the main process; result [RESULT_PATH]. The `arg` name is unused. */
        const val METHOD_DATA_ROOT_SETTLED = "dataRootSettled"
        /** [ARG_BEGIN] true: [DataRoot.useBegin], false: [DataRoot.useEnd]; the game process holds off the data move through it. */
        const val METHOD_DATA_ROOT_USE = "dataRootUse"
        const val ARG_BEGIN = "begin"
        const val RESULT_PATH = "path"

        fun authority(context: Context) = context.packageName + ".prefs"

        /** The calls from the game process, over the content resolver. */
        fun channel(context: Context): PrefsChannel {
            val resolver = context.applicationContext.contentResolver
            val uri = Uri.parse("content://" + authority(context))
            return PrefsChannel { method, name, args ->
                resolver.call(uri, method, name, encode(args))?.let(::decode) ?: error("No answer from the settings provider")
            }
        }

        private const val SCALARS = "v"
        private const val SETS = "s"
        private const val LISTS = "l"
        private const val MAPS = "m"

        /**
         * A Bundle cannot tell a `Set<String>` from a list (both go as an ArrayList), so each kind sits under its own
         * sub-bundle: scalars, sets, lists, nested maps.
         */
        @Suppress("UNCHECKED_CAST")
        fun encode(map: Map<String, Any?>): Bundle {
            val scalars = Bundle(); val sets = Bundle(); val lists = Bundle(); val maps = Bundle()
            for ((key, value) in map) when (value) {
                is Boolean -> scalars.putBoolean(key, value)
                is Int -> scalars.putInt(key, value)
                is Long -> scalars.putLong(key, value)
                is Float -> scalars.putFloat(key, value)
                is String -> scalars.putString(key, value)
                is Set<*> -> sets.putStringArrayList(key, ArrayList(value as Set<String>))
                is List<*> -> lists.putStringArrayList(key, ArrayList(value as List<String>))
                is Map<*, *> -> maps.putBundle(key, encode(value as Map<String, Any?>))
            }
            return Bundle().apply { putBundle(SCALARS, scalars); putBundle(SETS, sets); putBundle(LISTS, lists); putBundle(MAPS, maps) }
        }

        @Suppress("DEPRECATION")
        fun decode(bundle: Bundle): Map<String, Any?> {
            val out = HashMap<String, Any?>()
            bundle.getBundle(SCALARS)?.let { b -> b.keySet().forEach { out[it] = b.get(it) } }
            bundle.getBundle(SETS)?.let { b -> b.keySet().forEach { out[it] = HashSet(b.getStringArrayList(it).orEmpty()) } }
            bundle.getBundle(LISTS)?.let { b -> b.keySet().forEach { out[it] = ArrayList(b.getStringArrayList(it).orEmpty()) } }
            bundle.getBundle(MAPS)?.let { b -> b.keySet().forEach { out[it] = decode(b.getBundle(it)!!) } }
            return out
        }
    }
}
