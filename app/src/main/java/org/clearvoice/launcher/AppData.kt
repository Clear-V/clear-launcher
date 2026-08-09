package org.clearvoice.launcher

import android.graphics.drawable.Drawable

data class AppInfo(
    val name: String,
    val packageName: String,
    val isEnabled: Boolean = true,
    val icon: Drawable? = null
)