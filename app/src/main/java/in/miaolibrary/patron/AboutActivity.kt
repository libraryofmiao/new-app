package `in`.miaolibrary.patron

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Informational screen for app purpose, privacy, and support guidance. */
class AboutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.rgb(248, 250, 252))
        }

        content.addView(TextView(this).apply {
            text = "About & Privacy"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 20)
        })

        addSection(content, "About Miao Library", "This app provides access to your library account, currently issued books, issue history, and the library catalogue through the secure library gateway.")
        addSection(content, "Privacy", "The app stores your session token locally using Android Keystore encryption. Network requests are sent through the configured secure library gateway. Your library account information is displayed only to support the app's library functions.")
        addSection(content, "Support", "For account, loan, renewal, or notification questions, contact Miao Library through its official library channels.")

        content.addView(Button(this).apply {
            text = "Back"
            setOnClickListener { finish() }
        })

        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun addSection(parent: LinearLayout, title: String, body: String) {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 16, 0, 6)
        })
        parent.addView(TextView(this).apply {
            text = body
            textSize = 16f
            setGravity(Gravity.START)
            setPadding(0, 0, 0, 12)
        })
    }
}
