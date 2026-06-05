package dev.dimension.flare

import android.content.Context
import android.content.Intent
import android.app.assist.AssistContent
import android.net.ConnectivityManager
import android.net.Uri
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.dimension.flare.common.VideoDownloadHelper
import dev.dimension.flare.data.network.xiaohongshu.XhsAndroidWebSigner
import dev.dimension.flare.data.network.zhihu.ZhihuAndroidWebSearch
import dev.dimension.flare.ui.AppContainer
import dev.dimension.flare.ui.assist.AssistContentState
import dev.dimension.flare.ui.component.platform.LocalWifiState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private var keepSplashOnScreen = true
    private val videoDownloadHelper by inject<VideoDownloadHelper>()
    private val wifiStateFlow by lazy {
        observeWifiStateAsFlow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { keepSplashOnScreen }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        XhsAndroidWebSigner.attach(this)
        ZhihuAndroidWebSearch.attach(this)
        setContent {
            val wifiState by wifiStateFlow.collectAsState(false)
            CompositionLocalProvider(
                LocalWifiState provides wifiState,
            ) {
                AppContainer(
                    afterInit = {
                        keepSplashOnScreen = false
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        videoDownloadHelper.onResume()
    }

    override fun onPause() {
        super.onPause()
        videoDownloadHelper.onPause()
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        val webUri = AssistContentState.webUri ?: return
        val uri = Uri.parse(webUri)
        outContent.setWebUri(uri)
        outContent.setIntent(Intent(Intent.ACTION_VIEW, uri))
    }

    override fun onDestroy() {
        XhsAndroidWebSigner.detach(this)
        ZhihuAndroidWebSearch.detach(this)
        super.onDestroy()
    }
}

private fun Context.observeWifiStateAsFlow() =
    callbackFlow {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest =
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        val networkCallback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    trySend(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                }
            }
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        val currentNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(currentNetwork)
        trySend(caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }
