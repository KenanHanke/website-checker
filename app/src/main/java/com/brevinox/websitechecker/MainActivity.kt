package com.brevinox.websitechecker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.brevinox.websitechecker.ui.theme.WebsiteCheckerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences("website_checker", Context.MODE_PRIVATE)

        setContent {
            WebsiteCheckerTheme {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(text = "Website Checker")
                            },
                        )
                        InputBoxes(sharedPreferences)
                    }
                }
            }
        }

        setupWorker()
    }

    private fun setupWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val websiteCheckRequest =
            PeriodicWorkRequestBuilder<WebsiteCheckWorker>(2, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(2, TimeUnit.HOURS)
                .build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "WebsiteCheck",
                ExistingPeriodicWorkPolicy.UPDATE,
                websiteCheckRequest
            )
    }
}

@Composable
fun InputBoxes(sharedPreferences: SharedPreferences) {
    var boxes by remember {
        mutableStateOf(
            sharedPreferences.getString("boxes", "")?.split(",") ?: listOf("")
        )
    }
    var boxResults by remember {
        mutableStateOf(
            sharedPreferences.getString("boxResults", "")?.split(",")?.map { it.toBooleanStrictOrNull() } ?: listOf()
        )
    }

    val scrollState = rememberScrollState()
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val client = OkHttpClient()

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp)
            .verticalScroll(scrollState)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusManager.clearFocus() }
    ) {
        Text(
            text = "This app is intended to aid anyone who maintains websites. It can be used to check the status of websites manually and will also perform checks in the background every two hours.",
            modifier = Modifier.padding(bottom = 24.dp)
        )

        for (i in boxes.indices) {
            val label = when (boxResults.getOrNull(i)) {
                true -> "Website is up"
                false -> "Cannot reach website"
                else -> "New URL"
            }

            val outlineColor = when (boxResults.getOrNull(i)) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onBackground
            }

            OutlinedTextField(
                value = boxes[i],
                label = { Text(label) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = outlineColor,
                    unfocusedBorderColor = outlineColor
                ),
                onValueChange = { newValue ->
                    boxes = boxes.toMutableList().apply {
                        this[i] = newValue
                        if (this.last().isNotEmpty()) {
                            this.add("")
                            boxResults = boxResults.toMutableList().apply { this.add(null) }
                        } else if (newValue.isEmpty() && this.size > 1 && i != this.lastIndex) {
                            this.removeAt(i)
                            boxResults = boxResults.toMutableList().apply { this.removeAt(i) }
                        }
                    }
                    sharedPreferences.edit().putString("boxes", boxes.joinToString(",")).apply()
                    sharedPreferences.edit().putString("boxResults", boxResults.joinToString(",")).apply()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )
        }

        Button(
            onClick = {
                coroutineScope.launch {
                    // focusManager.clearFocus()

                    isLoading = true
                    val newBoxResults = mutableListOf<Boolean?>()

                    val deferredResults = boxes.filter { it.isNotEmpty() }.map { url ->
                        async(Dispatchers.IO) {
                            try {
                                val request = Request.Builder().url(url).build()
                                val response = client.newCall(request).execute()

                                val isSuccessful = response.isSuccessful
                                response.close()

                                Pair(url, isSuccessful)
                            } catch (e: Exception) {
                                Pair(url, false)
                            }
                        }
                    }

                    deferredResults.awaitAll().forEach { pair ->
                        val (url, isUp) = pair
                        newBoxResults.add(isUp)
                    }

                    boxResults = newBoxResults
                    isLoading = false

                    sharedPreferences.edit().putString("boxes", boxes.joinToString(",")).apply()
                    sharedPreferences.edit().putString("boxResults", boxResults.joinToString(",")).apply()
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(top = 20.dp)
        ) {
            if (isLoading) {
                Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            } else {
                Text(text = "Check websites")
            }
        }
    }
}

class WebsiteCheckWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val client = OkHttpClient()
    private val sharedPreferences =
        appContext.getSharedPreferences("website_checker", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        val boxes = sharedPreferences.getString("boxes", "")?.split(",")
        val boxResults = sharedPreferences.getString("boxResults", "")?.split(",")?.toMutableList()
        val unreachableWebsites = mutableListOf<String>()

        boxes?.forEachIndexed { index, url ->
            if (url.isEmpty()) {
                return@forEachIndexed
            }
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    boxResults?.set(index, "true")
                } else {
                    unreachableWebsites.add(url)
                    boxResults?.set(index, "false")
                }

                response.close()
            } catch (e: Exception) {
                unreachableWebsites.add(url)
                boxResults?.set(index, "false")
            }
        }

        if (unreachableWebsites.isNotEmpty()) {
            sendNotification(unreachableWebsites)
        }

        sharedPreferences.edit().putString("boxResults", boxResults?.joinToString(",")).apply()

        return Result.success()
    }

    private fun sendNotification(unreachableWebsites: List<String>) {
        val channelId = "website_checker"
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            channelId,
            "Website Checker",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, 0)

        val title = if (unreachableWebsites.size > 1) "Websites unreachable" else "Website unreachable"

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(unreachableWebsites.joinToString(", "))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(applicationContext)) {
            notify(0, builder.build())
        }
    }
}
