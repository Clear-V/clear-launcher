package org.clearvoice.launcher

import android.app.Application
import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps

    private val _enabledApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val enabledApps: StateFlow<List<AppInfo>> = _enabledApps

    init { loadApps() }

    fun loadApps() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
            val enabledPackages = PinStorage.getEnabledApps(context)

            val apps = resolveInfos
                .map { info ->
                    AppInfo(
                        name = info.loadLabel(pm).toString(),
                        packageName = info.activityInfo.packageName,
                        isEnabled = enabledPackages.contains(info.activityInfo.packageName),
                        icon = info.loadIcon(pm)
                    )
                }
                .filter { it.packageName != context.packageName }
                .sortedBy { it.name }

            _allApps.value = apps
            _enabledApps.value = if (PinStorage.isFirstRun(context)) {
                apps
            } else {
                apps.filter { it.isEnabled }
            }
        }
    }

    fun toggleApp(packageName: String, enabled: Boolean) {
        val context = getApplication<Application>()
        val current = PinStorage.getEnabledApps(context).toMutableSet()
        if (enabled) current.add(packageName) else current.remove(packageName)
        PinStorage.setEnabledApps(context, current)
        loadApps()
    }

    fun launchApp(packageName: String) {
        val context = getApplication<Application>()
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        intent?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(it) }
    }
}