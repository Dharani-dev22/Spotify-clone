package com.example.dmusic

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class User(val email: String, val pass: String, val name: String)

class AuthManager(context: Context) {
    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getUsers(): List<User> {
        val json = prefs.getString("users_list", null) ?: return emptyList()
        val type = object : TypeToken<List<User>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveUser(user: User) {
        val users = getUsers().toMutableList()
        users.add(user)
        val json = gson.toJson(users)
        prefs.edit().putString("users_list", json).apply()
    }

    fun saveSession(name: String) {
        prefs.edit().putString("logged_in_user", name).apply()
    }

    fun getLoggedInUser(): String? {
        return prefs.getString("logged_in_user", null)
    }

    fun clearSession() {
        prefs.edit().remove("logged_in_user").apply()
    }
}

@Composable
fun AuthSession(onAuthSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val authManager = remember { AuthManager(context) }
    var isLoginScreen by remember { mutableStateOf(true) }

    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundDark) {
        if (isLoginScreen) {
            LoginScreen(
                onNav = { isLoginScreen = false },
                onSuccess = onAuthSuccess,
                authManager = authManager
            )
        } else {
            RegisterScreen(
                onNav = { isLoginScreen = true },
                onSuccess = onAuthSuccess,
                authManager = authManager
            )
        }
    }
}

@Composable
fun LoginScreen(onNav: () -> Unit, onSuccess: (String) -> Unit, authManager: AuthManager) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "D Music", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = DarkGreen)
        Spacer(modifier = Modifier.height(40.dp))
        AuthField(email, { email = it }, "Email")
        Spacer(modifier = Modifier.height(16.dp))
        AuthField(pass, { pass = it }, "Password", true)

        if (error.isNotEmpty()) {
            Text(error, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                val users = authManager.getUsers()
                val user = users.find { it.email == email && it.pass == pass }
                if (user != null) onSuccess(user.name)
                else error = "Invalid email or password"
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
        ) {
            Text("LOG IN", color = Color.Black, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onNav) { Text("Don't have an account? Sign up free", color = Color.Gray) }
    }
}

@Composable
fun RegisterScreen(onNav: () -> Unit, onSuccess: (String) -> Unit, authManager: AuthManager) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Sign up", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(40.dp))
        AuthField(name, { name = it }, "Full Name")
        Spacer(modifier = Modifier.height(16.dp))
        AuthField(email, { email = it }, "Email")
        Spacer(modifier = Modifier.height(16.dp))
        AuthField(pass, { pass = it }, "Password", true)

        if (error.isNotEmpty()) {
            Text(error, color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (name.isNotBlank() && email.isNotBlank() && pass.isNotBlank()) {
                    authManager.saveUser(User(email, pass, name))
                    onSuccess(name)
                } else {
                    error = "Please fill all fields"
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkGreen)
        ) {
            Text("CREATE ACCOUNT", color = Color.Black, fontWeight = FontWeight.Bold)
        }
        TextButton(onNav) { Text("Already have an account? Log in", color = Color.Gray) }
    }
}

@Composable
fun AuthField(value: String, onValueChange: (String) -> Unit, label: String, isPass: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (isPass) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DarkGreen, focusedLabelColor = DarkGreen, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
    )
}