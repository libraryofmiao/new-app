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

bad_tail = '''    }) return true

        val status = first(item, "status", "issue_status", "item_status", "loan_status").lowercase()
        if (status.contains("return") || status.contains("checkin") || status.contains("closed")) return true
        if (status.contains("issue") || status.contains("checkout") || status.contains("loan") || status.contains("out")) return false
        return false
    }

    private fun isReturnedIssue(item: JSONObject): Boolean {'''
s = s.replace(bad_tail, '''    }

    private fun isReturnedIssue(item: JSONObject): Boolean {''', 1)

helper = '''    private fun isReturnedIssue(item: JSONObject): Boolean {
        val returnedKeys = listOf("date_returned", "returned_date", "return_date", "checkin_date", "date_checkin")
        if (returnedKeys.any {
                val value = item.optString(it, "").trim()
                value.isNotBlank() && value != "null"
            }) return true

        val status = first(item, "status", "issue_status", "item_status", "loan_status").lowercase()
        if (status.contains("return") || status.contains("checkin") || status.contains("closed")) return true
        if (status.contains("issue") || status.contains("checkout") || status.contains("loan") || status.contains("out")) return false
        return false
    }

'''

if "private fun isReturnedIssue(item: JSONObject)" not in s:
    marker = "    private fun account(body: LinearLayout) {"
    if marker not in s:
        raise SystemExit("account marker not found")
    s = s.replace(marker, helper + marker, 1)

account_start = s.find("    private fun account(body: LinearLayout) {")
account_end = s.find("\n\n    private fun displayValue", account_start)
if account_start < 0 or account_end < 0:
    raise SystemExit("account boundaries not found")

account = s[account_start:account_end]
if "Our Official Website : miaolibrary.in" not in account:
    footer = '''
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
    account = account.rstrip("\n") + footer + "}"
    s = s[:account_start] + account + s[account_end:]

path.write_text(s, encoding="utf-8")
