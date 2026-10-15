package com.zairvek.zairauto.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import com.zairvek.zairauto.R
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

/**
 * Android Auto se ejecuta en otro proceso y no puede leer los URIs del almacenamiento que el
 * usuario nos concedió. Este proveedor de solo lectura le sirve:
 *  - /icon/<nombre>   iconos de categorías renderizados a PNG
 *  - /file?u=...      imágenes de carátula (cover.jpg, folder.jpg...) de las carpetas concedidas
 *  - /embedded?u=...  la carátula incrustada en el propio archivo de audio
 */
class ArtworkProvider : ContentProvider() {

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Solo lectura")
        val ctx = context ?: throw FileNotFoundException()
        val token = Binder.clearCallingIdentity()
        try {
            return when (uri.pathSegments.firstOrNull()) {
                "icon" -> openIcon(ctx, uri.lastPathSegment.orEmpty())
                "file" -> {
                    val doc = allowedDoc(ctx, uri)
                    ctx.contentResolver.openFileDescriptor(doc, "r") ?: throw FileNotFoundException()
                }
                "embedded" -> openEmbedded(ctx, allowedDoc(ctx, uri))
                else -> throw FileNotFoundException(uri.toString())
            }
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun openIcon(ctx: Context, name: String): ParcelFileDescriptor {
        val res = ICONS[name] ?: throw FileNotFoundException(name)
        val file = File(ctx.cacheDir, "icons/$name.png")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            val d = ContextCompat.getDrawable(ctx, res)!!.mutate()
            d.setTint(Color.WHITE)
            val bmp = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, ICON_PX, ICON_PX)
            d.draw(Canvas(bmp))
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun openEmbedded(ctx: Context, doc: Uri): ParcelFileDescriptor {
        val dir = File(ctx.cacheDir, "art").apply { mkdirs() }
        val file = File(dir, sha1(doc.toString()))
        val missing = File(dir, sha1(doc.toString()) + ".none")
        if (missing.exists()) throw FileNotFoundException("Sin carátula")
        if (!file.exists()) {
            val bytes = MediaMetadataRetriever().run {
                try {
                    setDataSource(ctx, doc)
                    embeddedPicture
                } catch (e: Exception) {
                    null
                } finally {
                    release()
                }
            }
            if (bytes == null) {
                missing.createNewFile()
                throw FileNotFoundException("Sin carátula")
            }
            file.writeBytes(bytes)
            trimCache(dir)
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /** Solo servimos documentos que pertenecen a una carpeta que el usuario concedió. */
    private fun allowedDoc(ctx: Context, uri: Uri): Uri {
        val doc = Uri.parse(uri.getQueryParameter("u") ?: throw FileNotFoundException())
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(doc) }.getOrNull() ?: throw FileNotFoundException()
        val ok = ctx.contentResolver.persistedUriPermissions.any {
            it.isReadPermission && it.uri.authority == doc.authority &&
                runCatching { DocumentsContract.getTreeDocumentId(it.uri) }.getOrNull() == treeId
        }
        if (!ok) throw FileNotFoundException()
        return doc
    }

    private fun trimCache(dir: File) {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_CACHE) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_CACHE).forEach { it.delete() }
    }

    override fun getType(uri: Uri): String = when (uri.pathSegments.firstOrNull()) {
        "icon" -> "image/png"
        else -> "image/*"
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        private const val ICON_PX = 192
        private const val MAX_CACHE = 400

        private val ICONS = mapOf(
            "folder" to R.drawable.ic_folder,
            "star" to R.drawable.ic_star,
            "history" to R.drawable.ic_history,
            "radio" to R.drawable.ic_radio,
            "music" to R.drawable.ic_music,
            "movie" to R.drawable.ic_movie,
            "shuffle" to R.drawable.ic_shuffle,
            "library" to R.drawable.ic_library,
            "search" to R.drawable.ic_search,
        )

        private fun base(ctx: Context) = Uri.Builder().scheme("content").authority(ctx.packageName + ".art")

        fun icon(ctx: Context, name: String): Uri = base(ctx).appendPath("icon").appendPath(name).build()

        fun file(ctx: Context, docUri: String): Uri = base(ctx).appendPath("file").appendQueryParameter("u", docUri).build()

        fun embedded(ctx: Context, docUri: String): Uri = base(ctx).appendPath("embedded").appendQueryParameter("u", docUri).build()

        private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
