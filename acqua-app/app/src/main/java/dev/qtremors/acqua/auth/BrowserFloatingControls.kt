package dev.qtremors.acqua.auth

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import dev.qtremors.acqua.R
import kotlin.math.hypot

@SuppressLint("ClickableViewAccessibility")
internal fun createBrowserFloatingControls(
    context: Context,
    parent: FrameLayout,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
    onReturnToAcqua: () -> Unit
): View {
    val wrapper = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.END
    }
    val menu = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
        background = roundedBackground(BROWSER_SURFACE, context.dp(18).toFloat())
        elevation = context.dp(10).toFloat()
        visibility = View.GONE
    }
    lateinit var ball: ImageButton

    fun moveWithinBounds(targetX: Float, targetY: Float) {
        val minX = parent.paddingLeft.toFloat()
        val minY = parent.paddingTop.toFloat()
        val maxX = (parent.width - parent.paddingRight - wrapper.width)
            .coerceAtLeast(parent.paddingLeft).toFloat()
        val maxY = (parent.height - parent.paddingBottom - wrapper.height)
            .coerceAtLeast(parent.paddingTop).toFloat()
        wrapper.x = targetX.coerceIn(minX, maxX)
        wrapper.y = targetY.coerceIn(minY, maxY)
    }

    fun saveBallPosition() {
        if (parent.width <= 0 || parent.height <= 0 || ball.width <= 0) return
        val centerX = wrapper.x + ball.left + ball.width / 2f
        val centerY = wrapper.y + ball.top + ball.height / 2f
        context.getSharedPreferences(BROWSER_UI_PREFS, Context.MODE_PRIVATE).edit {
            putFloat(KEY_BUBBLE_X, centerX / parent.width)
            putFloat(KEY_BUBBLE_Y, centerY / parent.height)
        }
    }

    fun setMenuVisible(visible: Boolean) {
        val centerX = wrapper.x + ball.left + ball.width / 2f
        val centerY = wrapper.y + ball.top + ball.height / 2f
        menu.visibility = if (visible) View.VISIBLE else View.GONE
        wrapper.post {
            moveWithinBounds(
                centerX - ball.left - ball.width / 2f,
                centerY - ball.top - ball.height / 2f
            )
        }
    }

    fun menuAction(icon: Int, label: String, action: () -> Unit): View =
        LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(12), 0, context.dp(14), 0)
            background = roundedBackground(Color.TRANSPARENT, context.dp(12).toFloat())
            isClickable = true
            isFocusable = true
            contentDescription = label
            addView(ImageView(context).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
            }, LinearLayout.LayoutParams(context.dp(24), context.dp(24)))
            addView(TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(context.dp(12), 0, 0, 0)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(48)).apply {
                gravity = Gravity.CENTER_VERTICAL
            })
            setOnClickListener {
                setMenuVisible(false)
                action()
            }
        }

    menu.addView(
        menuAction(android.R.drawable.ic_popup_sync, context.getString(R.string.refresh), onRefresh),
        LinearLayout.LayoutParams(context.dp(156), context.dp(48))
    )
    menu.addView(
        menuAction(android.R.drawable.stat_sys_download_done, context.getString(R.string.download), onDownload),
        LinearLayout.LayoutParams(context.dp(156), context.dp(48))
    )
    menu.addView(
        menuAction(android.R.drawable.ic_menu_revert, context.getString(R.string.go_to_acqua), onReturnToAcqua),
        LinearLayout.LayoutParams(context.dp(156), context.dp(48))
    )
    wrapper.addView(menu, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    ball = ImageButton(context).apply {
        setImageResource(R.drawable.acqua)
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = roundedBackground(BROWSER_PRIMARY, context.dp(30).toFloat(), GradientDrawable.OVAL)
        contentDescription = context.getString(R.string.browser_controls)
        elevation = context.dp(12).toFloat()
        setPadding(context.dp(3), context.dp(3), context.dp(3), context.dp(3))
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        var dragging = false
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = wrapper.x
                    startY = wrapper.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY
                    if (!dragging && hypot(deltaX.toDouble(), deltaY.toDouble()) >= touchSlop.toDouble()) {
                        dragging = true
                    }
                    if (dragging) moveWithinBounds(startX + deltaX, startY + deltaY)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        saveBallPosition()
                    } else {
                        performClick()
                        setMenuVisible(menu.visibility != View.VISIBLE)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }
    wrapper.addView(ball, LinearLayout.LayoutParams(context.dp(58), context.dp(58)).apply {
        gravity = Gravity.END
        topMargin = context.dp(8)
    })
    parent.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        wrapper.post { moveWithinBounds(wrapper.x, wrapper.y) }
    }
    wrapper.post {
        val preferences = context.getSharedPreferences(BROWSER_UI_PREFS, Context.MODE_PRIVATE)
        val centerX = preferences.getFloat(KEY_BUBBLE_X, 0.9f) * parent.width
        val centerY = preferences.getFloat(KEY_BUBBLE_Y, 0.86f) * parent.height
        moveWithinBounds(
            centerX - ball.left - ball.width / 2f,
            centerY - ball.top - ball.height / 2f
        )
    }
    return wrapper
}

private fun roundedBackground(
    color: Int,
    radius: Float,
    backgroundShape: Int = GradientDrawable.RECTANGLE
) = GradientDrawable().apply {
    shape = backgroundShape
    setColor(color)
    cornerRadius = radius
}

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private const val BROWSER_UI_PREFS = "acqua_browser_ui"
private const val KEY_BUBBLE_X = "bubble_x"
private const val KEY_BUBBLE_Y = "bubble_y"
private val BROWSER_SURFACE = Color.rgb(36, 43, 48)
private val BROWSER_PRIMARY = Color.rgb(129, 216, 255)
