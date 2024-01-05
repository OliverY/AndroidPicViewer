package utils

import java.io.File

object UnzipUtils {

    fun runUnzipCommand(zipFilePath: String, destinationDirectory: String) {
        val processBuilder = ProcessBuilder("unzip", zipFilePath, "-d", destinationDirectory)
        processBuilder.directory(File(destinationDirectory))

        try {
            val process = processBuilder.start()
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                println("解压缩成功")
            } else {
                println("解压缩失败")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}