package com.brevinox.websitechecker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.brevinox.websitechecker.ui.theme.WebsiteCheckerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val scrollState = rememberScrollState()
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()


    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Button(
            onClick = {
                coroutineScope.launch {
                    isLoading = true
                    delay(3000) // simulating a background task
                    isLoading = false
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Text(text = "Check websites")
            }
        }

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
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    WebsiteCheckerTheme {
        InputBoxes()
    }
}