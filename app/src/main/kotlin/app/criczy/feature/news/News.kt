package app.criczy.feature.news

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.getValue
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
import app.criczy.core.data.Loadable
import app.criczy.core.data.loadOnce
import app.criczy.core.model.NewsArticle
import app.criczy.core.model.VideoClip
import app.criczy.core.network.CricketApiException
import app.criczy.di.AppContainer
import app.criczy.ui.LocalAppContainer
import app.criczy.ui.Refreshable
import app.criczy.ui.components.EmptyState
import app.criczy.ui.components.ErrorState
import app.criczy.ui.components.Panel
import app.criczy.ui.components.Pill
import app.criczy.ui.components.SkeletonList
import app.criczy.ui.format.Formatters
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.StateFlow

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
    fun refresh() = source.refresh()
}

@Composable
fun NewsRoute(onOpenUrl: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: NewsViewModel = viewModel { NewsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    NewsScreen(state, onOpenUrl, vm::refresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(state: Loadable<NewsFeed>, onOpenUrl: (String) -> Unit, onRefresh: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("NEWS", style = MaterialTheme.typography.headlineSmall) }) }) { padding ->
        PullToRefreshBox(isRefreshing = state.loading && state.data != null, onRefresh = onRefresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            val feed = state.data
            when {
                state.isInitialLoad -> SkeletonList(itemHeight = 220.dp, count = 3)
                feed == null -> ErrorState(state.error!!, onRefresh)
                feed.articles.isEmpty() -> EmptyState("No news right now", "Pull down to check again.")
                else -> LazyVerticalGrid(
                    GridCells.Adaptive(330.dp), contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (feed.videos.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text("VIDEOS", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(feed.videos, key = { it.id }) { video -> VideoCard(video) { onOpenUrl(video.url!!) } }
                                }
                            }
                        }
                    }
                    items(feed.articles, key = { it.id }) { article -> ArticleCard(article) { onOpenUrl(article.url) } }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("Stories open on cricket.com.au.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Formatters.duration(video.durationSeconds)?.let { Pill(it, Modifier.align(Alignment.BottomEnd).padding(6.dp)) }
        }
        Text(video.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}
