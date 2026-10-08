package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Payment
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OdinColors
import com.example.ui.theme.OdinTypography
import com.example.util.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_UPI_ID = "bholanitin308@okicici"
private const val DEFAULT_PAYEE_NAME = "Nitin Kumar"

@Composable
fun DonateScreen(
    onBack: () -> Unit,
    upiId: String = DEFAULT_UPI_ID,
    payeeName: String = DEFAULT_PAYEE_NAME
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val safeUpiId = remember(upiId) { upiId.trim() }
    val safePayeeName = remember(payeeName) { payeeName.trim().ifEmpty { DEFAULT_PAYEE_NAME } }

    val upiUriString = remember(safeUpiId, safePayeeName) {
        if (safeUpiId.isNotBlank()) {
            try {
                Uri.Builder()
                    .scheme("upi")
                    .authority("pay")
                    .appendQueryParameter("pa", safeUpiId)
                    .appendQueryParameter("pn", safePayeeName)
                    .appendQueryParameter("cu", "INR")
                    .build()
                    .toString()
            } catch (e: Throwable) {
                "upi://pay?pa=$safeUpiId&pn=Nitin%20Kumar&cu=INR"
            }
        } else {
            ""
        }
    }

    // Instant cached lookup or background generation
    var qrBitmap by remember(upiUriString) {
        mutableStateOf(QrCodeGenerator.getCached(upiUriString))
    }
    var isGeneratingQr by remember(upiUriString) {
        mutableStateOf(qrBitmap == null && upiUriString.isNotBlank())
    }

    LaunchedEffect(upiUriString) {
        if (upiUriString.isNotBlank()) {
            val cached = QrCodeGenerator.getCached(upiUriString)
            if (cached != null) {
                qrBitmap = cached
                isGeneratingQr = false
            } else {
                isGeneratingQr = true
                val generated = withContext(Dispatchers.Default) {
                    try {
                        QrCodeGenerator.generateQrBitmap(upiUriString, 512)
                    } catch (e: Throwable) {
                        null
                    }
                }
                qrBitmap = generated
                isGeneratingQr = false
            }
        } else {
            isGeneratingQr = false
            qrBitmap = null
        }
    }

    BackHandler {
        onBack()
    }

    fun openUpiIntent() {
        if (safeUpiId.isBlank()) {
            Toast.makeText(context, "UPI ID is not configured.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = Uri.parse(upiUriString)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No UPI payment app found on this device.", Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            Toast.makeText(context, "Unable to launch UPI app: ${e.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyUpiId() {
        if (safeUpiId.isBlank()) {
            Toast.makeText(context, "UPI ID is not configured.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText("UPI ID", safeUpiId)
                clipboard.setPrimaryClip(clip)
                copied = true
                Toast.makeText(context, "UPI ID Copied: $safeUpiId", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Clipboard service unavailable.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Throwable) {
            Toast.makeText(context, "Failed to copy UPI ID.", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("donate_screen_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Back button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .testTag("donate_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Support Development",
                    style = OdinTypography.title,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Description Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(18.dp)
            ) {
                Text(
                    text = "If you love Photo and want to support independent privacy-focused offline development, contributions are deeply appreciated!",
                    style = OdinTypography.subheadline,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // QR Code Container
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .clickable { openUpiIntent() }
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val currentBitmap = qrBitmap
                val imageBitmap = remember(currentBitmap) {
                    try {
                        currentBitmap?.takeUnless { it.isRecycled }?.asImageBitmap()
                    } catch (e: Throwable) {
                        null
                    }
                }

                when {
                    isGeneratingQr -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = OdinColors.DarkAccentBlue,
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Generating QR Code...",
                                style = OdinTypography.caption,
                                color = Color.Gray
                            )
                        }
                    }
                    imageBitmap != null -> {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "UPI QR Code",
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("donate_qr_image")
                        )
                    }
                    else -> {
                        FallbackQrView(onTap = { openUpiIntent() })
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // UPI ID Section
            if (safeUpiId.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .testTag("donate_upi_card")
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { openUpiIntent() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "UPI ID: $safeUpiId",
                        style = OdinTypography.headline,
                        fontWeight = FontWeight.Bold,
                        color = OdinColors.DarkAccentBlue,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("donate_upi_text")
                    )
                }

                if (copied) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Color(0xFF34C759),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Copied to clipboard!",
                            style = OdinTypography.caption,
                            color = Color(0xFF34C759),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "UPI ID is currently unavailable",
                        style = OdinTypography.caption,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Copy UPI ID Button
                TextButton(
                    onClick = { copyUpiId() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .testTag("donate_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Copy UPI ID",
                        style = OdinTypography.subheadline,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Pay via UPI app
                TextButton(
                    onClick = { openUpiIntent() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(OdinColors.DarkAccentBlue, RoundedCornerShape(12.dp))
                        .testTag("donate_pay_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Payment,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pay via UPI",
                        style = OdinTypography.subheadline,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun FallbackQrView(onTap: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onTap() }
            .testTag("donate_qr_fallback"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.QrCode,
            contentDescription = null,
            tint = OdinColors.DarkAccentBlue,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap to pay with UPI",
            style = OdinTypography.subheadline,
            fontWeight = FontWeight.Medium,
            color = OdinColors.DarkAccentBlue,
            textAlign = TextAlign.Center
        )
    }
}
