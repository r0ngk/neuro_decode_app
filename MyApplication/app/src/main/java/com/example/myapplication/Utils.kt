package com.example.myapplication

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class Utils {

    public fun assetFilePath(context: Context, fileName: String): String {
        val file = File(context.filesDir, fileName)
        try {
            val inputStream: InputStream = context.assets.open(fileName)
            try {
                val outputStream = FileOutputStream(file, false)
                val buffer = ByteArray(4*1024)
                var read: Int
                while (true) {
                    read = inputStream.read(buffer)
                    if (read == -1) {
                        break
                    }
                    outputStream.write(buffer, 0, read)
                }
                outputStream.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }
}