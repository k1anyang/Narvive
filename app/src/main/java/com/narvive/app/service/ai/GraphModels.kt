package com.narvive.app.service.ai

import org.json.JSONObject

/** 人物关系图：节点 + 关系边（AI 输出结构化 JSON 后解析得到） */
data class GraphNode(
    val name: String,
    val category: String,
    val description: String,
)

data class GraphEdge(
    val source: String,
    val target: String,
    val relation: String,
)

data class RelationshipGraph(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
)

/** 时间轴演进图：按时间顺序的事件条目 */
data class TimelineEvent(
    val time: String,
    val title: String,
    val description: String,
)

/** 时间轴的一个阶段（如「第X卷 / 第X章 / 某时间阶段」），其下含若干事件 */
data class TimelineStage(
    val label: String,
    val events: List<TimelineEvent>,
)

/** 解析 AI 输出：截取首个 { 到末个 }，容忍 markdown 包裹与前后废话 */
private fun extractJson(text: String): String? {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return text.substring(start, end + 1)
}

fun parseRelationshipGraph(text: String): RelationshipGraph? = runCatching {
    val raw = extractJson(text) ?: return null
    val o = JSONObject(raw)
    val nodes = o.optJSONArray("nodes")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            val n = arr.optJSONObject(i) ?: return@mapNotNull null
            GraphNode(
                name = n.optString("name").trim(),
                category = n.optString("category").trim(),
                description = n.optString("description").trim(),
            ).takeIf { it.name.isNotBlank() }
        }
    } ?: emptyList()
    val edges = o.optJSONArray("edges")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            val e = arr.optJSONObject(i) ?: return@mapNotNull null
            GraphEdge(
                source = e.optString("source").trim(),
                target = e.optString("target").trim(),
                relation = e.optString("relation").trim(),
            )
        }
    } ?: emptyList()
    if (nodes.isEmpty()) null else RelationshipGraph(nodes, edges)
}.getOrNull()

fun parseTimeline(text: String): List<TimelineStage>? = runCatching {
    val raw = extractJson(text) ?: return null
    val o = JSONObject(raw)
    val stages = o.optJSONArray("stages")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            val s = arr.optJSONObject(i) ?: return@mapNotNull null
            val events = s.optJSONArray("events")?.let { ea ->
                (0 until ea.length()).mapNotNull { j ->
                    val e = ea.optJSONObject(j) ?: return@mapNotNull null
                    TimelineEvent(
                        time = e.optString("time").trim(),
                        title = e.optString("title").trim(),
                        description = e.optString("description").trim(),
                    ).takeIf { it.title.isNotBlank() || it.description.isNotBlank() }
                }
            } ?: emptyList()
            TimelineStage(label = s.optString("label").trim(), events = events)
                .takeIf { it.label.isNotBlank() || it.events.isNotEmpty() }
        }
    } ?: emptyList()
    if (stages.isEmpty()) null else stages
}.getOrNull()
