package com.narvive.app.ui.screen.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.narvive.app.R
import com.narvive.app.service.ai.GraphEdge
import com.narvive.app.service.ai.GraphNode
import com.narvive.app.service.ai.RelationshipGraph
import com.narvive.app.service.ai.TimelineEvent
import com.narvive.app.service.ai.TimelineStage
import com.narvive.app.ui.theme.NarviveShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 关系图节点分类色（数据可视化固定色，首色由主题 primary 提供） */
private val GraphNodeCategoryColors = listOf(
    Color(0xFFDB2777),
    Color(0xFF7C3AED),
    Color(0xFF059669),
    Color(0xFFD97706),
    Color(0xFF475569),
)

/** 图表弹窗：人物关系图 / 时间轴演进图 */
@Composable
fun GraphSheetDialog(
    state: GraphSheet?,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    if (state == null && !loading) return
    val title = when (state) {
        is GraphSheet.Relationship -> stringResource(R.string.chat_graph_relationship_title)
        is GraphSheet.TimelineGraph -> stringResource(R.string.chat_graph_timeline_title)
        else -> stringResource(R.string.chat_graph_generating_title)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = NarviveShape.Lg,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.chat_action_close)) }
                }
                Spacer(Modifier.height(8.dp))
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.chat_graph_generating), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    state is GraphSheet.Relationship -> RelationshipGraphView(state.graph, Modifier.fillMaxSize())
                    state is GraphSheet.TimelineGraph -> TimelineView(state.stages, Modifier.fillMaxSize())
                    state is GraphSheet.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}

/** 人物关系图：放射状布局 + Canvas 连线 + 关系标签 + 缩放/拖拽 + 点节点高亮 + 底部固定高度图例 */
@Composable
private fun RelationshipGraphView(graph: RelationshipGraph, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var selected by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    val degree = mutableMapOf<String, Int>()
    graph.edges.forEach { e ->
        degree[e.source] = (degree[e.source] ?: 0) + 1
        degree[e.target] = (degree[e.target] ?: 0) + 1
    }
    val central = graph.nodes.maxByOrNull { degree[it.name] ?: 0 } ?: graph.nodes.first()
    val selectedNode = graph.nodes.firstOrNull { it.name == selected }

    fun isActive(nodeName: String): Boolean = when {
        selected == null -> true
        selected == nodeName -> true
        else -> graph.edges.any { (it.source == selected && it.target == nodeName) || (it.target == selected && it.source == nodeName) }
    }

    Column(modifier) {
        selectedNode?.takeIf { it.description.isNotBlank() }?.let { n ->
            val detail = if (n.category.isNotBlank()) {
                stringResource(R.string.chat_graph_node_detail_with_category, n.name, n.category, n.description)
            } else {
                stringResource(R.string.chat_graph_node_detail, n.name, n.description)
            }
            Surface(
                shape = NarviveShape.Md,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f).clip(NarviveShape.Md)
                .pointerInput(Unit) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        scale = (scale * zoomChange).coerceIn(0.5f, 3f)
                        pan += panChange
                    }
                },
        ) {
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            val cx = wPx / 2f
            val cy = hPx / 2f
            val radius = minOf(wPx, hPx) * 0.36f
            val nodeCenterPx = with(density) { 32.dp.toPx() }

            val positions = mutableMapOf<String, Offset>()
            positions[central.name] = Offset(cx, cy)
            val others = graph.nodes.filter { it.name != central.name }
            others.forEachIndexed { i, node ->
                val angle = (2.0 * PI * i / others.size).toFloat()
                positions[node.name] = Offset(cx + radius * cos(angle), cy + radius * sin(angle))
            }

            Box(Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = pan.x; translationY = pan.y
            }) {
                val edgeActiveColor = MaterialTheme.colorScheme.primary
                val edgeInactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                Canvas(Modifier.fillMaxSize()) {
                    graph.edges.forEach { e ->
                        val s = positions[e.source] ?: return@forEach
                        val t = positions[e.target] ?: return@forEach
                        val active = selected == null || selected == e.source || selected == e.target
                        drawLine(
                            color = if (active) edgeActiveColor else edgeInactiveColor,
                            start = s, end = t,
                            strokeWidth = if (active) 2.5.dp.toPx() else 1.5.dp.toPx(),
                        )
                    }
                }
                // 关系标签（线中点）
                graph.edges.forEach { e ->
                    val s = positions[e.source] ?: return@forEach
                    val t = positions[e.target] ?: return@forEach
                    if (e.relation.isNotBlank()) {
                        val mx = (s.x + t.x) / 2f
                        val my = (s.y + t.y) / 2f
                        Surface(
                            shape = NarviveShape.Xs,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.offset {
                                IntOffset(mx.roundToInt() - with(density) { 14.dp.toPx().roundToInt() }, my.roundToInt() - with(density) { 8.dp.toPx().roundToInt() })
                            },
                        ) {
                            Text(
                                shortRelation(e.relation),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                // 节点（圆形，自动缩字号显示全名）
                graph.nodes.forEach { node ->
                    val p = positions[node.name] ?: return@forEach
                    val active = isActive(node.name)
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((p.x - nodeCenterPx).roundToInt(), (p.y - nodeCenterPx).roundToInt()) }
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(nodeColor(node, MaterialTheme.colorScheme.primary))
                            .alpha(if (active) 1f else 0.3f)
                            .clickable { selected = if (selected == node.name) null else node.name },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            node.name,
                            color = Color.White,
                            fontSize = nodeFontSize(node.name),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }
            }
        }
        if (graph.edges.isNotEmpty()) {
            Surface(
                shape = NarviveShape.Md,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(120.dp),
            ) {
                Column {
                    Text(
                        stringResource(R.string.chat_graph_relation_list),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(graph.edges) { e ->
                            val edgeText = if (e.relation.isNotBlank()) {
                                stringResource(R.string.chat_graph_edge_with_relation, e.source, e.target, e.relation)
                            } else {
                                stringResource(R.string.chat_graph_edge, e.source, e.target)
                            }
                            Text(
                                edgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun nodeFontSize(name: String): TextUnit = when {
    name.length <= 2 -> 15.sp
    name.length <= 4 -> 13.sp
    name.length <= 6 -> 11.sp
    else -> 9.sp
}

/** 关系线上的简短关系：去掉括号补充部分（如「父子（养父）」→「父子」） */
private fun shortRelation(relation: String): String {
    val short = relation.substringBefore("（").substringBefore("(").trim()
    return short.ifBlank { relation }
}

private fun nodeColor(node: GraphNode, primary: Color): Color {
    val palette = listOf(primary) + GraphNodeCategoryColors
    return palette[(node.name.hashCode().and(Int.MAX_VALUE)) % palette.size]
}

/** 时间轴演进图：按阶段（章节/卷/时间阶段）分组，组内竖向时间轴（圆点 + 竖线 + 时间 + 标题 + 描述，点击展开） */
@Composable
private fun TimelineView(stages: List<TimelineStage>, modifier: Modifier = Modifier) {
    LazyColumn(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
        stages.forEachIndexed { si, stage ->
            // 阶段标题
            if (stage.label.isNotBlank()) {
                item(key = "stage-$si") {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(8.dp))
                        Text(stage.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            items(stage.events.size, key = { "stage-$si-event-$it" }) { i ->
                val e = stage.events[i]
                val isLastEvent = i == stage.events.lastIndex && si == stages.lastIndex
                var expanded by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                    Column(Modifier.width(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        if (!isLastEvent) {
                            Box(
                                Modifier.width(2.dp).height(72.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).padding(bottom = if (isLastEvent) 0.dp else 16.dp)) {
                        if (e.time.isNotBlank()) {
                            Surface(shape = NarviveShape.Sm, color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(
                                    e.time,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (e.title.isNotBlank()) {
                            Text(e.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        if (e.description.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                e.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp,
                                maxLines = if (expanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
