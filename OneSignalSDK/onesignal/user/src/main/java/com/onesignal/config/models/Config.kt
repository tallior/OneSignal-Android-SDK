package com.onesignal.config.models

import com.onesignal.models.modeling.Model

class Config : Model() {
    /**
     * Interval at which the [OperationRepo] will release batches of [Operation] when not forced
     */
    var operationReleaseInterval: Double = 30.0

    /**
     * Maximum number of [Operation] allowed in the [OperationRepo] before it releases a batch
     */
    var operationSoftCap: UInt = 5u

    /**
     * Maximum number of [Operation] allowed to be released by the [OperationRepo] at once
     */
    var operationHardCap: UInt = 10u

    /**
     * Whether or not [Operation] will be written to disk
     */
    var allowOffline: Boolean = false

    /**
     * Maximum time a request monitored by [RequestController] can be in flight before error
     */
    var requestTimeout: Double = 10.0

    /**
     * Maximum time in minutes a user can spend out of focus before a new session is generated by the [SessionController]
     */
    var sessionFocusTimeout: Double = 10.0

    /**
     * Operation repo file path
     */
    var operationRepoCachePath: String = "" // todo
}