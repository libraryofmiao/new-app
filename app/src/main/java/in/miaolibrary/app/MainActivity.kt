package in.miaolibrary.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); showLogin() }
    private fun showLogin() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(32,24,32,32); setBackgroundColor(Color.rgb(247,243,236)) }
        val logo = ImageView(this).apply { setImageResource(R.drawable.logo); scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription="Miao Library logo" }
        root.addView(logo, LinearLayout.LayoutParams(-1, 210))
        val title=TextView(this).apply { text="Welcome to Miao Library"; textSize=25f; setTextColor(Color.rgb(28,45,63)); gravity=Gravity.CENTER; setTypeface(typeface,1) }
        root.addView(title, LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,18,0,6)})
        val subtitle=TextView(this).apply{text="Sign in to access your library account";textSize=14f;setTextColor(Color.DKGRAY);gravity=Gravity.CENTER}
        root.addView(subtitle, LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,28)})
        val user=TextInputLayout(this).apply{hint="Library username"}; val userEdit=TextInputEditText(this); user.addView(userEdit); root.addView(user, LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,0,0,12)})
        val pass=TextInputLayout(this).apply{hint="Password"; endIconMode=TextInputLayout.END_ICON_PASSWORD_TOGGLE}; val passEdit=TextInputEditText(this); pass.addView(passEdit); root.addView(pass, LinearLayout.LayoutParams(-1,-2))
        val button=MaterialButton(this).apply{text="SIGN IN"; setTextSize(14f); isAllCaps=false}; root.addView(button, LinearLayout.LayoutParams(-1,58).apply{setMargins(0,24,0,8)})
        val forgot=TextView(this).apply{text="Forgot password?";gravity=Gravity.CENTER;setTextColor(Color.rgb(45,83,111));textSize=14f}; root.addView(forgot,LinearLayout.LayoutParams(-1,48))
        button.setOnClickListener { Toast.makeText(this,"Gateway login will be connected next.",Toast.LENGTH_SHORT).show() }
        setContentView(root)
    }
}
