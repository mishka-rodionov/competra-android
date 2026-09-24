package com.competra.remote.di

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.competra.remote.BuildConfig
import com.competra.domain.models.KindOfSport
import com.competra.domain.repository.auth.TokenRepository
import com.competra.remote.datasource.auth.AuthRemoteDataSource
import com.competra.remote.interceptors.MockInterceptor
import com.competra.remote.network.adapters.KindOfSportAdapter
import com.competra.remote.network.interceptors.AuthInterceptor
import com.competra.remote.network.retrofit.ResultCallAdapterFactory
import com.competra.remote.network.retrofit.TokenAuthenticator
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

private const val TIMEOUT_SECONDS = 60

/**
 * Таймауты клиента онлайн-трекинга: в лесу связь то есть, то нет — лучше быстро отказаться и
 * повторить батч, чем минуту держать соединение.
 */
private const val LIVE_TRACK_TIMEOUT_SECONDS = 10

/** Qualifier отдельного Retrofit онлайн-трекинга (свой OkHttp-пул, короткие таймауты). */
val LIVE_TRACK_RETROFIT = named("liveTrackRetrofit")

val retrofitModule = module {
    singleOf(::createGson)
    singleOf(::retrofit)
    single(LIVE_TRACK_RETROFIT) { liveTrackRetrofit(get(), get(), get()) }
}

fun retrofit(
    gson: Gson,
    tokenRepository: TokenRepository,
    context: Context
): Retrofit = buildRetrofit(gson, buildOkHttpClient(tokenRepository, context, TIMEOUT_SECONDS))

/**
 * Retrofit онлайн-трекинга: отдельный OkHttp-клиент, чтобы отправка точек не занимала соединения
 * основного API (синхронизация результатов, загрузка экранов), и короткие таймауты.
 */
fun liveTrackRetrofit(
    gson: Gson,
    tokenRepository: TokenRepository,
    context: Context
): Retrofit = buildRetrofit(gson, buildOkHttpClient(tokenRepository, context, LIVE_TRACK_TIMEOUT_SECONDS))

private fun buildOkHttpClient(tokenRepository: TokenRepository, context: Context, timeoutSeconds: Int): OkHttpClient {
    val builder = OkHttpClient.Builder()
    val collector = ChuckerCollector(context, true)
    val interceptor = ChuckerInterceptor
        .Builder(context)
        .collector(collector)
        .build()
    builder.addInterceptor(interceptor)
    // AuthInterceptor должен идти ДО HttpLoggingInterceptor, иначе в логе не видно
    // подставленный Authorization-заголовок (logger срабатывает раньше auth-интерсептора).
    // В release тела запросов/ответов не логируем, чтобы не светить данные и
    // токены в logcat прод-сборки.
    val logLevel = if (BuildConfig.DEBUG) {
        HttpLoggingInterceptor.Level.BODY
    } else {
        HttpLoggingInterceptor.Level.NONE
    }
    return builder
        .addInterceptor(AuthInterceptor(tokenRepository = tokenRepository))
        .addInterceptor(HttpLoggingInterceptor().setLevel(logLevel))
        .authenticator(TokenAuthenticator(tokenRepository = tokenRepository))
//        .addInterceptor(MockInterceptor())
        .retryOnConnectionFailure(true)
        .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .build()
}

private fun buildRetrofit(gson: Gson, okClient: OkHttpClient): Retrofit {
    // Базовый URL задаётся по buildType через BuildConfig (debug -> тест/override,
    // release -> прод). Старые dev-адреса оставлены для быстрого ручного переключения.
//    val localBaseUrl = "http://192.168.1.113:8080/"
//    val localBaseUrl = "http://188.68.223.12:8080/" // remote server
//    val localBaseUrl = "http://192.168.1.71:8080/"
    return Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .addCallAdapterFactory(ResultCallAdapterFactory())
        .client(okClient)
        .build()
}

private fun createGson(): Gson {
    return GsonBuilder()
        .registerTypeAdapter(KindOfSport::class.java, KindOfSportAdapter())
        .create()
}