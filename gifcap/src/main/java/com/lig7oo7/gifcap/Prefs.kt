package com.lig7oo7.gifcap

import android.content.Context
import android.graphics.Rect

/** 크롭 영역과 버튼 위치를 기억 */
object Prefs {
    private const val NAME = "gifcap"

    fun loadRect(ctx: Context, screenW: Int, screenH: Int): Rect {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val defW = (screenW * 0.5f).toInt()
        val defX = (screenW - defW) / 2
        val defY = (screenH * 0.45f).toInt()
        val x = sp.getInt("x", defX)
        val y = sp.getInt("y", defY)
        val w = sp.getInt("w", defW)
        val h = sp.getInt("h", defW)
        return Rect(x, y, x + w, y + h)
    }

    fun saveRect(ctx: Context, r: Rect) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt("x", r.left).putInt("y", r.top)
            .putInt("w", r.width()).putInt("h", r.height())
            .apply()
    }

    fun loadBar(ctx: Context, defX: Int, defY: Int): Pair<Int, Int> {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return sp.getInt("barX", defX) to sp.getInt("barY", defY)
    }

    fun saveBar(ctx: Context, x: Int, y: Int) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putInt("barX", x).putInt("barY", y).apply()
    }
}
