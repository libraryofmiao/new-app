package `in`.miaolibrary.patron

import android.view.ViewGroup
import android.widget.TextView

/** Small UI overload used by the modern dashboard without changing the existing UI helpers. */
fun ModernDashboardActivity.textButton(label: String, params: ViewGroup.LayoutParams): TextView =
    textButton(label) { }.apply { layoutParams = params }
