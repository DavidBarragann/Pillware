package com.example.pillware.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import java.security.MessageDigest

object Util {
    @RequiresApi(Build.VERSION_CODES.P)
    fun printFacebookKeyHash(context: Context) {
        try {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES // Para API >= 28
            )
            val signatures =
                info.signingInfo!!.apkContentsSigners

            for (signature in signatures!!) {
                val md = MessageDigest.getInstance("SHA")
                md.update(signature.toByteArray())
                val keyHash = Base64.encodeToString(md.digest(), Base64.NO_WRAP)
                Log.d("Facebook Key Hash", keyHash)
            }
        } catch (e: Exception) {
            Log.e("KeyHash", "Error printing key hash", e)
        }
    }
}