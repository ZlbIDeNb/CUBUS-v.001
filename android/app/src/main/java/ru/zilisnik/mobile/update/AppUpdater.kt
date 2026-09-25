package ru.zilisnik.mobile.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import ru.zilisnik.mobile.data.AppUpdateInfo
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object AppUpdater {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .callTimeout(10, TimeUnit.MINUTES)
        .build()

    suspend fun prepareApk(context: Context, update: AppUpdateInfo): File =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(directory, "CUBUS-${update.version_code}.apk")
            if (target.isFile && sha256(target).equals(update.sha256, ignoreCase = true)) {
                return@withContext target
            }
            target.delete()
            val temporary = File(directory, "${target.name}.part")
            temporary.delete()

            val request = Request.Builder().url(update.download_url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Сервер обновлений вернул HTTP ${response.code}")
                }
                val body = response.body ?: error("Сервер вернул пустой файл")
                body.byteStream().use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
            }
            if (update.file_size > 0 && temporary.length() != update.file_size) {
                temporary.delete()
                error("Размер загруженного обновления не совпадает")
            }
            if (!sha256(temporary).equals(update.sha256, ignoreCase = true)) {
                temporary.delete()
                error("Проверка безопасности обновления не пройдена")
            }
            check(temporary.renameTo(target)) { "Не удалось сохранить обновление" }
            target
        }

    fun requestInstall(context: Context, apk: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            apk,
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        return true
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
