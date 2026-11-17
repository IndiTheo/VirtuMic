package com.indidevs.virtumic.ui.component

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indidevs.virtumic.ui.model.AppItem
import com.indidevs.virtumic.ui.theme.spacing

enum class AndroidProfileType {
    PERSONAL_MAIN,
    WORK,
    PRIVATE
}

fun determineProfileType(context: Context, targetUser: UserHandle): AndroidProfileType {
    val userManager = context.getSystemService(
        UserManager::class.java
    ) ?: return AndroidProfileType.PERSONAL_MAIN
    val launcherApps = context.getSystemService(
        LauncherApps::class.java
    ) ?: return AndroidProfileType.PERSONAL_MAIN
    val currentUser = Process.myUserHandle()

    if (targetUser == currentUser) {
        return if (userManager.isManagedProfile) {
            AndroidProfileType.WORK
        } else {
            AndroidProfileType.PERSONAL_MAIN
        }
    }

    if (Build.VERSION.SDK_INT >= 35) {
        try {
            val userInfo = launcherApps.getLauncherUserInfo(targetUser)
            if (userInfo != null) {
                return when (userInfo.userType) {
                    UserManager.USER_TYPE_PROFILE_MANAGED -> AndroidProfileType.WORK
                    "android.os.usertype.profile.PRIVATE" -> AndroidProfileType.PRIVATE
                    else -> AndroidProfileType.PERSONAL_MAIN
                }
            }
        } catch (e: SecurityException) {
        }
    }

    if (launcherApps.profiles.contains(targetUser)) {
        return AndroidProfileType.WORK
    }

    return AndroidProfileType.PERSONAL_MAIN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPicker(
    apps: List<AppItem>,
    isLoading: Boolean = false,
    onAppSelected: (AppItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.medium)
        ) {
            Text(
                text = "Select Application",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = MaterialTheme.spacing.medium)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = MaterialTheme.spacing.medium),
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            if (isLoading && apps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.spacing.large),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.spacing.large),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) {
                            "No matching apps found"
                        } else {
                            "No apps available"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = filteredApps,
                        key = { "${it.packageName}_${it.userHandle}" }
                    ) { app ->
                        AppListItem(
                            app = app
                        ) {
                            onAppSelected(app)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListItem(app: AppItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val profileType = remember(app.userHandle, context) {
        determineProfileType(context, app.userHandle)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                vertical = MaterialTheme.spacing.medium,
                horizontal = MaterialTheme.spacing.small
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageBitmap = remember(app) { app.icon.asImageBitmap() }
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (profileType != AndroidProfileType.PERSONAL_MAIN) {
                    Spacer(modifier = Modifier.width(MaterialTheme.spacing.small))
                    ProfileBadge(profileType = profileType)
                }
            }
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProfileBadge(profileType: AndroidProfileType) {
    val (badgeText, containerColor, contentColor) = when (profileType) {
        AndroidProfileType.WORK -> Triple(
            "WORK",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        AndroidProfileType.PRIVATE -> Triple(
            "PRIVATE",
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )
        else -> Triple(
            "",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
    }

    if (badgeText.isEmpty()) return

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = badgeText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = contentColor
        )
    }
}
