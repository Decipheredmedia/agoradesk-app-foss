package com.agoradesk.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.JobIntentService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "MoneroWidget"

class MoneroWidget : AppWidgetProvider() {

  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    for (appWidgetId in appWidgetIds) {
      val intent = Intent(context, UpdateMoneroPriceService::class.java)
      intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
      UpdateMoneroPriceService.enqueueWork(context, intent)
    }
  }
}

class UpdateMoneroPriceService : JobIntentService() {

  override fun onHandleWork(intent: Intent) {
    val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
    if (appWidgetId == -1) return

    val prices = runBlocking { fetchMoneroPrices() }
    updateWidget(this, appWidgetId, prices)
  }

  private suspend fun fetchMoneroPrices(): Map<String, Double>? {
    return withContext(Dispatchers.IO) {
      var connection: HttpURLConnection? = null
      try {
        connection = URL("https://localmonero.co/web/ticker?currencyCode=USD")
          .openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.requestMethod = "GET"
        connection.connect()

        val result = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(result).getJSONObject("USD")
        mapOf(
          "avg_1h" to json.getDouble("avg_1h"),
          "avg_6h" to json.getDouble("avg_6h"),
          "avg_12h" to json.getDouble("avg_12h"),
          "avg_24h" to json.getDouble("avg_24h")
        )
      } catch (e: Exception) {
        Log.e(TAG, "Failed to fetch Monero prices", e)
        null
      } finally {
        connection?.disconnect()
      }
    }
  }

  private fun updateWidget(context: Context, appWidgetId: Int, prices: Map<String, Double>?) {
    val views = RemoteViews(context.packageName, R.layout.widget_layout)
    if (prices != null) {
      views.setTextViewText(R.id.textViewCurrentPrice, "$${prices["avg_1h"]}")
      views.setTextViewText(R.id.textViewAvg6h, "6h: $${prices["avg_6h"]}")
      views.setTextViewText(R.id.textViewAvg12h, "12h: $${prices["avg_12h"]}")
      views.setTextViewText(R.id.textViewAvg24h, "24h: $${prices["avg_24h"]}")
    } else {
      views.setTextViewText(R.id.textViewCurrentPrice, "Unavailable")
      views.setTextViewText(R.id.textViewAvg6h, "")
      views.setTextViewText(R.id.textViewAvg12h, "")
      views.setTextViewText(R.id.textViewAvg24h, "")
    }
    AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
  }

  companion object {
    private const val JOB_ID = 1

    fun enqueueWork(context: Context, intent: Intent) {
      enqueueWork(context, UpdateMoneroPriceService::class.java, JOB_ID, intent)
    }
  }
}