package eu.kanade.tachiyomi

import android.app.ActivityManager
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import eu.kanade.tachiyomi.data.coil.AnimeCoverFetcher
import eu.kanade.tachiyomi.data.coil.ByteBufferFetcher
import eu.kanade.tachiyomi.data.coil.MangaCoverFetcher
import eu.kanade.tachiyomi.data.coil.TachiyomiImageDecoder
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferenceValues
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.asImmediateFlow
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.system.notification
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.conscrypt.Conscrypt
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.security.Security

open class App : Application(), LifecycleObserver, ImageLoaderFactory {

    private val preferences: PreferencesHelper by injectLazy()

    private val disableIncognitoReceiver = DisableIncognitoReceiver()

    override fun onCreate() {
        super.onCreate()

        val builder: VmPolicy.Builder = VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        // TLS 1.3 support for Android < 10
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }

        // Avoid potential crashes
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val process = getProcessName()
            if (packageName != process) WebView.setDataDirectorySuffix(process)
        }

        Injekt.importModule(AppModule(this))

        setupAcra()
        setupNotificationChannels()

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Show notification to disable Incognito Mode when it's enabled
        preferences.incognitoMode().asFlow()
            .onEach { enabled ->
                val notificationManager = NotificationManagerCompat.from(this)
                if (enabled) {
                    disableIncognitoReceiver.register()
                    val notification = notification(Notifications.CHANNEL_INCOGNITO_MODE) {
                        setContentTitle(getString(R.string.pref_incognito_mode))
                        setContentText(getString(R.string.notification_incognito_text))
                        setSmallIcon(R.drawable.ic_glasses_24dp)
                        setOngoing(true)

                        val pendingIntent = PendingIntent.getBroadcast(
                            this@App,
                            0,
                            Intent(ACTION_DISABLE_INCOGNITO_MODE),
                            PendingIntent.FLAG_ONE_SHOT
                        )
                        setContentIntent(pendingIntent)
                    }
                    notificationManager.notify(Notifications.ID_INCOGNITO_MODE, notification)
                } else {
                    disableIncognitoReceiver.unregister()
                    notificationManager.cancel(Notifications.ID_INCOGNITO_MODE)
                }
            }
            .launchIn(ProcessLifecycleOwner.get().lifecycleScope)

        preferences.themeMode()
            .asImmediateFlow {
                AppCompatDelegate.setDefaultNightMode(
                    when (it) {
                        PreferenceValues.ThemeMode.light -> AppCompatDelegate.MODE_NIGHT_NO
                        PreferenceValues.ThemeMode.dark -> AppCompatDelegate.MODE_NIGHT_YES
                        PreferenceValues.ThemeMode.system -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                )
            }.launchIn(ProcessLifecycleOwner.get().lifecycleScope)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this).apply {
            componentRegistry {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder(this@App))
                } else {
                    add(GifDecoder())
                }
                add(TachiyomiImageDecoder(this@App.resources))
                add(ByteBufferFetcher())
                add(AnimeCoverFetcher())
                add(MangaCoverFetcher())
            }
            okHttpClient(Injekt.get<NetworkHelper>().coilClient)
            crossfade((300 * this@App.animatorDurationScale).toInt())
            allowRgb565(getSystemService<ActivityManager>()!!.isLowRamDevice)
        }.build()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    @Suppress("unused")
    fun onAppBackgrounded() {
        if (!AuthenticatorUtil.isAuthenticating && preferences.lockAppAfter().get() >= 0) {
            SecureActivityDelegate.locked = true
        }
    }

    protected open fun setupAcra() {
        // removed acra
    }

    protected open fun setupNotificationChannels() {
        Notifications.createChannels(this)
    }

    private inner class DisableIncognitoReceiver : BroadcastReceiver() {
        private var registered = false

        override fun onReceive(context: Context, intent: Intent) {
            preferences.incognitoMode().set(false)
        }

        fun register() {
            if (!registered) {
                registerReceiver(this, IntentFilter(ACTION_DISABLE_INCOGNITO_MODE))
                registered = true
            }
        }

        fun unregister() {
            if (registered) {
                unregisterReceiver(this)
                registered = false
            }
        }
    }

    companion object {
        private const val ACTION_DISABLE_INCOGNITO_MODE = "tachi.action.DISABLE_INCOGNITO_MODE"
    }
}
