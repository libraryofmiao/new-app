package `in`.miaolibrary.patron

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class LoginActivity : Activity() {
    private val api = LibraryApi()
    private val session by lazy { ModernSession(this) }

    private val green = Color.rgb(49, 92, 58)
    private val dark = Color.rgb(31, 43, 35)
    private val gold = Color.rgb(182, 122, 53)
    private val cream = Color.rgb(248, 247, 241)
    private val muted = Color.rgb(101, 112, 105)
    private val danger = Color.rgb(166, 65, 54)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!session.readToken().isNullOrBlank()) {
            openDashboard()
        } else {
            renderLogin()
        }
    }

    private fun renderLogin() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(cream)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(30), dp(28), dp(30))
        }

        val logo = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(loadRepositoryLogo())
            contentDescription = "Miao Library logo"
        }
        root.addView(logo, LinearLayout.LayoutParams(-1, dp(260)))

        root.addView(label("Miao Library", 34f, dark, true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        }, match())
        root.addView(label("Read  •  Learn  •  Grow", 16f, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(34))
        }, match())

        val username = field("Username", InputType.TYPE_CLASS_TEXT)
        val password = field("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        password.transformationMethod = PasswordTransformationMethod.getInstance()

        root.addView(username, marginBottom(dp(12)))
        val passwordRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        passwordRow.addView(password, LinearLayout.LayoutParams(0, dp(58), 1f))
        val show = TextView(this).apply {
            text = "SHOW"
            textSize = 12f
            setTextColor(green)
            gravity = Gravity.CENTER
            setOnClickListener {
                password.transformationMethod = if (password.transformationMethod == null) {
                    PasswordTransformationMethod.getInstance()
                } else {
                    HideReturnsTransformationMethod.getInstance()
                }
                password.setSelection(password.length())
            }
        }
        passwordRow.addView(show, LinearLayout.LayoutParams(dp(70), dp(58)))
        root.addView(passwordRow, marginBottom(dp(18)))

        val status = label("", 14f, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        val login = TextView(this).apply {
            text = "Login"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackground(rounded(green, 18))
            minHeight = dp(58)
            setOnClickListener {
                val user = username.text.toString().trim()
                val pass = password.text.toString()
                if (user.isBlank() || pass.isBlank()) {
                    status.setTextColor(danger)
                    status.text = "Enter your username and password."
                    return@setOnClickListener
                }
                isEnabled = false
                alpha = .65f
                status.setTextColor(muted)
                status.text = "Signing in…"
                Thread {
                    val result = api.login(user, pass)
                    runOnUiThread {
                        if (result.isSuccess) {
                            session.saveToken(result.getOrThrow())
                            openDashboard()
                        } else {
                            isEnabled = true
                            alpha = 1f
                            status.setTextColor(danger)
                            status.text = friendlyError(result.exceptionOrNull())
                        }
                    }
                }.start()
            }
        }
        root.addView(login, marginBottom(dp(8)))
        root.addView(status, match())
        root.addView(label("Forgot Password?", 15f, green, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, 0)
        }, match())
        root.addView(label("Miao Library — Your Community Library", 13f, muted, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(88), 0, 0)
        }, match())

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun openDashboard() {
        startActivity(Intent(this, ModernDashboardActivity::class.java))
        finish()
    }

    private fun loadRepositoryLogo(): Bitmap? {
        // Preferred source: app-new's logo.png copied into app/src/main/assets/logo.png.
        // The existing bundled logo remains as a build-safe fallback until that binary is present.
        return runCatching {
            assets.open("logo.png").use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: runCatching {
            assets.open("miao_logo_base64.txt").use {
                val bytes = android.util.Base64.decode(it.bufferedReader().readText(), android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }

    private fun field(hintText: String, type: Int) = EditText(this).apply {
        hint = hintText
        inputType = type
        textSize = 16f
        setSingleLine(true)
        minHeight = dp(58)
        setPadding(dp(18), 0, dp(14), 0)
        setBackground(rounded(Color.WHITE, 18))
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private fun rounded(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.rgb(220, 224, 218))
    }

    private fun match() = LinearLayout.LayoutParams(-1, -2)
    private fun marginBottom(value: Int) = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = value }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
