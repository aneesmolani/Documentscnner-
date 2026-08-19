package com.example.documentscanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.documentscanner.camera.CaptureUtils
import com.example.documentscanner.capture.BookSplitter
import com.example.documentscanner.capture.IdCardComposer
import com.example.documentscanner.capture.ScanMode
import com.example.documentscanner.editor.CropCorners
import com.example.documentscanner.editor.InteractiveCrop
import com.example.documentscanner.export.DocumentExporter
import com.example.documentscanner.export.ExportQuality
import com.example.documentscanner.export.PdfExportOptions
import com.example.documentscanner.export.PdfPageSize
import com.example.documentscanner.export.PrintUtils
import com.example.documentscanner.export.ShareUtils
import com.example.documentscanner.session.ScanSession
import com.example.documentscanner.session.SessionStore
import com.example.documentscanner.util.AppSettings
import com.example.documentscanner.util.ExifUtils
import com.example.documentscanner.vision.*
import com.example.documentscanner.quality.ScanQualityAnalyzer
import com.example.documentscanner.quality.ScanQualityReport
import org.opencv.android.OpenCVLoader
import org.opencv.core.Point
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

@Composable
fun ScannerScreen(onClose: () -> Unit, initialSessionId: String? = null) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val session = remember { ScanSession() }

    var camera by remember { mutableStateOf<CameraCapture?>(null) }
    var detected by remember { mutableStateOf<DetectedDocument?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("Move the document inside the frame") }
    var busy by remember { mutableStateOf(false) }
    var autoCapture by remember { mutableStateOf(AppSettings.getAutoCaptureDefault(context)) }
    var stability by remember { mutableStateOf(0f) }
    var filterSettings by remember { mutableStateOf(FilterSettings()) }
    var showCrop by remember { mutableStateOf(false) }
    var showPages by remember { mutableStateOf(initialSessionId != null) }
    var fatalError by remember { mutableStateOf<String?>(null) }
    var pdfOptions by remember {
        mutableStateOf(PdfExportOptions(quality = AppSettings.getDefaultQuality(context)))
    }
    var showPdfOptions by remember { mutableStateOf(false) }
    var scanMode by remember { mutableStateOf(ScanMode.DOCUMENT) }
    var idCardFront by remember { mutableStateOf<Bitmap?>(null) }
    var showBookSplit by remember { mutableStateOf(false) }

    val executor = remember { Executors.newSingleThreadExecutor() }
    val detector = remember { DocumentDetector() }
    val tracker = remember { DetectionStability() }
    val autoLock = remember { AtomicBoolean(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            executor.execute {
                uris.forEach { uri ->
                    decodeUri(context.contentResolver, uri)
                        ?.let { ExifUtils.correctOrientation(context.contentResolver, uri, it) }
                        ?.let { session.add(it) }
                }
                ContextCompat.getMainExecutor(context).execute {
                    status = "${session.pages.size} page(s) in session"
                    showPages = true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!OpenCVLoader.initLocal()) {
            fatalError = "OpenCV failed to initialize on this device. Scanning cannot continue."
        }
    }

    LaunchedEffect(initialSessionId) {
        val id = initialSessionId ?: return@LaunchedEffect
        executor.execute {
            val result = SessionStore.loadSession(context, id)
            ContextCompat.getMainExecutor(context).execute {
                result.onSuccess { bitmaps ->
                    bitmaps.forEach { session.add(it) }
                    status = "Loaded ${bitmaps.size} page(s)"
                }.onFailure {
                    status = "Could not open saved scan: ${it.message ?: "unknown error"}"
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            preview?.let { if (!it.isRecycled) it.recycle() }
            idCardFront?.let { if (!it.isRecycled) it.recycle() }
            session.clear()
        }
    }

    fatalError?.let { message ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Can't start scanner") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onClose) { Text("Close") }
            }
        )
        return
    }

    fun acceptPreview() {
        val bmp = preview ?: return
        when (scanMode) {
            ScanMode.DOCUMENT -> {
                session.add(bmp)
                preview = null
                filterSettings = FilterSettings()
                status = "Page ${session.pages.size} added"
            }
            ScanMode.ID_CARD -> {
                val front = idCardFront
                if (front == null) {
                    idCardFront = bmp
                    preview = null
                    filterSettings = FilterSettings()
                    status = "Front captured — now scan the back of the ID"
                } else {
                    val composite = IdCardComposer.compose(front, bmp)
                    session.add(composite)
                    if (!front.isRecycled) front.recycle()
                    if (bmp !== composite && !bmp.isRecycled) bmp.recycle()
                    idCardFront = null
                    preview = null
                    filterSettings = FilterSettings()
                    status = "ID card added (front + back)"
                }
            }
            ScanMode.BOOK -> {
                // Preview is intentionally kept — BookSplitScreen consumes it next.
                showBookSplit = true
            }
        }
    }

    fun cancelIdCapture() {
        idCardFront?.let { if (!it.isRecycled) it.recycle() }
        idCardFront = null
        status = "ID scan cancelled"
    }

    fun captureNow() {
        if (busy || autoLock.getAndSet(true)) return
        busy = true
        val cam = camera
        if (cam == null) {
            busy = false
            autoLock.set(false)
            status = "Camera is not ready yet"
            return
        }
        cam.capture(
            onSaved = { file ->
                executor.execute {
                    val bitmap = decodeDownsampled(file, 4096)
                    val d = detected
                    val corrected = if (bitmap != null && d != null) {
                        InteractiveCrop.apply(bitmap, mapDetection(d, bitmap.width, bitmap.height))
                    } else bitmap

                    ContextCompat.getMainExecutor(context).execute {
                        preview = corrected
                        if (corrected != null) status = "Scan ready" else status = "Could not process photo"
                        if (corrected !== bitmap) bitmap?.recycle()
                        busy = false
                        autoLock.set(false)
                        tracker.reset()
                        stability = 0f
                    }
                }
            },
            onFailed = { message ->
                status = "Capture failed: $message"
                busy = false
                autoLock.set(false)
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        if (preview == null && !showPages) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { view ->
                        val c = CameraCapture(
                            ctx, view, lifecycleOwner, detector, executor,
                            onDetected = { d ->
                                detected = d
                                stability = if (d != null) tracker.progress() else 0f
                                val base = when {
                                    d == null -> "Move the document inside the frame"
                                    stability >= 0.99f -> if (autoCapture) "Capturing…" else "Document ready"
                                    d.confidence >= 0.55f -> "Hold steady"
                                    else -> "Move closer / improve lighting"
                                }
                                status = when (scanMode) {
                                    ScanMode.ID_CARD -> {
                                        val side = if (idCardFront == null) "FRONT" else "BACK"
                                        "[ID $side] $base"
                                    }
                                    ScanMode.BOOK -> "[Book spread] $base"
                                    ScanMode.DOCUMENT -> base
                                }
                                if (autoCapture && d != null && tracker.update(d)) {
                                    stability = 1f
                                    captureNow()
                                }
                            },
                            onError = { message -> status = message }
                        )
                        camera = c
                        c.start()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            detected?.let { DetectionOverlay(it, Modifier.fillMaxSize()) }

            Surface(
                Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .85f)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(status)
                    if (stability > 0f) {
                        LinearProgressIndicator(
                            progress = { stability },
                            Modifier.width(190.dp).padding(top = 5.dp)
                        )
                    }
                }
            }

            if (idCardFront != null) {
                Surface(
                    Modifier.align(Alignment.TopCenter).padding(top = 96.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .85f)
                ) {
                    TextButton(onClick = { cancelIdCapture() }) { Text("Cancel ID scan") }
                }
            } else {
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ScanMode.entries.forEach { mode ->
                        FilterChip(
                            selected = scanMode == mode,
                            onClick = { scanMode = mode },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onClose) { Text("Close") }
                OutlinedButton(onClick = {
                    galleryLauncher.launch("image/*")
                }) { Text("Gallery") }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(onClick = { captureNow() }) {
                        Text(if (busy) "…" else "●")
                    }
                    TextButton(onClick = { autoCapture = !autoCapture }) {
                        Text(if (autoCapture) "Auto ON" else "Auto OFF")
                    }
                }

                OutlinedButton(onClick = { showPages = true }) {
                    Text("Pages ${session.pages.size}")
                }
            }
        } else if (preview != null && showBookSplit) {
            BookSplitScreen(
                bitmap = preview!!,
                onCancel = { showBookSplit = false },
                onSkip = {
                    val bmp = preview ?: return@BookSplitScreen
                    session.add(bmp)
                    preview = null
                    showBookSplit = false
                    filterSettings = FilterSettings()
                    status = "Page ${session.pages.size} added (spread kept as one page)"
                },
                onSplit = { fraction ->
                    val bmp = preview ?: return@BookSplitScreen
                    val (left, right) = BookSplitter.split(bmp, fraction)
                    session.add(left)
                    session.add(right)
                    if (!bmp.isRecycled) bmp.recycle()
                    preview = null
                    showBookSplit = false
                    filterSettings = FilterSettings()
                    status = "2 pages added (book split)"
                }
            )
        } else if (preview != null && !showCrop && !showPages) {
            ScanEditScreen(
                bitmap = preview!!,
                settings = filterSettings,
                confirmLabel = if (scanMode == ScanMode.ID_CARD && idCardFront == null) "Use Front" else
                    if (scanMode == ScanMode.ID_CARD) "Use Back" else "Add Page",
                onSettings = { settings ->
                    filterSettings = settings
                    val current = preview ?: return@ScanEditScreen
                    executor.execute {
                        val processed = ImageFilters.apply(current, settings)
                        ContextCompat.getMainExecutor(context).execute {
                            if (processed !== current && !current.isRecycled) current.recycle()
                            preview = processed
                        }
                    }
                },
                onRotate = {
                    val current = preview ?: return@ScanEditScreen
                    val matrix = Matrix().apply { postRotate(90f) }
                    val rotated = Bitmap.createBitmap(
                        current, 0, 0, current.width, current.height, matrix, true
                    )
                    if (rotated !== current) current.recycle()
                    preview = rotated
                },
                onCrop = { showCrop = true },
                onRetake = {
                    preview?.let { if (!it.isRecycled) it.recycle() }
                    preview = null
                    filterSettings = FilterSettings()
                    tracker.reset()
                },
                onUse = { acceptPreview() }
            )
        } else if (preview != null && showCrop) {
            CropEditor(
                bitmap = preview!!,
                onCancel = { showCrop = false },
                onApply = { corners ->
                    val current = preview ?: return@CropEditor
                    executor.execute {
                        val cropped = InteractiveCrop.apply(current, corners)
                        ContextCompat.getMainExecutor(context).execute {
                            if (cropped != null) {
                                if (!current.isRecycled) current.recycle()
                                preview = cropped
                            }
                            showCrop = false
                        }
                    }
                }
            )
        } else if (showPages) {
            PageManager(
                session = session,
                pdfOptions = pdfOptions,
                onBack = { showPages = false },
                onAdd = { showPages = false },
                onReorder = { from, to -> session.move(from, to) },
                onSaveSession = {
                    executor.execute {
                        val result = SessionStore.saveSession(context, session, null)
                        ContextCompat.getMainExecutor(context).execute {
                            status = result.fold(
                                { "Session saved to My Scans" },
                                { "Save failed: ${it.message ?: "unknown error"}" }
                            )
                        }
                    }
                },
                onShowPdfOptions = { showPdfOptions = true },
                onExportPdf = {
                    executor.execute {
                        val result = DocumentExporter.exportPdf(
                            context,
                            session.pages.map { it.bitmap },
                            "scan_${System.currentTimeMillis()}",
                            pdfOptions
                        )
                        ContextCompat.getMainExecutor(context).execute {
                            result.onSuccess { uri ->
                                status = "PDF saved"
                                ShareUtils.shareUri(context, uri, "application/pdf", "Share PDF")
                            }.onFailure {
                                status = "PDF export failed: ${it.message ?: "unknown error"}"
                            }
                        }
                    }
                },
                onPrint = {
                    executor.execute {
                        val result = DocumentExporter.exportPdf(
                            context,
                            session.pages.map { it.bitmap },
                            "scan_${System.currentTimeMillis()}",
                            pdfOptions
                        )
                        ContextCompat.getMainExecutor(context).execute {
                            result.onSuccess { uri ->
                                status = "Opening print dialog"
                                PrintUtils.printPdf(context, uri, "Document Scan")
                            }.onFailure {
                                status = "Could not prepare PDF for printing: ${it.message ?: "unknown error"}"
                            }
                        }
                    }
                },
                onExportJpeg = {
                    executor.execute {
                        val results = session.pages.map { page ->
                            DocumentExporter.exportJpeg(
                                context, page.bitmap,
                                "scan_page_${System.currentTimeMillis()}_${page.id}",
                                pdfOptions.quality
                            )
                        }
                        ContextCompat.getMainExecutor(context).execute {
                            val failed = results.count { it.isFailure }
                            status = if (failed == 0) {
                                "${results.size} JPEG(s) saved"
                            } else {
                                "$failed of ${results.size} JPEG export(s) failed"
                            }
                            results.firstOrNull { it.isSuccess }?.getOrNull()?.let { uri ->
                                ShareUtils.shareUri(context, uri, "image/jpeg", "Share scan")
                            }
                        }
                    }
                }
            )

            if (showPdfOptions) {
                PdfOptionsDialog(
                    options = pdfOptions,
                    onDismiss = { showPdfOptions = false },
                    onApply = { pdfOptions = it; showPdfOptions = false }
                )
            }
        }
    }
}

private fun mapDetection(d: DetectedDocument, width: Int, height: Int): CropCorners {
    val sx = width.toDouble() / d.imageWidth.toDouble()
    val sy = height.toDouble() / d.imageHeight.toDouble()
    fun p(p: Point) = Point(p.x * sx, p.y * sy)
    return CropCorners(
        p(d.quad.topLeft), p(d.quad.topRight),
        p(d.quad.bottomRight), p(d.quad.bottomLeft)
    )
}

private fun decodeDownsampled(file: File, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension) sample *= 2
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    )
}

private fun decodeUri(resolver: android.content.ContentResolver, uri: Uri): Bitmap? {
    return resolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input)
    }
}

private class CameraCapture(
    private val context: android.content.Context,
    private val previewView: PreviewView,
    private val lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    private val detector: DocumentDetector,
    private val executor: Executor,
    private val onDetected: (DetectedDocument?) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    private var imageCapture: ImageCapture? = null

    fun start() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(95)
                    .build()

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setOutputImageRotationEnabled(true)
                    .build()

                analysis.setAnalyzer(executor) { image ->
                    try {
                        val bitmap = image.toBitmap()
                        val d = try {
                            detector.detect(bitmap)
                        } catch (_: Throwable) {
                            null
                        }
                        ContextCompat.getMainExecutor(context).execute { onDetected(d) }
                        bitmap.recycle()
                    } catch (_: Throwable) {
                        ContextCompat.getMainExecutor(context).execute { onDetected(null) }
                    } finally {
                        image.close()
                    }
                }

                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture, analysis
                    )
                } catch (e: IllegalArgumentException) {
                    // No back camera on this device (emulator without camera, some tablets).
                    onError("No usable camera was found on this device.")
                } catch (e: IllegalStateException) {
                    onError("Camera could not be started: ${e.message ?: "unknown error"}")
                }
            } catch (e: Exception) {
                onError("Camera initialization failed: ${e.message ?: "unknown error"}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun capture(onSaved: (File) -> Unit, onFailed: (String) -> Unit = {}) {
        val capture = imageCapture ?: run { onFailed("Camera is not ready yet"); return }
        val file = CaptureUtils.newCaptureFile(context)
        capture.takePicture(
            CaptureUtils.outputOptions(file),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(result: ImageCapture.OutputFileResults) = onSaved(file)
                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    onFailed(exception.message ?: "Capture failed")
                }
            }
        )
    }
}

private fun ImageProxy.toBitmap(): Bitmap {
    val plane = planes.first()
    val buffer = plane.buffer
    buffer.rewind()
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val rowPadding = rowStride - pixelStride * width
    val bitmap = Bitmap.createBitmap(
        width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
    )
    bitmap.copyPixelsFromBuffer(buffer)
    val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
    if (cropped !== bitmap) bitmap.recycle()

    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return cropped
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(
        cropped, 0, 0, cropped.width, cropped.height, matrix, true
    )
    if (rotated !== cropped) cropped.recycle()
    return rotated
}

@Composable
private fun DetectionOverlay(doc: DetectedDocument, modifier: Modifier) {
    Canvas(modifier) {
        val sx = size.width / doc.imageWidth.toFloat()
        val sy = size.height / doc.imageHeight.toFloat()
        val pts = listOf(
            doc.quad.topLeft, doc.quad.topRight,
            doc.quad.bottomRight, doc.quad.bottomLeft
        ).map { Offset(it.x.toFloat() * sx, it.y.toFloat() * sy) }
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path, Color.White,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f))
    }
}

@Composable
private fun ScanEditScreen(
    bitmap: Bitmap,
    settings: FilterSettings,
    confirmLabel: String = "Add Page",
    onSettings: (FilterSettings) -> Unit,
    onRotate: () -> Unit,
    onCrop: () -> Unit,
    onRetake: () -> Unit,
    onUse: () -> Unit
) {
    var qualityReport by remember(bitmap) { mutableStateOf<ScanQualityReport?>(null) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("Scan Editor", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("On-device editor", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { qualityReport = ScanQualityAnalyzer.analyze(bitmap) }) {
                Text("Check quality")
            }
        }
        Image(
            bitmap.asImageBitmap(), "Scan",
            Modifier.fillMaxWidth().weight(1f)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = onCrop) { Text("Crop") }
            OutlinedButton(onClick = onRotate) { Text("Rotate") }
            FilterChip(
                selected = settings.grayscale,
                onClick = { onSettings(settings.copy(grayscale = !settings.grayscale)) },
                label = { Text("Gray") }
            )
            FilterChip(
                selected = settings.sharpen,
                onClick = { onSettings(settings.copy(sharpen = !settings.sharpen)) },
                label = { Text("Sharp") }
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onRetake, Modifier.weight(1f)) { Text("Retake") }
            Button(onClick = onUse, Modifier.weight(1f)) { Text(confirmLabel) }
        }
    }

    qualityReport?.let { report ->
        AlertDialog(
            onDismissRequest = { qualityReport = null },
            title = { Text("Scan quality: ${report.score}/100") },
            text = {
                Column {
                    Text("Blur: ${report.blurScore}/100")
                    Text("Brightness: ${report.brightnessScore}/100")
                    Text("Detail: ${report.clippingScore}/100")
                    Spacer(Modifier.height(8.dp))
                    if (report.warnings.isEmpty()) {
                        Text("No major quality warning detected.")
                    } else {
                        report.warnings.forEach { Text("• $it") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { qualityReport = null }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun CropEditor(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onApply: (CropCorners) -> Unit
) {
    var corners by remember(bitmap) {
        mutableStateOf(
            CropCorners(
                Point(bitmap.width * .08, bitmap.height * .08),
                Point(bitmap.width * .92, bitmap.height * .08),
                Point(bitmap.width * .92, bitmap.height * .92),
                Point(bitmap.width * .08, bitmap.height * .92)
            )
        )
    }

    Box(Modifier.fillMaxSize()) {
        Image(bitmap.asImageBitmap(), "Crop editor", Modifier.fillMaxSize())
        Canvas(
            Modifier.fillMaxSize().pointerInput(bitmap) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val x = change.position.x.coerceIn(0f, size.width.toFloat())
                    val y = change.position.y.coerceIn(0f, size.height.toFloat())
                    val sx = bitmap.width.toFloat() / size.width
                    val sy = bitmap.height.toFloat() / size.height
                    val p = Point(x * sx, y * sy)

                    val list = listOf(
                        corners.topLeft, corners.topRight,
                        corners.bottomRight, corners.bottomLeft
                    )
                    val nearest = list.indices.minByOrNull { i ->
                        val dx = list[i].x - p.x
                        val dy = list[i].y - p.y
                        dx * dx + dy * dy
                    } ?: 0

                    corners = when (nearest) {
                        0 -> corners.copy(topLeft = p)
                        1 -> corners.copy(topRight = p)
                        2 -> corners.copy(bottomRight = p)
                        else -> corners.copy(bottomLeft = p)
                    }
                }
            }
        ) {
            val sx = size.width / bitmap.width.toFloat()
            val sy = size.height / bitmap.height.toFloat()
            val pts = listOf(
                corners.topLeft, corners.topRight,
                corners.bottomRight, corners.bottomLeft
            ).map { Offset((it.x * sx).toFloat(), (it.y * sy).toFloat()) }

            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, Color.White,
                style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
            pts.forEach { drawCircle(Color.White, 18f, it) }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = { onApply(corners) }) { Text("Apply Crop") }
        }
    }
}

@Composable
private fun BookSplitScreen(
    bitmap: Bitmap,
    onCancel: () -> Unit,
    onSkip: () -> Unit,
    onSplit: (Float) -> Unit
) {
    var fraction by remember(bitmap) { mutableStateOf(0.5f) }

    Box(Modifier.fillMaxSize()) {
        Image(bitmap.asImageBitmap(), "Book spread", Modifier.fillMaxSize())

        Canvas(
            Modifier.fillMaxSize().pointerInput(bitmap) {
                detectDragGestures { change, _ ->
                    change.consume()
                    fraction = (change.position.x / size.width).coerceIn(0.1f, 0.9f)
                }
            }
        ) {
            val x = size.width * fraction
            drawLine(
                Color.White,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 5f
            )
        }

        Surface(
            Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .85f)
        ) {
            Text("Drag the line onto the book's center crease", Modifier.padding(10.dp))
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            OutlinedButton(onClick = onSkip) { Text("Keep as one page") }
            Button(onClick = { onSplit(fraction) }) { Text("Split into 2 pages") }
        }
    }
}

@Composable
private fun PageManager(
    session: ScanSession,
    pdfOptions: PdfExportOptions,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    onSaveSession: () -> Unit,
    onShowPdfOptions: () -> Unit,
    onExportPdf: () -> Unit,
    onPrint: () -> Unit,
    onExportJpeg: () -> Unit
) {
    var tick by remember { mutableIntStateOf(0) }
    val pages = remember(tick) { session.pages }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pages (${pages.size})", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))

        if (pages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No pages yet")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(pages.size) { index ->
                    val page = pages[index]
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            page.bitmap.asImageBitmap(), "Page ${index + 1}",
                            Modifier.fillMaxWidth().height(160.dp)
                        )
                        Text("Page ${index + 1}", Modifier.padding(top = 4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                enabled = index > 0,
                                onClick = { onReorder(index, index - 1); tick++ }
                            ) { Text("↑") }
                            TextButton(
                                enabled = index < pages.size - 1,
                                onClick = { onReorder(index, index + 1); tick++ }
                            ) { Text("↓") }
                            TextButton(onClick = { session.remove(page.id); tick++ }) { Text("Delete") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack, Modifier.weight(1f)) { Text("Back") }
            Button(onClick = onAdd, Modifier.weight(1f)) { Text("Add") }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSaveSession, Modifier.weight(1f), enabled = pages.isNotEmpty()) {
                Text("Save")
            }
            OutlinedButton(onClick = onShowPdfOptions, Modifier.weight(1f)) {
                Text("PDF options")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExportJpeg, Modifier.weight(1f), enabled = pages.isNotEmpty()) {
                Text("JPEG + Share")
            }
            Button(onClick = onExportPdf, Modifier.weight(1f), enabled = pages.isNotEmpty()) {
                Text("PDF + Share")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPrint, Modifier.fillMaxWidth(), enabled = pages.isNotEmpty()) {
                Text("Print")
            }
        }
        Text(
            "PDF: ${pdfOptions.pageSize.label} · ${pdfOptions.quality.label}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun PdfOptionsDialog(
    options: PdfExportOptions,
    onDismiss: () -> Unit,
    onApply: (PdfExportOptions) -> Unit
) {
    var pageSize by remember { mutableStateOf(options.pageSize) }
    var quality by remember { mutableStateOf(options.quality) }
    var margin by remember { mutableStateOf(options.marginPt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF export options") },
        text = {
            Column {
                Text("Page size", style = MaterialTheme.typography.labelLarge)
                PdfPageSize.entries.forEach { size ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = pageSize == size, onClick = { pageSize = size })
                        Text(size.label)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Quality / compression", style = MaterialTheme.typography.labelLarge)
                ExportQuality.entries.forEach { q ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = quality == q, onClick = { quality = q })
                        Text(q.label)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Margin")
                    Slider(
                        value = margin,
                        onValueChange = { margin = it },
                        valueRange = 0f..60f,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(PdfExportOptions(pageSize = pageSize, marginPt = margin, quality = quality))
            }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
