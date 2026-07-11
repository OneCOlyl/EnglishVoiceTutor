package com.example.englishvoicetutor.presentation.setup

sealed interface ModelSetupState {
    data object Idle : ModelSetupState
    data class Downloading(
        val progressPercent: Int,
        val downloadedMb: Long,
        val totalMb: Long,
        val status: String
    ) : ModelSetupState
    data class Loading(val message: String) : ModelSetupState
    data class Error(val message: String) : ModelSetupState
}