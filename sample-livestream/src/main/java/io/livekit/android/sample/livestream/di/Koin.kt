package io.livekit.android.sample.livestream.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.livekit.android.sample.livestream.model.AppModel
import io.livekit.android.sample.livestream.model.MainNav
import io.livekit.android.sample.livestream.model.RoomNav
import io.livekit.android.sample.livestream.ServerInfo
import io.livekit.android.sample.livestream.model.MainNavModel
import io.livekit.android.sample.livestream.model.RoomNavModel
import io.livekit.android.sample.livestream.room.data.LKJson
import io.livekit.android.sample.livestream.room.data.LivestreamApi
import io.livekit.android.sample.livestream.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.KoinAppDeclaration
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import retrofit2.Retrofit

fun initKoin(enableNetworkLogs: Boolean = false, appDeclaration: KoinAppDeclaration = {}) =
    startKoin {
        appDeclaration()
        modules(appModule())
    }

fun appModule() = module {
    val contentType = "application/json".toMediaType()

    single { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    single<MainNav> { MainNavModel(get()) }
    single<RoomNav> { RoomNavModel(get()) }

    singleOf(::AppModel)
    singleOf(::PreferencesManager)

    single<OkHttpClient> {
        OkHttpClient.Builder()
            // TODO take in consideration enableNetworkLogs parameter
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    setLevel(HttpLoggingInterceptor.Level.HEADERS)
                }
            )
            .build()
    }

    single<Retrofit> {
        Retrofit.Builder()
            .baseUrl(ServerInfo.API_SERVER_URL)
            .client(get())
            .addConverterFactory(LKJson.asConverterFactory(contentType))
            .build()
    }

    single<LivestreamApi> {
        get<Retrofit>().create(LivestreamApi::class.java)
    }

}
