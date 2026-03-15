package app.olauncher.helper

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs

class PinItemActivity : AppCompatActivity() {

    private var pendingPinRequest: LauncherApps.PinItemRequest? = null
    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val appWidgetHost by lazy { AppWidgetHost(this, Constants.APP_WIDGET_HOST_ID) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set window to be transparent
        window.setBackgroundDrawable(null)

        val launcherApps = getSystemService(LauncherApps::class.java)
        val pinItemRequest = launcherApps.getPinItemRequest(intent)

        if (pinItemRequest == null) {
            showToast("Invalid pin request")
            finish()
            return
        }

        when (pinItemRequest.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> {
                handleShortcutRequest(pinItemRequest)
                finish()
            }

            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET -> {
                handleWidgetRequest(pinItemRequest)
                // Don't finish yet — may need to wait for the bind permission dialog
            }

            else -> {
                showToast("Unknown action not supported")
                finish()
            }
        }
    }

    private fun handleShortcutRequest(pinItemRequest: LauncherApps.PinItemRequest) {
        val shortcutInfo = pinItemRequest.shortcutInfo
        if (shortcutInfo != null) {
            val success = pinItemRequest.accept()
            val message = when (success) {
                true -> "Shortcut pinned successfully"
                false -> "Failed to pin shortcut"
            }
            showToast(message)
        } else {
            showToast("Invalid shortcut info")
        }
    }

    private fun handleWidgetRequest(pinItemRequest: LauncherApps.PinItemRequest) {
        val providerInfo = pinItemRequest.appWidgetProviderInfo
        if (providerInfo == null) {
            showToast("Invalid widget info")
            finish()
            return
        }

        pendingPinRequest = pinItemRequest
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val newWidgetId = appWidgetHost.allocateAppWidgetId()
        pendingWidgetId = newWidgetId

        val bound = appWidgetManager.bindAppWidgetIdIfAllowed(newWidgetId, providerInfo.provider)
        if (bound) {
            finalizeWidgetPin(newWidgetId)
        } else {
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(bindIntent, Constants.REQUEST_CODE_WIDGET_BIND)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Constants.REQUEST_CODE_WIDGET_BIND) {
            if (resultCode == RESULT_OK && pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                finalizeWidgetPin(pendingWidgetId)
            } else {
                showToast(getString(R.string.widget_binding_denied))
            }
            finish()
        }
    }

    private fun finalizeWidgetPin(widgetId: Int) {
        val request = pendingPinRequest ?: run {
            finish()
            return
        }

        val extras = Bundle().apply {
            putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        val success = request.accept(extras)
        if (success) {
            Prefs(this).addWidgetId(widgetId)
            showToast(getString(R.string.widget_added))
        } else {
            showToast(getString(R.string.widget_add_failed))
        }
        finish()
    }
}