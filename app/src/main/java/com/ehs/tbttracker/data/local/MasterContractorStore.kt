package com.ehs.tbttracker.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Caches the "Master Contractors" names so the portal works offline. */
interface MasterContractorStore {
    val names: Flow<List<String>>
    fun save(names: List<String>)
}

@Singleton
class PrefsMasterContractorStore @Inject constructor(
    @ApplicationContext context: Context,
) : MasterContractorStore {
    private val prefs = context.getSharedPreferences("master_contractors", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    override val names: Flow<List<String>> = state.asStateFlow()

    override fun save(names: List<String>) {
        val clean = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        prefs.edit().putString(KEY, clean.joinToString(SEP)).apply()
        state.value = clean
    }

    private fun load(): List<String> =
        prefs.getString(KEY, null)?.split(SEP)?.filter { it.isNotBlank() }.orEmpty()

    private companion object {
        const val KEY = "names"
        const val SEP = "\u001F"
    }
}
