from pathlib import Path

path = Path("app/src/main/java/in/miaolibrary/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

home_footer = '''    val footer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(30), 0, dp(12)) }
    val website = text("Our Official Website : miaolibrary.in", 14f, ink).apply { gravity = Gravity.CENTER; setTypeface(typeface, 1); setOnClickListener { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://miaolibrary.in"))) } }
    footer.addView(website)
    footer.addView(text("© Sub Divisional Library, Miao. All Rights Reserved.", 12f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
    body.addView(footer)
'''
s = s.replace(home_footer, "", 1)

old_account = '''    private fun account(body: LinearLayout) {
    body.addView(card(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; addView(text("Library account", 21f).apply { gravity = Gravity.CENTER; setTypeface(typeface, 1) }); addView(text(username(), 16f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }); addView(text("Your Koha patron account", 13f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) }) }), LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(14), 0, dp(16)) })
    val my = button("View my books", false).apply { textSize = 15f }; body.addView(my, LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(0, 0, 0, dp(10)) }); my.setOnClickListener { dashboard("My Books") }
    val refresh = button("Refresh account", false).apply { textSize = 15f }; body.addView(refresh, LinearLayout.LayoutParams(-1, dp(52)).apply { setMargins(0, 0, 0, dp(10)) }); refresh.setOnClickListener { dashboard("Account") }
    val logout = button("Log out", true).apply { background = shape(Color.rgb(155, 76, 76), 16); textSize = 15f }; body.addView(logout, LinearLayout.LayoutParams(-1, dp(52))); logout.setOnClickListener { prefs().edit().clear().apply(); login() }
}'''

account_footer = '''
    val footer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(0, dp(34), 0, dp(12))
    }
    val website = text("Our Official Website : miaolibrary.in", 14f, ink).apply {
        gravity = Gravity.CENTER
        setTypeface(typeface, 1)
        setOnClickListener {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://miaolibrary.in")))
        }
    }
    footer.addView(website)
    footer.addView(text("© Sub Divisional Library, Miao. All Rights Reserved.", 12f, muted).apply {
        gravity = Gravity.CENTER
        setPadding(0, dp(8), 0, 0)
    })
    body.addView(footer)
}'''

if old_account not in s:
    raise SystemExit("expected Account block not found")

if "Our Official Website : miaolibrary.in" not in old_account:
    s = s.replace(old_account, old_account[:-1] + account_footer, 1)
else:
    s = s.replace(old_account, old_account, 1)

path.write_text(s, encoding="utf-8")
