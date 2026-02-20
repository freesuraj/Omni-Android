package com.suraj.apps.omni.core.data.entitlement

import android.content.Context
import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BillingRepositoryTest {
    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun loadPlansUsesRemoteCatalogWhenAvailable() = runBlocking {
        val store = FakePremiumAccessStore()
        val repository = BillingRepository(
            context = appContext,
            premiumAccessStore = store,
            billingCatalogClient = FakeBillingCatalogClient(
                catalog = listOf(
                    RemoteBillingProduct(
                        productId = PLAN_YEARLY,
                        priceLabel = "$24.99/year",
                        hasFreeTrial = true
                    )
                )
            )
        )

        val plans = repository.loadPlans()

        val yearly = plans.first { it.productId == PLAN_YEARLY }
        assertEquals("$24.99/year", yearly.priceLabel)
        assertTrue(yearly.hasFreeTrial)
        assertEquals(BillingPlanSource.PLAY_STORE, yearly.source)
    }

    @Test
    fun purchasePlanUnlocksPremiumAndStoresSelectedPlan() = runBlocking {
        val store = FakePremiumAccessStore()
        val repository = BillingRepository(
            context = appContext,
            premiumAccessStore = store,
            billingCatalogClient = FakeBillingCatalogClient()
        )

        val result = repository.purchasePlan(PLAN_MONTHLY, createActivity())

        assertTrue(result is BillingPurchaseResult.Success)
        assertTrue(store.isPremiumUnlocked())
        assertEquals(PLAN_MONTHLY, store.getActivePlanId())
    }

    @Test
    fun restorePurchasesUsesActivePlayPurchases() = runBlocking {
        val store = FakePremiumAccessStore()
        val repository = BillingRepository(
            context = appContext,
            premiumAccessStore = store,
            billingCatalogClient = FakeBillingCatalogClient(
                purchases = listOf(PLAN_LIFETIME)
            )
        )

        val result = repository.restorePurchases()

        assertTrue(result is BillingRestoreResult.Restored)
        assertEquals(PLAN_LIFETIME, (result as BillingRestoreResult.Restored).planId)
        assertTrue(store.isPremiumUnlocked())
        assertEquals(PLAN_LIFETIME, store.getActivePlanId())
    }

    @Test
    fun restorePurchasesFailsWhenNoPurchaseFound() = runBlocking {
        val store = FakePremiumAccessStore()
        val repository = BillingRepository(
            context = appContext,
            premiumAccessStore = store,
            billingCatalogClient = FakeBillingCatalogClient()
        )

        val result = repository.restorePurchases()

        assertTrue(result is BillingRestoreResult.Failure)
        assertFalse(store.isPremiumUnlocked())
    }
}

private class FakePremiumAccessStore : PremiumAccessStore {
    private var premiumUnlocked: Boolean = false
    private var liveRecordings: Int = 0
    private var activePlanId: String? = null

    override fun isPremiumUnlocked(): Boolean = premiumUnlocked

    override fun setPremiumUnlocked(unlocked: Boolean) {
        premiumUnlocked = unlocked
    }

    override fun getLiveRecordingsCreated(): Int = liveRecordings

    override fun setLiveRecordingsCreated(count: Int) {
        liveRecordings = count
    }

    override fun getActivePlanId(): String? = activePlanId

    override fun setActivePlanId(planId: String?) {
        activePlanId = planId
    }
}

private class FakeBillingCatalogClient(
    private val catalog: List<RemoteBillingProduct> = emptyList(),
    private val purchases: List<String> = emptyList(),
    private val purchaseResult: BillingPurchaseResult = BillingPurchaseResult.Success
) : BillingCatalogClient {
    override suspend fun queryProductCatalog(): List<RemoteBillingProduct> = catalog

    override suspend fun queryActivePurchases(): List<String> = purchases

    override suspend fun launchPurchase(activity: Activity, planId: String): BillingPurchaseResult = purchaseResult

    override fun disconnect() = Unit
}

private fun createActivity(): Activity {
    return Robolectric.buildActivity(Activity::class.java).setup().get()
}
