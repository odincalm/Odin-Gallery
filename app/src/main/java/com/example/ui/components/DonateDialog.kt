package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Payment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.OdinColors
import com.example.ui.theme.OdinTypography

@Composable
fun DonateDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val upiId = "bholanitin308@okicici"
    var copied by remember { mutableStateOf(false) }

    fun openUpiIntent() {
        val safeUpiId = upiId.trim()
        if (safeUpiId.isEmpty()) return
        try {
            val uri = Uri.parse("upi://pay?pa=$safeUpiId&pn=Nitin%20Kumar&cu=INR")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No UPI payment app is installed.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to open UPI payment app.", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyUpiId() {
        try {
            val safeUpiId = upiId.trim()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null) {
                val clip = ClipData.newPlainText("UPI ID", safeUpiId)
                clipboard.setPrimaryClip(clip)
                copied = true
                Toast.makeText(context, "UPI ID Copied: $safeUpiId", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to copy UPI ID", Toast.LENGTH_SHORT).show()
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

                // UPI QR Code Image Container
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable { openUpiIntent() },
                    contentAlignment = Alignment.Center
                ) {
                    val qrImageRequest = remember(context) {
                        ImageRequest.Builder(context)
                            .data("https://i.ibb.co/chhRLdxC/qr.jpg")
                            .crossfade(true)
                            .build()
                    }

                    AsyncImage(
                        model = qrImageRequest,
                        contentDescription = "UPI QR Code",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // UPI ID Label - Clickable
                Text(
                    text = "UPI ID: $upiId",
                    style = OdinTypography.headline,
                    fontWeight = FontWeight.Bold,
                    color = OdinColors.DarkAccentBlue,
                    modifier = Modifier.clickable { openUpiIntent() }
                )

                if (copied) {
                    Text(
                        text = "Copied to clipboard!",
                        style = OdinTypography.caption,
                        color = OdinColors.DarkAccentBlue,
                        modifier = Modifier.padding(top = 4.dp)
                    )
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
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(text = "Copy UPI ID", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(text = "Pay via UPI", color = Color.White)
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
