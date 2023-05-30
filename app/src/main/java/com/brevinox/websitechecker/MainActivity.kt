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
            OutlinedTextField(
                value = boxes[i],
                label = { Text("New URL") },
                onValueChange = { newValue ->
                    boxes = boxes.toMutableList().apply {
                        this[i] = newValue
                        if (this.last().isNotEmpty()) {
                            this.add("")
                        } else if (newValue.isEmpty() && this.size > 1 && i != this.lastIndex) {
                            this.removeAt(i)
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

                    boxes.filter { it.isNotEmpty() }.map { url ->
                        async(Dispatchers.IO) {
                            try {
                                val request = Request.Builder().url(url).build()
                                val response = client.newCall(request).execute()
                                response.close()
                                "$url is up"
                            } catch (e: Exception) {
                                "$url is down"
                            }
                        }
                    }.forEach { deferred ->
                        newResults.add(deferred.await())
                    }

                    resultLabels = newResults
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

        resultLabels.forEach { label ->
            Text(text = label)
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
