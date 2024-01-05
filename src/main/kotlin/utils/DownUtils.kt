package utils

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object DownUtils {

    fun downloadFile(url: String, destinationDirectory: String) {
        val fileName = url.substringAfterLast("/")
        val outputFile = File(destinationDirectory, fileName)

        BufferedInputStream(URL(url).openStream()).use { inputStream ->
            FileOutputStream(outputFile).use { outputStream ->
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
        }
    }
}