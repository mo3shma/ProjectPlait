package app.plait.ai

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

/**
 * Looks for model files the user already has, so nothing is downloaded twice. A file counts only if its name matches what
 * the host calls it (or Plait's own name for it) and its size is exactly right. Matching files are copied into Plait's own
 * storage; the originals are left alone.
 */
object ModelFinder {
    data class Found(val spec: ModelSpec, val file: ModelFile)

    /** Which expected file, if any, a file called [name] of [length] bytes is. */
    fun match(name: String, length: Long): Found? {
        for (spec in ModelCatalog.all) for (f in spec.files) {
            if (length == f.bytes && (name.equals(f.sourceName, ignoreCase = true) || (f.name != "model.task" && name.equals(f.name, ignoreCase = true)))) return Found(spec, f)
        }
        return null
    }

    data class Report(val copied: List<String>, val completed: List<ModelSpec>, val partial: List<String>)

    /** The folder a person can copy model files into over USB, with no permission needed: Android/data/<app>/files/models. */
    fun dropFolder(context: Context): File? = context.getExternalFilesDir("models")?.also { it.mkdirs() }

    /** Looks in the drop folder and in Plait's own folder for files that belong to a model but have not been counted yet. */
    fun scanThisPhone(context: Context): Report {
        val roots = listOfNotNull(dropFolder(context), File(context.filesDir, "models"))
        val found = ArrayList<Pair<Found, File>>()
        for (root in roots) root.walkTopDown().maxDepth(3).filter { it.isFile && !it.name.endsWith(".part") }.forEach { f ->
            match(f.name, f.length())?.let { found.add(it to f) }
        }
        return importAll(context, found.map { (m, file) -> Candidate(m) { out -> file.inputStream().use { it.copyTo(out) } } })
    }

    /** Looks inside a folder the user picked (any depth up to three), including cloud-backed ones. */
    fun scanTree(context: Context, tree: Uri): Report {
        val root = DocumentFile.fromTreeUri(context, tree) ?: return Report(emptyList(), emptyList(), emptyList())
        val found = ArrayList<Candidate>()
        fun walk(d: DocumentFile, depth: Int) {
            for (c in d.listFiles()) {
                if (c.isDirectory && depth < 3) walk(c, depth + 1)
                else if (c.isFile) match(c.name.orEmpty(), c.length())?.let { m -> found.add(Candidate(m) { out -> context.contentResolver.openInputStream(c.uri)?.use { it.copyTo(out) } }) }
            }
        }
        walk(root, 0)
        return importAll(context, found)
    }

    /** Individual files the user picked. */
    fun scanFiles(context: Context, uris: List<Uri>): Report {
        val found = uris.mapNotNull { u ->
            val doc = DocumentFile.fromSingleUri(context, u) ?: return@mapNotNull null
            match(doc.name.orEmpty(), doc.length())?.let { m -> Candidate(m) { out -> context.contentResolver.openInputStream(u)?.use { it.copyTo(out) } } }
        }
        return importAll(context, found)
    }

    private class Candidate(val found: Found, val copyTo: (java.io.OutputStream) -> Unit)

    private fun importAll(context: Context, candidates: List<Candidate>): Report {
        val copied = ArrayList<String>()
        for (c in candidates.distinctBy { it.found.spec.id + "/" + it.found.file.name }) {
            val target = File(ModelCenter.dir(context, c.found.spec.id).also { it.mkdirs() }, c.found.file.name)
            if (target.exists() && target.length() == c.found.file.bytes) continue // already here
            val tmp = File(target.parentFile, target.name + ".part")
            runCatching {
                FileOutputStream(tmp).use { c.copyTo(it) }
                if (tmp.length() == c.found.file.bytes && tmp.renameTo(target)) copied.add("${c.found.spec.title}: ${c.found.file.sourceName}") else tmp.delete()
            }.onFailure { tmp.delete() }
        }
        ModelCenter.refresh(context)
        val completed = ModelCatalog.all.filter { ModelCenter.completeOnDisk(context, it) && ModelCenter.adopt(context, it) }
        completed.filter { s -> copied.any { it.startsWith(s.title) } }.forEach { AiPrefs.makeDefault(context, it) }
        val partial = ModelCatalog.all.filter { m -> !ModelCenter.isInstalled(context, m.id) && m.files.any { f -> File(ModelCenter.dir(context, m.id), f.name).let { it.exists() && it.length() == f.bytes } } }
            .map { it.title }
        return Report(copied, completed.filter { s -> copied.any { it.startsWith(s.title) } }, partial)
    }
}
