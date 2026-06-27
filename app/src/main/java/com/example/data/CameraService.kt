package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.InputStream

class CameraService(private val context: Context) {

    /**
     * Converts a selection URI from the gallery to an Android Bitmap.
     */
    fun getBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Helper to scale and prepare a bitmap for AI vision analysis (usually 224x224).
     */
    fun preprocessBitmapForVision(bitmap: Bitmap, targetWidth: Int = 224, targetHeight: Int = 224): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    /**
     * Decodes local storage file to Bitmap
     */
    fun getBitmapFromFilePath(filePath: String): Bitmap? {
        val file = java.io.File(filePath)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }
}
