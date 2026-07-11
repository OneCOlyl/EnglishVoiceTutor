package com.example.englishvoicetutor.domain.model

sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    data class Downloading(
        val progressPercent: Int,
        val logs: List<String>
    ) : ModelDownloadState
    data object Ready : ModelDownloadState
    data class Error(val message: String) : ModelDownloadState
}