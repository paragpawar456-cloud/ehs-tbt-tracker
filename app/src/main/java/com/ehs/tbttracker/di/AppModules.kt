package com.ehs.tbttracker.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.ehs.tbttracker.BuildConfig
import com.ehs.tbttracker.data.local.TbtDao
import com.ehs.tbttracker.data.local.TbtDatabase
import com.ehs.tbttracker.data.local.MasterContractorStore
import com.ehs.tbttracker.data.local.PrefsMasterContractorStore
import com.ehs.tbttracker.data.network.NetworkConnectivityObserver
import com.ehs.tbttracker.data.photo.FilePhotoStorage
import com.ehs.tbttracker.data.photo.FusedLocationProvider
import com.ehs.tbttracker.data.photo.LocationProvider
import com.ehs.tbttracker.data.photo.PhotoStorage
import com.ehs.tbttracker.data.remote.BackendConfig
import com.ehs.tbttracker.data.remote.SheetsWebAppApi
import com.ehs.tbttracker.data.repository.TbtRepositoryImpl
import com.ehs.tbttracker.data.sync.WorkManagerSyncScheduler
import com.ehs.tbttracker.domain.repository.ConnectivityObserver
import com.ehs.tbttracker.domain.repository.SyncScheduler
import com.ehs.tbttracker.domain.repository.TbtRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @Singleton
    fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun backendConfig(): BackendConfig = BackendConfig(BuildConfig.WEB_APP_URL, BuildConfig.API_TOKEN)

    @Provides @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // Apps Script cold starts + Drive upload can take a while.
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .apply {
            if (BuildConfig.DEBUG) {
                // Masks the shared secret before anything reaches logcat.
                val tokenPattern = Regex("token=[^&\\s]+")
                addInterceptor(
                    HttpLoggingInterceptor { msg -> android.util.Log.d("TbtHttp", msg.replace(tokenPattern, "token=***")) }
                        .apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
            }
        }
        .build()

    @Provides @Singleton
    fun api(client: OkHttpClient, json: Json): SheetsWebAppApi = Retrofit.Builder()
        .baseUrl("https://script.google.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SheetsWebAppApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): TbtDatabase =
        Room.databaseBuilder(context, TbtDatabase::class.java, TbtDatabase.NAME).build()

    @Provides
    fun tbtDao(db: TbtDatabase): TbtDao = db.tbtDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds @Singleton abstract fun repository(impl: TbtRepositoryImpl): TbtRepository
    @Binds @Singleton abstract fun syncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler
    @Binds @Singleton abstract fun connectivity(impl: NetworkConnectivityObserver): ConnectivityObserver
    @Binds @Singleton abstract fun photoStorage(impl: FilePhotoStorage): PhotoStorage
    @Binds @Singleton abstract fun locationProvider(impl: FusedLocationProvider): LocationProvider
    @Binds @Singleton abstract fun masterStore(impl: PrefsMasterContractorStore): MasterContractorStore
}
