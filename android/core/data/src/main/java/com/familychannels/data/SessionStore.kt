package com.familychannels.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("session")

class SessionStore(private val context: Context) {
    private val tokenKey = stringPreferencesKey("token")
    private val familyKey = stringPreferencesKey("family_code")
    private val childKey = stringPreferencesKey("child_id")
    private val langKey = stringPreferencesKey("lang")

    suspend fun saveSession(token: String, familyCode: String, childId: String) {
        context.dataStore.edit {
            it[tokenKey] = token
            it[familyKey] = familyCode
            it[childKey] = childId
        }
    }

    suspend fun token(): String? =
        context.dataStore.data.map { it[tokenKey] }.first()

    suspend fun familyCode(): String? =
        context.dataStore.data.map { it[familyKey] }.first()

    suspend fun setLang(lang: String) {
        context.dataStore.edit { it[langKey] = lang }
    }

    suspend fun lang(): String =
        context.dataStore.data.map { it[langKey] ?: "fr" }.first()

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
