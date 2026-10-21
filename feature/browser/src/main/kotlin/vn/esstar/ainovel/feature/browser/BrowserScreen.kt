package vn.esstar.ainovel.feature.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.esstar.ainovel.domain.model.UrlKind

@Composable
internal fun BrowserRoute(
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenReader: (Long, Long) -> Unit,
    viewModel: BrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BrowserEvent.OpenBook -> onOpenBook(event.bookId)
                is BrowserEvent.OpenReader -> onOpenReader(event.bookId, event.chapterId)
                BrowserEvent.Unsupported -> snackbar.showSnackbar(context.getString(R.string.browser_unsupported))
                BrowserEvent.Failed -> snackbar.showSnackbar(context.getString(R.string.browser_open_failed))
            }
        }
    }

    BrowserScreen(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onUrlChanged = viewModel::onUrlChanged,
        onOpenCurrent = viewModel::openCurrent,
    )
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserScreen(
    state: BrowserUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onUrlChanged: (String) -> Unit,
    onOpenCurrent: () -> Unit,
) {
    val context = LocalContext.current
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    onUrlChanged(url)
                    canGoBack = view.canGoBack()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    onUrlChanged(url)
                    canGoBack = view.canGoBack()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress
                }
            }
            loadUrl(state.initialUrl)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    BackHandler(enabled = canGoBack) { webView.goBack() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            state.currentUrl.removePrefix("https://").removePrefix("http://"),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.browser_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { webView.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.browser_reload))
                        }
                    },
                )
                if (progress in 1..99) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        bottomBar = {
            BottomAppBar {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.sourceName?.let { stringResource(R.string.browser_supported, it) }
                                ?: stringResource(R.string.browser_not_supported),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = onOpenCurrent, enabled = state.sourceName != null && !state.isOpening) {
                        if (state.isOpening) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(
                                when (state.urlKind) {
                                    UrlKind.CHAPTER -> R.string.browser_read_chapter
                                    UrlKind.BOOK -> R.string.browser_open_book
                                    UrlKind.UNKNOWN -> R.string.browser_open_page
                                },
                            ),
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
