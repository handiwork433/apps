package com.example.telegramtextapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.tokenDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "token_store"
)

class TokenStore(private val context: Context) {

    private val tokenKey = stringPreferencesKey("token")

    val tokenFlow: Flow<String?> = context.tokenDataStore.data.map { preferences ->
        preferences[tokenKey]
    }

    suspend fun save(token: String) {
        context.tokenDataStore.edit { preferences ->
            preferences[tokenKey] = token
        }
    }

    suspend fun clear() {
        context.tokenDataStore.edit { preferences ->
            preferences.remove(tokenKey)
        }
    }
}
