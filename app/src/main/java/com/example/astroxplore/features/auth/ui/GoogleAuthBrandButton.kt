package com.example.astroxplore.features.auth.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.astroxplore.R

@Composable
internal fun GoogleAuthBrandButton(
    isSignUp: Boolean,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val buttonBackground = if (isDarkTheme) {
        Color(0xFF131314)
    } else {
        Color.White
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(243.dp)
                .height(54.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (isSignUp) {
                        "Sign up with Google, coming soon"
                    } else {
                        "Sign in with Google, coming soon"
                    }
                    role = Role.Button
                    disabled()
                }
        ) {
            Image(
                painter = painterResource(
                    id = if (isDarkTheme) R.drawable.google_signin_dark else R.drawable.google_signin_light
                ),
                contentDescription = null,
                modifier = Modifier.matchParentSize()
            )

            if (isSignUp) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = 54.dp)
                        .width(189.dp)
                        .fillMaxHeight()
                        .background(
                            color = buttonBackground,
                            shape = RoundedCornerShape(topEnd = 27.dp, bottomEnd = 27.dp)
                        )
                )
                Text(
                    text = "Sign up with Google",
                    color = if (isDarkTheme) Color(0xFFE3E3E3) else Color(0xFF1F1F1F),
                    fontSize = 19.sp,
                    lineHeight = 27.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(189.dp)
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            width = 1.35.dp,
                            color = if (isDarkTheme) Color(0xFF8E918F) else Color(0xFF747775),
                            shape = RoundedCornerShape(27.dp)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Coming soon",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.68f)
        )
    }
}