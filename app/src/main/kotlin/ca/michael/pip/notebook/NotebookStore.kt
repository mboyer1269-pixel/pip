package ca.michael.pip.notebook

import android.content.Context
import ca.michael.pip.core.notebook.GcmBlob
import ca.michael.pip.core.notebook.NotebookCodec
import ca.michael.pip.core.notebook.NotebookEntry
import ca.michael.pip.security.KeystoreKeys
import java.io.File

/**
 * Encrypted on-device notebook (V1-5). One file: IV + AES-GCM ciphertext of
 * the JSON array (SPEC 6.1). Never backed up, synced or exported (FR-5.4).
 */
class NotebookStore(context: Context) {
    private val file = File(context.filesDir, "notebook.bin")
    private var cache: List<NotebookEntry>? = null

    @Synchronized
    fun all(): List<NotebookEntry> {
        cache?.let { return it }
        val loaded = if (!file.exists()) emptyList() else {
            val plain = GcmBlob.open(KeystoreKeys.getOrCreate(KeystoreKeys.NOTEBOOK), file.readBytes())
            NotebookCodec.decode(plain.toString(Charsets.UTF_8))
        }
        cache = loaded
        return loaded
    }

    @Synchronized
    fun upsert(entry: NotebookEntry) {
        val list = all().filterNot { it.id == entry.id } + entry
        save(list.sortedBy { it.createdAt })
    }

    @Synchronized
    fun delete(id: String) = save(all().filterNot { it.id == id })

    /** FR-5.3: erase all deletes the data and the Keystore key. */
    @Synchronized
    fun eraseAll() {
        file.delete()
        KeystoreKeys.delete(KeystoreKeys.NOTEBOOK)
        cache = emptyList()
    }

    private fun save(list: List<NotebookEntry>) {
        // Fresh random IV on every write (FR-5.2), via GcmBlob.
        val sealed = GcmBlob.seal(KeystoreKeys.getOrCreate(KeystoreKeys.NOTEBOOK), NotebookCodec.encode(list).toByteArray(Charsets.UTF_8))
        val tmp = File(file.parentFile, "notebook.bin.tmp")
        tmp.writeBytes(sealed)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        cache = list
    }
}
