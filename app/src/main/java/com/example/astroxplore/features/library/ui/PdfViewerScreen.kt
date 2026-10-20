package com.example.astroxplore.features.library.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.astroxplore.core.ui.components.AstroLoadingSize
import com.example.astroxplore.core.ui.components.AstroM3CircularProgressIndicator
import com.example.astroxplore.features.library.ui.pdf.PdfPageRendererEngine
import com.example.astroxplore.features.library.ui.pdf.PdfThumbnailGridSheet
import com.example.astroxplore.features.library.ui.pdf.ZoomableBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    paperTitle: String,
    pdfFilePath: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(pdfFilePath) { File(pdfFilePath) }

    val engine = remember(file) { PdfPageRendererEngine(file) }
    DisposableEffect(engine) {
        onDispose { engine.close() }
    }

    var isLoading by remember { mutableStateOf(true) }
    var totalPages by remember { mutableIntStateOf(0) }
    var renderError by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()

    var scale by remember { mutableFloatStateOf(1f) }
    var isNightMode by remember { mutableStateOf(false) }
    var showThumbnailSheet by remember { mutableStateOf(false) }
    var isScrubberVisible by remember { mutableStateOf(true) }

    // High contrast night-mode color inversion matrix
    val nightModeColorFilter = remember(isNightMode) {
        if (isNightMode) {
            ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -1f,  0f,  0f, 0f, 255f,
                         0f, -1f,  0f, 0f, 255f,
                         0f,  0f, -1f, 0f, 255f,
                         0f,  0f,  0f, 1f,   0f
                    )
                )
            )
        } else null
    }

    LaunchedEffect(file, reloadTrigger) {
        isLoading = true
        renderError = null

        withContext(Dispatchers.IO) {
            var attempts = 0
            while (!file.exists() && attempts < 6) {
                delay(500)
                attempts++
            }

            if (!file.exists() || file.length() == 0L) {
                withContext(Dispatchers.Main) {
                    renderError = "The PDF file is still being downloaded or not found locally."
                    isLoading = false
                }
                return@withContext
            }

            val initialized = engine.init()
            withContext(Dispatchers.Main) {
                if (initialized && engine.pageCount > 0) {
                    totalPages = engine.pageCount
                    isLoading = false
                } else {
                    renderError = "Failed to parse PDF document. File may be corrupted."
                    isLoading = false
                }
            }
        }
    }

    val visibleItemIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    val currentPage = remember(visibleItemIndex) { (visibleItemIndex + 1).coerceAtLeast(1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = paperTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.DownloadDone,
                                        contentDescription = null,
                                        modifier = Modifier.size(10.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Offline Storage",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            if (totalPages > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Page $currentPage of $totalPages",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Dark Mode / Night Inversion Toggle
                    IconButton(
                        onClick = { isNightMode = !isNightMode },
                        modifier = Modifier.testTag("pdf_viewer_night_mode_button")
                    ) {
                        Icon(
                            imageVector = if (isNightMode) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = if (isNightMode) "Light Mode" else "Night Inversion Mode",
                            tint = if (isNightMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Thumbnail Grid Sheet Action
                    IconButton(
                        onClick = { showThumbnailSheet = true },
                        enabled = totalPages > 0,
                        modifier = Modifier.testTag("pdf_viewer_thumbnails_button")
                    ) {
                        Icon(Icons.Outlined.GridView, contentDescription = "Thumbnails Overview")
                    }

                    // Print PDF Document Action
                    IconButton(
                        onClick = { printPdfDocument(context, file, paperTitle) },
                        modifier = Modifier.testTag("pdf_viewer_print_button")
                    ) {
                        Icon(Icons.Outlined.Print, contentDescription = "Print PDF")
                    }

                    // Share PDF Document Action
                    IconButton(
                        onClick = {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Research PDF"))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.testTag("pdf_viewer_share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share PDF")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (isNightMode) Color(0xFF0F1117) else Color(0xFF1E1E1E))
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AstroM3CircularProgressIndicator(
                            size = AstroLoadingSize.LARGE,
                            label = "Opening PDF reader...",
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                renderError != null -> {
                    Card(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(0.9f)
                            .align(Alignment.Center),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Preparing Document",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = renderError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Go Back")
                                }
                                Button(
                                    onClick = { reloadTrigger++ },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
                else -> {
                    // Continuous Vertical Scroll inside Zoomable 2D Pan Box
                    ZoomableBox(
                        modifier = Modifier.fillMaxSize(),
                        scale = scale,
                        onScaleChanged = { scale = it }
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp, start = 12.dp, end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                count = totalPages,
                                key = { it }
                            ) { pageIndex ->
                                PdfPageView(
                                    pageIndex = pageIndex,
                                    engine = engine,
                                    colorFilter = nightModeColorFilter
                                )
                            }
                        }
                    }

                    // Sticky Floating Current Page Badge (Top End)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        tonalElevation = 6.dp,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Page $currentPage / $totalPages",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Interactive Bottom Scrubber & Quick Zoom Dock
                    AnimatedVisibility(
                        visible = isScrubberVisible && totalPages > 0,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            tonalElevation = 8.dp,
                            shadowElevation = 6.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Scrubber Slider with Quick Jump
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                val target = (currentPage - 2).coerceAtLeast(0)
                                                listState.animateScrollToItem(target)
                                            }
                                        },
                                        enabled = currentPage > 1,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Page")
                                    }

                                    var sliderValue by remember(currentPage) { mutableFloatStateOf(currentPage.toFloat()) }

                                    Slider(
                                        value = sliderValue,
                                        onValueChange = { sliderValue = it },
                                        onValueChangeFinished = {
                                            val target = (sliderValue.roundToInt() - 1).coerceIn(0, totalPages - 1)
                                            scope.launch {
                                                listState.animateScrollToItem(target)
                                            }
                                        },
                                        valueRange = 1f..totalPages.toFloat().coerceAtLeast(1f),
                                        steps = (totalPages - 2).coerceAtLeast(0),
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp)
                                            .testTag("pdf_viewer_page_slider")
                                    )

                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                val target = currentPage.coerceAtMost(totalPages - 1)
                                                listState.animateScrollToItem(target)
                                            }
                                        },
                                        enabled = currentPage < totalPages,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Page")
                                    }
                                }

                                // Zoom Controls & Reset
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${(scale * 100).roundToInt()}% Zoom",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { scale = (scale - 0.25f).coerceAtLeast(1f) },
                                            enabled = scale > 1f,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Outlined.Remove, contentDescription = "Zoom out", modifier = Modifier.size(16.dp))
                                        }

                                        TextButton(
                                            onClick = { scale = 1f },
                                            enabled = scale != 1f,
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Reset", style = MaterialTheme.typography.labelMedium)
                                        }

                                        IconButton(
                                            onClick = { scale = (scale + 0.25f).coerceAtMost(4f) },
                                            enabled = scale < 4f,
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Outlined.Add, contentDescription = "Zoom in", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showThumbnailSheet) {
        PdfThumbnailGridSheet(
            engine = engine,
            currentPage = currentPage,
            onPageSelected = { targetPage ->
                scope.launch {
                    listState.animateScrollToItem(targetPage)
                }
            },
            onDismiss = { showThumbnailSheet = false }
        )
    }
}

/**
 * Renders an individual PDF page on-demand using the engine's LRU cache.
 */
@Composable
private fun PdfPageView(
    pageIndex: Int,
    engine: PdfPageRendererEngine,
    colorFilter: ColorFilter?
) {
    val context = LocalContext.current
    val targetWidth = remember {
        (context.resources.displayMetrics.widthPixels * 1.5f).toInt()
    }

    val pageBitmap by produceState<Bitmap?>(initialValue = null, pageIndex, engine) {
        value = engine.renderPage(pageIndex, targetWidth)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pdf_page_card_$pageIndex"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        if (pageBitmap != null) {
            Image(
                bitmap = pageBitmap!!.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Page ${pageIndex + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.DarkGray
                    )
                }
            }
        }
    }
}

/**
 * Native Android Document Printing integration.
 * Enables direct printing of cached research PDFs via Android PrintManager.
 */
fun printPdfDocument(context: Context, file: File, title: String) {
    if (!file.exists()) return
    try {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val printAdapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                metadata: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onWriteCancelled()
                    return
                }
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination?.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage)
                }
            }
        }
        val jobName = "AstroXplore - $title"
        printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
