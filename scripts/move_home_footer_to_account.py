from pathlib import Path
import re

path = Path("app/src/main/java/in/miaolibrary/app/MainActivity.kt")
s = path.read_text(encoding="utf-8")

home_footer = '''    val footer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(30), 0, dp(12)) }
    val website = text("Our Official Website : miaolibrary.in", 14f, ink).apply { gravity = Gravity.CENTER; setTypeface(typeface, 1); setOnClickListener { startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://miaolibrary.in"))) } }
    footer.addView(website)
    footer.addView(text("© Sub Divisional Library, Miao. All Rights Reserved.", 12f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
    body.addView(footer)
'''

s = s.replace(home_footer, "", 1)

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
'''

pattern = r'    private fun account\(body: LinearLayout\) \{.*?\n    \}\n\n    private fun displayValue'
match = re.search(pattern, s, flags=re.S)
if not match:
    raise SystemExit("account function not found")

account = match.group(0)
if "Our Official Website : miaolibrary.in" not in account:
    account = account.replace("\n    private fun displayValue", account_footer + "\n    private fun displayValue", 1)

s = s[:match.start()] + account + s[match.end():]
path.write_text(s, encoding="utf-8")
