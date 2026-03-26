package com.soul.neurokaraoke.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.soul.neurokaraoke.ui.theme.NeonTheme

private const val TAG = "WebViewAuth"
private const val LOGIN_URL = "https://neurokaraoke.com/login-page"
private const val MAX_POLL_ATTEMPTS = 60

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewAuthScreen(
    onJwtObtained: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface

    var currentUrl by remember { mutableStateOf(LOGIN_URL) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var jwtFound by remember { mutableStateOf(false) }

    // Keep callback reference current across recompositions
    val currentOnJwtObtained by rememberUpdatedState(onJwtObtained)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
    ) {
        // Themed top bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceColor)
                .drawBehind {
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.15f),
                        topLeft = Offset(0f, size.height - 2.dp.toPx()),
                        size = Size(size.width, 4.dp.toPx()),
                        cornerRadius = CornerRadius(0f)
                    )
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // URL display
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(backgroundColor.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = primaryColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentUrl
                            .removePrefix("https://")
                            .removePrefix("www.")
                            .take(50),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(48.dp))
            }

            // Progress bar
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = primaryColor,
                    trackColor = surfaceColor,
                    strokeCap = StrokeCap.Round
                )
            }
        }

        // WebView
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        Log.d(TAG, "WebView factory: creating WebView, loading $LOGIN_URL")

                        // Dark background to avoid white flash while pages load
                        setBackgroundColor(android.graphics.Color.parseColor("#121318"))

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.userAgentString = settings.userAgentString + " NeuroKaraokeApp"
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                Log.d(TAG, "onPageStarted: $url")
                                url?.let { currentUrl = it }
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                Log.d(TAG, "onPageFinished: $url")
                                isLoading = false
                                url?.let { currentUrl = it }

                                // Start polling for JWT once back on neurokaraoke.com
                                if (url != null && "neurokaraoke.com" in url && !jwtFound) {
                                    Log.d(TAG, "On neurokaraoke.com, starting JWT polling")
                                    startJwtPolling(view, 0) { jwt ->
                                        jwtFound = true
                                        currentOnJwtObtained(jwt)
                                    }
                                }
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                Log.d(TAG, "shouldOverrideUrlLoading: $url")
                                // Block custom scheme redirects (e.g. neurokaraoke://auth)
                                // that would crash the WebView with an error page
                                if (!url.startsWith("http")) {
                                    Log.d(TAG, "Blocked non-http redirect: $url")
                                    return true
                                }
                                return false
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                val url = request?.url?.toString() ?: ""
                                val isMainFrame = request?.isForMainFrame == true
                                Log.e(TAG, "WebView error (mainFrame=$isMainFrame) on $url: ${error?.description}")
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: android.webkit.WebResourceResponse?
                            ) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                val url = request?.url?.toString() ?: ""
                                val isMainFrame = request?.isForMainFrame == true
                                Log.w(TAG, "HTTP error (mainFrame=$isMainFrame) ${errorResponse?.statusCode} on $url")
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                if (newProgress % 20 == 0 || newProgress == 100) {
                                    Log.d(TAG, "Progress: $newProgress% (url=${view?.url})")
                                }
                                loadProgress = newProgress
                            }
                        }

                        Log.d(TAG, "Calling loadUrl($LOGIN_URL)")
                        loadUrl(LOGIN_URL)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Loading spinner while page loads
            if (isLoading && loadProgress < 80) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = primaryColor,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading sign-in page...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "This may take up to a minute the first time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // "Signing you in" overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = jwtFound,
                enter = fadeIn(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backgroundColor.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = primaryColor,
                            modifier = Modifier.size(40.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Signing you in...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Poll localStorage for the authToken JWT.
 * Blazor WASM can take several seconds after page load to store the token,
 * so we retry every second for up to [MAX_POLL_ATTEMPTS] seconds.
 */
private fun startJwtPolling(
    webView: WebView?,
    attempt: Int,
    onFound: (String) -> Unit
) {
    if (webView == null || attempt > MAX_POLL_ATTEMPTS) return

    // Only run JS when we're actually on neurokaraoke.com — not on error pages or Discord
    val currentUrl = webView.url ?: ""
    if ("neurokaraoke.com" !in currentUrl) {
        Log.d(TAG, "Not on neurokaraoke.com (url=$currentUrl), retrying in 2s...")
        webView.postDelayed(
            { startJwtPolling(webView, attempt + 1, onFound) },
            2000
        )
        return
    }

    webView.evaluateJavascript(
        "(function() { try { return localStorage.getItem('authToken') || ''; } catch(e) { return ''; } })()"
    ) { result ->
        val jwt = result
            ?.trim()
            ?.removeSurrounding("\"")
            ?.replace("\\\"", "\"")
            ?: ""

        if (jwt.isNotBlank() && jwt.startsWith("eyJ")) {
            Log.d(TAG, "JWT captured on attempt $attempt")
            onFound(jwt)
        } else {
            webView.postDelayed(
                { startJwtPolling(webView, attempt + 1, onFound) },
                1000
            )
        }
    }
}
