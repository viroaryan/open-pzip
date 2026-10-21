package app.pzip.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.pzip.BuildConfig
import app.pzip.ui.Nav
import app.pzip.ui.components.Gap
import app.pzip.ui.components.PCard
import app.pzip.ui.components.SectionHeader
import app.pzip.ui.components.SettingRow
import app.pzip.ui.components.TopBar
import app.pzip.ui.theme.P

@Composable
fun WhatsNewScreen() {
    val links = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize().background(P.c.bg), contentPadding = PaddingValues(bottom = 48.dp)) {
        item { TopBar("What's new", onBack = { Nav.back() }) }
        item {
            PCard(Modifier.fillMaxWidth().padding(20.dp)) {
                Column {
                    Text("VERSION ${BuildConfig.VERSION_NAME}", color = P.c.accent, style = MaterialTheme.typography.labelLarge)
                    Gap(10)
                    Text("Everything in one place.", color = P.c.text, style = MaterialTheme.typography.headlineMedium)
                    Gap(8)
                    Text("The first pzip release brings your everyday files, media and cleanup tools together.", color = P.c.textMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { SectionHeader("Highlights") }
        item {
            SettingRow("File manager", "Browse, search, sort and organize your files", Icons.Rounded.Folder)
            SettingRow("Archive tools", "Create and extract ZIP, 7z and other formats", Icons.Rounded.Code)
            SettingRow("Media player", "Video, audio, subtitles and picture-in-picture", Icons.Rounded.Movie)
            SettingRow("Storage cleanup", "Review duplicates, large files and empty folders", Icons.Rounded.Storage)
        }
        item { SectionHeader("Latest release") }
        item {
            SettingRow("Release notes & downloads", "Open official GitHub releases", Icons.Rounded.Download, onClick = {
                links.openUri("https://github.com/viroaryan/pzip-releases/releases")
            })
        }
    }
}
