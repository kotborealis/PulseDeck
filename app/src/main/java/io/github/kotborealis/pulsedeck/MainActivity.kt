package io.github.kotborealis.pulsedeck

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media.session.MediaButtonReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var controller: MediaControllerCompat? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ControllerScreen() }
    }

    private fun activeController(): MediaControllerCompat? = try {
        val manager = getSystemService(MediaSessionManager::class.java)
        val sessions = manager.getActiveSessions(ComponentName(this, MediaNotificationListener::class.java))
        sessions.firstOrNull()?.let { MediaControllerCompat(this, android.support.v4.media.session.MediaSessionCompat.Token.fromToken(it.sessionToken)) }
    } catch (_: Throwable) { null }

    @Composable
    private fun ControllerScreen() {
        var current by remember { mutableStateOf<MediaControllerCompat?>(null) }
        var title by remember { mutableStateOf("Нет активного трека") }
        var artist by remember { mutableStateOf("Запусти музыку и вернись сюда") }
        var cover by remember { mutableStateOf<Bitmap?>(null) }
        var playing by remember { mutableStateOf(false) }
        var position by remember { mutableLongStateOf(0L) }
        var duration by remember { mutableLongStateOf(0L) }


        fun sync() {
            current = activeController(); controller = current
            current?.let { c ->
                title = c.metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: "Без названия"
                artist = c.metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: c.packageName
                cover = c.metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                    ?: c.metadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ART)
                playing = c.playbackState?.state == PlaybackStateCompat.STATE_PLAYING
                position = c.playbackState?.position?.coerceAtLeast(0L) ?: 0L
                duration = c.metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
            }
        }
        LaunchedEffect(Unit) { sync() }
        LaunchedEffect(current) {
            while (true) {
                val discovered = activeController()
                if (discovered != null && current?.sessionToken != discovered.sessionToken) {
                    current = discovered
                    controller = discovered
                }
                current?.let { c ->
                    try {
                        title = c.metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE) ?: title
                        artist = c.metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) ?: artist
                        val state = c.playbackState
                        playing = state?.state == PlaybackStateCompat.STATE_PLAYING
                        position = state?.position?.coerceAtLeast(0L) ?: position
                        duration = c.metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: duration
                    } catch (_: Throwable) { }
                }
                delay(500)
            }
        }

        val base = Color(0xFF101114)
        Box(Modifier.fillMaxSize().background(base)) {
            cover?.let { bitmap ->
                Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().blur(34.dp), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f)))
            }
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Spacer(Modifier.height(24.dp))
                Column(Modifier.fillMaxWidth()) {
                    Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1200, repeatDelayMillis = 1400))
                    Spacer(Modifier.height(4.dp))
                    Text(artist, color = Color.White.copy(.66f), fontSize = 15.sp, maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(.24f))) {
                        val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                        Box(Modifier.fillMaxWidth(progress).height(5.dp).clip(RoundedCornerShape(5.dp)).background(Color.White))
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        LargeButton(Icons.Default.SkipPrevious) { safely { current?.transportControls?.skipToPrevious() } }
                        Box(Modifier.size(76.dp).clip(CircleShape).background(Color.White).clickable { safely { if (playing) current?.transportControls?.pause() else current?.transportControls?.play() } }, contentAlignment = Alignment.Center) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color(0xFF151619), modifier = Modifier.size(42.dp))
                        }
                        LargeButton(Icons.Default.SkipNext) { safely { current?.transportControls?.skipToNext() } }
                    }
                }
            }
        }
    }

    @Composable private fun SmallButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = Color.White, action: () -> Unit) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = action), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(29.dp)) }
    }

    private fun safely(action: () -> Unit) {
        try { action() } catch (_: Throwable) { }
    }

    @Composable private fun LargeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = Color.White, action: () -> Unit) {
        Box(Modifier.size(70.dp).clip(CircleShape).clickable(onClick = action), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(42.dp)) }
    }
}
