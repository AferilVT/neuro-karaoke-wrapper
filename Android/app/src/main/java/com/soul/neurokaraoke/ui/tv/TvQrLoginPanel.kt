package com.soul.neurokaraoke.ui.tv

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soul.neurokaraoke.viewmodel.AuthViewModel
import com.soul.neurokaraoke.viewmodel.QrPhase

/**
 * QR device-login panel for TV. Shows a server-issued QR the user scans + approves on
 * their phone; polls until approved, then the shared auth state flips to signed-in.
 * Styled to match [com.soul.neurokaraoke.ui.components.LoginCard] so it sits beside it.
 */
@Composable
fun TvQrLoginPanel(
    authViewModel: AuthViewModel,
    modifier: Modifier = Modifier,
) {
    val qr by authViewModel.qrLogin.collectAsStateWithLifecycle()

    // Run the QR loop only while this panel is on-screen.
    DisposableEffect(Unit) {
        authViewModel.startQrLogin()
        onDispose { authViewModel.stopQrLogin() }
    }

    val qrBitmap: ImageBitmap? = remember(qr.imageDataUrl) { decodeQrDataUrl(qr.imageDataUrl) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(horizontal = 32.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Sign in with your phone",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        // QR canvas — kept on a white plate so the code stays scannable in any theme.
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            when {
                qr.phase == QrPhase.Error -> Text(
                    qr.message ?: "Couldn't load QR code",
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
                qrBitmap != null -> Image(
                    bitmap = qrBitmap,
                    contentDescription = "Sign-in QR code",
                    modifier = Modifier.size(216.dp)
                )
                else -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        Text(
            "Scan this code with your phone and approve the sign-in.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Status line.
        when (qr.phase) {
            QrPhase.Waiting -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Waiting for approval…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            QrPhase.Approved -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Approved! Signing you in…", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            QrPhase.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("Generating code…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            QrPhase.Error -> Spacer(Modifier.height(1.dp)) // message already shown on the plate
        }
    }
}

/** Decode a `data:image/png;base64,…` URL into an [ImageBitmap], or null if it can't be read. */
private fun decodeQrDataUrl(dataUrl: String?): ImageBitmap? {
    if (dataUrl.isNullOrBlank()) return null
    val base64 = dataUrl.substringAfter("base64,", "").ifBlank { return null }
    return try {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
