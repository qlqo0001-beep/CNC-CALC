package com.bmt.nosecalc

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bmt.nosecalc.calc.NoseR
import com.bmt.nosecalc.ui.BmtBlue
import com.bmt.nosecalc.ui.BmtMark

@Composable
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(BmtMark, contentDescription = null, Modifier.size(56.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("BMT", color = BmtBlue, fontSize = 48.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black)
                }
                Text("노즈R 계산기", fontSize = 22.sp)
                Text("0.4R 45° 보정 = ${NoseR.taperDeltaZ(0.4, 45.0).toString().take(5)}")
            }
        }
    }
}
