package com.ikev2split.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ikev2split.app.R
import com.ikev2split.app.VpnViewModel
import com.ikev2split.app.auth.AuthState

@Composable
fun LoginScreen(vm: VpnViewModel) {
    val auth by vm.auth.collectAsState()
    val err by vm.authError.collectAsState()
    var u by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    val busy = auth is AuthState.Busy

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(92.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF08CAD7)), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_logo_fg), null, Modifier.size(92.dp))
        }
        Text("MYLO VPN", Modifier.padding(top = 16.dp, bottom = 28.dp), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            u, { u = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true, enabled = !busy,
        )
        OutlinedTextField(
            p, { p = it }, Modifier.fillMaxWidth().padding(top = 12.dp), label = { Text("Password") }, singleLine = true, enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        err?.let { Text(it, Modifier.padding(top = 14.dp), color = Danger, fontSize = 14.sp, textAlign = TextAlign.Center) }
        Button(
            onClick = { vm.login(u.trim(), p) },
            enabled = !busy && u.isNotBlank() && p.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Color.White) else Text("Sign in", fontSize = 17.sp)
        }
        Text("Need an account? Ask the administrator.", Modifier.padding(top = 22.dp), color = Muted, fontSize = 13.sp)
    }
}
