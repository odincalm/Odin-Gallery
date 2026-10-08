package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Payment
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.example.ui.theme.OdinColors
import com.example.ui.theme.OdinTypography
import com.example.util.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DEFAULT_UPI_ID = "bholanitin308@okicici"
private const val DEFAULT_PAYEE_NAME = "Nitin Kumar"

@Composable
fun DonateDialog(
    onDismiss: () -> Unit,
    upiId: String = DEFAULT_UPI_ID,
    payeeName: String = DEFAULT_PAYEE_NAME
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

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
            Toast.makeText(context, "No UPI payment app found on device.", Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            Toast.makeText(context, "Unable to open UPI payment app.", Toast.LENGTH_SHORT).show()
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
            }
        } catch (e: Throwable) {
            Toast.makeText(context, "Failed to copy UPI ID.", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Support Development",
                style = OdinTypography.headline,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "If you love Photo and want to support independent privacy-focused offline development, contributions are deeply appreciated!",
                    style = OdinTypography.subheadline,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // QR Code Image Container
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .clickable { openUpiIntent() }
                        .padding(12.dp),
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
                            CircularProgressIndicator(
                                color = OdinColors.DarkAccentBlue,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                        }
                        imageBitmap != null -> {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "UPI QR Code",
                                modifier = Modifier
                                    .size(166.dp)
                                    .testTag("dialog_qr_image")
                            )
                        }
                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.QrCode,
                                    contentDescription = null,
                                    tint = OdinColors.DarkAccentBlue,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap to pay with UPI",
                                    style = OdinTypography.caption,
                                    color = OdinColors.DarkAccentBlue
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // UPI ID Label - Clickable
                if (safeUpiId.isNotBlank()) {
                    Text(
                        text = "UPI ID: $safeUpiId",
                        style = OdinTypography.headline,
                        fontWeight = FontWeight.Bold,
                        color = OdinColors.DarkAccentBlue,
                        modifier = Modifier
                            .clickable { openUpiIntent() }
                            .testTag("dialog_upi_text")
                    )

                    if (copied) {
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Copied to clipboard!",
                                style = OdinTypography.caption,
                                color = Color(0xFF34C759)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "UPI ID is currently unavailable",
                            style = OdinTypography.caption,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Copy UPI ID Button
                    TextButton(
                        onClick = { copyUpiId() },
                        modifier = Modifier
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Copy UPI ID",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = OdinTypography.caption,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Pay via UPI app
                    TextButton(
                        onClick = { openUpiIntent() },
                        modifier = Modifier
                            .weight(1f)
                            .background(OdinColors.DarkAccentBlue, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Payment,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Pay via UPI",
                            color = Color.White,
                            style = OdinTypography.caption,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Close", style = OdinTypography.headline, color = OdinColors.DarkAccentBlue)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp)
    )
}
