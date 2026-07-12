package com.warden.blocker.security

/** Outcome of a PIN verification attempt. */
sealed interface PinResult {
    data object Ok : PinResult
    data object Wrong : PinResult
    /** Locked out due to too many failures; [seconds] until it can be retried. */
    data class Locked(val seconds: Int) : PinResult
}
