package com.suraj.apps.omni.core.data.entitlement

import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class BillingPlan(
    val productId: String,
    val title: String,
    val subtitle: String,
    val priceLabel: String,
    val hasFreeTrial: Boolean,
    val isBestValue: Boolean,
    val isLifetime: Boolean,
    val source: BillingPlanSource
)

enum class BillingPlanSource {
    PLAY_STORE,
    LOCAL_FALLBACK
}

data class BillingEntitlement(
    val isPremiumUnlocked: Boolean,
    val activePlanId: String?
)

sealed interface BillingPurchaseResult {
    data object Success : BillingPurchaseResult
    data class Failure(val message: String) : BillingPurchaseResult
}

sealed interface BillingRestoreResult {
    data class Restored(val planId: String?) : BillingRestoreResult
    data class Failure(val message: String) : BillingRestoreResult
}

data class RemoteBillingProduct(
    val productId: String,
    val priceLabel: String,
    val hasFreeTrial: Boolean
)

interface BillingCatalogClient {
    suspend fun queryProductCatalog(): List<RemoteBillingProduct>
    suspend fun queryActivePurchases(): List<String>
    fun disconnect()
}

class BillingRepository(
    context: Context,
    private val premiumAccessStore: PremiumAccessStore = SharedPrefsPremiumAccessStore(context),
    private val billingCatalogClient: BillingCatalogClient = GooglePlayBillingCatalogClient(context),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun entitlement(): BillingEntitlement {
        return BillingEntitlement(
            isPremiumUnlocked = premiumAccessStore.isPremiumUnlocked(),
            activePlanId = premiumAccessStore.getActivePlanId()
        )
    }

    suspend fun loadPlans(): List<BillingPlan> = withContext(ioDispatcher) {
        val defaults = defaultPlans()
        val remoteCatalog = runCatching {
            billingCatalogClient.queryProductCatalog()
        }.getOrDefault(emptyList())
        if (remoteCatalog.isEmpty()) return@withContext defaults

        defaults.map { default ->
            val remote = remoteCatalog.firstOrNull { it.productId == default.productId }
            if (remote == null) {
                default
            } else {
                default.copy(
                    priceLabel = remote.priceLabel,
                    hasFreeTrial = default.hasFreeTrial || remote.hasFreeTrial,
                    source = BillingPlanSource.PLAY_STORE
                )
            }
        }
    }

    suspend fun purchasePlan(planId: String): BillingPurchaseResult = withContext(ioDispatcher) {
        if (planId !in PLAN_PRODUCT_IDS) {
            return@withContext BillingPurchaseResult.Failure("Selected plan is unavailable.")
        }

        premiumAccessStore.setPremiumUnlocked(true)
        premiumAccessStore.setActivePlanId(planId)
        BillingPurchaseResult.Success
    }

    suspend fun restorePurchases(): BillingRestoreResult = withContext(ioDispatcher) {
        if (premiumAccessStore.isPremiumUnlocked()) {
            return@withContext BillingRestoreResult.Restored(premiumAccessStore.getActivePlanId())
        }

        val storedPlanId = premiumAccessStore.getActivePlanId()
        if (!storedPlanId.isNullOrBlank()) {
            premiumAccessStore.setPremiumUnlocked(true)
            return@withContext BillingRestoreResult.Restored(storedPlanId)
        }

        val purchases = runCatching {
            billingCatalogClient.queryActivePurchases()
        }.getOrDefault(emptyList())
        val restoredPlan = purchases.firstOrNull { it in PLAN_PRODUCT_IDS }
            ?: return@withContext BillingRestoreResult.Failure("No restorable purchases found for this account.")

        premiumAccessStore.setPremiumUnlocked(true)
        premiumAccessStore.setActivePlanId(restoredPlan)
        BillingRestoreResult.Restored(restoredPlan)
    }

    private fun defaultPlans(): List<BillingPlan> {
        return listOf(
            BillingPlan(
                productId = PLAN_MONTHLY,
                title = "Monthly",
                subtitle = "Flexible monthly access",
                priceLabel = "$4.99/month",
                hasFreeTrial = false,
                isBestValue = false,
                isLifetime = false,
                source = BillingPlanSource.LOCAL_FALLBACK
            ),
            BillingPlan(
                productId = PLAN_YEARLY_TRIAL,
                title = "Yearly",
                subtitle = "7-day free trial, then annual billing",
                priceLabel = "$29.99/year",
                hasFreeTrial = true,
                isBestValue = true,
                isLifetime = false,
                source = BillingPlanSource.LOCAL_FALLBACK
            ),
            BillingPlan(
                productId = PLAN_LIFETIME,
                title = "Lifetime",
                subtitle = "One-time purchase, no recurring fees",
                priceLabel = "$79.99 one-time",
                hasFreeTrial = false,
                isBestValue = false,
                isLifetime = true,
                source = BillingPlanSource.LOCAL_FALLBACK
            )
        )
    }
}

class GooglePlayBillingCatalogClient(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BillingCatalogClient {
    private val billingClient = BillingClient.newBuilder(context)
        .enablePendingPurchases()
        .setListener { _: BillingResult, _: MutableList<Purchase>? -> }
        .build()

    override suspend fun queryProductCatalog(): List<RemoteBillingProduct> = withContext(ioDispatcher) {
        if (!ensureConnected()) return@withContext emptyList()

        val subscriptionDetails = queryProductDetails(
            productIds = listOf(PLAN_MONTHLY, PLAN_YEARLY_TRIAL),
            productType = BillingClient.ProductType.SUBS
        )
        val inAppDetails = queryProductDetails(
            productIds = listOf(PLAN_LIFETIME),
            productType = BillingClient.ProductType.INAPP
        )

        (subscriptionDetails + inAppDetails).mapNotNull { details ->
            val mappedPrice = when (details.productType) {
                BillingClient.ProductType.SUBS -> {
                    details.subscriptionOfferDetails
                        ?.firstOrNull()
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.lastOrNull()
                        ?.formattedPrice
                }

                BillingClient.ProductType.INAPP -> {
                    details.oneTimePurchaseOfferDetails?.formattedPrice
                }

                else -> null
            }

            val hasTrial = details.subscriptionOfferDetails
                ?.any { offer ->
                    offer.pricingPhases.pricingPhaseList.any { phase -> phase.priceAmountMicros == 0L }
                }
                ?: false

            val priceLabel = mappedPrice ?: return@mapNotNull null
            RemoteBillingProduct(
                productId = details.productId,
                priceLabel = priceLabel,
                hasFreeTrial = hasTrial
            )
        }
    }

    override suspend fun queryActivePurchases(): List<String> = withContext(ioDispatcher) {
        if (!ensureConnected()) return@withContext emptyList()

        val subscriptions = queryPurchases(BillingClient.ProductType.SUBS)
        val oneTime = queryPurchases(BillingClient.ProductType.INAPP)
        (subscriptions + oneTime)
            .flatMap { purchase -> purchase.products }
            .distinct()
    }

    override fun disconnect() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    private suspend fun ensureConnected(): Boolean {
        if (billingClient.isReady) return true

        return suspendCancellableCoroutine { continuation ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    continuation.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (!continuation.isCompleted) {
                        continuation.resume(false)
                    }
                }
            })
        }
    }

    private suspend fun queryProductDetails(
        productIds: List<String>,
        productType: String
    ): List<ProductDetails> {
        if (productIds.isEmpty()) return emptyList()

        val products = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        return suspendCancellableCoroutine { continuation ->
            billingClient.queryProductDetailsAsync(params) { result, details ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(details)
                } else {
                    continuation.resume(emptyList())
                }
            }
        }
    }

    private suspend fun queryPurchases(productType: String): List<Purchase> {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(productType)
            .build()

        return suspendCancellableCoroutine { continuation ->
            billingClient.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    continuation.resume(purchases)
                } else {
                    continuation.resume(emptyList())
                }
            }
        }
    }
}

const val PLAN_MONTHLY = "omni_pro_monthly"
const val PLAN_YEARLY_TRIAL = "omni_pro_yearly_trial"
const val PLAN_LIFETIME = "omni_pro_lifetime"

private val PLAN_PRODUCT_IDS = setOf(
    PLAN_MONTHLY,
    PLAN_YEARLY_TRIAL,
    PLAN_LIFETIME
)
