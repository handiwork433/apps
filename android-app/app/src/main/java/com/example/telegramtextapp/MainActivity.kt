package com.example.telegramtextapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.telegramtextapp.data.TokenStore
import com.example.telegramtextapp.ui.theme.TelegramTextAppTheme
import com.example.telegramtextapp.util.LocalDateFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(applicationContext, TextsRepository())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TelegramTextAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val uiState by viewModel.uiState.collectAsState()
                    MainScreen(
                        state = uiState,
                        onSubmitToken = { viewModel.submitToken(it) },
                        onRetry = { viewModel.refresh() },
                        onChangeToken = { viewModel.clearToken() }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    state: MainUiState,
    onSubmitToken: (String) -> Unit,
    onRetry: () -> Unit,
    onChangeToken: () -> Unit
) {
    when (state) {
        is MainUiState.Loading -> LoadingState()
        is MainUiState.LoadingTexts -> LoadingState()
        is MainUiState.NeedToken -> TokenEntryScreen(
            message = state.message,
            botLink = state.botLink,
            onSubmitToken = onSubmitToken
        )
        is MainUiState.Texts -> ActiveSubscriptionState(
            state = state,
            onRefresh = onRetry,
            onChangeToken = onChangeToken
        )
        is MainUiState.SubscriptionInactive -> SubscriptionInactiveState(
            state = state,
            onRetry = onRetry,
            onChangeToken = onChangeToken
        )
        is MainUiState.Error -> ErrorState(
            message = state.message,
            botLink = state.botLink,
            onRetry = onRetry,
            onChangeToken = onChangeToken
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(id = R.string.loading))
    }
}

@Composable
private fun TokenEntryScreen(
    message: String?,
    botLink: String?,
    onSubmitToken: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val uriHandler = LocalUriHandler.current
    var token by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.token_prompt),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.token_instruction),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(text = stringResource(id = R.string.token_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onSubmitToken(token)
            })
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                focusManager.clearFocus()
                onSubmitToken(token)
            }
        ) {
            Text(text = stringResource(id = R.string.save_token))
        }
        if (!botLink.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { uriHandler.openUri(botLink) }) {
                Text(text = stringResource(id = R.string.open_bot))
            }
        }
    }
}

@Composable
private fun ActiveSubscriptionState(
    state: MainUiState.Texts,
    onRefresh: () -> Unit,
    onChangeToken: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = state.data.title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = state.data.subtitle,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = state.data.body,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        state.lastUpdated?.let { formatted ->
            Text(
                text = stringResource(id = R.string.last_updated, formatted),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        state.subscriptionExpiresAt?.let { expires ->
            Text(
                text = stringResource(id = R.string.subscription_expires, expires),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(modifier = Modifier.weight(1f), onClick = onRefresh) {
                Text(text = stringResource(id = R.string.refresh))
            }
            OutlinedButton(modifier = Modifier.weight(1f), onClick = onChangeToken) {
                Text(text = stringResource(id = R.string.change_token))
            }
        }
        if (!state.botLink.isNullOrBlank()) {
            TextButton(onClick = { uriHandler.openUri(state.botLink) }) {
                Text(text = stringResource(id = R.string.manage_in_bot))
            }
        }
    }
}

@Composable
private fun SubscriptionInactiveState(
    state: MainUiState.SubscriptionInactive,
    onRetry: () -> Unit,
    onChangeToken: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.subscription_inactive_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        state.subscriptionExpiresAt?.let { expires ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.subscription_was_until, expires),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(id = R.string.retry))
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onChangeToken, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(id = R.string.change_token))
        }
        if (!state.botLink.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { uriHandler.openUri(state.botLink) }) {
                Text(text = stringResource(id = R.string.open_bot))
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    botLink: String?,
    onRetry: () -> Unit,
    onChangeToken: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message.ifBlank { stringResource(id = R.string.error_generic) },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(id = R.string.retry))
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(onClick = onChangeToken) {
            Text(text = stringResource(id = R.string.change_token))
        }
        if (!botLink.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { uriHandler.openUri(botLink) }) {
                Text(text = stringResource(id = R.string.open_bot))
            }
        }
    }
}

class MainViewModel(
    private val tokenStore: TokenStore,
    private val repository: TextsRepository,
    private val formatter: LocalDateFormatter = LocalDateFormatter()
) : ViewModel() {

    private val _uiState: MutableStateFlow<MainUiState> = MutableStateFlow(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var currentToken: String? = null
    private var latestBotLink: String? = null

    init {
        viewModelScope.launch {
            tokenStore.tokenFlow
                .distinctUntilChanged()
                .collectLatest { token ->
                    val normalized = token?.trim()?.takeIf { it.isNotEmpty() }
                    currentToken = normalized
                    if (normalized == null) {
                        _uiState.value = MainUiState.NeedToken(
                            message = null,
                            botLink = resolvedBotLink()
                        )
                    } else {
                        fetchTexts(normalized)
                    }
                }
        }
    }

    fun submitToken(input: String) {
        val normalized = input.trim()
        if (normalized.isEmpty()) {
            _uiState.value = MainUiState.NeedToken(
                message = EMPTY_TOKEN_MESSAGE,
                botLink = resolvedBotLink()
            )
            return
        }
        viewModelScope.launch {
            tokenStore.save(normalized)
        }
    }

    fun refresh() {
        val token = currentToken
        if (token == null) {
            _uiState.value = MainUiState.NeedToken(
                message = null,
                botLink = resolvedBotLink()
            )
            return
        }
        viewModelScope.launch {
            fetchTexts(token)
        }
    }

    fun clearToken() {
        viewModelScope.launch {
            tokenStore.clear()
        }
    }

    private suspend fun fetchTexts(token: String) {
        _uiState.value = MainUiState.LoadingTexts
        when (val result = repository.fetchTexts(token)) {
            is RemoteResult.Success -> {
                val payload = result.payload
                val formattedUpdated = formatter.format(payload.lastUpdated)
                val formattedExpires = formatter.format(payload.subscription?.expiresAt)
                _uiState.value = MainUiState.Texts(
                    data = payload.texts,
                    lastUpdated = formattedUpdated,
                    subscriptionExpiresAt = formattedExpires,
                    botLink = resolvedBotLink()
                )
            }

            is RemoteResult.SubscriptionInactive -> {
                latestBotLink = result.botLink ?: latestBotLink
                val formattedExpires = formatter.format(result.subscription?.expiresAt)
                _uiState.value = MainUiState.SubscriptionInactive(
                    message = result.message,
                    subscriptionExpiresAt = formattedExpires,
                    botLink = resolvedBotLink()
                )
            }

            is RemoteResult.InvalidToken -> {
                latestBotLink = result.botLink ?: latestBotLink
                tokenStore.clear()
                _uiState.value = MainUiState.NeedToken(
                    message = result.message,
                    botLink = resolvedBotLink()
                )
            }

            is RemoteResult.NetworkError -> {
                _uiState.value = MainUiState.Error(
                    message = result.message,
                    botLink = resolvedBotLink()
                )
            }
        }
    }

    private fun resolvedBotLink(): String? {
        if (latestBotLink.isNullOrBlank()) {
            val fallback = BuildConfig.TELEGRAM_BOT_URL
            if (fallback.isNotBlank()) {
                latestBotLink = fallback
            }
        }
        return latestBotLink
    }

    companion object {
        private const val EMPTY_TOKEN_MESSAGE = "Введите токен, полученный в Telegram-боте."

        fun factory(context: Context, repository: TextsRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                        @Suppress("UNCHECKED_CAST")
                        return MainViewModel(TokenStore(context), repository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
    }
}

sealed class MainUiState {
    data object Loading : MainUiState()
    data object LoadingTexts : MainUiState()
    data class NeedToken(val message: String?, val botLink: String?) : MainUiState()
    data class Texts(
        val data: DisplayTexts,
        val lastUpdated: String?,
        val subscriptionExpiresAt: String?,
        val botLink: String?
    ) : MainUiState()

    data class SubscriptionInactive(
        val message: String,
        val subscriptionExpiresAt: String?,
        val botLink: String?
    ) : MainUiState()

    data class Error(val message: String, val botLink: String?) : MainUiState()
}
