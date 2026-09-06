package com.tinder.clone.moderation.infrastructure.http

import com.tinder.clone.moderation.application.policy.RuntimePolicyRegistry
import com.tinder.clone.moderation.application.service.ModerationDecisionStore
import com.tinder.clone.moderation.application.service.ReviewStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(produces = [MediaType.TEXT_HTML_VALUE])
class AdminController(
    private val decisionStore: ModerationDecisionStore,
    private val policyRegistry: RuntimePolicyRegistry
) {
    @GetMapping("/admin")
    fun dashboard(): String = page("Dashboard", """
        <section class="cards">
          <article><strong>${decisionStore.list().size}</strong><span>decisions</span></article>
          <article><strong>${decisionStore.listReviews(ReviewStatus.OPEN).size}</strong><span>open reviews</span></article>
          <article><strong>${policyRegistry.list().size}</strong><span>policy versions</span></article>
        </section>
    """)

    @GetMapping("/admin/decisions")
    fun decisions(): String = page("Decisions", table(
        listOf("ID", "Content", "Decision", "Policy", "Provider", "Created"),
        decisionStore.list().map { listOf(
            it.decisionId, it.contentId, it.decision, it.evidence.policyVersion,
            "${it.evidence.provider} / ${it.evidence.model}", it.createdAt
        ) }
    ))

    @GetMapping("/admin/reviews")
    fun reviews(): String = page("Review queue", table(
        listOf("Review", "Decision", "Status", "Resolution", "Version", "Created"),
        decisionStore.listReviews().map { listOf(
            it.reviewTaskId, it.decisionId, it.status, it.resolution ?: "—", it.aggregateVersion, it.createdAt
        ) }
    ))

    @GetMapping("/admin/policies")
    fun policies(): String = page("Policies", """
        ${table(
            listOf("Version", "Status", "Scopes", "Version", "Published"),
            policyRegistry.list().map { listOf(it.version, it.status, it.scopes.size, it.aggregateVersion, it.publishedAt ?: "—") }
        )}
        <section class="panel">
          <h2>Policy operations</h2>
          <p>Create, validate, publish and activate versions through the authenticated Policy API.
             Published versions are immutable; rollback changes only the active pointer.</p>
          <p><code>/internal/v1/policies</code> · <code>/validation</code> · <code>/publication</code> · <code>/activation</code></p>
        </section>
    """)

    private fun table(headers: List<String>, rows: List<List<Any?>>): String = buildString {
        append("<div class=\"table-wrap\"><table><thead><tr>")
        headers.forEach { append("<th>${escape(it)}</th>") }
        append("</tr></thead><tbody>")
        if (rows.isEmpty()) append("<tr><td colspan=\"${headers.size}\" class=\"empty\">No records</td></tr>")
        rows.forEach { row ->
            append("<tr>")
            row.forEach { append("<td>${escape(it?.toString() ?: "")}</td>") }
            append("</tr>")
        }
        append("</tbody></table></div>")
    }

    private fun page(title: String, content: String): String = """<!doctype html>
      <html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
      <title>${escape(title)} · Moderation Admin</title><style>
      :root{font-family:Inter,system-ui,sans-serif;color:#e8edf4;background:#0b1017}body{margin:0}nav{display:flex;gap:20px;padding:18px 28px;background:#111925;border-bottom:1px solid #263246}nav a{color:#a8c7fa;text-decoration:none}main{max-width:1180px;margin:32px auto;padding:0 24px}h1{font-size:30px}.cards{display:grid;grid-template-columns:repeat(3,1fr);gap:18px}.cards article,.panel{background:#131d2a;border:1px solid #27364b;border-radius:12px;padding:22px}.cards strong{display:block;font-size:34px}.cards span{color:#9eacbd}.table-wrap{overflow:auto;background:#131d2a;border:1px solid #27364b;border-radius:12px}table{border-collapse:collapse;width:100%}th,td{text-align:left;padding:13px;border-bottom:1px solid #27364b}th{color:#9eacbd;font-size:12px;text-transform:uppercase}.empty{text-align:center;color:#9eacbd}code{color:#9bd2ff}@media(max-width:700px){.cards{grid-template-columns:1fr}nav{flex-wrap:wrap}}
      </style></head><body><nav><strong>Moderation</strong><a href="/admin">Dashboard</a><a href="/admin/decisions">Decisions</a><a href="/admin/reviews">Reviews</a><a href="/admin/policies">Policies</a></nav>
      <main><h1>${escape(title)}</h1>$content</main></body></html>"""

    private fun escape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")
}
