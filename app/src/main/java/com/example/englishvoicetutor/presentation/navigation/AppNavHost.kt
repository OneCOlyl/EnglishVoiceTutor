package com.example.englishvoicetutor.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.englishvoicetutor.data.engine.LiteRtLlmEngine
import com.example.englishvoicetutor.domain.model.NEW_CONVERSATION_ID
import com.example.englishvoicetutor.presentation.conversation.ConversationScreen
import com.example.englishvoicetutor.presentation.history.HistoryScreen
import com.example.englishvoicetutor.presentation.setup.ModelSetupScreen

private const val ROUTE_HISTORY = "history"
private const val ROUTE_CONVERSATION = "conversation/{conversationId}"
private const val ARG_CONVERSATION_ID = "conversationId"
private const val ROUTE_SETUP = "setup"
@Composable
fun AppNavHost(
    micPermissionGranted: Boolean,
    onRequestMicPermission: () -> Unit,
    isModelReady: Boolean
) {
    val navController = rememberNavController()
    val startDestination = if (isModelReady) ROUTE_HISTORY else ROUTE_SETUP

    NavHost(navController = navController, startDestination = startDestination) {
        composable(ROUTE_SETUP) {
            ModelSetupScreen(
                onModelReady = {
                    navController.navigate(ROUTE_HISTORY) {
                        popUpTo(ROUTE_SETUP) { inclusive = true }
                    }
                }
            )
        }
        composable(ROUTE_HISTORY) {
            HistoryScreen(
                onOpenConversation = { id -> navController.navigate("conversation/$id") },
                onNewConversation = { navController.navigate("conversation/$NEW_CONVERSATION_ID") }
            )
        }

        composable(
            route = ROUTE_CONVERSATION,
            arguments = listOf(navArgument(ARG_CONVERSATION_ID) { type = NavType.LongType })
        ) {
            ConversationScreen(
                micPermissionGranted = micPermissionGranted,
                onRequestMicPermission = onRequestMicPermission,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
