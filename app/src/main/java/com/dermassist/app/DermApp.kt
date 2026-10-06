package com.dermassist.app

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun DermApp(vm: DermViewModel = viewModel()) {
    val context = LocalContext.current
    val showDeviceBadge = LocalConfiguration.current.screenWidthDp >= 380 && LocalDensity.current.fontScale <= 1.2f
    var confirmExit by remember { mutableStateOf(false) }
    val inFlow = vm.screen in setOf(Screen.CONSENT, Screen.PHOTO, Screen.SYMPTOMS, Screen.RESULT)
    BackHandler(enabled = vm.screen != Screen.HOME) {
        if (!vm.busy) { if (vm.screen == Screen.CONSENT) confirmExit = true else vm.back() }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::importPhoto)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = vm.pendingCamera
        if (success && path != null) vm.importPhoto(Uri.fromFile(File(path))) else vm.cameraCancelled()
    }
    fun takePhoto() {
        try {
            val file = vm.newCapture()
            camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.photos", file))
        } catch (_: Exception) {
            vm.cameraCancelled()
            vm.notifyError("A camera app could not be opened. Choose a photo instead.")
        }
    }
    Scaffold(
        containerColor = Parchment,
        topBar = {
            Row(Modifier.fillMaxWidth().testTag("app-top-bar").statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (inFlow) IconButton(onClick = { if (vm.screen == Screen.CONSENT) confirmExit = true else vm.back() }, enabled = !vm.busy) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Go back")
                } else Icon(Icons.Outlined.Spa, null, Modifier.padding(12.dp).size(25.dp))
                Text("DermAssist", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (inFlow) TextButton(onClick = { confirmExit = true }, enabled = !vm.busy) { Text("Close") }
                else if (showDeviceBadge) Text("ON YOUR DEVICE", style = MaterialTheme.typography.labelSmall, color = Ash)
            }
        },
        bottomBar = {
            if (!inFlow) {
                Surface(color = Parchment) {
                    Column {
                        HorizontalDivider(color = Mist)
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            NavItem("Home", Icons.Outlined.Home, vm.screen == Screen.HOME) { vm.navigate(Screen.HOME) }
                            NavItem("Journal", Icons.Outlined.BookmarkBorder, vm.screen == Screen.JOURNAL) { vm.navigate(Screen.JOURNAL) }
                            NavItem("About", Icons.Outlined.Info, vm.screen == Screen.ABOUT) { vm.navigate(Screen.ABOUT) }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            key(vm.screen) {
                Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (inFlow) Steps(vm.screen)
                    vm.error?.let { Note(Icons.Outlined.Info, "Please try again", it) }
                    when (vm.screen) {
                        Screen.HOME -> Home(vm)
                        Screen.CONSENT -> Consent { vm.navigate(Screen.PHOTO) }
                        Screen.PHOTO -> PhotoScreen(vm, ::takePhoto) {
                            try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                            catch (_: Exception) { vm.notifyError("The photo picker could not be opened. Try taking a photo.") }
                        }
                        Screen.SYMPTOMS -> SymptomsScreen(vm)
                        Screen.RESULT -> ResultScreen(vm) {
                            runCatching { shareReport(context, vm.current) }.onFailure { vm.notifyError("No sharing app could be opened. You can save the record locally.") }
                        }
                        Screen.JOURNAL -> JournalScreen(vm) { record ->
                            runCatching { shareReport(context, record) }.onFailure { vm.notifyError("No sharing app could be opened. Your saved record is still here.") }
                        }
                        Screen.ABOUT -> AboutScreen(vm)
                    }
                }
            }
        }
    }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false }, title = { Text("Close this check?") },
        text = { Text("The temporary photo will be deleted. A record you chose to save stays in your journal.") },
        confirmButton = { TextButton(onClick = { vm.finish(); confirmExit = false }) { Text("Close check") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep going") } },
    )
}

@Composable
private fun RowScope.NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.textButtonColors(containerColor = if (selected) Linen else Color.Transparent)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(22.dp), tint = if (selected) Ink else Ash)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected) Ink else Ash, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Steps(screen: Screen) {
    val index = when (screen) { Screen.CONSENT -> 0; Screen.PHOTO -> 1; Screen.SYMPTOMS -> 2; else -> 3 }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(4) { step -> Box(Modifier.weight(1f).height(2.dp).background(if (step <= index) Signal else Mist)) }
        }
        Text("${index + 1} OF 4  /  ${listOf("BEFORE YOU BEGIN", "YOUR PHOTO", "A LITTLE CONTEXT", "YOUR RECORD")[index]}", style = MaterialTheme.typography.labelSmall, color = Ash)
    }
}

@Composable
private fun Home(vm: DermViewModel) {
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Eyebrow("A LITTLE ATTENTION. A FIRST STEP.")
        Text("Your skin,\nin a little more focus.", style = MaterialTheme.typography.displaySmall)
        Text("A quiet place to document a skin concern and prepare for a conversation about it.", color = Ash, style = MaterialTheme.typography.bodyLarge)
    }
    BotanicalPanel()
    PrimaryAction("Start a skin check", Icons.AutoMirrored.Outlined.ArrowForward, onClick = vm::start)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp), tint = Ash)
        Text("Private by default. No account needed.", style = MaterialTheme.typography.bodyMedium, color = Ash)
    }
    HorizontalDivider(color = Mist)
    Text("One step at a time", style = MaterialTheme.typography.headlineSmall)
    GuideRow("01", "Photograph the concern", "Take a clear photo or choose one from your phone.")
    GuideRow("02", "Add a little context", "Tell us where it is and what you’ve noticed.")
    GuideRow("03", "Keep a useful record", "Save your notes or share them with a health worker.")
    Note(Icons.Outlined.Info, "An early prototype", "A real research model runs on this phone. Its first evaluation was not reliable enough for condition suggestions, so this build keeps your concern unassessed.")
}

@Composable
private fun Consent(onContinue: () -> Unit) {
    var accepted by rememberSaveable { mutableStateOf(false) }
    PageHeading("Before we begin.", "Your photo. Your choice.")
    Paper {
        InfoRow(Icons.Outlined.PhoneAndroid, "Stays on this phone", "Your photo is prepared locally. Nothing is uploaded, and photos are not used for training.")
        HorizontalDivider(color = Mist)
        InfoRow(Icons.Outlined.BookmarkBorder, "Save only if you choose", "Your journal stores symptom notes, not photos. Delete saved records at any time.")
        HorizontalDivider(color = Mist)
        InfoRow(Icons.Outlined.Visibility, "Know the limits", "The model is an AI coursework experiment. Its evaluation was not reliable enough for condition suggestions, so this version returns unable to assess.")
    }
    Text("The photo stays temporarily in the app cache until you close the check. Android may also clear it. Your camera app may keep its own copy.", style = MaterialTheme.typography.bodyMedium, color = Ash)
    Surface(onClick = { accepted = !accepted }, color = Linen, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
            Text("I understand the limitations and agree to local photo processing.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
    }
    PrimaryAction("Continue to photo", Icons.AutoMirrored.Outlined.ArrowForward, enabled = accepted, onClick = onContinue)
}

@Composable
private fun PhotoScreen(vm: DermViewModel, takePhoto: () -> Unit, choosePhoto: () -> Unit) {
    var checked by rememberSaveable(vm.photo) { mutableStateOf(false) }
    PageHeading("A clear place to start.", "Frame the area you’d like to document.")
    if (vm.photo == null) {
        Surface(color = Linen, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Mist)) {
            Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Outlined.CenterFocusWeak, null, Modifier.size(80.dp), tint = Ash)
                Text("Keep the concern in focus", style = MaterialTheme.typography.headlineSmall)
                Text("Include a little surrounding skin.", color = Ash, style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else {
        PhotoPreview(vm.photo!!)
    }
    if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Signal)
    PrimaryAction(if (vm.photo == null) "Take a photo" else "Retake photo", Icons.Outlined.PhotoCamera, !vm.busy, takePhoto)
    SecondaryAction("Choose from photos", Icons.Outlined.PhotoLibrary, !vm.busy, choosePhoto)
    if (vm.photo != null) {
        Surface(onClick = { checked = !checked }, color = Linen, shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked, { checked = it })
                Text("The area is clear, well lit, and in focus.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
        PrimaryAction("Use this photo", Icons.AutoMirrored.Outlined.ArrowForward, checked && !vm.busy) { vm.navigate(Screen.SYMPTOMS) }
    }
    Paper {
        Eyebrow("A FEW PHOTO TIPS")
        Text("Use even, natural light.\nKeep the camera steady.\nAvoid filters, glare, and deep shadows.", style = MaterialTheme.typography.bodyLarge)
        Text("Resolution is checked now. Screening also checks extreme exposure and very low detail. These checks cannot guarantee a suitable photo.", style = MaterialTheme.typography.bodyMedium, color = Ash)
    }
}

@Composable
private fun PhotoPreview(path: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
    }
    bitmap?.let { Image(it.asImageBitmap(), "Your selected skin concern photo", Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)).background(Linen), contentScale = ContentScale.Fit) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choice(label: String, options: List<String>, selected: String, enabled: Boolean = true, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { option ->
                FilterChip(enabled = enabled, selected = selected == option, onClick = { onSelect(option) }, label = { Text(option) },
                    modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Dusk, selectedLabelColor = Color.White))
            }
        }
    }
}

@Composable
private fun SymptomsScreen(vm: DermViewModel) {
    PageHeading("A little more context.", "Choose what best describes your concern. Every question needs an answer.")
    val s = vm.symptoms
    Choice("Where is the concern?", listOf("Face or scalp", "Arms or hands", "Torso", "Legs or feet", "Another area"), s.location, !vm.busy) { vm.update(s.copy(location = it)) }
    Choice("How long has it been there?", listOf("A few days", "1–2 weeks", "Over 2 weeks", "Not sure"), s.duration, !vm.busy) { vm.update(s.copy(duration = it)) }
    Choice("Does it itch?", listOf("No", "A little", "A lot", "Not sure"), s.itching, !vm.busy) { vm.update(s.copy(itching = it)) }
    Choice("Is it painful?", listOf("No", "A little", "A lot", "Not sure"), s.pain, !vm.busy) { vm.update(s.copy(pain = it)) }
    Choice("Is it spreading?", listOf("No", "Yes", "Not sure"), s.spread, !vm.busy) { vm.update(s.copy(spread = it)) }
    Note(Icons.Outlined.Info, "Your notes are for a conversation", "This prototype does not evaluate symptoms or urgency. Do not wait for an app result if you believe you need medical help.")
    if (vm.busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Signal)
        Text("Checking the photo on your device…", style = MaterialTheme.typography.bodyMedium)
    }
    PrimaryAction("Run research screening", Icons.AutoMirrored.Outlined.ArrowForward, s.complete && !vm.busy, vm::showResult)
}

@Composable
private fun ResultScreen(vm: DermViewModel, onShare: () -> Unit) {
    PageHeading("Your screening record.", "An experimental result to discuss with a qualified health worker.")
    Paper {
        Icon(Icons.Outlined.Info, null, Modifier.size(32.dp), tint = Ash)
        Eyebrow(if (vm.screening.status == ScreeningStatus.EXPERIMENTAL_MATCH) "EXPERIMENTAL SUGGESTION" else "NO RELIABLE SUGGESTION")
        Text(vm.screening.title, style = MaterialTheme.typography.headlineLarge)
        Text(vm.screening.reason, style = MaterialTheme.typography.bodyLarge)
        Text("Not a diagnosis. Other conditions are possible, including ones this model cannot recognize.", style = MaterialTheme.typography.bodyMedium, color = Ash)
        if (vm.screening.status == ScreeningStatus.PHOTO_REJECTED) {
            SecondaryAction("Retake the photo", Icons.Outlined.PhotoCamera) { vm.navigate(Screen.PHOTO) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Your next step", style = MaterialTheme.typography.headlineSmall)
        Text("Share your concern with a qualified health worker. Your notes may help explain what you have noticed.", style = MaterialTheme.typography.bodyLarge, color = Ash)
    }
    Paper {
        Eyebrow("WHAT YOU NOTICED")
        Text(vm.symptoms.summary(), style = MaterialTheme.typography.bodyLarge)
    }
    PrimaryAction(if (vm.isSaved) "Saved in your journal" else "Save notes to journal", if (vm.isSaved) Icons.Outlined.Check else Icons.Outlined.BookmarkBorder, !vm.isSaved && !vm.busy, vm::save)
    SecondaryAction("Share text record", Icons.Outlined.Share, onClick = onShare)
    Text("Saving is optional. Saved and shared records contain your symptoms and the prototype limitation, without your photo.", style = MaterialTheme.typography.bodyMedium, color = Ash)
    TextButton(onClick = vm::finish, modifier = Modifier.alignCenter(), enabled = !vm.busy) { Text("Finish and remove photo") }
}

private fun Modifier.alignCenter() = this.fillMaxWidth().heightIn(min = 48.dp)

@Composable
private fun JournalScreen(vm: DermViewModel, share: (Assessment) -> Unit) {
    var deleting by remember { mutableStateOf<Assessment?>(null) }
    Spacer(Modifier.height(8.dp))
    PageHeading("Your skin journal.", "Notes you chose to keep. Only on this phone.")
    if (vm.history.isEmpty()) {
        Paper {
            Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(36.dp), tint = Ash)
            Text("A fresh page.", style = MaterialTheme.typography.headlineSmall)
            Text("Saved checks will appear here. Start with a photo and a few notes whenever you’re ready.", style = MaterialTheme.typography.bodyLarge, color = Ash)
            PrimaryAction("Start a skin check", Icons.AutoMirrored.Outlined.ArrowForward, onClick = vm::start)
        }
    }
    vm.history.forEach { entry ->
        key(entry.id) {
            Paper {
                Eyebrow(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.createdAt)).uppercase())
                Text(entry.symptoms.location, style = MaterialTheme.typography.headlineSmall)
                Text(entry.symptoms.summary(), style = MaterialTheme.typography.bodyMedium, color = Ash)
                Text(entry.screening.title + " · Research prototype", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = { share(entry) }) { Icon(Icons.Outlined.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Share") }
                    TextButton(onClick = { deleting = entry }, enabled = !vm.busy) { Text("Delete") }
                }
            }
        }
    }
    Text("Photos are never saved to your journal. Uninstalling the app removes these records.", style = MaterialTheme.typography.bodyMedium, color = Ash)
    deleting?.let { entry -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this record?") }, text = { Text("This removes the notes from your phone. You cannot undo this.") },
        confirmButton = { TextButton(onClick = { vm.delete(entry.id); deleting = null }) { Text("Delete record") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep record") } }) }
}

@Composable
private fun AboutScreen(vm: DermViewModel) {
    var deleteAll by remember { mutableStateOf(false) }
    Spacer(Modifier.height(8.dp))
    PageHeading("Care begins\nwith attention.", "DermAssist is a student project exploring accessible, on-device skin screening.")
    Paper {
        Eyebrow("VERSION 0.2 · AI RESEARCH PROTOTYPE")
        Text("Built for a first conversation", style = MaterialTheme.typography.headlineSmall)
        Text("A small neural network was trained on public SCIN photos of eczema, urticaria, and folliculitis. It runs on this phone, but condition suggestions are disabled because the first evaluation was unreliable. Symptom notes are recorded separately.", style = MaterialTheme.typography.bodyLarge)
    }
    Paper {
        Text("How it learns", style = MaterialTheme.typography.headlineSmall)
        Text("Transfer learning adapts an image-recognition network. Separate data is used to train, tune, calibrate, and test it. Confidence and visual-distance checks can reject uncertain inputs.", style = MaterialTheme.typography.bodyMedium)
        Text("First held-out study: 37 of 86 cases classified correctly (43%). Only 6 of 10 accepted predictions were correct. These results do not support screening use.", style = MaterialTheme.typography.bodyMedium)
        Text("This is a coursework model, trained on a small US-sourced dataset. It has not been clinically validated for your population. Skin-tone coverage is uneven, and rejection is imperfect.", style = MaterialTheme.typography.bodyMedium, color = Ash)
        Text("Data: SCIN, Google Research and collaborators. SCIN Data Use License. https://github.com/google-research-datasets/scin", style = MaterialTheme.typography.bodyMedium, color = Ash)
    }
    Text("Your privacy, plainly", style = MaterialTheme.typography.headlineSmall)
    InfoRow(Icons.Outlined.WifiOff, "Works without an account", "There is no backend, analytics, or network permission. The app does not upload your photo or notes.")
    InfoRow(Icons.Outlined.Lock, "Local and optional", "Photos are temporary. Only notes you explicitly save remain in private app storage. Device backups are disabled.")
    InfoRow(Icons.Outlined.Share, "Sharing is your choice", "The Android share sheet sends text only to an app you choose. Copies you share are controlled by the receiving app.")
    HorizontalDivider(color = Mist)
    SecondaryAction("Delete all saved records", Icons.Outlined.DeleteOutline, vm.history.isNotEmpty() && !vm.busy) { deleteAll = true }
    Text("English · Android 8.0 and above", style = MaterialTheme.typography.bodyMedium, color = Ash)
    if (deleteAll) AlertDialog(onDismissRequest = { deleteAll = false }, title = { Text("Clear your journal?") }, text = { Text("All ${vm.history.size} saved records will be permanently removed from this phone.") },
        confirmButton = { TextButton(onClick = { vm.deleteAll(); deleteAll = false }) { Text("Delete all records") } },
        dismissButton = { TextButton(onClick = { deleteAll = false }) { Text("Cancel") } })
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Ash)
    }
}
@Composable
private fun Eyebrow(text: String) = Text(text, style = MaterialTheme.typography.labelSmall, color = Ash)

@Composable
private fun Paper(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Mist)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
@Composable
private fun Note(icon: ImageVector, title: String, body: String) {
    Surface(color = Linen, shape = RoundedCornerShape(12.dp)) { Box(Modifier.padding(16.dp)) { InfoRow(icon, title, body) } }
}
@Composable
private fun InfoRow(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, Modifier.size(22.dp), tint = Ash)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Ash)
        }
    }
}
@Composable
private fun GuideRow(number: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(number, style = MaterialTheme.typography.bodyMedium, color = Ash, modifier = Modifier.padding(top = 3.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Ash)
        }
    }
}
@Composable
private fun PrimaryAction(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (enabled) Signal else Mist), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Dusk)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(12.dp)); Icon(icon, null, Modifier.size(20.dp))
    }
}
@Composable
private fun SecondaryAction(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Mist), contentPadding = PaddingValues(16.dp)) {
        Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(label)
    }
}

/** Lightweight botanical line art: no downloaded assets, animation, or runtime shaders. */
@Composable
private fun BotanicalPanel() {
    Box(Modifier.fillMaxWidth().heightIn(min = 180.dp).clip(RoundedCornerShape(16.dp)).background(Linen)) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            drawCircle(Color(0xFFE7ECE5), h * .44f, Offset(w * .79f, h * .42f))
            drawCircle(Parchment, h * .31f, Offset(w * .79f, h * .42f))
            for (i in 0..3) {
                val x = w * (.47f + i * .135f)
                val top = h * (.25f + (i % 2) * .14f)
                val stem = Path().apply { moveTo(x, h); cubicTo(x - 28, h * .7f, x + 20, h * .5f, x, top) }
                drawPath(stem, Color(0xFF777F75), style = Stroke(1.4.dp.toPx()))
                for (j in 0..2) {
                    val y = top + h * (.15f + .15f * j)
                    val side = if ((i + j) % 2 == 0) 1 else -1
                    val leaf = Path().apply {
                        moveTo(x, y + 22); cubicTo(x + side * 52, y + 14, x + side * 55, y - 23, x + side * 45, y - 30)
                        cubicTo(x + side * 7, y - 24, x - side * 5, y + 6, x, y + 22)
                    }
                    drawPath(leaf, Color(0xFFDEE2DE)); drawPath(leaf, Color(0xFF777F75), style = Stroke(1.dp.toPx()))
                }
            }
        }
        Column(Modifier.align(Alignment.CenterStart).padding(24.dp).widthIn(max = 150.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("A moment\nfor yourself.", style = MaterialTheme.typography.headlineSmall)
            Text("Notice. Document.\nTalk about it.", style = MaterialTheme.typography.bodyMedium, color = Ash)
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun WelcomePreview() {
    DermTheme {
        Surface(color = Parchment) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("DermAssist", style = MaterialTheme.typography.headlineSmall)
                Eyebrow("A LITTLE ATTENTION. A FIRST STEP.")
                Text("Your skin,\nin a little more focus.", style = MaterialTheme.typography.displaySmall)
                Text("A quiet place to document a skin concern and prepare for a conversation about it.", color = Ash)
                BotanicalPanel()
                PrimaryAction("Start a skin check", Icons.AutoMirrored.Outlined.ArrowForward) {}
                GuideRow("01", "Photograph the concern", "Take a clear photo or choose one from your phone.")
            }
        }
    }
}
