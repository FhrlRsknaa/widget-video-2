package com.example

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FrameExtractor
import com.example.data.VideoWidgetConfig
import com.example.data.WidgetAnimationManager
import com.example.data.WidgetDatabase
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var initialShowConfigId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle possible intent coming from tapping on Home Screen Widget
        if (intent?.action == "SHOW_VIDEO") {
            initialShowConfigId = intent.getIntExtra("config_id", -1)
        }

        setContent {
            MyApplicationTheme {
                MainScreen(initialShowConfigId = initialShowConfigId)
            }
        }

        // Keep animated widget loops hot while context compiles
        VideoWidgetProvider.registerScreenReceiver(this)
        WidgetAnimationManager.startAnimationLoop(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == "SHOW_VIDEO") {
            val configId = intent.getIntExtra("config_id", -1)
            if (configId != -1) {
                // Re-trigger show state on runtime
                setContent {
                    MyApplicationTheme {
                        MainScreen(initialShowConfigId = configId)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(initialShowConfigId: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { WidgetDatabase.getDatabase(context).videoWidgetDao() }

    // List of saved widgets state
    var savedWidgetConfigs by remember { mutableStateOf<List<VideoWidgetConfig>>(emptyList()) }
    var activeBindingsCount by remember { mutableStateOf(0) }

    // Screen navigation / overlay state
    var showSetupDialog by remember { mutableStateOf(false) }
    var selectedViewConfig by remember { mutableStateOf<VideoWidgetConfig?>(null) }

    // Loader configuration states
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoName by remember { mutableStateOf("") }
    var customTitle by remember { mutableStateOf("") }
    var sliderFrameCount by remember { mutableStateOf(15f) } // Default 15 frames for high compactness
    var sliderFps by remember { mutableStateOf(5f) } // Default 5 frames per second
    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableStateOf(0f) }

    // Pick media launcher callback
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            selectedVideoName = getFileName(context, uri)
            // Autofill clean title
            customTitle = selectedVideoName.substringBeforeLast(".")
                .replace("_", " ")
                .replace("-", " ")
                .take(15)
        }
    }

    // Load static data
    LaunchedEffect(Unit) {
        dao.getAllConfigs().collect { list ->
            savedWidgetConfigs = list
        }
    }

    LaunchedEffect(Unit) {
        val bindings = dao.getAllBindings()
        activeBindingsCount = bindings.size
    }

    // Immediate show if clicked from widget
    LaunchedEffect(initialShowConfigId) {
        if (initialShowConfigId != -1) {
            scope.launch {
                val config = withContext(Dispatchers.IO) {
                    dao.getConfigById(initialShowConfigId)
                }
                if (config != null) {
                    selectedViewConfig = config
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Reset fields & launch picker
                    selectedVideoUri = null
                    selectedVideoName = ""
                    customTitle = ""
                    sliderFrameCount = 20f
                    sliderFps = 5f
                    showSetupDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Buat Widget Baru", modifier = Modifier.size(28.dp))
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                // Sleek Custom App Header
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Widget Video",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Hidupkan home screen dengan klip favorit Anda",
                            fontSize = 13.sp,
                            color = Color(0xFF524341)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                // Trigger animation reboot
                                WidgetAnimationManager.startAnimationLoop(context)
                                Toast.makeText(context, "Render ulang widget berhasil!", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Widget",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Stats Dashboard Indicator Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatsMetricCard(
                        title = "Loop Terbuat",
                        value = "${savedWidgetConfigs.size}",
                        color = MaterialTheme.colorScheme.surface,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    StatsMetricCard(
                        title = "Widget Aktif",
                        value = "$activeBindingsCount",
                        color = MaterialTheme.colorScheme.surface,
                        accentColor = Color(0xFF47B275), // vibrant green is preserved for status
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Body Section
                if (savedWidgetConfigs.isEmpty()) {
                    // Modern Empty State Illustration
                    EmptyDashboardContent {
                        pickMediaLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                } else {
                    Text(
                        text = "Koleksi Animasi Loop Anda",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(savedWidgetConfigs) { config ->
                            VideoConfigDisplayCard(
                                config = config,
                                onDelete = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            // Delete files
                                            val folder = File(context.filesDir, config.localFolderName)
                                            folder.deleteRecursively()
                                            // Remove from database
                                            dao.deleteConfigById(config.id)
                                        }
                                        Toast.makeText(context, "Loop dihapus", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onClick = {
                                    selectedViewConfig = config
                                }
                            )
                        }
                    }
                }
            }

            // Create Loop Setup Sheet (Compose Popup overlay overlay)
            if (showSetupDialog) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xAA201A19))
                        .clickable(enabled = !isProcessing) { showSetupDialog = false },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f)
                            .clickable(enabled = false) {} // Prevent event leak
                            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp)
                        ) {
                            // Sheet Title Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Buat Video Widget Loop",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                IconButton(
                                    onClick = { if (!isProcessing) showSetupDialog = false },
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            if (selectedVideoUri == null) {
                                // Upload video callout
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                                        .clickable {
                                            pickMediaLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "Ketuk untuk Memilih Video",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Mendukung format video MP4, MOV, dll",
                                            fontSize = 11.sp,
                                            color = Color(0xFF524341)
                                        )
                                    }
                                }
                            } else {
                                // Video selected, show simple parameter wizard
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                                            .padding(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                selectedVideoName,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 14.sp
                                            )
                                            Text("Berkas Video Terpilih", fontSize = 11.sp, color = Color(0xFF524341))
                                        }
                                        IconButton(onClick = { selectedVideoUri = null }) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = "Ganti Video",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    // Title Input
                                    OutlinedTextField(
                                        value = customTitle,
                                        onValueChange = { customTitle = it },
                                        label = { Text("Nama Widget (Contoh: Kucing Imut)", color = Color(0xFF524341)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                                            unfocusedLabelColor = Color(0xFF524341),
                                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Frames amount slider
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Jumlah Bingkai/Frame", color = Color(0xFF524341), fontSize = 13.sp)
                                            Text("${sliderFrameCount.toInt()} Frame", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Slider(
                                            value = sliderFrameCount,
                                            onValueChange = { sliderFrameCount = it },
                                            valueRange = 10f..30f,
                                            steps = 3, // allows clean steps (10, 15, 20, 25, 30)
                                            colors = SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        )
                                        Text(
                                            "Semakin sedikit bingkai, semakin cepat pemrosesan & lebih hemat ram widget.",
                                            fontSize = 10.sp,
                                            color = Color(0xFF524341).copy(alpha = 0.7f)
                                        )
                                    }

                                    // FPS multiplier slider
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Kecepatan Putar (FPS)", color = Color(0xFF524341), fontSize = 13.sp)
                                            Text("${sliderFps.toInt()} FPS", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Slider(
                                            value = sliderFps,
                                            onValueChange = { sliderFps = it },
                                            valueRange = 1f..15f,
                                            steps = 13, // 1 to 15 step sequence
                                            colors = SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    if (isProcessing) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "Mengekstrak video menjadi frame foto...",
                                                color = MaterialTheme.colorScheme.onBackground,
                                                fontSize = 14.sp
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LinearProgressIndicator(
                                                progress = { processingProgress },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                            )
                                        }
                                    } else {
                                        // Process trigger button
                                        Button(
                                            onClick = {
                                                val uri = selectedVideoUri ?: return@Button
                                                isProcessing = true
                                                processingProgress = 0.1f
                                                
                                                scope.launch {
                                                    val subFolderName = "frames_${System.currentTimeMillis()}"
                                                    val framesCount = sliderFrameCount.toInt()
                                                    
                                                    // Run processing on background dispatcher
                                                    val extracted = withContext(Dispatchers.IO) {
                                                        FrameExtractor.extractFrames(
                                                            context = context,
                                                            videoUri = uri,
                                                            targetFolderName = subFolderName,
                                                            frameCount = framesCount
                                                        )
                                                    }

                                                    processingProgress = 0.8f
                                                    
                                                    if (extracted > 0) {
                                                        // Save to SQLite
                                                        val finalTitle = if (customTitle.isBlank()) "Video Loop" else customTitle
                                                        val config = VideoWidgetConfig(
                                                            title = finalTitle,
                                                            localFolderName = subFolderName,
                                                            frameCount = extracted,
                                                            fps = sliderFps.toInt(),
                                                            videoUriString = uri.toString()
                                                        )

                                                        withContext(Dispatchers.IO) {
                                                            dao.insertConfig(config)
                                                        }

                                                        delay(300)
                                                        processingProgress = 1.0f
                                                        isProcessing = false
                                                        showSetupDialog = false
                                                        Toast.makeText(context, "Loop berhasil dibuat!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        isProcessing = false
                                                        Toast.makeText(context, "Gagal memproses video. Coba video lain.", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(54.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                        ) {
                                            Icon(Icons.Default.Done, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Proses & Buat Loop", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // High resolution playback display dialog (When config clicked)
            selectedViewConfig?.let { config ->
                FullScreenPreviewOverlay(
                    config = config,
                    onClose = {
                        selectedViewConfig = null
                        // Refresh binding count
                        scope.launch {
                            val list = dao.getAllBindings()
                            activeBindingsCount = list.size
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun StatsMetricCard(
    title: String,
    value: String,
    color: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                title,
                color = Color(0xFF524341),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
        }
    }
}

@Composable
fun EmptyDashboardContent(
    onCreateLoop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Vibrant Theme Mock Home Screen Preview Section (as requested in HTML template)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFE7E0FF), Color(0xFFFFDADA))
                    )
                )
                .border(4.dp, MaterialTheme.colorScheme.background, RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Floating Video Widget Example (HTML Layout Match)
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(124.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF201A19))
                    .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp))
            ) {
                // Play Button Center
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                // Summer vibe metadata overlay exactly like tailwind code
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "04:20",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "Summer_Vibe.mp4",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Pasang Video Pertama Anda!",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Buat video favorit berdurasi pendek terus berputar halus sebagai ornamen estetik widget Anda.",
            fontSize = 13.sp,
            color = Color(0xFF524341),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onCreateLoop,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(48.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Pilih Video", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Quick visual guide box
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "Cara Memasang Widget:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "1. Tekan lama home screen HP Anda.\n" +
                        "2. Pilih opsi 'Widget' dan cari 'Widget Video'.\n" +
                        "3. Seret widget tersebut ke halaman home screen.\n" +
                        "4. Pilih video loop yang telah Anda buat di aplikasi ini.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VideoConfigDisplayCard(
    config: VideoWidgetConfig,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live loop visualization container right in dashboard list
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF2C2A4A)),
                contentAlignment = Alignment.Center
            ) {
                ComposeVideoLoopView(config = config, modifier = Modifier.fillMaxSize())
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Konfigurasi: ${config.frameCount} frame",
                    fontSize = 12.sp,
                    color = Color(0xFF524341)
                )
                Text(
                    text = "Kecepatan putar: ${config.fps} FPS",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Delete action button
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Hapus",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ComposeVideoLoopView(
    config: VideoWidgetConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentFrameIndex by remember { mutableStateOf(0) }
    var frameBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Live continuous slide loop rendering keyframes
    LaunchedEffect(config) {
        val folder = File(context.filesDir, config.localFolderName)
        while (true) {
            val frameFile = File(folder, "frame_$currentFrameIndex.jpg")
            if (frameFile.exists()) {
                val bitmap = withContext(Dispatchers.IO) {
                    try {
                        BitmapFactory.decodeFile(frameFile.absolutePath)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (bitmap != null) {
                    frameBitmap = bitmap
                }
            }
            
            val durationMs = 1000L / config.fps.coerceAtLeast(1)
            delay(durationMs)
            currentFrameIndex = (currentFrameIndex + 1) % config.frameCount
        }
    }

    frameBitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Live Preview",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } ?: Box(
        modifier = modifier.background(Color(0xFF2C2A4A)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Gray)
    }
}

@Composable
fun FullScreenPreviewOverlay(
    config: VideoWidgetConfig,
    onClose: () -> Unit
) {
    var isMuted by remember { mutableStateOf(true) }
    val context = LocalContext.current
    var boundCount by remember { mutableStateOf(0) }

    LaunchedEffect(config) {
        val dao = WidgetDatabase.getDatabase(context).videoWidgetDao()
        val bindings = withContext(Dispatchers.IO) { dao.getAllBindings() }
        boundCount = bindings.count { it.configId == config.id }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE0201A19))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                .clickable(enabled = false) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = config.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Preview Video loop langsung",
                        fontSize = 11.sp,
                        color = Color(0xFF524341)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Immersive continuous video frame player
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                ComposeVideoLoopView(config = config, modifier = Modifier.fillMaxSize())
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Widget status details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Bingkai", color = Color(0xFF524341), fontSize = 11.sp)
                    Text("${config.frameCount} Frame", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Laju Frame", color = Color(0xFF524341), fontSize = 11.sp)
                    Text("${config.fps} FPS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Pemasangan", color = Color(0xFF524341), fontSize = 11.sp)
                    Text("$boundCount Widget", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "💡 Petunjuk: Ukuran widget ini dapat Anda atur secara bebas dan fleksibel saat diletakkan di home screen HP Anda dengan menekan lama widget lalu menyeret sudut-sudutnya.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Selesai & Tutup", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Simple Helper to retrieve original selected media file name
private fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "video_${System.currentTimeMillis()}.mp4"
}
