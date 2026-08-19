package com.gawi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.gawi.core.ui.theme.GawiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GawiTheme {
                Greeting()
            }
        }
    }
}

@Composable
fun Greeting() {
    Text(text = stringResource(R.string.hello_momo))
}

@Preview(showBackground = true)
@Composable
private fun GreetingPreview() {
    GawiTheme {
        Greeting()
    }
}
