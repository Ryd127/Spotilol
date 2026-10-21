package com.project.lol.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowsSort
import com.project.lol.R
import java.text.Collator
import java.util.Locale

internal enum class NativePlaylistOrder {
    ORIGINAL, TITLE_ASC, TITLE_DESC, ARTIST_ASC, ARTIST_DESC;

    companion object {
        fun parse(value: String?) = entries.firstOrNull { it.name == value } ?: ORIGINAL
    }
}

/** Display order only. Do not mutate API/cache objects or the remote playlist/queue. */
internal fun orderNativeTracks(items: List<MobileBrowseItem>, order: NativePlaylistOrder): List<MobileBrowseItem> {
    if (order == NativePlaylistOrder.ORIGINAL) return items
    val artist = order == NativePlaylistOrder.ARTIST_ASC || order == NativePlaylistOrder.ARTIST_DESC
    val direction = if (order == NativePlaylistOrder.ARTIST_DESC || order == NativePlaylistOrder.TITLE_DESC) -1 else 1
    val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
    return items.sortedWith { a, b ->
        val x = (if (artist) a.subtitle else a.title).trim()
        val y = (if (artist) b.subtitle else b.title).trim()
        when {
            x.isBlank() && y.isBlank() -> 0
            x.isBlank() -> 1
            y.isBlank() -> -1
            else -> collator.compare(x, y) * direction
        }
    }
}

@Composable
private fun orderLabel(order: NativePlaylistOrder): String {
    val title = stringResource(R.string.native_sort_title)
    val artist = stringResource(R.string.widget_studio_info_artist)
    val ascending = stringResource(R.string.native_sort_ascending)
    val descending = stringResource(R.string.native_sort_descending)
    return when (order) {
        NativePlaylistOrder.ORIGINAL -> stringResource(R.string.native_sort_original)
        NativePlaylistOrder.TITLE_ASC -> "$title · $ascending"
        NativePlaylistOrder.TITLE_DESC -> "$title · $descending"
        NativePlaylistOrder.ARTIST_ASC -> "$artist · $ascending"
        NativePlaylistOrder.ARTIST_DESC -> "$artist · $descending"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativePlaylistSortButton(path: String, order: NativePlaylistOrder, onOrder: (NativePlaylistOrder) -> Unit) {
    var open by rememberSaveable(path) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().testTag("native-sort-open")
        .clickable(role = Role.Button) { open = true }.padding(horizontal = 17.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(TablerIcons.ArrowsSort, stringResource(R.string.native_sort_heading), tint = Color(0xFFB3B3B3))
        Text(orderLabel(order), color = Color(0xFFB3B3B3), fontFamily = FontFamily.SansSerif,
            modifier = Modifier.padding(start = 10.dp))
    }
    if (open) {
        val sheet = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = sheet,
            containerColor = Color(0xFF242424), contentColor = Color.White,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .7f).dp)
            .verticalScroll(rememberScrollState()).padding(bottom = 20.dp).testTag("native-sort-sheet")) {
            Text(stringResource(R.string.native_sort_heading), color = Color.White, fontFamily = FontFamily.SansSerif,
                fontSize = 24.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
            NativePlaylistOrder.entries.forEach { option ->
                Row(Modifier.fillMaxWidth().testTag("native-sort-${option.name}")
                    .selectable(selected = order == option, role = Role.RadioButton) { onOrder(option); open = false }
                    .padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = order == option, onClick = null)
                    Text(orderLabel(option), color = Color.White, fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }
    }
}
