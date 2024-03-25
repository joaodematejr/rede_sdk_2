package com.pocrede.rede

import android.content.Context
import android.widget.Toast
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class RedeModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "RedeModule"

    @ReactMethod(isBlockingSynchronousMethod = true)
    fun show(title: String) {
        val context: Context = reactApplicationContext
        Toast.makeText(context, title, Toast.LENGTH_LONG).show();
    }

}