package com.brevinox.websitechecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.brevinox.websitechecker.ui.theme.WebsiteCheckerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitAll
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WebsiteCheckerTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    InputBoxes()
                }
            }
        }
    }
}

@Composable
fun InputBoxes() {
    var boxes by remember { mutableStateOf(listOf("")) }
    var resultLabels by remember { mutableStateOf(listOf<String>()) }
    var boxResults by remember { mutableStateOf(listOf<Boolean?>(null)) }
    val scrollState = rememberScrollState()
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val client = OkHttpClient()

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        for (i in boxes.indices) {
            val label = if (boxResults.getOrNull(i) == true) {
                "Website is up"
            } else if (boxResults.getOrNull(i) == false) {
                "Cannot reach this website"
            } else {
                "New URL"
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
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = {
                coroutineScope.launch {
                    isLoading = true
                    val newResults = mutableListOf<String>()
                    val newBoxResults = mutableListOf<Boolean?>()

                    val deferredResults = boxes.filter { it.isNotEmpty() }.map { url ->
                        async(Dispatchers.IO) {
                            try {
                                val request = Request.Builder().url(url).build()
                                val response = client.newCall(request).execute()
                                response.close()
                                Pair(url, true)
                            } catch (e: Exception) {
                                Pair(url, false)
                            }
                        }
                    }

                    deferredResults.awaitAll().forEach { pair ->
                        val (url, isUp) = pair
                        newResults.add(if (isUp) "$url is up" else "$url is down")
                        newBoxResults.add(isUp)
                    }

                    boxResults = newBoxResults
                    isLoading = false
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

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    WebsiteCheckerTheme {
        InputBoxes()
    }
}
