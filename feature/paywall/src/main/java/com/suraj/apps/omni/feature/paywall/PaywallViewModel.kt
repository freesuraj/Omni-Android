package com.suraj.apps.omni.feature.paywall

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.suraj.apps.omni.core.data.entitlement.BillingPlan
import com.suraj.apps.omni.core.data.entitlement.BillingPurchaseResult
import com.suraj.apps.omni.core.data.entitlement.BillingRepository
import com.suraj.apps.omni.core.data.entitlement.BillingRestoreResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaywallPlanUi(
    val productId: String,
    val title: String,
    val subtitle: String,
    val priceLabel: String,
    val badge: String? = null
)

data class PaywallUiState(
    val plans: List<PaywallPlanUi> = emptyList(),
    val selectedPlanId: String? = null,
    val isPremiumUnlocked: Boolean = false,
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val shouldClose: Boolean = false
)

class PaywallViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val repository = BillingRepository(application.applicationContext)
    private val app = application

    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            refresh()
        }
    }

    fun selectPlan(planId: String) {
        _uiState.update { it.copy(selectedPlanId = planId) }
    }

    fun purchaseSelectedPlan() {
        val planId = _uiState.value.selectedPlanId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null, successMessage = null) }
            when (val result = repository.purchasePlan(planId)) {
                BillingPurchaseResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isPremiumUnlocked = true,
                            isWorking = false,
                            successMessage = app.getString(R.string.paywall_message_unlocked),
                            shouldClose = true
                        )
                    }
                }

                is BillingPurchaseResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun restorePurchases() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null, successMessage = null) }
            when (val restore = repository.restorePurchases()) {
                is BillingRestoreResult.Restored -> {
                    _uiState.update {
                        it.copy(
                            isPremiumUnlocked = true,
                            isWorking = false,
                            successMessage = app.getString(R.string.paywall_message_restored),
                            shouldClose = true
                        )
                    }
                }

                is BillingRestoreResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            errorMessage = restore.message
                        )
                    }
                }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun consumeSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    fun consumeCloseSignal() {
        _uiState.update { it.copy(shouldClose = false) }
    }

    private suspend fun refresh() {
        val entitlement = repository.entitlement()
        val plans = repository.loadPlans().map { plan -> plan.toUi() }
        val defaultSelection = plans.firstOrNull { it.badge != null }?.productId
            ?: plans.firstOrNull()?.productId

        _uiState.update {
            it.copy(
                plans = plans,
                selectedPlanId = defaultSelection,
                isPremiumUnlocked = entitlement.isPremiumUnlocked,
                isLoading = false,
                isWorking = false,
                errorMessage = null
            )
        }
    }

    private fun BillingPlan.toUi(): PaywallPlanUi {
        val badge = when {
            isBestValue && hasFreeTrial -> app.getString(R.string.paywall_badge_best_value_trial)
            isBestValue -> app.getString(R.string.paywall_badge_best_value)
            isLifetime -> app.getString(R.string.paywall_badge_one_time)
            else -> null
        }
        return PaywallPlanUi(
            productId = productId,
            title = title,
            subtitle = subtitle,
            priceLabel = priceLabel,
            badge = badge
        )
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PaywallViewModel(application = application) as T
                }
            }
        }
    }
}
