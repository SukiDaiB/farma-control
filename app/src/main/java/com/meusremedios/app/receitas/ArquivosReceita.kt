package com.meusremedios.app.receitas

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.meusremedios.app.data.Receita
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

data class ArquivoReceita(val nome: String, val tipo: String) {
    val ehPdf: Boolean get() = tipo == TIPO_PDF

    companion object {
        const val TIPO_PDF = "application/pdf"
        const val TIPO_JPEG = "image/jpeg"
    }
}

/** Guarda os arquivos das receitas na pasta interna do app (incluída no backup do Android). */
class ArquivosReceita(private val context: Context) {
    private val pasta: File get() = File(context.filesDir, "receitas").apply { mkdirs() }
    private val fotoCamera: File get() = File(File(context.cacheDir, "camera").apply { mkdirs() }, "foto.jpg")

    fun arquivo(nome: String): File = File(pasta, nome)

    /** Uri onde o app de câmera deve salvar a foto. */
    fun uriParaCamera(): Uri = FileProvider.getUriForFile(context, AUTORIDADE, fotoCamera)

    suspend fun importarFotoDaCamera(): ArquivoReceita = withContext(Dispatchers.IO) {
        salvarImagem(decodificar(Uri.fromFile(fotoCamera))).also { fotoCamera.delete() }
    }

    suspend fun importar(uri: Uri): ArquivoReceita = withContext(Dispatchers.IO) {
        if (context.contentResolver.getType(uri) == ArquivoReceita.TIPO_PDF) {
            val nome = "${UUID.randomUUID()}.pdf"
            context.contentResolver.openInputStream(uri)!!.use { entrada ->
                FileOutputStream(arquivo(nome)).use { entrada.copyTo(it) }
            }
            ArquivoReceita(nome, ArquivoReceita.TIPO_PDF)
        } else {
            salvarImagem(decodificar(uri))
        }
    }

    fun apagar(nome: String) {
        arquivo(nome).delete()
    }

    /** Páginas prontas para mostrar: a própria foto ou cada página do PDF. */
    suspend fun renderizar(receita: Receita, larguraPx: Int): List<Bitmap> = withContext(Dispatchers.IO) {
        val arquivo = arquivo(receita.arquivo)
        if (receita.tipoArquivo != ArquivoReceita.TIPO_PDF) {
            return@withContext listOfNotNull(BitmapFactory.decodeFile(arquivo.path))
        }
        ParcelFileDescriptor.open(arquivo, ParcelFileDescriptor.MODE_READ_ONLY).use { descritor ->
            PdfRenderer(descritor).use { pdf ->
                (0 until minOf(pdf.pageCount, MAX_PAGINAS_PDF)).map { indice ->
                    pdf.openPage(indice).use { pagina ->
                        val altura = larguraPx * pagina.height / pagina.width
                        Bitmap.createBitmap(larguraPx, altura, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(Color.WHITE)
                            pagina.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
    }

    fun compartilhar(context: Context, receita: Receita) {
        val uri = uri(receita)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(receita.tipoArquivo)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, receita.titulo)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri(receita.titulo, uri)
        context.startActivity(Intent.createChooser(intent, "Compartilhar receita"))
    }

    fun abrirComOutroApp(context: Context, receita: Receita) {
        val uri = uri(receita)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, receita.tipoArquivo)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri(receita.titulo, uri)
        context.startActivity(Intent.createChooser(intent, "Abrir receita com"))
    }

    private fun uri(receita: Receita): Uri = FileProvider.getUriForFile(context, AUTORIDADE, arquivo(receita.arquivo))

    /** Reduz a foto (o lado maior fica com até [LADO_MAXIMO] px), respeitando a rotação da câmera. */
    private fun decodificar(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val fonte = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(fonte) { decoder, info, _ ->
                val escala = minOf(1f, LADO_MAXIMO.toFloat() / max(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * escala).toInt(), (info.size.height * escala).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, limites) }
        var amostra = 1
        while (max(limites.outWidth, limites.outHeight) / amostra > LADO_MAXIMO) amostra *= 2
        return context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = amostra })!!
        }
    }

    private fun salvarImagem(bitmap: Bitmap): ArquivoReceita {
        val nome = "${UUID.randomUUID()}.jpg"
        FileOutputStream(arquivo(nome)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALIDADE_JPEG, it) }
        bitmap.recycle()
        return ArquivoReceita(nome, ArquivoReceita.TIPO_JPEG)
    }

    private companion object {
        const val AUTORIDADE = "com.meusremedios.app.arquivos"
        const val LADO_MAXIMO = 2000
        const val QUALIDADE_JPEG = 85
        const val MAX_PAGINAS_PDF = 30
    }
}
