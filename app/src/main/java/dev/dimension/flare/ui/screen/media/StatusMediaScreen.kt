package dev.dimension.flare.ui.screen.media

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.window.core.layout.WindowSizeClass
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.CircleInfo
import compose.icons.fontawesomeicons.solid.Copy
import compose.icons.fontawesomeicons.solid.Download
import compose.icons.fontawesomeicons.solid.Pause
import compose.icons.fontawesomeicons.solid.Play
import compose.icons.fontawesomeicons.solid.ShareNodes
import compose.icons.fontawesomeicons.solid.Tv
import compose.icons.fontawesomeicons.solid.Xmark
import dev.dimension.flare.R
import dev.dimension.flare.common.VideoDownloadHelper
import dev.dimension.flare.data.datasource.xiaohongshu.XhsStatusMediaLazyResolver
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.Glassify
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.SurfaceBindingManager
import dev.dimension.flare.ui.component.VideoPlayer
import dev.dimension.flare.ui.component.placeholder
import dev.dimension.flare.ui.component.status.CommonStatusComponent
import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.model.StatusMediaRouteCache
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.getFileName
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.theme.FlareTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.Viewport
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.spatial.CoordinateSpace
import moe.tlaster.precompose.molecule.producePresenter
import moe.tlaster.swiper.Swiper
import moe.tlaster.swiper.rememberSwiperState
import org.koin.compose.koinInject
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

private const val MediaOverlayPostMaxLines = 2

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalPermissionsApi::class,
)
@Composable
internal fun StatusMediaScreen(
    statusKey: MicroBlogKey,
    accountType: AccountType,
    index: Int,
    preview: String?,
    onDismiss: () -> Unit,
    toStatusDetail: () -> Unit,
    toAltText: (UiMedia) -> Unit,
    uriHandler: UriHandler,
    surfaceBindingManager: SurfaceBindingManager = koinInject(),
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val isBigScreen = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)
    val hapticFeedback = LocalHapticFeedback.current
    val context = LocalContext.current
    val appearanceSettings = LocalAppearanceSettings.current
    val permissionState =
        rememberPermissionState(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    val state by producePresenter {
        statusMediaPresenter(
            statusKey = statusKey,
            initialIndex = index,
            initialShowUi = !appearanceSettings.hideMediaPostInfoByDefault,
            context = context,
            accountType = accountType,
        )
    }
    val pagerState =
        rememberPagerState(
            initialPage = index,
            pageCount = {
                when (val medias = state.medias) {
                    is UiState.Error -> 1
                    is UiState.Loading -> 1
                    is UiState.Success -> medias.data.size
                }
            },
        )
    LaunchedEffect(pagerState.currentPage) {
        state.setCurrentPage(pagerState.currentPage)
    }
    val configuration = LocalConfiguration.current
    val activity = remember(context) { context.findActivity() }
    val currentMedia = state.medias.takeSuccess()?.getOrNull(state.currentPage)
    val isCurrentVideo = currentMedia is UiMedia.Video
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    var videoFullscreen by rememberSaveable { mutableStateOf(false) }
    val landscapeVideoMode = isCurrentVideo && (videoFullscreen || isLandscape)
    VideoFullscreenEffect(
        activity = activity,
        enabled = videoFullscreen,
    )
    LaunchedEffect(isCurrentVideo) {
        if (!isCurrentVideo) {
            videoFullscreen = false
        }
    }
    BackHandler(enabled = videoFullscreen) {
        videoFullscreen = false
    }
    FlareTheme(darkTheme = true) {
        val swiperState =
            rememberSwiperState(
                onDismiss = onDismiss,
            )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 1 - swiperState.progress))
                    .alpha(1 - swiperState.progress),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                Row {
                    Box(
                        modifier = Modifier.weight(1f),
                    ) {
                        Swiper(
                            state = swiperState,
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                userScrollEnabled = !state.lockPager,
                                key = {
                                    it
                                },
                            ) { index ->
                                AnimatedContent(
                                    state.medias,
                                    transitionSpec = {
                                        EnterTransition.None togetherWith ExitTransition.None
                                    },
                                ) {
                                    it
                                        .onSuccess { medias ->
                                            val media = medias[index]
                                            val imageUrl =
                                                when (media) {
                                                    is UiMedia.Audio -> media.previewUrl ?: media.url
                                                    is UiMedia.Gif -> media.url
                                                    is UiMedia.Image -> media.url
                                                    is UiMedia.Video -> media.thumbnailUrl
                                                }
                                            val previewUrl =
                                                when (media) {
                                                    is UiMedia.Audio -> media.previewUrl ?: media.url
                                                    is UiMedia.Gif -> media.previewUrl
                                                    is UiMedia.Image -> media.previewUrl
                                                    is UiMedia.Video -> media.thumbnailUrl
                                                }
                                            if (pagerState.currentPage != index || media is UiMedia.Image || media is UiMedia.Gif) {
                                                ImageItem(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxSize(),
                                                    url = imageUrl,
                                                    previewUrl = previewUrl,
                                                    description = media.description,
                                                    onClick = {
                                                        state.setShowUi(!state.showUi)
                                                    },
                                                    setLockPager = {
                                                        if (pagerState.currentPage == index) {
                                                            if (state.lockPager != it.locked) {
                                                                if (it.locked) {
                                                                    if (!isBigScreen) {
                                                                        state.setShowUi(false)
                                                                    }
                                                                    state.setLockPager(true)
                                                                } else {
                                                                    state.setLockPager(false)
                                                                    if (!isBigScreen && it.showUiOnUnlock) {
                                                                        state.setShowUi(true)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        hapticFeedback.performHapticFeedback(
                                                            HapticFeedbackType.LongPress,
                                                        )
                                                        state.setShowSheet(true)
                                                    },
                                                )
                                            } else if (media is UiMedia.Video) {
                                                val qualityOptions =
                                                    remember(media) {
                                                        media.qualityOptions()
                                                    }
                                                var selectedQuality by remember(media.url, qualityOptions) {
                                                    mutableStateOf(qualityOptions.first())
                                                }
                                                var videoPlaybackSpeed by rememberSaveable(media.url) {
                                                    mutableStateOf(1f)
                                                }
                                                var videoSpeedOverride by remember(media.url) {
                                                    mutableStateOf<Float?>(null)
                                                }
                                                var videoSpeedLocked by rememberSaveable(media.url) {
                                                    mutableStateOf(false)
                                                }
                                                var videoZoom by remember(media.url) {
                                                    mutableFloatStateOf(1f)
                                                }
                                                var videoOffsetX by remember(media.url) {
                                                    mutableFloatStateOf(0f)
                                                }
                                                var videoOffsetY by remember(media.url) {
                                                    mutableFloatStateOf(0f)
                                                }
                                                var videoViewportSize by remember(media.url) {
                                                    mutableStateOf(IntSize.Zero)
                                                }
                                                LaunchedEffect(videoZoom, landscapeVideoMode) {
                                                    state.setLockPager(landscapeVideoMode && videoZoom > 1.02f)
                                                }
                                                LaunchedEffect(videoZoom, videoViewportSize) {
                                                    videoOffsetX = videoOffsetX.coerceVideoOffset(videoZoom, videoViewportSize.width)
                                                    videoOffsetY = videoOffsetY.coerceVideoOffset(videoZoom, videoViewportSize.height)
                                                }
                                                LaunchedEffect(landscapeVideoMode) {
                                                    if (!landscapeVideoMode) {
                                                        videoSpeedOverride = null
                                                        videoSpeedLocked = false
                                                    }
                                                }
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxSize()
                                                            .clipToBounds(),
                                                ) {
                                                    VideoPlayer(
                                                        uri = selectedQuality.url,
                                                        previewUri = media.thumbnailUrl,
                                                        contentDescription = media.description,
                                                        customHeaders = media.customHeaders,
                                                        aspectRatio = media.aspectRatio,
                                                        autoPlay = true,
                                                        onClick = {
                                                            state.setShowUi(!state.showUi, force = true)
                                                        },
                                                        showControls = true,
                                                        keepScreenOn = true,
                                                        muted = false,
                                                        contentScale = ContentScale.Fit,
                                                        enableSpeedGesture = !landscapeVideoMode,
                                                        videoScale = videoZoom,
                                                        videoTranslationX = videoOffsetX,
                                                        videoTranslationY = videoOffsetY,
                                                        playbackSpeed = videoSpeedOverride ?: videoPlaybackSpeed,
                                                        modifier =
                                                            Modifier
                                                                .fillMaxSize()
                                                                .onSizeChanged {
                                                                    videoViewportSize = it
                                                                },
                                                        onLongClick = {
                                                            hapticFeedback.performHapticFeedback(
                                                                HapticFeedbackType.LongPress,
                                                            )
                                                            state.setShowSheet(true)
                                                        },
                                                    )
                                                    if (landscapeVideoMode) {
                                                        VideoGestureLayer(
                                                            onTap = {
                                                                state.setShowUi(!state.showUi, force = true)
                                                            },
                                                            onTransform = { pan, zoom ->
                                                                val nextZoom = (videoZoom * zoom).coerceIn(1f, 3f)
                                                                videoZoom = nextZoom
                                                                videoOffsetX =
                                                                    (videoOffsetX + pan.x)
                                                                        .coerceVideoOffset(nextZoom, videoViewportSize.width)
                                                                videoOffsetY =
                                                                    (videoOffsetY + pan.y)
                                                                        .coerceVideoOffset(nextZoom, videoViewportSize.height)
                                                            },
                                                            onLongPressSpeedChange = { pressed ->
                                                                videoSpeedOverride = if (pressed) 2f else null
                                                            },
                                                            onSpeedLockChange = { locked ->
                                                                videoSpeedLocked = locked
                                                                videoSpeedOverride = if (locked) 2f else null
                                                            },
                                                            isSpeedLocked = videoSpeedLocked,
                                                            speedLockDistancePx = with(density) { 56.dp.toPx() },
                                                            speedUnlockDistancePx = with(density) { 56.dp.toPx() },
                                                            basePlaybackSpeed = videoPlaybackSpeed,
                                                            onBasePlaybackSpeedChange = {
                                                                videoPlaybackSpeed = it
                                                            },
                                                            modifier = Modifier.fillMaxSize(),
                                                        )
                                                    }
                                                    if (state.showUi && landscapeVideoMode) {
                                                        Glassify(
                                                            modifier =
                                                                Modifier
                                                                    .align(Alignment.BottomCenter)
                                                                    .systemBarsPadding()
                                                                    .padding(horizontal = 24.dp, vertical = 10.dp)
                                                                    .fillMaxWidth(),
                                                            color = Color.Black.copy(alpha = 0.48f),
                                                            contentColor = Color.White,
                                                        ) {
                                                            PlayerControl(
                                                                surfaceBindingManager.player,
                                                                modifier =
                                                                    Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(horizontal = 8.dp),
                                                            )
                                                        }
                                                    }
                                                    if (state.showUi || !landscapeVideoMode) {
                                                        VideoFloatingControls(
                                                            isLandscape = landscapeVideoMode,
                                                            qualityOptions = qualityOptions,
                                                            selectedQuality = selectedQuality,
                                                            onQualitySelected = {
                                                                selectedQuality = it
                                                            },
                                                            playbackSpeed = videoPlaybackSpeed,
                                                            onPlaybackSpeedSelected = {
                                                                videoPlaybackSpeed = it
                                                            },
                                                            zoom = videoZoom,
                                                            onZoomChange = {
                                                                videoZoom = it
                                                                videoOffsetX = videoOffsetX.coerceVideoOffset(it, videoViewportSize.width)
                                                                videoOffsetY = videoOffsetY.coerceVideoOffset(it, videoViewportSize.height)
                                                            },
                                                            onEnterFullscreen = {
                                                                videoFullscreen = true
                                                            },
                                                            onExitFullscreen = {
                                                                videoFullscreen = false
                                                            },
                                                            modifier = Modifier.fillMaxSize(),
                                                        )
                                                    }
                                                }
                                            } else if (media is UiMedia.Audio) {
                                                VideoPlayer(
                                                    uri = media.url,
                                                    previewUri = null,
                                                    contentDescription = media.description,
                                                    autoPlay = false,
                                                    onClick = {
                                                        state.setShowUi(!state.showUi)
                                                    },
                                                    onLongClick = {
                                                        hapticFeedback.performHapticFeedback(
                                                            HapticFeedbackType.LongPress,
                                                        )
                                                        state.setShowSheet(true)
                                                    },
                                                )
                                            }
                                        }.onLoading {
                                            if (preview != null) {
                                                ImageItem(
                                                    url = preview,
                                                    previewUrl = preview,
                                                    description = null,
                                                    onClick = { /*TODO*/ },
                                                    setLockPager = {
                                                        if (state.lockPager != it.locked) {
                                                            if (it.locked) {
                                                                if (!isBigScreen) {
                                                                    state.setShowUi(false)
                                                                }
                                                                state.setLockPager(true)
                                                            } else {
                                                                state.setLockPager(false)
                                                                if (!isBigScreen && it.showUiOnUnlock) {
                                                                    state.setShowUi(true)
                                                                }
                                                            }
                                                        }
                                                    },
                                                    modifier =
                                                        Modifier
                                                            .fillMaxSize(),
                                                    onLongClick = { },
                                                )
                                            } else {
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .aspectRatio(1f)
                                                            .fillMaxSize()
                                                            .placeholder(true),
                                                )
                                            }
                                        }
                                }
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = state.showUi && !landscapeVideoMode,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter),
                            enter = slideInVertically { -it },
                            exit = slideOutVertically { -it },
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .systemBarsPadding()
                                        .padding(horizontal = 4.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Glassify(
                                    onClick = {
                                        onDismiss.invoke()
                                    },
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
//                            colors = IconButtonDefaults.filledTonalIconButtonColors(
//                                containerColor = Color.Transparent,
//                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
//                            )
                                ) {
                                    FAIcon(
                                        FontAwesomeIcons.Solid.Xmark,
                                        contentDescription = stringResource(id = R.string.navigate_back),
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                state.medias.onSuccess { medias ->
                                    val current = medias[state.currentPage]
                                    if (!current.description.isNullOrEmpty()) {
                                        Glassify(
                                            onClick = {
                                                toAltText.invoke(current)
                                            },
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.size(40.dp),
                                            shape = CircleShape,
                                        ) {
                                            FAIcon(
                                                FontAwesomeIcons.Solid.CircleInfo,
                                                contentDescription = stringResource(id = R.string.media_alt_text),
                                            )
                                        }
                                    }
                                    Glassify(
                                        onClick = {
                                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                                if (!permissionState.status.isGranted) {
                                                    permissionState.launchPermissionRequest()
                                                } else {
                                                    state.save(current)
                                                }
                                            } else {
                                                state.save(current)
                                            }
                                        },
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(40.dp),
                                        shape = CircleShape,
                                    ) {
                                        FAIcon(
                                            FontAwesomeIcons.Solid.Download,
                                            contentDescription = stringResource(id = R.string.media_menu_save),
                                        )
                                    }
                                    AnimatedVisibility(current is UiMedia.Image) {
                                        Glassify(
                                            onClick = {
                                                state.shareMedia(current)
                                            },
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.size(40.dp),
                                            shape = CircleShape,
                                        ) {
                                            FAIcon(
                                                FontAwesomeIcons.Solid.ShareNodes,
                                                contentDescription = stringResource(id = R.string.media_menu_share_image),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        state.status.onSuccess { status ->
                            val content = status as? UiTimelineV2.Post
                            if (content is UiTimelineV2.Post) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = state.showUi && !landscapeVideoMode,
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomCenter),
                                    enter = slideInVertically { it },
                                    exit = slideOutVertically { it },
                                ) {
                                    Glassify(
                                        modifier =
                                            Modifier
                                                .let {
                                                    if (isBigScreen) {
                                                        it
                                                            .safeContentPadding()
                                                            .clip(
                                                                MaterialTheme.shapes.medium,
                                                            )
                                                    } else {
                                                        it.fillMaxWidth()
                                                    }
                                                },
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        contentColor = MaterialTheme.colorScheme.onBackground,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            if (pagerState.pageCount > 1) {
                                                Row(
                                                    modifier =
                                                        Modifier.let {
                                                            if (isBigScreen) {
                                                                it
                                                            } else {
                                                                it.padding(top = 8.dp)
                                                            }
                                                        },
                                                    horizontalArrangement = Arrangement.Center,
                                                ) {
                                                    repeat(pagerState.pageCount) { iteration ->
                                                        val color =
                                                            if (pagerState.currentPage == iteration) {
                                                                MaterialTheme.colorScheme.primary
                                                            } else {
                                                                MaterialTheme.colorScheme.onBackground.copy(
                                                                    alpha = 0.5f,
                                                                )
                                                            }
                                                        Box(
                                                            modifier =
                                                                Modifier
                                                                    .padding(2.dp)
                                                                    .clip(CircleShape)
                                                                    .background(color)
                                                                    .size(8.dp),
                                                        )
                                                    }
                                                }
                                            }
                                            state.medias.onSuccess { medias ->
                                                val current =
                                                    remember(
                                                        medias,
                                                        state.currentPage,
                                                    ) {
                                                        medias[state.currentPage]
                                                    }
                                                if (current is UiMedia.Video) {
                                                    PlayerControl(
                                                        surfaceBindingManager.player,
                                                        modifier =
                                                            Modifier
                                                                .widthIn(max = 480.dp),
                                                    )
                                                }
                                            }
                                            if (!isBigScreen) {
                                                CompositionLocalProvider(
                                                    LocalComponentAppearance provides
                                                        LocalComponentAppearance.current.copy(
                                                            showMedia = false,
                                                            showLinkPreview = false,
                                                        ),
                                                    LocalUriHandler provides uriHandler,
                                                ) {
                                                    CommonStatusComponent(
                                                        item = content,
                                                        showMedia = false,
                                                        modifier =
                                                            Modifier
                                                                .padding(
                                                                    horizontal = screenHorizontalPadding,
                                                                    vertical = 8.dp,
                                                                ).windowInsetsPadding(
                                                                    WindowInsets.systemBars.only(
                                                                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                                                    ),
                                                                ).clickable {
                                                                    toStatusDetail()
                                                        },
                                                        maxLines = MediaOverlayPostMaxLines,
                                                        showExpandButton = false,
                                                        isDetail = true,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (isBigScreen && !landscapeVideoMode) {
                        AnimatedVisibility(state.showUi) {
                            Surface(
                                modifier =
                                    Modifier
                                        .width(320.dp)
                                        .fillMaxHeight()
                                        .verticalScroll(rememberScrollState()),
                                color = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ) {
                                state.status.onSuccess {
                                    val content = it as? UiTimelineV2.Post
                                    if (content is UiTimelineV2.Post) {
                                        CompositionLocalProvider(
                                            LocalComponentAppearance provides
                                                LocalComponentAppearance.current.copy(
                                                    showMedia = false,
                                                    showLinkPreview = false,
                                                ),
                                            LocalUriHandler provides uriHandler,
                                        ) {
                                            CommonStatusComponent(
                                                item = content,
                                                showMedia = false,
                                                modifier =
                                                    Modifier
                                                        .padding(
                                                            horizontal = screenHorizontalPadding,
                                                            vertical = 8.dp,
                                                        ).windowInsetsPadding(
                                                            WindowInsets.systemBars.only(
                                                                WindowInsetsSides.End + WindowInsetsSides.Vertical,
                                                            ),
                                                ),
                                                maxLines = MediaOverlayPostMaxLines,
                                                showExpandButton = false,
                                                isQuote = false,
                                                isDetail = true,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (state.showSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    state.setShowSheet(false)
                },
            ) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(id = R.string.media_menu_save))
                    },
                    leadingContent = {
                        FAIcon(
                            FontAwesomeIcons.Solid.Download,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    modifier =
                        Modifier
                            .clickable {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                    if (!permissionState.status.isGranted) {
                                        permissionState.launchPermissionRequest()
                                    } else {
                                        state.medias.onSuccess { medias ->
                                            state.save(medias[state.currentPage])
                                        }
                                    }
                                } else {
                                    state.medias.onSuccess { medias ->
                                        state.save(medias[state.currentPage])
                                    }
                                }
                                state.setShowSheet(false)
                            },
                    colors =
                        ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                )
                state.medias.onSuccess { medias ->
                    if (medias[state.currentPage] is UiMedia.Image) {
                        ListItem(
                            headlineContent = {
                                Text(stringResource(id = R.string.media_menu_share_image))
                            },
                            leadingContent = {
                                FAIcon(
                                    FontAwesomeIcons.Solid.ShareNodes,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            modifier =
                                Modifier
                                    .clickable {
                                        state.shareMedia(medias[state.currentPage])
                                        state.setShowSheet(false)
                                    },
                            colors =
                                ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                ),
                        )
                    }
                }

                state.medias.onSuccess { medias ->
                    val label = stringResource(R.string.media_menu_media_link)
                    ListItem(
                        headlineContent = {
                            Text(stringResource(id = R.string.media_menu_copy_link))
                        },
                        leadingContent = {
                            FAIcon(
                                FontAwesomeIcons.Solid.Copy,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        modifier =
                            Modifier
                                .clickable {
                                    scope.launch {
                                        val url = medias[state.currentPage].url
                                        clipboard.setClipEntry(
                                            ClipEntry(
                                                ClipData.newRawUri(
                                                    label,
                                                    url.toUri(),
                                                ),
                                            ),
                                        )
                                        state.setShowSheet(false)
                                    }
                                },
                        colors =
                            ListItemDefaults.colors(
                                containerColor = Color.Transparent,
                            ),
                    )
                }
            }
        }
    }
}

private data class VideoQualityOption(
    val label: String,
    val url: String,
)

private fun UiMedia.Video.qualityOptions(): List<VideoQualityOption> {
    val options =
        variants
            .filter { it.url.isNotBlank() }
            .distinctBy { it.url }
            .sortedWith(
                compareByDescending<UiMedia.VideoVariant> { it.height }
                    .thenByDescending { it.bitrate ?: 0 },
            ).mapIndexed { index, variant ->
                VideoQualityOption(
                    label = variant.qualityLabel(index),
                    url = variant.url,
                )
            }.ifEmpty {
                listOf(
                    VideoQualityOption(
                        label = "原始",
                        url = url,
                    ),
                )
            }
    return if (options.any { it.url == url }) {
        options
    } else {
        listOf(VideoQualityOption("原始", url)) + options
    }
}

private fun UiMedia.VideoVariant.qualityLabel(index: Int): String =
    when {
        height > 0f && (bitrate ?: 0) > 0 -> "${height.toInt()}P ${bitrate.orZeroKbps()}K"
        height > 0f -> "${height.toInt()}P"
        (bitrate ?: 0) > 0 -> "${bitrate.orZeroKbps()}K"
        index == 0 -> "原始"
        else -> "线路 ${index + 1}"
    }

private fun Int?.orZeroKbps(): Int = ((this ?: 0) / 1000).coerceAtLeast(1)

@Composable
private fun VideoGestureLayer(
    onTap: () -> Unit,
    onTransform: (pan: Offset, zoom: Float) -> Unit,
    onLongPressSpeedChange: (Boolean) -> Unit,
    onSpeedLockChange: (Boolean) -> Unit,
    isSpeedLocked: Boolean,
    speedLockDistancePx: Float,
    speedUnlockDistancePx: Float,
    basePlaybackSpeed: Float,
    onBasePlaybackSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewConfiguration = LocalViewConfiguration.current
    Box(
        modifier =
            modifier.pointerInput(
                onTap,
                onTransform,
                onLongPressSpeedChange,
                onSpeedLockChange,
                isSpeedLocked,
                speedLockDistancePx,
                speedUnlockDistancePx,
                basePlaybackSpeed,
                onBasePlaybackSpeedChange,
                viewConfiguration,
            ) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var isTap = true
                    var isReleased = false
                    var isTransforming = false
                    var isLongPressSpeed = false
                    var unlockLockedOnRelease = false
                    var dragY = 0f
                    var speedLocked = isSpeedLocked
                    val beforeLongPress =
                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressedChanges = event.changes.filter { it.pressed }
                                if (pressedChanges.isEmpty()) {
                                    isReleased = true
                                    return@withTimeoutOrNull
                                }
                                if (pressedChanges.size > 1) {
                                    isTap = false
                                    isTransforming = true
                                    return@withTimeoutOrNull
                                }
                                if (
                                    pressedChanges.any {
                                        (it.position - down.position).getDistance() > viewConfiguration.touchSlop
                                    }
                                ) {
                                    isTap = false
                                }
                            }
                        }
                    if (isReleased) {
                        if (isTap) {
                            onTap()
                        }
                        return@awaitEachGesture
                    }
                    if (!isTransforming && beforeLongPress == null) {
                        isTap = false
                        if (speedLocked) {
                            unlockLockedOnRelease = true
                        } else {
                            isLongPressSpeed = true
                            onLongPressSpeedChange(true)
                        }
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedChanges = event.changes.filter { it.pressed }
                        if (pressedChanges.isEmpty()) {
                            if (isTap) {
                                onTap()
                            }
                            break
                        }
                        if (pressedChanges.size > 1) {
                            if (isLongPressSpeed) {
                                isLongPressSpeed = false
                                onLongPressSpeedChange(false)
                            }
                            isTap = false
                            isTransforming = true
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f || pan != Offset.Zero) {
                                onTransform(pan, zoom)
                            }
                            event.changes.forEach { it.consume() }
                        } else if (
                            pressedChanges.any {
                                (it.position - down.position).getDistance() > viewConfiguration.touchSlop
                            }
                        ) {
                            isTap = false
                            val change = pressedChanges.firstOrNull { it.id == down.id }
                            if (change != null) {
                                dragY += change.position.y - change.previousPosition.y
                                if (isLongPressSpeed && dragY <= -speedLockDistancePx) {
                                    isLongPressSpeed = false
                                    speedLocked = true
                                    unlockLockedOnRelease = false
                                    onLongPressSpeedChange(false)
                                    onSpeedLockChange(true)
                                    onBasePlaybackSpeedChange(2f)
                                } else if ((isLongPressSpeed || speedLocked || unlockLockedOnRelease) &&
                                    dragY >= speedUnlockDistancePx
                                ) {
                                    isLongPressSpeed = false
                                    speedLocked = false
                                    unlockLockedOnRelease = false
                                    onLongPressSpeedChange(false)
                                    onSpeedLockChange(false)
                                    onBasePlaybackSpeedChange(1f)
                                }
                            }
                            pressedChanges.forEach { it.consume() }
                        }
                    }
                    if (isLongPressSpeed) {
                        onLongPressSpeedChange(false)
                    }
                    if (unlockLockedOnRelease && speedLocked) {
                        onSpeedLockChange(false)
                        onBasePlaybackSpeedChange(1f)
                    }
                }
            },
    )
}

private val videoPlaybackSpeeds = listOf(0.1f, 0.2f, 0.5f, 1f, 2f, 3f, 5f)

@Composable
private fun VideoFloatingControls(
    isLandscape: Boolean,
    qualityOptions: List<VideoQualityOption>,
    selectedQuality: VideoQualityOption,
    onQualitySelected: (VideoQualityOption) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedSelected: (Float) -> Unit,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onEnterFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isLandscape) {
        Row(
            modifier =
                modifier
                    .systemBarsPadding()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Top,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            VideoQualityMenu(
                qualityOptions = qualityOptions,
                selectedQuality = selectedQuality,
                onQualitySelected = onQualitySelected,
            )
            Spacer(modifier = Modifier.width(8.dp))
            VideoSpeedMenu(
                playbackSpeed = playbackSpeed,
                onPlaybackSpeedSelected = onPlaybackSpeedSelected,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Glassify(
                onClick = {
                    onZoomChange(nextVideoZoom(zoom))
                },
                color = Color.Black.copy(alpha = 0.48f),
                contentColor = Color.White,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = "缩放 ${zoom.oneDecimal()}x",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Glassify(
                onClick = onExitFullscreen,
                modifier = Modifier.size(40.dp),
                color = Color.Black.copy(alpha = 0.48f),
                contentColor = Color.White,
                shape = CircleShape,
            ) {
                FAIcon(
                    FontAwesomeIcons.Solid.Xmark,
                    contentDescription = "退出横屏",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    } else {
        Box(modifier = modifier) {
            Glassify(
                onClick = onEnterFullscreen,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(40.dp),
                color = Color.Black.copy(alpha = 0.48f),
                contentColor = Color.White,
                shape = CircleShape,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    FAIcon(
                        FontAwesomeIcons.Solid.Tv,
                        contentDescription = "横屏全屏",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoSpeedMenu(
    playbackSpeed: Float,
    onPlaybackSpeedSelected: (Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Glassify(
            onClick = {
                expanded = true
            },
            color = Color.Black.copy(alpha = 0.48f),
            contentColor = Color.White,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = "速度 ${playbackSpeed.speedLabel()}x",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            },
        ) {
            videoPlaybackSpeeds.forEach { speed ->
                DropdownMenuItem(
                    text = {
                        Text("${speed.speedLabel()}x")
                    },
                    onClick = {
                        onPlaybackSpeedSelected(speed)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun VideoQualityMenu(
    qualityOptions: List<VideoQualityOption>,
    selectedQuality: VideoQualityOption,
    onQualitySelected: (VideoQualityOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Glassify(
            onClick = {
                expanded = true
            },
            color = Color.Black.copy(alpha = 0.48f),
            contentColor = Color.White,
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                text = selectedQuality.label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            },
        ) {
            qualityOptions.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(option.label)
                    },
                    onClick = {
                        onQualitySelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun nextVideoZoom(current: Float): Float =
    when {
        current < 1.25f -> 1.5f
        current < 1.75f -> 2f
        current < 2.5f -> 3f
        else -> 1f
    }

private fun Float.coerceVideoOffset(
    zoom: Float,
    viewport: Int,
): Float {
    if (zoom <= 1.02f || viewport <= 0) {
        return 0f
    }
    val maxOffset = viewport * (zoom - 1f) / 2f
    return coerceIn(-maxOffset, maxOffset)
}

private fun Float.oneDecimal(): String =
    if (this % 1f == 0f) {
        toInt().toString()
    } else {
        "%.1f".format(this)
    }

private fun Float.speedLabel(): String =
    if (this % 1f == 0f) {
        toInt().toString()
    } else {
        toString().trimEnd('0').trimEnd('.')
    }

@Composable
private fun VideoFullscreenEffect(
    activity: Activity?,
    enabled: Boolean,
) {
    DisposableEffect(activity, enabled) {
        if (activity == null || !enabled) {
            onDispose { }
        } else {
            val window = activity.window
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                if (!activity.isChangingConfigurations) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerControl(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
) {
    val playPauseButtonState = rememberPlayPauseButtonState(player)
    var isLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        while (!isLoaded) {
            isLoaded = player.isPlaying
            awaitFrame()
        }
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!isLoaded) {
            LinearWavyProgressIndicator(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            )
        } else {
            var time by remember { mutableStateOf("") }
            var isSliderChanging by remember {
                mutableStateOf(false)
            }
            var sliderValue by remember {
                mutableFloatStateOf(0f)
            }
            if (!playPauseButtonState.showPlay && !isSliderChanging) {
                LaunchedEffect(Unit) {
                    while (true) {
                        sliderValue = player.currentPosition.toFloat() / player.duration.toFloat()
                        time =
                            buildString {
                                append(player.currentPosition.milliseconds.humanize())
                                append(" / ")
                                append(player.duration.milliseconds.humanize())
                            }
                        awaitFrame()
                    }
                }
            }
            IconButton(
                onClick = {
                    playPauseButtonState.onClick()
                },
                enabled = playPauseButtonState.isEnabled,
            ) {
                Icon(
                    if (playPauseButtonState.showPlay) {
                        FontAwesomeIcons.Solid.Play
                    } else {
                        FontAwesomeIcons.Solid.Pause
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = {
                    isSliderChanging = true
                    sliderValue = it
                    time =
                        buildString {
                            append((player.duration * it).toLong().milliseconds.humanize())
                            append(" / ")
                            append(player.duration.milliseconds.humanize())
                        }
                },
                onValueChangeFinished = {
                    player.seekTo((player.duration * sliderValue).toLong())
                    isSliderChanging = false
                },
                modifier = Modifier.weight(1f),
            )
            Text(time)
            Spacer(Modifier.width(screenHorizontalPadding))
        }
    }
}

@OptIn(ExperimentalTelephotoApi::class)
@Composable
private fun ImageItem(
    url: String,
    previewUrl: String,
    description: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    setLockPager: (ImagePagerLockState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val zoomableState =
        rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 10f))
    var keepUiHiddenOnNextUnlock by remember {
        mutableStateOf(false)
    }
    LaunchedEffect(zoomableState.zoomFraction) {
        val locked = (zoomableState.zoomFraction ?: 0f) > 0.01f
        setLockPager(
            ImagePagerLockState(
                locked = locked,
                showUiOnUnlock = !keepUiHiddenOnNextUnlock,
            ),
        )
        if (!locked) {
            keepUiHiddenOnNextUnlock = false
        }
    }
    BackHandler(
        enabled = (zoomableState.zoomFraction ?: 0f) > 0.01f,
    ) {
        scope.launch {
            keepUiHiddenOnNextUnlock = true
            zoomableState.resetZoom()
        }
    }
    var alignment by remember {
        mutableStateOf(Alignment.Center)
    }
    var contentScale by remember {
        mutableStateOf(ContentScale.Fit)
    }
    LaunchedEffect(zoomableState.coordinateSystem.contentBounds(false)) {
        // only set once to prevent jitter
        if (contentScale == ContentScale.Fit) {
            val aspectRatio =
                with(zoomableState.coordinateSystem) {
                    contentBounds(false).rectIn(CoordinateSpace.Viewport)
                }.let {
                    it.height / it.width
                }
            val targetAspectRatio = 19.5f / 9f
            if (aspectRatio > targetAspectRatio) {
                alignment = Alignment.TopCenter
                contentScale = ContentScale.FillWidth
            }
        }
    }

    val context = LocalContext.current
    val stablePreviewUrl = remember { mutableStateOf(previewUrl) }
    if (stablePreviewUrl.value.isBlank() && previewUrl.isNotBlank()) {
        stablePreviewUrl.value = previewUrl
    }
    var highResReady by remember(url) {
        mutableStateOf(url == stablePreviewUrl.value)
    }
    Box(
        modifier =
            modifier.then(
                if (!highResReady) {
                    Modifier.pointerInput(onClick, onLongClick) {
                    detectTapGestures(
                        onTap = { onClick.invoke() },
                        onLongPress = { onLongClick.invoke() },
                    )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(context)
                    .data(stablePreviewUrl.value)
                    .size(Size.ORIGINAL)
                    .build(),
            contentDescription = description,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            alignment = alignment,
        )
        if (!highResReady) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .size(Size.ORIGINAL)
                        .build(),
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .alpha(0f),
                onSuccess = {
                    highResReady = true
                },
            )
        }
        if (highResReady) {
            ZoomableAsyncImage(
                model =
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .placeholderMemoryCacheKey(stablePreviewUrl.value)
                        .size(Size.ORIGINAL)
                        .build(),
                contentDescription = description,
                state = rememberZoomableImageState(zoomableState),
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                alignment = alignment,
                onClick = {
                    if ((zoomableState.zoomFraction ?: 0f) <= 0.01f) {
                        onClick.invoke()
                    }
                },
                onLongClick = {
                    onLongClick.invoke()
                },
                onDoubleClick = DoubleClickToZoomListener.cycle(2f),
            )
        }
    }
}

private data class ImagePagerLockState(
    val locked: Boolean,
    val showUiOnUnlock: Boolean,
)

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun statusMediaPresenter(
    statusKey: MicroBlogKey,
    initialIndex: Int,
    initialShowUi: Boolean,
    context: Context,
    accountType: AccountType,
    scope: CoroutineScope = koinInject(),
    videoDownloadHelper: VideoDownloadHelper = koinInject(),
) = run {
    var showSheet by remember {
        mutableStateOf(false)
    }
    var showUi by remember {
        mutableStateOf(initialShowUi)
    }
    var lockPager by remember {
        mutableStateOf(false)
    }
    val cachedStatus =
        remember(statusKey, accountType) {
            StatusMediaRouteCache.get(statusKey, accountType)
        }
    var resolvedStatus by remember(statusKey, accountType) {
        mutableStateOf(cachedStatus)
    }
    var medias: UiState<ImmutableList<UiMedia>> by remember(statusKey) {
        mutableStateOf(
            cachedStatus
                ?.images
                ?.let { UiState.Success(it.toImmutableList()) }
                ?: UiState.Loading(),
        )
    }
    var currentPage by remember(statusKey, initialIndex) {
        mutableIntStateOf(initialIndex)
    }
    LaunchedEffect(cachedStatus) {
        if (cachedStatus?.platformType == PlatformType.Xiaohongshu) {
            runCatching {
                XhsStatusMediaLazyResolver.resolve(cachedStatus)
            }.getOrNull()
                ?.takeIf { it.images.isNotEmpty() }
                ?.let {
                    preloadMediaImages(context, it.images, currentPage)
                    StatusMediaRouteCache.put(it)
                    resolvedStatus = it
                    medias = UiState.Success(it.images.toImmutableList())
                }
        }
    }
    val statusState: UiState<UiTimelineV2> =
        resolvedStatus
            ?.let { UiState.Success(it) }
            ?: UiState.Loading()
    object {
        val status = statusState
        val medias = medias
        val showUi = showUi
        val currentPage = currentPage
        val lockPager = lockPager
        val showSheet = showSheet

        fun setShowSheet(value: Boolean) {
            showSheet = value
        }

        fun setShowUi(
            value: Boolean,
            force: Boolean = false,
        ) {
            if (force || !lockPager) {
                showUi = value
            }
        }

        fun setCurrentPage(value: Int) {
            currentPage = value
        }

        fun setLockPager(value: Boolean) {
            lockPager = value
        }

        fun save(data: UiMedia) {
            val status = statusState.takeSuccess() as? UiTimelineV2.Post
            if (status != null) {
                val statusKeyString = statusKey.toString()
                val userHandle = status.user?.handle?.canonical ?: "unknown"
                val fileName = data.getFileName(statusKeyString, userHandle)

                when (data) {
                    is UiMedia.Audio -> download(data.url, fileName)
                    is UiMedia.Gif -> download(data.url, fileName)
                    is UiMedia.Image -> save(data.url, fileName)
                    is UiMedia.Video -> download(data.url, fileName)
                }
            }
        }

        fun shareMedia(data: UiMedia) {
            when (data) {
                is UiMedia.Audio -> {
                    Unit
                }

                is UiMedia.Gif -> {
                    Unit
                }

                is UiMedia.Image -> {
                    scope.launch {
                        context.imageLoader.diskCache?.openSnapshot(data.url)?.use {
                            val originFile = it.data.toFile()
                            val status = statusState.takeSuccess() as? UiTimelineV2.Post
                            val statusKeyString = statusKey.toString()
                            val userHandle = status?.user?.handle?.canonical ?: "unknown"
                            val targetFile =
                                File(
                                    context.cacheDir,
                                    data.getFileName(statusKeyString, userHandle),
                                )
                            originFile.copyTo(targetFile, overwrite = true)
                            val uri =
                                FileProvider.getUriForFile(
                                    context,
                                    context.packageName + ".provider",
                                    targetFile,
                                )
                            val intent =
                                Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    setDataAndType(
                                        uri,
                                        "image/*",
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            context.startActivity(
                                Intent.createChooser(
                                    intent,
                                    context.getString(R.string.media_menu_share_image),
                                ),
                            )
                        } ?: run {
                            withContext(Dispatchers.Main) {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.media_is_downloading),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        }
                    }
                }

                is UiMedia.Video -> {
                    Unit
                }
            }
        }

        fun download(
            uri: String,
            fileName: String,
        ) {
            scope.launch {
                videoDownloadHelper.downloadVideo(
                    uri = uri,
                    fileName = fileName,
                    callback =
                        object : VideoDownloadHelper.DownloadCallback {
                            override fun onDownloadSuccess(downloadId: Long) {
                                scope.launch {
                                    withContext(Dispatchers.Main) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(R.string.media_save_success),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                }
                            }

                            override fun onDownloadFailed(downloadId: Long) {
                                scope.launch {
                                    withContext(Dispatchers.Main) {
                                        Toast
                                            .makeText(
                                                context,
                                                context.getString(R.string.media_save_fail),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                    }
                                }
                            }
                        },
                )
            }
        }

        fun save(
            uri: String,
            fileName: String,
        ) {
            scope.launch {
                context.imageLoader.diskCache?.openSnapshot(uri)?.use {
                    val byteArray = it.data.toFile().readBytes()
                    saveByteArrayToDownloads(context, byteArray, fileName)
                    withContext(Dispatchers.Main) {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.media_save_success),
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                } ?: withContext(Dispatchers.Main) {
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.media_is_downloading),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }
    }
}

private suspend fun preloadMediaImages(
    context: Context,
    medias: List<UiMedia>,
    firstIndex: Int,
) {
    val orderedImages =
        medias
            .mapIndexedNotNull { index, media ->
                val url =
                    when (media) {
                        is UiMedia.Image -> media.url
                        is UiMedia.Gif -> media.url
                        else -> null
                    }?.takeIf { it.isNotBlank() }
                url?.let { index to it }
            }.sortedBy { (index, _) ->
                if (index == firstIndex) 0 else 1
            }.map { (_, url) -> url }
            .distinct()
    withContext(Dispatchers.IO) {
        orderedImages.forEach { url ->
            runCatching {
                context.imageLoader.execute(
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .size(Size.ORIGINAL)
                        .build(),
                )
            }
        }
    }
}
