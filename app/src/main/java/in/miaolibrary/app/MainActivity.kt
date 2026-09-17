package `in`.miaolibrary.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private val gatewayBaseUrl = "https://api.miaolibrary.in"
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = getSharedPreferences("session", MODE_PRIVATE).getString("access_token", null)
        if (token.isNullOrBlank()) showLogin() else showHome()
    }

    private fun showLogin() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(24), dp(32), dp(32))
            setBackgroundColor(Color.rgb(247, 243, 236))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Miao Library logo"
        }
        root.addView(logo, LinearLayout.LayoutParams(-1, dp(210)))
        val title = TextView(this).apply {
            text = "Welcome to Miao Library"
            textSize = 25f
            setTextColor(Color.rgb(28, 45, 63))
            gravity = Gravity.CENTER
            setTypeface(typeface, 1)
        }
        root.addView(title, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(18), 0, dp(6)) })
        val subtitle = TextView(this).apply {
            text = "Sign in to access your library account"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
        }
        root.addView(subtitle, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(28)) })
        val userLayout = TextInputLayout(this).apply { hint = "Library username" }
        val userEdit = TextInputEditText(this)
        userLayout.addView(userEdit)
        root.addView(userLayout, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(12)) })
        val passLayout = TextInputLayout(this).apply {
            hint = "Password"
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val passEdit = TextInputEditText(this)
        passLayout.addView(passEdit)
        root.addView(passLayout, LinearLayout.LayoutParams(-1, -2))
        val button = MaterialButton(this).apply {
            text = "Sign in"
            isAllCaps = false
            textSize = 15f
            minHeight = 0
            minimumHeight = 0
            setPadding(0, 0, 0, 0)
        }
        root.addView(button, LinearLayout.LayoutParams(-1, dp(56)).apply { setMargins(0, dp(24), 0, 0) })
        button.setOnClickListener {
            val username = userEdit.text?.toString()?.trim().orEmpty()
            val password = passEdit.text?.toString().orEmpty()
            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Enter your username and password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            button.isEnabled = false
            button.text = "Signing in…"
            signIn(username, password, button)
        }
        setContentView(root)
    }

    private fun signIn(username: String, password: String, button: MaterialButton) {
        thread {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("$gatewayBaseUrl/login").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }
                val body = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }.toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) throw Exception("Login failed ($status)")
                val json = JSONObject(response)
                val token = json.optString("access_token").ifBlank { json.optString("token") }
                if (token.isBlank()) throw Exception("Gateway did not return a session token")
                getSharedPreferences("session", MODE_PRIVATE).edit()
                    .putString("access_token", token)
                    .putString("username", username)
                    .apply()
                runOnUiThread {
                    button.isEnabled = true
                    button.text = "Sign in"
                    showHome()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    button.isEnabled = true
                    button.text = "Sign in"
                    Toast.makeText(this, error.message ?: "Unable to sign in. Please try again.", Toast.LENGTH_LONG).show()
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun showHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 243, 236))
            setPadding(dp(24), dp(32), dp(24), 0)
        }
        val heading = TextView(this).apply {
            text = "Miao Library"
            textSize = 28f
            setTypeface(typeface, 1)
            setTextColor(Color.rgb(28, 45, 63))
        }
        root.addView(heading)
        val welcome = TextView(this).apply {
            text = "Welcome back. Your library at a glance."
            textSize = 16f
            setTextColor(Color.DKGRAY)
        }
        root.addView(welcome, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(8), 0, dp(24)) })
        val card = TextView(this).apply {
            text = "Home\n\nAnnouncements, library updates and featured resources will appear here."
            textSize = 17f
            setTextColor(Color.rgb(28, 45, 63))
            setPadding(dp(20), dp(20), dp(20), dp(20))
            setBackgroundColor(Color.WHITE)
        }
        root.addView(card, LinearLayout.LayoutParams(-1, dp(170)))
        val spacer = LinearLayout(this)
        root.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        listOf("Home", "Catalogue", "My Books", "Account").forEach { label ->
            val item = TextView(this).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(45, 83, 111))
                setPadding(dp(4), dp(16), dp(4), dp(16))
                setOnClickListener { Toast.makeText(this@MainActivity, "$label section", Toast.LENGTH_SHORT).show() }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, dp(64), 1f))
        }
        root.addView(nav)
        setContentView(root)
    }
}
