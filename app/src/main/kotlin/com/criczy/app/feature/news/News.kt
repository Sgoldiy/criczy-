package com.criczy.app.feature.news

import com.criczy.app.ui.components.listPaddingAboveBar
import com.criczy.app.ads.AdUnit
import com.criczy.app.ads.AdsPolicy
import com.criczy.app.ads.LocalAds
import com.criczy.app.ads.NativeAdSlot
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.criczy.app.core.data.FavouriteType
import com.criczy.app.ui.WhileUiVisible
import com.criczy.app.ui.components.NewsRow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.criczy.app.core.data.Loadable
import com.criczy.app.core.data.loadOnce
import com.criczy.app.core.model.NewsArticle
import com.criczy.app.core.model.VideoClip
import com.criczy.app.core.network.CricketApiException
import com.criczy.app.di.AppContainer
import com.criczy.app.ui.LocalAppContainer
import com.criczy.app.ui.Refreshable
import com.criczy.app.ui.components.EmptyState
import com.criczy.app.ui.components.ErrorState
import com.criczy.app.ui.components.Panel
import com.criczy.app.ui.components.Pill
import com.criczy.app.ui.components.SkeletonList
import com.criczy.app.ui.format.Formatters
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.StateFlow
import com.criczy.app.ui.components.AppTitle
import androidx.compose.runtime.getValue

data class NewsFeed(val articles: List<NewsArticle>, val videos: List<VideoClip>)

class NewsViewModel(private val container: AppContainer) : ViewModel() {
    private val source = Refreshable(viewModelScope) {
        loadOnce {
            val articles = container.news.news()
            // Videos are a bonus strip; the page is still useful without them.
            val videos = try {
                container.news.homeFeed().videos.filter { it.url != null }
            } catch (_: CricketApiException) {
                emptyList()
            }
            NewsFeed(articles, videos)
        }
    }
    val state: StateFlow<Loadable<NewsFeed>> = source.state

    /** Followed teams and series, for the Following filter. */
    val follows: StateFlow<NewsFollows> = container.favourites.all.map { all ->
        NewsFollows(
            teams = all.filter { it.type == FavouriteType.TEAM }.mapTo(HashSet()) { it.id },
            competitions = all.filter { it.type == FavouriteType.COMPETITION }.mapTo(HashSet()) { it.id },
        )
    }.stateIn(viewModelScope, WhileUiVisible, NewsFollows())

    fun refresh() = source.refresh()
}

data class NewsFollows(val teams: Set<Int> = emptySet(), val competitions: Set<Int> = emptySet()) {
    val isEmpty: Boolean get() = teams.isEmpty() && competitions.isEmpty()
}

/** Narrows the news to match reports, or to stories tagged with a followed team or series. */
enum class NewsFilter(val label: String) {
    ALL("All"), MATCH_REPORTS("Match reports"), FOLLOWING("Following");

    fun accepts(article: NewsArticle, follows: NewsFollows): Boolean = when (this) {
        ALL -> true
        MATCH_REPORTS -> article.isMatchReport
        FOLLOWING -> article.teamIds.any { it in follows.teams } || article.competitionIds.any { it in follows.competitions }
    }
}

@Composable
fun NewsRoute(onOpenUrl: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: NewsViewModel = viewModel { NewsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val follows by vm.follows.collectAsStateWithLifecycle()
    NewsScreen(state, onOpenUrl, vm::refresh, follows)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(state: Loadable<NewsFeed>, onOpenUrl: (String) -> Unit, onRefresh: () -> Unit, follows: NewsFollows = NewsFollows()) {
    var filter by rememberSaveable { mutableStateOf(NewsFilter.ALL) }
    Scaffold(topBar = { TopAppBar(title = { AppTitle("News") }) }) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && state.data != null, onRefresh = onRefresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            val feed = state.data
            when {
                state.isInitialLoad -> SkeletonList(itemHeight = 220.dp, count = 3)
                feed == null -> ErrorState(state.error!!, onRefresh)
                feed.articles.isEmpty() -> EmptyState("No news right now", "Pull down to check again.")
                else -> {
                    // Following is offered once something is followed.
                    val filters = NewsFilter.entries.filter { it != NewsFilter.FOLLOWING || !follows.isEmpty }
                    val active = filter.takeIf { it in filters } ?: NewsFilter.ALL
                    val articles = feed.articles.filter { active.accepts(it, follows) }
                    val adState by LocalAds.current.state.collectAsState()
                    val adsAfter = if (adState.shows(AdUnit.NATIVE_NEWS)) AdsPolicy.nativePositions(articles.size, adState.config.unit(AdUnit.NATIVE_NEWS)).toSet() else emptySet()
                    LazyVerticalGrid(
                        GridCells.Adaptive(330.dp), contentPadding = listPaddingAboveBar(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                filters.forEach { f ->
                                    val count = if (f == NewsFilter.ALL) null else feed.articles.count { f.accepts(it, follows) }
                                    FilterChip(active == f, onClick = { filter = f }, label = { Text(count?.let { "${f.label} · $it" } ?: f.label) })
                                }
                            }
                        }
                        if (feed.videos.isNotEmpty() && active == NewsFilter.ALL) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "videos") {
                                Column {
                                    Text("VIDEOS", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(feed.videos, key = { it.id }) { video -> VideoCard(video) { onOpenUrl(video.url!!) } }
                                    }
                                }
                            }
                        }
                        if (articles.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "none") {
                                Text(
                                    if (active == NewsFilter.FOLLOWING) "No stories about the teams and series you follow right now." else "No stories here right now.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        }
                        // The lead story large; the rest as compact rows that are quick to scan.
                        articles.firstOrNull()?.let { lead ->
                            item(span = { GridItemSpan(maxLineSpan) }, key = "lead-${lead.id}") { ArticleCard(lead) { onOpenUrl(lead.url) } }
                        }
                        // Native ads between stories (positions from the ad configuration), counting the lead story.
                        articles.drop(1).forEachIndexed { index, article ->
                            item(key = article.id) { NewsRow(article, onClick = { onOpenUrl(article.url) }) }
                            val shown = index + 2
                            if (shown in adsAfter) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "ad-news-$shown") { NativeAdSlot(AdUnit.NATIVE_NEWS, "news-$shown") }
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "source") {
                            Text("Stories open on cricket.com.au.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleCard(article: NewsArticle, onClick: () -> Unit) {
    Panel(Modifier.clickable(onClickLabel = "Read story", onClick = onClick)) {
        Column {
            if (article.imageUrl != null) {
                AsyncImage(
                    model = article.imageUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(MaterialTheme.shapes.medium),
                )
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (article.isMatchReport) Pill("MATCH REPORT")
                    Formatters.ago(article.published)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text(article.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                article.summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun VideoCard(video: VideoClip, onClick: () -> Unit) {
    Column(Modifier.width(220.dp).clickable(onClickLabel = "Watch video", onClick = onClick)) {
        Box {
            AsyncImage(
                model = video.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f).clip(MaterialTheme.shapes.small),
            )
            Icon(
                Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(36.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(6.dp),
            )
            Formatters.duration(video.durationSeconds)?.let { Pill(it, Modifier.align(Alignment.BottomEnd).padding(6.dp)) }
        }
        Text(video.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}
