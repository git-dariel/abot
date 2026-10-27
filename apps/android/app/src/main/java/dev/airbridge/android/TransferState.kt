package dev.airbridge.android

enum class TransferState {
    IDLE,
    FILE_SELECTED,
    GESTURE_ARMED,
    TRANSFER_OFFERED,
    AWAITING_REMOTE_CONFIRMATION,
    ACCEPTED,
    TRANSFERRING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED,
}
