package com.example.data

import android.util.Log
import com.example.BuildConfig

object DebugLog {
    fun i(message: String) {
        if (BuildConfig.DEBUG) {
            Log.i("KRISHIMITRA_DEBUG", message)
        }
    }

    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("KRISHIMITRA_DEBUG", message)
        }
    }

    fun w(message: String) {
        if (BuildConfig.DEBUG) {
            Log.w("KRISHIMITRA_DEBUG", message)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable == null) {
                Log.e("KRISHIMITRA_DEBUG", message)
            } else {
                Log.e("KRISHIMITRA_DEBUG", message, throwable)
            }
        }
    }
}
