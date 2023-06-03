package com.brevinox.websitechecker

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
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
import androidx.compose.ui.unit.dp
import com.brevinox.websitechecker.ui.theme.WebsiteCheckerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import okhttp3.OkHttpClient
import okhttp3.Request

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
                    InputBoxes(sharedPreferences)
                }
            }
        }
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
        for (i in boxes.indices) {
            val label = when (boxResults.getOrNull(i)) {
                true -> "Website is up"
                false -> "Cannot reach this website"
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
                    .padding(bottom = 8.dp)
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
                .height(64.dp)
                .padding(top = 8.dp)
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
