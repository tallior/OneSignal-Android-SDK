package com.onesignal.core.internal.operations

import com.onesignal.core.internal.operations.executors.SubscriptionOperationExecutor

internal class UpdateSubscriptionOperation(
    val id: String,
    val property: String,
    val value: Any?
) : Operation(SubscriptionOperationExecutor.UPDATE_SUBSCRIPTION)