"""Hybrid9 polish: real search songs, page motion, and no inert menu controls."""
from pathlib import Path

root=Path("app/src/main/java/com/project/lol")
def one(rel,old,new):
    p=root/rel
    s=p.read_text(encoding="utf-8")
    n=s.count(old)
    if n!=1: raise RuntimeError(f"polish anchor in {rel}: {n} {old[:90]!r}")
    p.write_text(s.replace(old,new,1),encoding="utf-8")

shell="ui/components/NativeMobileShell.kt"
one(shell,
    'val detail = listOf("/playlist/", "/album/", "/artist/", "/collection/", "/show/").any { browse.path.startsWith(it) } && !(tab == "Library" && browse.path == "/collection/tracks")',
    'val detail = listOf("/playlist/", "/album/", "/artist/", "/collection/", "/show/").any { browse.path.startsWith(it) }')
one(shell,
    'onNavigate(when(label) { "Home" -> "/"; "Library" -> "/collection/tracks"; else -> "/search" })',
    'onNavigate(when(label) { "Home", "Library" -> "/"; else -> "/search" })')
one(shell,
    '    var query by rememberSaveable { mutableStateOf("") }',
    '    var query by rememberSaveable { mutableStateOf("") }\n'
    '    var musicOnly by rememberSaveable { mutableStateOf(false) }')
one(shell,
    '    val rows = if(tab == "Library" && !detail) browse.library else browse.items',
    '    val allRows = if(tab == "Library" && !detail) browse.library else browse.items\n'
    '    val rows = if(tab == "Home" && musicOnly && !detail) '
    'allRows.filter { it.kind == "album" || it.kind == "playlist" || it.kind == "artist" } '
    'else allRows')
one(shell,
    '''Box(Modifier.clip(CircleShape).background(if (chip == "All") mobileGreen else Color(0xFF252525))
                            .clickable { onNavigate("/") }.padding(horizontal = 17.dp, vertical = 7.dp)) {
                            Text(chip, fontSize = 13.sp, color = if(chip == "All") Color.Black else Color.White)''',
    '''Box(Modifier.clip(CircleShape).background(if ((chip == "Music") == musicOnly) mobileGreen else Color(0xFF252525))
                            .clickable { musicOnly = chip == "Music" }.padding(horizontal = 17.dp, vertical = 7.dp)) {
                            Text(chip, fontSize = 13.sp, color = if ((chip == "Music") == musicOnly) Color.Black else Color.White)''')
one(shell,
    '''            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),''',
    '''            Crossfade(targetState = browse.path + ":" + tab, animationSpec = tween(240),
                label = "nativeScreen", modifier = Modifier.weight(1f)) { _ ->
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),''')
one(shell,
    '''            }
            if(track.title.isNotBlank()) {''',
    '''            }
            }
            if(track.title.isNotBlank()) {''')
one(shell,
    '                items(rows, key = { it.path + "|" + it.title }) { row ->',
    '''                if (tab == "Home" && !detail) {
                    items(rows.chunked(2)) { pair ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { entry ->
                                Row(
                                    Modifier.weight(1f).height(62.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF282828))
                                        .clickable {
                                            returnPath = browse.path
                                            onNavigate(entry.path)
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MobileArt(entry.image, Modifier.size(62.dp))
                                    Text(entry.title, modifier = Modifier.padding(horizontal = 7.dp),
                                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if(pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                items(rows, key = { it.path + "|" + it.title }) { row ->''')
one(shell,
    '''                }
            }
            }
            if(track.title.isNotBlank()) {''',
    '''                }
                }
            }
            }
            if(track.title.isNotBlank()) {''')
one(shell,
    '''                        Text("⋮", fontSize = 25.sp, color = mobileMuted,
                            modifier = Modifier.padding(horizontal = 9.dp))''',
    '''                        Spacer(Modifier.width(12.dp))''')
one(shell,
    '''                        Text("⋮", color = Color.White, fontSize = 27.sp)''',
    '''                        Text("⋮", color = Color.White, fontSize = 27.sp,
                            modifier = Modifier.clickable(onClick = onOpenWeb))''')
# The fraction calculation is duplicated in the mini/full player.
p=root/shell
s=p.read_text(encoding="utf-8")
assert s.count("track.position.toFloat() / track.duration)")==2
p.write_text(s.replace("track.position.toFloat() / track.duration)","track.position.toFloat() / track.duration.toFloat())",2),encoding="utf-8")
js="ui/components/NativeMobileJs.kt"
one(js,
    "if(!p||seen[p]||kind(p)==='track')continue;",
    "if(!p||seen[p]||(kind(p)==='track' && location.pathname==='/'))continue;")
# Native mode must not show the old floating quick-access menu over the mobile toolbar.
one("ui/MainActivity.kt",
    "if (hideTopBar && !pipFilling) {",
    "if (hideTopBar && !nativeMobileActive && !pipFilling) {")

gradle=Path("app/build.gradle.kts")
s=gradle.read_text(encoding="utf-8")
for a,b in [("versionCode = 38","versionCode = 39"),
 ('versionName = "1.2.0-vivo-hybrid8-native-ui"','versionName = "1.2.0-vivo-hybrid9-native-ui"')]:
    assert s.count(a)==1,a
    s=s.replace(a,b,1)
gradle.write_text(s,encoding="utf-8")
print("Hybrid9: animated screens, working Music chip, search tracks and fewer dummy controls")
