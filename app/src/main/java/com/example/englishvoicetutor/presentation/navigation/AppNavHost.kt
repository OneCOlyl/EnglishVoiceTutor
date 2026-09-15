package com.example.englishvoicetutor.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.englishvoicetutor.domain.model.NEW_CONVERSATION_ID
import com.example.englishvoicetutor.presentation.conversation.ConversationScreen
import com.example.englishvoicetutor.presentation.history.HistoryScreen
import com.example.englishvoicetutor.presentation.learning.GrammarScreen
import com.example.englishvoicetutor.presentation.learning.ReviewScreen
import com.example.englishvoicetutor.presentation.learning.RoadmapScreen
import com.example.englishvoicetutor.presentation.learning.RuleScreen
import com.example.englishvoicetutor.presentation.learning.TopicScreen
import com.example.englishvoicetutor.presentation.learning.VocabularyScreen
import com.example.englishvoicetutor.presentation.settings.SettingsScreen
import com.example.englishvoicetutor.presentation.setup.ModelSetupScreen

private const val ROUTE_ROADMAP = "roadmap"
private const val ROUTE_VOCABULARY = "vocabulary"
private const val ROUTE_GRAMMAR = "grammar"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_REVIEW = "review"
private const val ROUTE_TOPIC = "topic/{topicId}"
private const val ROUTE_RULE = "rule/{ruleId}"
private const val ROUTE_CONVERSATION = "conversation/{conversationId}?topicId={topicId}"
private const val ARG_CONVERSATION_ID = "conversationId"
private const val ARG_TOPIC_ID = "topicId"
private const val ARG_RULE_ID = "ruleId"
private const val ROUTE_SETUP = "setup"
private const val ROUTE_SETTINGS = "settings"

/** Вкладка нижней панели. Порядок здесь = порядок вкладок на экране. */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    ROADMAP(ROUTE_ROADMAP, "Курс", Icons.Filled.Route),
    VOCABULARY(ROUTE_VOCABULARY, "Слова", Icons.Filled.Style),
    GRAMMAR(ROUTE_GRAMMAR, "Правила", Icons.AutoMirrored.Filled.MenuBook),
    HISTORY(ROUTE_HISTORY, "Диалоги", Icons.AutoMirrored.Filled.Chat)
}

@Composable
fun AppNavHost(
    micPermissionGranted: Boolean,
    onRequestMicPermission: () -> Unit,
    isModelReady: Boolean
) {
    val navController = rememberNavController()
    val startDestination = if (isModelReady) ROUTE_ROADMAP else ROUTE_SETUP
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        bottomBar = {
            // Панель видна только на корневых вкладках: на экранах-деталях
            // (диалог, тема, правило, настройка модели) она только мешает.
            if (Tab.entries.any { it.route == currentRoute }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.switchTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable(ROUTE_SETUP) {
                ModelSetupScreen(
                    onModelReady = {
                        navController.navigate(ROUTE_ROADMAP) {
                            popUpTo(ROUTE_SETUP) { inclusive = true }
                        }
                    }
                )
            }

            composable(ROUTE_ROADMAP) {
                RoadmapScreen(
                    onOpenTopic = { topicId -> navController.navigate("topic/$topicId") },
                    onStartReview = { navController.navigate(ROUTE_REVIEW) }
                )
            }

            composable(ROUTE_VOCABULARY) {
                VocabularyScreen(onStartReview = { navController.navigate(ROUTE_REVIEW) })
            }

            composable(ROUTE_GRAMMAR) {
                GrammarScreen(onOpenRule = { ruleId -> navController.navigate("rule/$ruleId") })
            }

            composable(ROUTE_HISTORY) {
                HistoryScreen(
                    onOpenConversation = { id -> navController.navigate("conversation/$id") },
                    onNewConversation = {
                        navController.navigate("conversation/$NEW_CONVERSATION_ID")
                    },
                    onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
                )
            }

            composable(ROUTE_REVIEW) {
                ReviewScreen(
                    micPermissionGranted = micPermissionGranted,
                    onRequestMicPermission = onRequestMicPermission,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = ROUTE_TOPIC,
                arguments = listOf(navArgument(ARG_TOPIC_ID) { type = NavType.StringType })
            ) {
                TopicScreen(
                    onBack = { navController.popBackStack() },
                    onStartConversation = { topicId ->
                        navController.navigate(
                            "conversation/$NEW_CONVERSATION_ID?topicId=$topicId"
                        )
                    },
                    onOpenRule = { ruleId -> navController.navigate("rule/$ruleId") }
                )
            }

            composable(
                route = ROUTE_RULE,
                arguments = listOf(navArgument(ARG_RULE_ID) { type = NavType.StringType })
            ) {
                RuleScreen(onBack = { navController.popBackStack() })
            }

            composable(ROUTE_SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = ROUTE_CONVERSATION,
                arguments = listOf(
                    navArgument(ARG_CONVERSATION_ID) { type = NavType.LongType },
                    navArgument(ARG_TOPIC_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                ConversationScreen(
                    micPermissionGranted = micPermissionGranted,
                    onRequestMicPermission = onRequestMicPermission,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * Переход между вкладками: без накопления стека и с сохранением состояния каждой
 * вкладки — стандартное поведение нижней навигации.
 */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
