package com.stupidsavacan.clocky.design.storage

import android.content.Context
import android.content.SharedPreferences
import com.stupidsavacan.clocky.design.model.DigitalDesign
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** A user-saved design: library metadata around an independent snapshot of a [DigitalDesign]. */
data class SavedDesign(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val favorite: Boolean,
    val design: DigitalDesign,
)

/**
 * The My Designs library (Phase 3C-1). Saved designs are snapshots: placed widgets never read this
 * repository, so editing or deleting an entry cannot change a widget (and vice versa).
 */
interface DesignRepository {
    /** Saves [design] as a new entry (a fresh UUID). Throws [IllegalArgumentException] for a blank name. */
    fun create(name: String, design: DigitalDesign): SavedDesign

    /**
     * Saves an imported schema-2 `design` object as a new entry (a fresh UUID), storing it verbatim so unknown
     * fields survive (Phase 3C-2). Throws [IllegalArgumentException] for a blank name or a document the codec
     * cannot decode. Never touches an existing entry or a placed widget.
     */
    fun createFromDocument(name: String, design: JSONObject): SavedDesign

    /** The stored schema-2 `design` object of [id] exactly as saved (unknown fields included), or null. */
    fun rawDesign(id: String): JSONObject?

    fun get(id: String): SavedDesign?

    /** Valid entries, most recently updated first. Unreadable files are skipped, never deleted. */
    fun list(): List<SavedDesign>

    fun duplicate(id: String, name: String): SavedDesign?
    fun rename(id: String, name: String): SavedDesign?
    fun setFavorite(id: String, favorite: Boolean): SavedDesign?
    fun delete(id: String): Boolean
}

/**
 * One file per design: `<dir>/<uuid>.json` =
 * `{"format":1,"id","name","createdAt","updatedAt","favorite","design":{schema-2}}`.
 *
 * Writes go to a temp file that is renamed into place, so a crash leaves either the old or the new
 * document. Metadata-only updates (rename, favorite) edit the stored JSON and never re-encode the
 * `design` object, so unknown fields inside it survive; unknown envelope fields are kept too. A file
 * that cannot be decoded is ignored and left on disk.
 */
class FileDesignRepository(
    private val dir: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : DesignRepository {
    private val lock = Any()

    constructor(context: Context) : this(File(context.filesDir, DIR_NAME))

    override fun create(name: String, design: DigitalDesign): SavedDesign =
        createFromDocument(name, DigitalDesignCodec.encode(design))

    override fun createFromDocument(name: String, design: JSONObject): SavedDesign = synchronized(lock) {
        val clean = cleanName(name)
        // Decode first: a document the codec rejects must never reach disk.
        DigitalDesignCodec.decode(design)
        val now = clock()
        val id = freshId()
        val root = JSONObject()
            .put("format", FORMAT)
            .put("id", id)
            .put("name", clean)
            .put("createdAt", now)
            .put("updatedAt", now)
            .put("favorite", false)
            .put("design", JSONObject(design.toString()))
        write(id, root)
        checkNotNull(read(id)) { "Saved design could not be read back" }
    }

    override fun rawDesign(id: String): JSONObject? = synchronized(lock) {
        if (read(id) == null) null else readRoot(id)?.optJSONObject("design")
    }

    override fun get(id: String): SavedDesign? = synchronized(lock) { read(id) }

    override fun list(): List<SavedDesign> = synchronized(lock) {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) } ?: return emptyList()
        files.mapNotNull { read(it.name.removeSuffix(SUFFIX)) }
            .sortedWith(compareByDescending<SavedDesign> { it.updatedAt }.thenBy { it.id })
    }

    override fun duplicate(id: String, name: String): SavedDesign? = synchronized(lock) {
        val source = readRoot(id) ?: return null
        if (read(id) == null) return null
        val now = clock()
        val copyId = freshId()
        // Copy the stored JSON verbatim so unknown fields are carried over.
        val root = JSONObject(source.toString())
            .put("id", copyId)
            .put("name", cleanName(name))
            .put("createdAt", now)
            .put("updatedAt", now)
            .put("favorite", false)
        write(copyId, root)
        read(copyId)
    }

    override fun rename(id: String, name: String): SavedDesign? = synchronized(lock) {
        val clean = cleanName(name)
        update(id) { it.put("name", clean).put("updatedAt", clock()) }
    }

    override fun setFavorite(id: String, favorite: Boolean): SavedDesign? = synchronized(lock) {
        // Favorite is organisation metadata, not a design edit: updatedAt is left alone.
        update(id) { it.put("favorite", favorite) }
    }

    override fun delete(id: String): Boolean = synchronized(lock) {
        isValidId(id) && fileFor(id).delete()
    }

    private fun freshId(): String {
        repeat(MAX_ID_ATTEMPTS) {
            val id = newId()
            require(isValidId(id)) { "Invalid design id" }
            // A UUID collision is vanishingly unlikely, but an existing design is never overwritten.
            if (!fileFor(id).exists()) return id
        }
        error("Could not allocate a unique design id")
    }

    private fun update(id: String, change: (JSONObject) -> JSONObject): SavedDesign? {
        val root = readRoot(id) ?: return null
        if (read(id) == null) return null
        write(id, change(root))
        return read(id)
    }

    private fun read(id: String): SavedDesign? {
        val root = readRoot(id) ?: return null
        return runCatching {
            if (root.getString("id") != id) return null
            SavedDesign(
                id = id,
                name = root.getString("name"),
                createdAt = root.getLong("createdAt"),
                updatedAt = root.getLong("updatedAt"),
                favorite = root.optBoolean("favorite", false),
                design = DigitalDesignCodec.decode(root.getJSONObject("design")),
            )
        }.getOrNull()
    }

    private fun readRoot(id: String): JSONObject? {
        if (!isValidId(id)) return null
        val file = fileFor(id)
        if (!file.isFile) return null
        return runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
    }

    private fun write(id: String, root: JSONObject) {
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create ${dir.path}" }
        val target = fileFor(id)
        val tmp = File(dir, id + TMP_SUFFIX)
        FileOutputStream(tmp).use { out ->
            out.write(root.toString().toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        // rename(2) replaces atomically on Android; a filesystem that refuses to replace (Windows JVM
        // in unit tests) needs the target removed first.
        if (!tmp.renameTo(target) && !(target.delete() && tmp.renameTo(target))) {
            tmp.delete()
            error("Cannot write " + target.name)
        }
    }

    private fun fileFor(id: String) = File(dir, id + SUFFIX)

    companion object {
        const val DIR_NAME = "designs"
        const val FORMAT = 1
        const val MAX_NAME_LENGTH = 40
        private const val SUFFIX = ".json"
        private const val TMP_SUFFIX = ".json.tmp"
        private const val MAX_ID_ATTEMPTS = 8
        private val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        /** Ids become file names, so only canonical lowercase UUIDs are accepted (no path traversal). */
        fun isValidId(id: String) = UUID_PATTERN.matches(id)

        /** Whitespace-collapsed, single-line and bounded; blank input is rejected rather than stored. */
        fun cleanName(raw: String): String {
            val name = raw.replace(Regex("\\s+"), " ").trim().take(MAX_NAME_LENGTH).trim()
            require(name.isNotEmpty()) { "Design name must not be blank" }
            return name
        }
    }
}

/**
 * Favorite flags for the immutable built-in designs. Built-ins themselves are never stored or
 * modified; only their ids are recorded here.
 */
class BuiltinFavorites(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun ids(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun isFavorite(builtinId: String) = builtinId in ids()

    fun set(builtinId: String, favorite: Boolean) {
        val next = ids().let { if (favorite) it + builtinId else it - builtinId }
        prefs.edit().putStringSet(KEY, next).apply()
    }

    private companion object {
        const val PREFS_NAME = "clocky_design_library"
        const val KEY = "builtin.favorites"
    }
}
