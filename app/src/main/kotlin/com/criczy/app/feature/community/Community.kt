package com.criczy.app.feature.community

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.criczy.app.core.data.Communities
import com.criczy.app.core.data.CommunityPost
import com.criczy.app.core.data.CommunityProfile
import com.criczy.app.core.data.CommunityRuleException
import com.criczy.app.core.data.DailyPostLimitException
import com.criczy.app.core.data.FavouriteType
import com.criczy.app.core.data.PostDraft
import com.criczy.app.core.data.MatchPolls
import com.criczy.app.core.model.Match
import com.criczy.app.core.model.MatchState
import com.criczy.app.ui.components.MatchCard
import com.criczy.app.ui.latestBoard
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import com.criczy.app.core.data.PostKind
import com.criczy.app.core.data.UsernameTakenException
import com.criczy.app.di.AppContainer
import com.criczy.app.ui.LocalAppContainer
import com.criczy.app.ui.WhileUiVisible
import com.criczy.app.ui.components.Badge
import com.criczy.app.ui.components.MessageState
import com.criczy.app.ui.components.bottomBarPadding
import com.criczy.app.ui.theme.cricketColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A feed: all fans, or one followed team. */
data class CommunitySpace(val id: String, val name: String, val shortName: String, val logoUrl: String? = null, val colorHex: String? = null)

enum class CommunityPhase { LOADING, UNAVAILABLE, NEEDS_USERNAME, READY, ERROR }

data class CommunityUiState(
    val phase: CommunityPhase = CommunityPhase.LOADING,
    val profile: CommunityProfile? = null,
    val spaces: List<CommunitySpace> = listOf(ALL_FANS),
    val selected: String = Communities.ALL,
    val posts: List<CommunityPost> = emptyList(),
    val refreshing: Boolean = false,
    val busy: Boolean = false,
    /** A problem to show: a refused username or post, or a failed load. */
    val message: String? = null,
    /** Bumped after a successful post so the composer closes and clears. */
    val posted: Int = 0,
    /** The selected team's match in play, or starting within a day, shown above its feed. */
    val featured: Match? = null,
    val now: Long = System.currentTimeMillis(),
)

val ALL_FANS = CommunitySpace(Communities.ALL, "All fans", "ALL")

class CommunityActions(
    val onJoin: (String) -> Unit = {},
    val onSelect: (String) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onPost: (PostDraft) -> Unit = {},
    val onVote: (CommunityPost, Int) -> Unit = { _, _ -> },
    val onReport: (CommunityPost) -> Unit = {},
    val onBlock: (CommunityPost) -> Unit = {},
    val onDelete: (CommunityPost) -> Unit = {},
    val onDismissMessage: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMatch: (Int) -> Unit = {},
)

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.community
    private val local = MutableStateFlow(CommunityUiState(phase = if (repo.isAvailable) CommunityPhase.LOADING else CommunityPhase.UNAVAILABLE))
    private val selected = MutableStateFlow(Communities.ALL)
    private val fetched = HashSet<String>()

    private val spaces = container.favourites.all.map { favourites ->
        listOf(ALL_FANS) + favourites.filter { it.type == FavouriteType.TEAM }.take(Communities.MAX_TEAMS).map {
            CommunitySpace(Communities.team(it.id), it.title, it.subtitle ?: it.title, it.imageUrl, it.colorHex)
        }
    }

    private val board = container.matches.latestBoard()

    val state: StateFlow<CommunityUiState> = combine(
        combine(local, repo.profile, spaces, selected, selected.flatMapLatest { repo.feed(it) }) { base, profile, spaces, chosen, posts ->
            val current = chosen.takeIf { id -> spaces.any { it.id == id } } ?: Communities.ALL
            base.copy(profile = profile, spaces = spaces, selected = current, posts = posts, now = System.currentTimeMillis())
        },
        board,
    ) { s, b -> s.copy(featured = Communities.teamId(s.selected)?.let { MatchPolls.featured(it, b.all, Instant.now()) }) }
        .stateIn(viewModelScope, WhileUiVisible, local.value)

    init { connect() }

    fun connect() {
        if (!repo.isAvailable) return
        viewModelScope.launch {
            local.update { it.copy(phase = CommunityPhase.LOADING, message = null) }
            try {
                val profile = repo.connect()
                local.update { it.copy(phase = if (profile == null) CommunityPhase.NEEDS_USERNAME else CommunityPhase.READY) }
                if (profile != null) load(selected.value, full = false)
            } catch (e: Exception) {
                local.update { it.copy(phase = CommunityPhase.ERROR, message = "Could not reach the community. Check your connection.") }
            }
        }
    }

    fun join(name: String) = viewModelScope.launch {
        local.update { it.copy(busy = true, message = null) }
        try {
            repo.claimUsername(name)
            local.update { it.copy(phase = CommunityPhase.READY, busy = false) }
            load(selected.value, full = false)
        } catch (e: UsernameTakenException) {
            local.update { it.copy(busy = false, message = "@$name is taken. Try another.") }
        } catch (e: CommunityRuleException) {
            local.update { it.copy(busy = false, message = e.message) }
        } catch (e: Exception) {
            local.update { it.copy(busy = false, message = "Could not join. Check your connection.") }
        }
    }

    fun select(id: String) {
        selected.value = id
        if (local.value.phase == CommunityPhase.READY && id !in fetched) viewModelScope.launch { load(id, full = false) }
    }

    fun refresh() = viewModelScope.launch { load(state.value.selected, full = true) }

    private suspend fun load(community: String, full: Boolean) {
        local.update { it.copy(refreshing = true) }
        try {
            repo.refresh(community, full)
            fetched += community
            local.update { it.copy(refreshing = false) }
        } catch (e: Exception) {
            local.update { it.copy(refreshing = false, message = "Could not load new posts.") }
            return
        }
        openMatchPolls(community)
    }

    /** Opens "Who wins?" and "Player of the match?" polls for the team's matches; quiet on failure. */
    private suspend fun openMatchPolls(community: String) {
        val teamId = Communities.teamId(community) ?: return
        try {
            val now = Instant.now()
            val matches = board.first().all
            val polls = MatchPolls.winPolls(teamId, matches, now) +
                MatchPolls.recentlyFinished(teamId, matches, now).take(2)
                    .filter { !repo.hasPost("match-${it.id}-pom-$community") }
                    .mapNotNull { m -> container.matches.detail(m.id)?.let { MatchPolls.playerOfTheMatch(teamId, it) } }
            repo.ensureMatchPolls(polls)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Polls are extras; the feed works without them.
        }
    }

    fun post(draft: PostDraft) = viewModelScope.launch {
        local.update { it.copy(busy = true, message = null) }
        try {
            repo.publish(draft)
            local.update { it.copy(busy = false, posted = it.posted + 1) }
        } catch (e: DailyPostLimitException) {
            local.update { it.copy(busy = false, message = "You have already posted today. ${nextPostText(e.nextPostAt, System.currentTimeMillis())}") }
        } catch (e: CommunityRuleException) {
            local.update { it.copy(busy = false, message = e.message) }
        } catch (e: Exception) {
            local.update { it.copy(busy = false, message = "Could not post. Check your connection.") }
        }
    }

    fun vote(post: CommunityPost, choice: Int) = guarded("Could not save your vote.") { repo.vote(post, choice) }
    fun report(post: CommunityPost) = guarded("Could not send the report.") { repo.report(post) }
    fun block(post: CommunityPost) = guarded("Could not block.") { repo.block(post) }
    fun delete(post: CommunityPost) = guarded("Could not delete the post.") { repo.delete(post) }
    fun dismissMessage() = local.update { it.copy(message = null) }

    private fun guarded(failure: String, block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch (e: Exception) { local.update { it.copy(message = failure) } }
    }
}

@Composable
fun CommunityRoute(onMatch: (Int) -> Unit = {}) {
    val container = LocalAppContainer.current
    val vm: CommunityViewModel = viewModel { CommunityViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    CommunityContent(
        state,
        CommunityActions(
            onJoin = { vm.join(it) }, onSelect = vm::select, onRefresh = { vm.refresh() }, onPost = { vm.post(it) },
            onVote = { p, c -> vm.vote(p, c) }, onReport = { vm.report(it) }, onBlock = { vm.block(it) }, onDelete = { vm.delete(it) },
            onDismissMessage = vm::dismissMessage, onRetry = vm::connect, onMatch = onMatch,
        ),
    )
}

@Composable
fun CommunityContent(state: CommunityUiState, actions: CommunityActions) {
    when (state.phase) {
        CommunityPhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        CommunityPhase.UNAVAILABLE -> MessageState(
            Icons.Outlined.CloudOff, "Community unavailable",
            "This build of Criczy is not connected to the fan community.",
        )
        CommunityPhase.ERROR -> MessageState(
            Icons.Outlined.CloudOff, "Community offline", state.message ?: "Could not reach the community.",
            actionLabel = "Try again", onAction = actions.onRetry,
        )
        CommunityPhase.NEEDS_USERNAME -> JoinCommunity(state, actions)
        CommunityPhase.READY -> CommunityFeed(state, actions)
    }
}

@Composable
private fun JoinCommunity(state: CommunityUiState, actions: CommunityActions) {
    var name by rememberSaveable { mutableStateOf("") }
    var agreed by rememberSaveable { mutableStateOf(false) }
    val problem = name.takeIf { it.isNotEmpty() }?.let { Communities.usernameProblem(it.trim()) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 24.dp, bottom = bottomBarPadding(24.dp)),
    ) {
        Icon(Icons.Outlined.Groups, contentDescription = null, tint = cricketColors.accent, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("Join the fan community", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        listOf(
            "Only a username. No email, phone number or password.",
            "A community for each of your favourite teams (up to 5), plus one for all fans.",
            "One post or poll a day. Agree, disagree and vote as much as you like.",
            "Everything disappears after 24 hours.",
            "Be respectful. Report or block anything abusive.",
        ).forEach { line ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("•", color = cricketColors.accent, modifier = Modifier.width(16.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.filter { c -> !c.isWhitespace() }.take(20) },
            label = { Text("Username") },
            prefix = { Text("@") },
            singleLine = true,
            isError = problem != null,
            supportingText = { Text(problem ?: state.message ?: "3 to 20 letters, numbers or _. It cannot be changed.") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text("Community rules", style = MaterialTheme.typography.titleSmall)
        Communities.RULES.forEachIndexed { index, rule ->
            Text("${index + 1}. $rule", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).toggleable(value = agreed, role = Role.Checkbox) { agreed = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = agreed, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            Text("I agree to the community rules", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { actions.onJoin(name.trim()) },
            enabled = !state.busy && name.isNotBlank() && problem == null && agreed,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.busy) "Joining…" else "Join") }
    }
}

@Composable
private fun CommunityFeed(state: CommunityUiState, actions: CommunityActions) {
    val space = state.spaces.firstOrNull { it.id == state.selected } ?: ALL_FANS
    LazyColumn(contentPadding = PaddingValues(bottom = bottomBarPadding(24.dp))) {
        item(key = "spaces") { SpaceChips(state, actions.onSelect) }
        state.featured?.let { match ->
            item(key = "featured-${match.id}") {
                MatchCard(match, onClick = { actions.onMatch(match.id) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), isFavourite = true)
            }
        }
        item(key = "composer") { Composer(state, space, actions) }
        state.message?.let { message ->
            item(key = "message") { Notice(message, actions.onDismissMessage) }
        }
        if (state.posts.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (state.refreshing) "Loading posts…" else "No posts in ${space.name} in the last 24 hours. Start the conversation.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(state.posts, key = { it.id }) { post ->
                PostCard(post, isMine = post.authorId == state.profile?.uid, now = state.now, actions = actions)
            }
        }
    }
}

@Composable
private fun SpaceChips(state: CommunityUiState, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        state.spaces.forEach { space ->
            FilterChip(
                selected = space.id == state.selected,
                onClick = { onSelect(space.id) },
                label = { Text(space.name, maxLines = 1) },
                leadingIcon = {
                    if (space.id == Communities.ALL) Icon(Icons.Outlined.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                    else Badge(space.shortName, space.logoUrl, space.colorHex, size = 20.dp)
                },
            )
        }
    }
}

@Composable
private fun Composer(state: CommunityUiState, space: CommunitySpace, actions: CommunityActions) {
    val profile = state.profile ?: return
    var open by rememberSaveable(state.posted) { mutableStateOf(false) }
    var kind by rememberSaveable(state.posted) { mutableStateOf(PostKind.OPINION) }
    var text by rememberSaveable(state.posted) { mutableStateOf("") }
    val options = remember(state.posted) { mutableStateListOf("", "") }
    val live = state.featured?.takeIf { it.state == MatchState.LIVE || it.state == MatchState.IN_PROGRESS }
    // After the daily post, one extra post is allowed while the team's match is in play.
    val matchPost = !profile.canPost(state.now) && live != null && profile.canMatchPost(state.now)
    val canPost = profile.canPost(state.now) || matchPost

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile.username, 32)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("@${profile.username}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            matchPost -> "Match post: one extra post during ${live!!.title}"
                            canPost -> "Your post for today goes to ${space.name}"
                            else -> nextPostText(profile.nextPostAt(), state.now)
                        },
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canPost && !open) {
                    Button(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 14.dp)) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Post")
                    }
                } else if (open) {
                    IconButton(onClick = { open = false }) { Icon(Icons.Outlined.Close, contentDescription = "Close composer") }
                } else {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = actions.onRefresh, enabled = !state.refreshing) { Icon(Icons.Outlined.Refresh, contentDescription = "Refresh posts") }
            }
            if (open && canPost) {
                Spacer(Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(PostKind.OPINION to "Opinion", PostKind.POLL to "Poll").forEachIndexed { index, (value, label) ->
                        SegmentedButton(selected = kind == value, onClick = { kind = value }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(Communities.MAX_TEXT) },
                    label = { Text(if (kind == PostKind.POLL) "Question" else "Your take") },
                    supportingText = { Text("${text.length}/${Communities.MAX_TEXT}") },
                    minLines = if (kind == PostKind.POLL) 1 else 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == PostKind.POLL) {
                    options.forEachIndexed { index, option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = option, onValueChange = { options[index] = it.replace('\n', ' ').take(Communities.MAX_OPTION) },
                                label = { Text("Option ${index + 1}") }, singleLine = true, modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                            )
                            if (options.size > 2) IconButton(onClick = { options.removeAt(index) }) { Icon(Icons.Outlined.Close, contentDescription = "Remove option ${index + 1}") }
                        }
                    }
                    if (options.size < 4) TextButton(onClick = { options.add("") }) { Icon(Icons.Outlined.Add, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("Add option") }
                }
                val draft = PostDraft(space.id, text, kind, if (kind == PostKind.POLL) options.toList() else emptyList(), matchId = live?.id)
                val problem = if (text.isBlank()) null else Communities.draftProblem(draft)
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "One post a day. It disappears after 24 hours.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { actions.onPost(draft) }, enabled = !state.busy && text.isNotBlank() && problem == null) {
                        Text(if (state.busy) "Posting…" else "Post to ${space.shortName}")
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer) }
    }
}

@Composable
private fun PostCard(post: CommunityPost, isMine: Boolean, now: Long, actions: CommunityActions) {
    var menu by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (post.isSystem) Badge("CZ", null, "#FFB300", size = 28.dp) else Avatar(post.author, 28)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (post.isSystem) "Criczy · Match poll" else "@${post.author}" + if (isMine) " · you" else "",
                        style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${ago(post.createdAt, now)} · ${if (post.kind == PostKind.POLL) "Poll" else "Opinion"} · ${left(post.expiresAt, now)}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!post.isSystem) Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More for @${post.author}'s post") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (isMine) {
                            DropdownMenuItem(text = { Text("Delete post") }, onClick = { menu = false; actions.onDelete(post) })
                        } else {
                            DropdownMenuItem(text = { Text("Report post") }, onClick = { menu = false; actions.onReport(post) })
                            DropdownMenuItem(text = { Text("Block @${post.author}") }, onClick = { menu = false; actions.onBlock(post) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(post.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 10.dp))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.padding(end = 10.dp)) {
                if (post.kind == PostKind.POLL) PollOptions(post) { actions.onVote(post, it) } else AgreeDisagree(post) { actions.onVote(post, it) }
            }
        }
    }
}

@Composable
private fun AgreeDisagree(post: CommunityPost, onVote: (Int) -> Unit) {
    val agree = post.counts.getOrElse(0) { 0 }
    val disagree = post.counts.getOrElse(1) { 0 }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(0, "Agree", agree), Triple(1, "Disagree", disagree)).forEach { (index, label, count) ->
                val chosen = post.myVote == index
                OutlinedButton(
                    onClick = { onVote(index) },
                    enabled = post.myVote == null,
                    border = BorderStroke(1.dp, if (chosen) cricketColors.accent else MaterialTheme.colorScheme.outlineVariant),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.semantics { contentDescription = "$label, $count" + if (chosen) ", your vote" else "" },
                ) {
                    Icon(if (index == 0) Icons.Outlined.ThumbUp else Icons.Outlined.ThumbDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (post.myVote == null) label else "$label $count", fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        if (post.myVote != null && post.totalVotes > 0) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (agree > 0) Box(Modifier.weight(agree.toFloat()).fillMaxHeight().background(cricketColors.accent))
                if (disagree > 0) Box(Modifier.weight(disagree.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
            }
            Text("${percent(agree, post.totalVotes)}% agree · ${votesText(post.totalVotes)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun PollOptions(post: CommunityPost, onVote: (Int) -> Unit) {
    val voted = post.myVote != null
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        post.options.forEachIndexed { index, option ->
            val count = post.counts.getOrElse(index) { 0 }
            val share = if (post.totalVotes == 0) 0f else count.toFloat() / post.totalVotes
            val chosen = post.myVote == index
            Box(
                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable(enabled = !voted, onClickLabel = "Vote $option") { onVote(index) },
            ) {
                if (voted && share > 0f) {
                    Box(Modifier.fillMaxWidth(share).fillMaxHeight().background(if (chosen) cricketColors.accent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant))
                }
                Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(option, style = MaterialTheme.typography.bodyMedium, fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (chosen) { Icon(Icons.Filled.CheckCircle, contentDescription = "Your vote", tint = cricketColors.accent, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                    if (voted) Text("${percent(count, post.totalVotes)}%", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Text(
            if (voted) votesText(post.totalVotes) else "Tap an option to vote · ${votesText(post.totalVotes)}",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Avatar(name: String, size: Int) {
    val palette = listOf(0xFF1E88E5, 0xFF43A047, 0xFFE53935, 0xFF8E24AA, 0xFFFB8C00, 0xFF00897B, 0xFF3949AB, 0xFFD81B60)
    val color = palette[Math.floorMod(name.lowercase().hashCode(), palette.size)]
    Badge(name, null, "#%06X".format(color and 0xFFFFFF), size = size.dp)
}

internal fun percent(count: Int, total: Int): Int = if (total == 0) 0 else Math.round(count * 100f / total)

internal fun votesText(total: Int): String = if (total == 1) "1 vote" else "$total votes"

/** "now", "5m ago", "3h ago". */
internal fun ago(time: Long, now: Long): String {
    val minutes = ((now - time) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m ago"
        else -> "${minutes / 60}h ago"
    }
}

/** Time until a post disappears: "23h left", "40m left". */
internal fun left(expiresAt: Long, now: Long): String {
    val minutes = ((expiresAt - now) / 60_000).coerceAtLeast(0)
    return if (minutes >= 60) "${minutes / 60}h left" else "${minutes}m left"
}

internal fun nextPostText(nextPostAt: Long?, now: Long): String {
    if (nextPostAt == null || nextPostAt <= now) return "You can post now"
    val minutes = (nextPostAt - now + 59_999) / 60_000
    return if (minutes >= 60) "Next post in ${minutes / 60}h ${minutes % 60}m" else "Next post in ${minutes}m"
}
