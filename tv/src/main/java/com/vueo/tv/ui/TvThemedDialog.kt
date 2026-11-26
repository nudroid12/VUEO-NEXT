package com.vueo.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TvThemedDialog(
    title: String,
    initialFocus: FocusRequester,
    onDismissRequest: () -> Unit,
    dismissOnBackPress: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    TvModalDialog(
        onDismissRequest = onDismissRequest,
        initialFocus = initialFocus,
        dismissOnBackPress = dismissOnBackPress,
    ) {
        val shape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier.fillMaxSize()
                .background(TvDesign.Black.copy(alpha = .72f))
                .padding(horizontal = 32.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth()
                    .background(TvDesign.Surface, shape)
                    .border(1.dp, TvDesign.White.copy(alpha = .22f), shape)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(title, color = TvDesign.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                content()
            }
        }
    }
}

@Composable
internal fun tvDialogInputColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TvDesign.White,
    unfocusedTextColor = TvDesign.White,
    focusedBorderColor = TvDesign.White,
    unfocusedBorderColor = TvDesign.White.copy(alpha = .28f),
    focusedContainerColor = TvDesign.SurfaceRaised,
    unfocusedContainerColor = TvDesign.SurfaceRaised,
    disabledContainerColor = TvDesign.SurfaceRaised,
    errorContainerColor = TvDesign.SurfaceRaised,
    focusedLabelColor = TvDesign.White,
    unfocusedLabelColor = TvDesign.Muted,
    cursorColor = TvDesign.White,
)

@Composable
internal fun TvDialogButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.onFocusChanged { focused = it.isFocused }
            .background(if (focused) TvDesign.White else TvDesign.Surface, shape)
            .border(1.dp, TvDesign.White.copy(alpha = if (focused) 1f else .25f), shape),
        colors = ButtonDefaults.textButtonColors(
            contentColor = when {
                focused -> TvDesign.Black
                destructive -> MaterialTheme.colorScheme.error
                else -> TvDesign.White
            },
            disabledContentColor = TvDesign.Dim,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
}
