package com.project.lol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal typealias NativeActionExecutor = (NativeActionRequest, (NativeActionReply) -> Unit) -> Unit
private val actionGreen = Color(0xFF1ED760)
private val actionGrey = Color(0xFFB3B3B3)

internal class NativeLibraryActions(private val execute: NativeActionExecutor) {
    var mode by mutableStateOf("")
    var target by mutableStateOf<MobileBrowseItem?>(null)
    var working by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var uncertain by mutableStateOf(false)
    var error by mutableStateOf("")
    var collaborative by mutableStateOf(false)
    var name by mutableStateOf("")
    var fromPicker by mutableStateOf(false)
    var playlists by mutableStateOf<List<MobileBrowseItem>>(emptyList())
    var more by mutableStateOf(false)
    var message by mutableStateOf("")
    var messageSerial by mutableStateOf(0)
    val saved = mutableStateMapOf<String, Boolean>()
    val pending = mutableStateMapOf<String, Boolean>()
    private val reading = mutableSetOf<String>()
    private var generation = 0
    private val revisions = mutableMapOf<String, Int>()

    fun notify(text: String) { message = text; messageSerial++ }
    fun dismiss() { mode = ""; generation++; error = "" }
    private fun open(mode: String) {
        generation++; this.mode = mode; working = false; uncertain = false; error = ""
    }
    fun createMenu() { target = null; fromPicker = false; open("create") }
    fun createForm(collaborative: Boolean, fromPicker: Boolean = false) {
        this.collaborative = collaborative; this.fromPicker = fromPicker; name = ""; open("name")
    }
    fun menu(item: MobileBrowseItem, fallback: Boolean = false) {
        val uri = mediaUri(item.path)
        if (fallback && uri !in saved) saved[uri] = true
        target = item; open("menu"); readSaved(item)
    }
    fun isSaved(item: MobileBrowseItem, fallback: Boolean = false) = saved[mediaUri(item.path)] ?: fallback
    fun readSaved(item: MobileBrowseItem) {
        val uri = mediaUri(item.path)
        if (uri.isEmpty() || pending[uri] == true || !reading.add(uri)) return
        val revision = revisions[uri] ?: 0
        execute(NativeActionRequest("savedStatus", uri)) { reply ->
            reading.remove(uri)
            if ((revisions[uri] ?: 0) == revision && reply.state == "success" && reply.uri == uri && reply.saved != null)
                saved[uri] = reply.saved
        }
    }
    fun favorite(item: MobileBrowseItem, fallback: Boolean = false) {
        if (isSaved(item, fallback)) menu(item, fallback) else setSaved(item, true)
    }
    fun setSaved(item: MobileBrowseItem, value: Boolean) {
        val uri = mediaUri(item.path)
        if (uri.isEmpty()) { notify("Не удалось определить объект. Открой его страницу."); return }
        if (pending[uri] == true) return
        pending[uri] = true; revisions[uri] = (revisions[uri] ?: 0) + 1
        val ticket = generation
        execute(NativeActionRequest("setSaved", uri, saved = value)) { reply ->
            pending.remove(uri)
            if (reply.state == "success" && reply.uri == uri && reply.saved == value) {
                saved[uri] = value
                if (ticket == generation && mode == "menu") dismiss()
                notify(if (value) "Добавлено в медиатеку" else "Удалено из медиатеки")
            } else {
                val text = reply.errorText()
                if (ticket == generation && mode.isNotEmpty()) { error = text; uncertain = reply.state == "unknown" }
                else notify(text)
            }
        }
    }
    fun choosePlaylist(item: MobileBrowseItem) {
        target = item; open("playlists"); loadPlaylists()
    }
    fun loadPlaylists() {
        if (loading) return
        loading = true; error = ""
        execute(NativeActionRequest("playlists")) { reply ->
            loading = false
            if (reply.state == "success") { playlists = reply.playlists; more = reply.more }
            else if (mode == "playlists") error = reply.errorText()
        }
    }
    fun create(onCreated: (String) -> Unit) {
        if (working || uncertain || name.isBlank() || name.length > 100) return
        working = true; error = ""; val ticket = generation
        val title = name.trim(); val returnToPicker = fromPicker
        execute(NativeActionRequest("createPlaylist", name = title, collaborative = collaborative)) { reply ->
            if (ticket != generation) {
                if (reply.state == "success") notify("Плейлист создан") else notify(reply.errorText())
                return@execute
            }
            working = false
            val uri = mediaUri(reply.uri)
            if (reply.state == "success" && uri.startsWith("spotify:playlist:")) {
                if (returnToPicker) {
                    val item = MobileBrowseItem("/playlist/" + uri.substringAfterLast(':'), title, "Мой плейлист", kind = "playlist")
                    playlists = listOf(item) + playlists.filter { it.path != item.path }
                    open("playlists"); notify("Плейлист создан. Выбери его, чтобы добавить трек.")
                } else { dismiss(); onCreated("/playlist/" + uri.substringAfterLast(':')) }
            } else { error = reply.errorText(); uncertain = reply.state == "unknown" }
        }
    }
    fun addTo(playlist: MobileBrowseItem) {
        val item = target ?: return
        val uri = mediaUri(item.path); val destination = mediaUri(playlist.path)
        if (working || uncertain || uri.isBlank() || !destination.startsWith("spotify:playlist:")) return
        working = true; error = ""; val ticket = generation
        execute(NativeActionRequest("addToPlaylist", uri, target = destination)) { reply ->
            if (ticket != generation) {
                notify(if (reply.state == "success") "Добавлено в плейлист" else reply.errorText()); return@execute
            }
            working = false
            if (reply.state == "success") { dismiss(); notify("Добавлено в плейлист") }
            else { error = reply.errorText(); uncertain = reply.state == "unknown" }
        }
    }
}

@Composable
internal fun rememberNativeLibraryActions(execute: NativeActionExecutor): NativeLibraryActions {
    val current = rememberUpdatedState(execute)
    return remember { NativeLibraryActions { request, result -> current.value(request, result) } }
}

@Composable
internal fun NativeSaveButton(
    actions: NativeLibraryActions, item: MobileBrowseItem, fallback: Boolean = false, modifier: Modifier = Modifier
) {
    val saved = actions.isSaved(item, fallback)
    val busy = actions.pending[mediaUri(item.path)] == true
    val interactions = remember { MutableInteractionSource() }
    IconButton(onClick = { actions.favorite(item, fallback) }, enabled = !busy, interactionSource = interactions,
        modifier = modifier.size(48.dp).nativePressScale(interactions).testTag("save:" + item.path)) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = actionGreen, strokeWidth = 2.dp)
        else Icon(if (saved) NativeIcons.CheckCircle else NativeIcons.PlusCircle,
            if (saved) "Сохранено: действия" else "Добавить в медиатеку",
            tint = if (saved) actionGreen else Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String = "", enabled: Boolean = true,
    image: String = "", color: Color = Color.White, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        if (image.isNotBlank()) Cover(image, Modifier.size(48.dp))
        else Icon(icon, null, tint = if (enabled) color else actionGrey, modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Color.White else actionGrey, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) Text(subtitle, color = actionGrey, fontSize = 13.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeActionsSheet(
    actions: NativeLibraryActions, onCreated: (String) -> Unit, onOpenWeb: (String) -> Unit
) {
    if (actions.mode.isEmpty()) return
    val sheet = rememberBottomSheetState(initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .88f).dp
    var filter by remember(actions.mode, actions.target?.path) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = { actions.dismiss() }, sheetState = sheet,
        containerColor = Color(0xFF242424), contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
        ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = FontFamily.SansSerif)) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).imePadding()
            .then(if (actions.mode != "playlists") Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(bottom = 16.dp).testTag("native-actions-sheet")) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when (actions.mode) { "create" -> "Создать"; "name" -> "Новый плейлист";
                    "playlists" -> "Добавить в плейлист"; else -> "Действия" },
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { actions.dismiss() }) { Icon(NativeIcons.Close, "Закрыть меню") }
            }
            when (actions.mode) {
                "create" -> {
                    ActionRow(NativeIcons.PlaylistAdd, "Плейлист", "Собери музыку в одном месте") { actions.createForm(false) }
                    ActionRow(NativeIcons.PlusCircle, "Совместный плейлист", "Создай плейлист для совместного редактирования") { actions.createForm(true) }
                }
                "name" -> {
                    Spacer(Modifier.height(20.dp))
                    Text("Как назовём плейлист?", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
                    BasicTextField(value = actions.name, onValueChange = { actions.name = it.take(100) }, singleLine = true,
                        cursorBrush = SolidColor(actionGreen),
                        enabled = !actions.working && !actions.uncertain,
                        textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.SansSerif,
                            fontSize = 26.sp, textAlign = TextAlign.Center),
                        modifier = Modifier.fillMaxWidth().padding(24.dp).testTag("native-playlist-name"),
                        decorationBox = { inner ->
                            Column { Box(Modifier.fillMaxWidth()) {
                                if (actions.name.isEmpty()) Text("Название плейлиста", color = actionGrey,
                                    fontSize = 22.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                inner()
                            }; Spacer(Modifier.height(12.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(actionGrey)) }
                        })
                    if (actions.collaborative) Text("Совместный · не публикуется в профиле", color = actionGrey,
                        modifier = Modifier.padding(horizontal = 24.dp), fontSize = 13.sp)
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(16.dp).clip(CircleShape)
                        .background(if (actions.name.isNotBlank() && !actions.uncertain) actionGreen else Color(0xFF535353))
                        .clickable(enabled = actions.name.isNotBlank() && !actions.working && !actions.uncertain,
                            role = Role.Button) { actions.create(onCreated) }.padding(horizontal = 34.dp, vertical = 15.dp)) {
                        if (actions.working) CircularProgressIndicator(Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
                        else Text("Создать плейлист", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                "playlists" -> {
                    actions.target?.let { Text(it.title, color = actionGrey, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) }
                    BasicTextField(filter, { filter = it.take(100) }, singleLine = true,
                        cursorBrush = SolidColor(actionGreen),
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(5.dp)).background(Color(0xFF363636)).padding(14.dp)
                            .testTag("native-playlist-filter"),
                        decorationBox = { inner -> Box { if (filter.isEmpty()) Text("Найти плейлист", color = actionGrey); inner() } })
                    ActionRow(NativeIcons.PlaylistAdd, "Новый плейлист", enabled = !actions.working && !actions.uncertain) {
                        actions.createForm(false, fromPicker = true)
                    }
                    if (actions.loading || actions.working) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally)
                        .padding(12.dp).size(24.dp), color = actionGreen, strokeWidth = 2.dp)
                    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                        val choices = actions.playlists.filter { it.title.contains(filter, ignoreCase = true) }
                        items(choices, key = { it.path }) { playlist ->
                            ActionRow(NativeIcons.LibraryOutline, playlist.title, playlist.subtitle,
                                enabled = !actions.loading && !actions.working && !actions.uncertain, image = playlist.image) { actions.addTo(playlist) }
                        }
                        if (choices.isEmpty() && !actions.loading) item {
                            Text(if (filter.isBlank()) "Пока нет доступных для редактирования плейлистов" else "Ничего не найдено",
                                color = actionGrey, modifier = Modifier.padding(24.dp))
                        }
                        if (actions.more) item { Text("Показаны первые 500 плейлистов. Остальные доступны в веб-плеере.",
                            color = actionGrey, fontSize = 13.sp, modifier = Modifier.padding(24.dp)) }
                    }
                }
                "menu" -> actions.target?.let { item ->
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Cover(item.image, Modifier.size(48.dp), round = item.kind == "artist")
                        Column(Modifier.weight(1f)) {
                        Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.subtitle, color = actionGrey, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                    val saved = actions.isSaved(item)
                    if (mediaUri(item.path).isNotBlank()) ActionRow(if (saved) NativeIcons.CheckCircle else NativeIcons.PlusCircle,
                        if (saved) "Удалить из медиатеки" else "Добавить в медиатеку",
                        color = if (saved) actionGreen else Color.White,
                        enabled = actions.pending[mediaUri(item.path)] != true && !actions.uncertain) { actions.setSaved(item, !saved) }
                    if (item.kind in listOf("track", "episode")) ActionRow(NativeIcons.PlaylistAdd, "Добавить в плейлист") {
                        actions.choosePlaylist(item)
                    }
                    ActionRow(NativeIcons.More, "Открыть в веб-плеере", "Дополнительные возможности Spotify") {
                        actions.dismiss(); onOpenWeb(item.path)
                    }
                }
            }
            if (actions.error.isNotBlank()) {
                Text(actions.error, color = Color(0xFFFFCFB0), fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).testTag("native-action-error"))
                if (actions.mode == "playlists" && !actions.working && !actions.uncertain)
                    Text("Обновить список", color = actionGreen, modifier = Modifier.clickable { actions.loadPlaylists() }.padding(24.dp))
                Text("Открыть веб-плеер", color = actionGreen, modifier = Modifier.clickable {
                    val path = actions.target?.path ?: "/"; actions.dismiss(); onOpenWeb(path)
                }.padding(horizontal = 24.dp, vertical = 12.dp))
            }
        }
        }
    }
}

@Composable
internal fun NativeActionMessage(actions: NativeLibraryActions, modifier: Modifier = Modifier) {
    LaunchedEffect(actions.messageSerial) {
        if (actions.message.isNotBlank()) { delay(3500); actions.message = "" }
    }
    if (actions.message.isNotBlank()) Box(modifier.padding(16.dp).clip(RoundedCornerShape(8.dp))
        .background(Color.White).padding(16.dp).testTag("native-action-message")) {
        Text(actions.message, color = Color.Black, fontSize = 14.sp)
    }
}
