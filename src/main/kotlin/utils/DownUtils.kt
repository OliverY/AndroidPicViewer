package utils

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object DownUtils {

    fun downloadZipAndExtractFile(url:String,destPath:String){
        val connection = URL(url).openConnection()
        val inputStream = connection.getInputStream()
        val zipInputStream = ZipInputStream(BufferedInputStream(inputStream))

        var entry: ZipEntry? = zipInputStream.nextEntry
        while (entry!=null){
            val entryPath = "$destPath/${entry.name}"

            if(entry.isDirectory){
                File(entryPath).mkdirs()
            }else{
                val outputStream = BufferedOutputStream(FileOutputStream(entryPath))
                val buffer = ByteArray(1024)
                var count: Int

                while (zipInputStream.read(buffer).also { count = it } != -1){
                    outputStream.write(buffer,0,count)
                }

                outputStream.close()
            }
            zipInputStream.closeEntry()
            entry = zipInputStream.nextEntry
        }
        zipInputStream.close()
    }
}