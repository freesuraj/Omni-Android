package com.suraj.apps.omni.core.data.entitlement

import android.content.Context

const val ACCESS_PREFS_NAME = "omni_access"
const val KEY_PREMIUM_UNLOCKED = "premium_unlocked"
const val KEY_LIVE_RECORDINGS_CREATED = "live_recordings_created"
const val KEY_ACTIVE_PLAN_ID = "active_plan_id"

interface PremiumAccessStore {
    fun isPremiumUnlocked(): Boolean
    fun setPremiumUnlocked(unlocked: Boolean)
    fun getLiveRecordingsCreated(): Int
    fun setLiveRecordingsCreated(count: Int)
    fun getActivePlanId(): String?
    fun setActivePlanId(planId: String?)
}

class SharedPrefsPremiumAccessStore(
    context: Context
) : PremiumAccessStore {
    private val prefs = context.getSharedPreferences(ACCESS_PREFS_NAME, Context.MODE_PRIVATE)

    override fun isPremiumUnlocked(): Boolean {
        return prefs.getBoolean(KEY_PREMIUM_UNLOCKED, false)
    }

    override fun setPremiumUnlocked(unlocked: Boolean) {
        prefs.edit().putBoolean(KEY_PREMIUM_UNLOCKED, unlocked).apply()
    }

    override fun getLiveRecordingsCreated(): Int {
        return prefs.getInt(KEY_LIVE_RECORDINGS_CREATED, 0)
    }

    override fun setLiveRecordingsCreated(count: Int) {
        prefs.edit().putInt(KEY_LIVE_RECORDINGS_CREATED, count.coerceAtLeast(0)).apply()
    }

    override fun getActivePlanId(): String? {
        return prefs.getString(KEY_ACTIVE_PLAN_ID, null)
    }

    override fun setActivePlanId(planId: String?) {
        prefs.edit().putString(KEY_ACTIVE_PLAN_ID, planId).apply()
    }
}
