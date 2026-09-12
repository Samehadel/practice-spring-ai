# Production-Grade RAG with Spring AI
### An intermediate → advanced tutorial for engineers who already know the naive "embed + stuff into prompt" pattern and want systems that don't fall over in production

---

## 0. Where this starts

I'm assuming you already know:
- What embeddings and cosine/dot-product similarity are
- Basic Spring AI: `ChatClient`, `EmbeddingModel`, a `VectorStore` bean
- You've built a toy RAG app (reader → splitter → vector store → retrieve top-k → stuff into prompt)

This tutorial is about everything that breaks between that toy app and something you'd put in front of real users: retrieval quality, grounding, latency, cost, evaluation, and failure handling. Code targets **Spring AI 1.0.x / 1.1.x** (`org.springframework.ai.rag.advisor` in 1.1.0+; it was `org.springframework.ai.chat.client.advisor` in 1.0.0-M6 and earlier — check your version if imports don't resolve).

---

## 1. Naive RAG's failure taxonomy (know what you're fighting)

Before writing code, internalize the five ways RAG breaks. Every technique below maps to one of these:

| Failure mode | Symptom | Section that fixes it |
|---|---|---|
| **Retrieval miss** | Right chunk exists, wrong chunk retrieved | §2 chunking, §5 query transform, §6 hybrid+rerank |
| **Retrieval starvation** | No relevant chunk exists at all | §8 graceful degradation |
| **Context dilution** | Right chunk retrieved, buried among noise, LLM ignores it | §6 rerank, §8 compression |
| **Ungrounded generation** | LLM answers from parametric memory, ignoring retrieved context | §9 grounding & citations |
| **Silent drift** | Works in demo, degrades over months as corpus/queries change | §10 evaluation, §11 observability |

A production RAG system is a pipeline with a checkpoint against each of these, not a single vector search call.

---

## 2. Ingestion: the ETL pipeline and why chunking is where most quality is won or lost

Spring AI models ingestion as three interfaces you compose:

```java
public interface DocumentReader extends Supplier<List<Document>> {}
public interface DocumentTransformer extends Function<List<Document>, List<Document>> {}
public interface DocumentWriter extends Consumer<List<Document>> {}
```

The naive pipeline:

```java
VectorStore vectorStore; // injected
DocumentReader reader = new TikaDocumentReader(resource); // pdf, docx, html, etc via Apache Tika
TokenTextSplitter splitter = new TokenTextSplitter(); // defaults: 800 tokens, 350 min chars, 20% overlap-ish behavior

vectorStore.write(splitter.split(reader.read()));
```

This works and is also why most RAG demos have mediocre retrieval. Three problems:

### 2.1 Fixed-size token splitting ignores document structure

`TokenTextSplitter` cuts mid-sentence, mid-table, mid-code-block. A chunk boundary landing inside a legal clause or a code function destroys retrievability — the embedding of a half-sentence doesn't match the semantic query.

**Fix: structure-aware splitting.** For Markdown/HTML-sourced content, split on structural boundaries *first*, then token-limit within them:

```java
@Component
public class StructureAwareSplitter {

    private final TokenTextSplitter tokenSplitter =
        new TokenTextSplitter(512, 100, 5, 10000, true);

    public List<Document> split(List<Document> documents) {
        List<Document> structural = new ArrayList<>();
        for (Document doc : documents) {
            // Split on markdown headers / double-newlines first so a chunk
            // never straddles two unrelated sections.
            String[] sections = doc.getText().split("(?=\\n#{1,3} )");
            for (String section : sections) {
                if (section.isBlank()) continue;
                Map<String, Object> meta = new HashMap<>(doc.getMetadata());
                meta.put("section_heading", extractHeading(section));
                structural.add(new Document(section, meta));
            }
        }
        // Now token-limit each structural section — most sections will
        // already be under the limit and pass through untouched.
        return tokenSplitter.apply(structural);
    }

    private String extractHeading(String section) {
        return section.lines().findFirst()
            .filter(l -> l.startsWith("#"))
            .orElse("untitled");
    }
}
```

For code, use language-aware splitting (split on function/class boundaries, not tokens) — Spring AI doesn't ship this, so you'd wire tree-sitter or a regex-based fallback per language.

### 2.2 Chunk size is a retrieval/generation tradeoff, not a constant

- **Small chunks (100–300 tokens)**: precise retrieval (the embedding represents one idea cleanly), but generation loses context — the LLM sees an isolated sentence without its surrounding argument.
- **Large chunks (800–1500 tokens)**: better generation context, but the embedding is a blurry average of multiple ideas, hurting retrieval precision.

**The production answer is to decouple the two: retrieve small, generate large.** This is "parent-child" or "small-to-big" retrieval:

```java
public class ParentChildIndexer {

    private final VectorStore childVectorStore; // small chunks, embedded
    private final Map<String, Document> parentStore; // larger context, keyed by parent_id — a real KV store in prod (Redis/Postgres), not a Map

    public void index(List<Document> rawDocs) {
        TokenTextSplitter parentSplitter = new TokenTextSplitter(1200, 200, 5, 10000, true);
        TokenTextSplitter childSplitter = new TokenTextSplitter(200, 50, 5, 10000, true);

        for (Document parent : parentSplitter.apply(rawDocs)) {
            String parentId = UUID.randomUUID().toString();
            parentStore.put(parentId, parent);

            List<Document> children = childSplitter.apply(List.of(parent));
            for (Document child : children) {
                child.getMetadata().put("parent_id", parentId);
            }
            childVectorStore.write(children);
        }
    }
}
```

At retrieval time you search `childVectorStore` for precision, then resolve `parent_id` → `parentStore.get(...)` and feed the *parent* text to the LLM. You get precise matching with rich generation context. This is the single highest-leverage change you can make to a mediocre RAG system.

### 2.3 Chunks need metadata or filtering becomes impossible later

Every chunk should carry, at minimum: `source_id`, `source_url`, `doc_version`/`ingested_at`, and any access-control or tenant field you'll need later. Retrofitting this after 500k chunks are indexed is painful — decide your metadata schema before your first production ingest, not after.

```java
Document child = new Document(text, Map.of(
    "parent_id", parentId,
    "source_id", sourceDoc.getId(),
    "tenant_id", tenantId,
    "doc_type", "policy",
    "ingested_at", Instant.now().toString(),
    "acl", String.join(",", allowedRoles)
));
```

---

## 3. Vector store setup: pgvector as the default sane choice

For most teams, Postgres + `pgvector` is the right default (you likely already run Postgres; no new infra; transactional writes alongside your business data).

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1536
        max-document-batch-size: 10000
```

```java
@Bean
VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
    return PgVectorStore.builder(jdbcTemplate, embeddingModel)
        .indexType(PgVectorStore.PgIndexType.HNSW)
        .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
        .dimensions(1536)
        .build();
}
```

**HNSW vs IVFFlat**: HNSW gives better recall at query time and doesn't need a training/rebuild step as data grows — use it unless you have a very specific reason not to. Set `ef_search` higher for accuracy-sensitive workloads (compliance/legal RAG), lower for latency-sensitive ones (chat autocomplete).

**Reindexing is an operational concern from day one.** If you change your embedding model, chunking strategy, or metadata schema, you need a re-embed-everything path. Design your ingestion as idempotent (upsert on `source_id` + `chunk_index`, not append-only) so re-runs don't duplicate data.

---

## 4. Retrieval via the Advisor API

Spring AI's RAG abstraction is `RetrievalAugmentationAdvisor` — a pluggable pipeline of query transformation → retrieval → document joining → context augmentation, attached to a `ChatClient` call.

```java
DocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
    .vectorStore(vectorStore)
    .similarityThreshold(0.55)
    .topK(6)
    .build();

Advisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
    .documentRetriever(retriever)
    .build();

String answer = chatClient.prompt()
    .advisors(ragAdvisor)
    .user(question)
    .call()
    .content();
```

### 4.1 `similarityThreshold` vs `topK` — pick both deliberately

- `topK` alone (no threshold) means a query with zero relevant documents still returns *k* documents — the least-bad matches, which the LLM will happily hallucinate a confident answer from.
- `similarityThreshold` alone (no topK) can return hundreds of matches for broad queries, blowing your context budget.

Use both: `topK` bounds cost/latency, `similarityThreshold` bounds relevance. Tune the threshold empirically against your embedding model — 0.5–0.75 cosine similarity is a common starting range, but it's model- and corpus-specific. This is exactly what §10's evaluation harness is for — don't guess this number once and forget it.

### 4.2 Per-request metadata filtering (multi-tenancy, access control)

```java
Advisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
    .documentRetriever(retriever)
    .build();

String answer = chatClient.prompt()
    .advisors(ragAdvisor)
    .advisors(a -> a.param(
        VectorStoreDocumentRetriever.FILTER_EXPRESSION,
        "tenant_id == '" + currentTenantId + "' && acl in ['user','admin']"
    ))
    .user(question)
    .call()
    .content();
```

**This is a security boundary, not just a relevance tweak.** If your RAG corpus mixes tenants or access levels, the filter expression must be built server-side from the authenticated principal — never from user input, and never optional. Treat a missing filter as a bug that leaks data across tenants, and write a test that asserts it's always present.

---

## 5. Query transformation: the query the user typed is rarely the query you should search with

### 5.1 Rewriting for retrieval (strips conversational noise)

```java
QueryTransformer rewriter = RewriteQueryTransformer.builder()
    .chatClientBuilder(chatClientBuilder)
    .build();

Advisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
    .queryTransformers(rewriter)
    .documentRetriever(retriever)
    .build();
```

Turns `"and what about the premium tier?"` (meaningless without conversation history) into a self-contained query like `"What features does the premium tier include?"` using an LLM call against the conversation history.

### 5.2 Compression (long conversation → focused query)

```java
QueryTransformer compressor = CompressionQueryTransformer.builder()
    .chatClientBuilder(chatClientBuilder)
    .build();
```

For long chat histories, `CompressionQueryTransformer` distills the accumulated context into one dense retrieval query instead of naively concatenating everything.

### 5.3 Multi-query expansion (recall booster)

A single embedding of a question captures one phrasing of intent. Generating 3–4 paraphrases and searching with each, then merging results, substantially improves recall for ambiguous or jargon-heavy queries:

```java
QueryExpander expander = MultiQueryExpander.builder()
    .chatClientBuilder(chatClientBuilder)
    .numberOfQueries(4)
    .build();

Advisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
    .queryExpander(expander)
    .documentRetriever(retriever)
    .documentJoiner(new ConcatenationDocumentJoiner()) // dedupes across the expanded queries
    .build();
```

Cost tradeoff: this multiplies your retrieval calls by `numberOfQueries` and adds one LLM call for expansion. Reserve it for queries your evaluation harness (§10) flags as low-recall, or gate it behind query classification (§12.2) rather than applying it universally.

### 5.4 HyDE (Hypothetical Document Embeddings) — for when queries and answers live in different "semantic registers"

If your corpus is written in formal/technical prose but users ask casual questions, direct query-embedding similarity is weak (a question doesn't look like an answer, vector-space-wise). HyDE has the LLM draft a *hypothetical answer* first, then embeds and searches with *that* — because an LLM-generated answer resembles your corpus's register far more than the user's question does.

Spring AI doesn't ship this as a built-in `QueryTransformer`, but the interface is one method, so it's a natural custom implementation:

```java
public class HyDEQueryTransformer implements QueryTransformer {

    private final ChatClient chatClient;

    public HyDEQueryTransformer(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Override
    public Query transform(Query query) {
        String hypotheticalAnswer = chatClient.prompt()
            .user(u -> u.text("""
                Write a short, plausible passage that would answer this
                question, in the style of technical documentation.
                Do not hedge or say you don't know — invent plausible
                specifics; this text is only used for semantic search,
                never shown to the user.

                Question: {question}
                """).param("question", query.text()))
            .call()
            .content();

        return query.mutate().text(hypotheticalAnswer).build();
    }
}
```

Wire it in like any other transformer:

```java
Advisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
    .queryTransformers(new HyDEQueryTransformer(chatClientBuilder))
    .documentRetriever(retriever)
    .build();
```

**Caveat**: HyDE trades a hallucination-shaped search query for better recall. It works well for domains with genuine semantic mismatch (question vs. technical prose) and can hurt precision in domains where the hallucinated details actively mislead retrieval (e.g., invented numbers in a finance corpus pulling in wrong-but-numerically-similar chunks). Evaluate before shipping.

---

## 6. Hybrid search and reranking: the two techniques with the best ROI

If you implement only two "advanced" techniques from this whole tutorial, make them these two.

### 6.1 Why dense vector search alone isn't enough

Dense embeddings are excellent at *semantic* similarity but weak at exact-match on rare tokens: product SKUs, error codes, acronyms, proper nouns, version numbers. `"error E4021"` and `"error E4029"` embed nearly identically — cosine similarity can't distinguish them — but a keyword/BM25 search nails it instantly. This is the single most common cause of "the answer is right there and it still didn't find it" bug reports.

### 6.2 Reciprocal Rank Fusion (RRF)

Run both a dense (vector) search and a sparse (keyword/BM25 — via Postgres full-text search, Elasticsearch, or OpenSearch) search, then merge by rank rather than raw score (dense cosine scores and BM25 scores aren't on comparable scales, so naive score-averaging is meaningless):

```java
public class HybridDocumentRetriever implements DocumentRetriever {

    private final VectorStoreDocumentRetriever denseRetriever;
    private final SparseTextRetriever sparseRetriever; // your BM25/full-text wrapper
    private static final int RRF_K = 60; // standard smoothing constant

    @Override
    public List<Document> retrieve(Query query) {
        List<Document> denseResults = denseRetriever.retrieve(query);
        List<Document> sparseResults = sparseRetriever.retrieve(query);

        Map<String, Double> fusedScores = new HashMap<>();
        Map<String, Document> byId = new HashMap<>();

        fuseIntoScores(denseResults, fusedScores, byId);
        fuseIntoScores(sparseResults, fusedScores, byId);

        return fusedScores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .map(e -> byId.get(e.getKey()))
            .limit(20) // over-fetch — reranker (§6.3) will cut this down
            .toList();
    }

    private void fuseIntoScores(List<Document> results, Map<String, Double> scores, Map<String, Document> byId) {
        for (int rank = 0; rank < results.size(); rank++) {
            Document doc = results.get(rank);
            byId.put(doc.getId(), doc);
            scores.merge(doc.getId(), 1.0 / (RRF_K + rank + 1), Double::sum);
        }
    }
}
```

Postgres full-text search as your sparse leg (no new infra needed):

```sql
SELECT id, content,
       ts_rank_cd(to_tsvector('english', content), plainto_tsquery('english', :query)) AS rank
FROM vector_store
WHERE to_tsvector('english', content) @@ plainto_tsquery('english', :query)
ORDER BY rank DESC
LIMIT 20;
```

### 6.3 Reranking: cheap recall, expensive precision

Hybrid retrieval over-fetches (e.g., top 20–30) on the theory that recall is cheap and casting a wide net catches the right chunk *somewhere* in the list. A **reranker** — a cross-encoder model that scores (query, document) pairs jointly, rather than comparing independent embeddings — then re-sorts that candidate set for precision. Cross-encoders are far more accurate than bi-encoder cosine similarity because they attend across both texts jointly, but too slow to run over your whole corpus — hence the two-stage retrieve-then-rerank pattern.

Implement as a `DocumentPostProcessor` in the advisor chain:

```java
public class CrossEncoderRerankPostProcessor implements DocumentPostProcessor {

    private final RerankClient rerankClient; // wraps a cross-encoder endpoint —
                                              // e.g. Cohere Rerank, a self-hosted
                                              // BAAI/bge-reranker via a small
                                              // sidecar service, etc.
    private final int topN;

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        List<RerankResult> ranked = rerankClient.rerank(
            query.text(),
            documents.stream().map(Document::getText).toList()
        );

        return ranked.stream()
            .sorted(Comparator.comparingDouble(RerankResult::score).reversed())
            .limit(topN)
            .map(r -> documents.get(r.index()))
            .toList();
    }
}
```

```java
Advisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
    .documentRetriever(new HybridDocumentRetriever(denseRetriever, sparseRetriever))
    .documentPostProcessors(new CrossEncoderRerankPostProcessor(rerankClient, 5))
    .build();
```

The measured pattern across most production RAG teams: **hybrid retrieval + rerank consistently outperforms fancier query-transformation tricks for the same engineering effort.** If you only have budget for one investment, this is it — do it before HyDE, before multi-query expansion.

---

## 7. Graceful degradation: what happens when nothing relevant is found

This is the most-skipped step in RAG tutorials and the most common production embarrassment (confident wrong answers). `ContextualQueryAugmenter` controls this explicitly:

```java
Advisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
    .documentRetriever(retriever)
    .queryAugmenter(ContextualQueryAugmenter.builder()
        .allowEmptyContext(false) // default: refuse rather than guess
        .build())
    .build();
```

With `allowEmptyContext(false)` (the safer default for factual/support domains), an empty retrieval result swaps the prompt for a refusal template instead of silently asking the LLM to answer from parametric memory. You can customize that template:

```java
PromptTemplate emptyContextTemplate = new PromptTemplate("""
    The user's question could not be matched to anything in the knowledge base.
    Reply that you don't have information on this topic in the current
    knowledge base, and ask them to rephrase or contact support — do not
    attempt to answer from general knowledge.

    Question: {query}
    """);

Advisor ragAdvisor = RetrievalAugmentationAdvisor.builder()
    .documentRetriever(retriever)
    .queryAugmenter(ContextualQueryAugmenter.builder()
        .allowEmptyContext(false)
        .emptyContextPromptTemplate(emptyContextTemplate)
        .build())
    .build();
```

Set `allowEmptyContext(true)` only when general-knowledge fallback is an acceptable UX (e.g., an internal coding assistant that's *usually* grounded but fine falling back to the base model's knowledge). Never default to `true` for compliance, medical, legal, or support domains — that's the direct path to a hallucinated policy answer.

---

## 8. Context assembly: dedup and compress before the LLM sees it

Two problems even after reranking:
1. **Near-duplicate chunks** (the same fact appears in an FAQ and a manual) waste context budget and can bias the model toward whatever's repeated.
2. **Irrelevant-but-similar chunks** survive the threshold cut, diluting the signal ("needle in a haystack" — LLMs demonstrably attend less reliably to the middle of a long context).

```java
public class DedupCompressionPostProcessor implements DocumentPostProcessor {

    private static final double DEDUP_SIMILARITY_THRESHOLD = 0.92;

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        List<Document> deduped = new ArrayList<>();
        for (Document candidate : documents) {
            boolean isDuplicate = deduped.stream()
                .anyMatch(existing -> jaccardSimilarity(existing.getText(), candidate.getText()) > DEDUP_SIMILARITY_THRESHOLD);
            if (!isDuplicate) deduped.add(candidate);
        }
        return deduped;
    }

    private double jaccardSimilarity(String a, String b) {
        Set<String> wordsA = Set.of(a.toLowerCase().split("\\W+"));
        Set<String> wordsB = Set.of(b.toLowerCase().split("\\W+"));
        Set<String> intersection = new HashSet<>(wordsA);
        intersection.retainAll(wordsB);
        Set<String> union = new HashSet<>(wordsA);
        union.addAll(wordsB);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }
}
```

Order matters in the chain: **retrieve → rerank → dedup → assemble.** Chain multiple `DocumentPostProcessor`s in the advisor builder in that order.

---

## 9. Grounding: making the LLM actually use what you gave it

Retrieval succeeding doesn't guarantee the LLM's answer is grounded in it — models frequently blend retrieved context with parametric memory, especially on topics they "think" they know.

### 9.1 Force inline citations, then verify them

Prompt for citation markers tied to source IDs, and treat missing/invalid citations as a signal, not just cosmetics:

```java
SystemPromptTemplate system = new SystemPromptTemplate("""
    Answer strictly using the numbered sources below. After every factual
    claim, cite the source number in brackets, e.g. [2]. If the sources
    don't contain the answer, say so explicitly — do not use outside
    knowledge.

    {context}
    """);
```

Then, post-generation, verify: does every `[n]` reference an `n` that was actually in the retrieved set? Does the answer contain claims with *no* citation? A response with zero citations against a non-empty context is a strong hallucination signal worth logging/flagging for review, even if you don't block on it synchronously.

### 9.2 A lightweight groundedness check (NLI-style, cheap)

For higher-stakes domains, run a second, smaller/cheaper LLM call (or a dedicated NLI model) that checks each claim in the answer against the retrieved context before returning it to the user:

```java
public class GroundednessChecker {

    private final ChatClient checkerClient; // can be a cheaper/faster model than the generator

    public GroundednessResult check(String answer, List<Document> context) {
        String contextText = context.stream().map(Document::getText).collect(Collectors.joining("\n---\n"));

        String verdict = checkerClient.prompt()
            .user(u -> u.text("""
                Context:
                {context}

                Claim: {answer}

                Is the claim fully supported by the context? Reply with
                exactly one word: SUPPORTED, PARTIAL, or UNSUPPORTED.
                """)
                .param("context", contextText)
                .param("answer", answer))
            .call()
            .content();

        return GroundednessResult.fromVerdict(verdict.trim());
    }
}
```

Use `UNSUPPORTED`/`PARTIAL` verdicts to trigger a retry-with-different-retrieval, a refusal, or a flagged-for-review path — depending on your latency budget and risk tolerance. This adds latency and cost, so gate it by domain risk (run it on every response for a medical/legal assistant; run it only on a sampled percentage for a low-stakes internal tool, feeding sampled results into §10's eval loop).

---

## 10. Evaluation: RAG you can't measure is RAG you can't trust

The RAGAS-style framework decomposes RAG quality into four independently measurable metrics — build these as a Spring Boot test suite or a scheduled batch job, not a one-off notebook, so it runs on every corpus/prompt/model change.

| Metric | Question it answers | How to compute |
|---|---|---|
| **Context precision** | Of the retrieved chunks, how many were actually relevant? | LLM-judge scores each retrieved chunk against the question |
| **Context recall** | Of the chunks that *should* have been retrieved, how many were? | Requires a labeled golden set with known relevant chunks |
| **Faithfulness** | Is the answer supported by the retrieved context? | Same technique as §9.2's groundedness checker, aggregated |
| **Answer relevancy** | Does the answer actually address the question asked? | LLM-judge scores answer against original question |

```java
@Component
public class RagEvaluationHarness {

    private final ChatClient judgeClient; // a strong model, used purely as an evaluator — decouple it from the model under test
    record EvalCase(String question, String expectedAnswerSummary, Set<String> goldChunkIds) {}

    public EvalReport evaluate(List<EvalCase> goldenSet, RagPipeline pipelineUnderTest) {
        List<CaseResult> results = new ArrayList<>();

        for (EvalCase testCase : goldenSet) {
            RagResponse response = pipelineUnderTest.answer(testCase.question());

            double contextPrecision = scoreContextPrecision(testCase.question(), response.retrievedDocs());
            double contextRecall = scoreContextRecall(testCase.goldChunkIds(), response.retrievedDocs());
            double faithfulness = scoreFaithfulness(response.answer(), response.retrievedDocs());
            double relevancy = scoreRelevancy(testCase.question(), response.answer());

            results.add(new CaseResult(testCase, response, contextPrecision, contextRecall, faithfulness, relevancy));
        }
        return EvalReport.aggregate(results);
    }

    private double scoreContextRecall(Set<String> goldIds, List<Document> retrieved) {
        Set<String> retrievedIds = retrieved.stream().map(Document::getId).collect(Collectors.toSet());
        Set<String> hit = new HashSet<>(goldIds);
        hit.retainAll(retrievedIds);
        return goldIds.isEmpty() ? 1.0 : (double) hit.size() / goldIds.size();
    }

    // scoreContextPrecision / scoreFaithfulness / scoreRelevancy follow the same
    // LLM-judge pattern as GroundednessChecker in §9.2 — one focused prompt each,
    // parsed to a 0-1 or categorical score.
}
```

**Build your golden set from real production queries + human-labeled relevant chunks**, not synthetic questions you invent while writing the eval — synthetic questions tend to be easier than what users actually ask, which flatters your numbers and hides real gaps. Aim for 50–200 cases spanning your query distribution (easy factual lookups, multi-hop questions, edge cases, questions with no answer in the corpus).

Run this harness in CI whenever chunking strategy, embedding model, retrieval config, or prompts change — regressions in RAG quality are otherwise invisible until a user complains.

---

## 11. Production concerns: latency, cost, caching, observability

### 11.1 Semantic caching

Many production RAG workloads (support bots, internal docs assistants) have highly repetitive query patterns. A semantic cache — embed the incoming query, check for a near-duplicate of a previously-answered query, return the cached answer if similarity exceeds a high threshold — cuts both latency and LLM cost substantially:

```java
@Component
public class SemanticCache {

    private final VectorStore cacheStore; // separate from your knowledge-base vector store
    private static final double CACHE_HIT_THRESHOLD = 0.97; // deliberately high — false cache hits are worse than cache misses

    public Optional<String> get(String query) {
        List<Document> matches = cacheStore.similaritySearch(SearchRequest.builder()
            .query(query)
            .topK(1)
            .similarityThreshold(CACHE_HIT_THRESHOLD)
            .build());
        return matches.isEmpty()
            ? Optional.empty()
            : Optional.of((String) matches.get(0).getMetadata().get("answer"));
    }

    public void put(String query, String answer) {
        cacheStore.add(List.of(new Document(query, Map.of(
            "answer", answer,
            "cached_at", Instant.now().toString()
        ))));
    }
}
```

**Set the threshold aggressively high** (0.95+) — a wrong cache hit that returns a plausible-but-mismatched answer is a worse failure mode than the latency cost of a cache miss. Also invalidate/expire cache entries when the underlying corpus changes, or you'll serve stale answers after a knowledge base update.

### 11.2 Observability: instrument every stage, not just the final answer

When a user reports a bad answer, you need to know *which stage* failed — was it retrieval (wrong chunks) or generation (right chunks, model ignored them)? Wrap the advisor chain with tracing spans that record intermediate state:

```java
@Around("@annotation(Traced)")
public Object traceRagStage(ProceedingJoinPoint pjp) throws Throwable {
    Span span = tracer.nextSpan().name(pjp.getSignature().getName()).start();
    try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
        Object result = pjp.proceed();
        if (result instanceof List<?> docs) {
            span.tag("retrieved.count", String.valueOf(docs.size()));
        }
        return result;
    } finally {
        span.end();
    }
}
```

At minimum, log per-request: the transformed query (post-rewrite), the retrieved chunk IDs with scores, the post-rerank/post-dedup final set, and whether groundedness checks passed. This turns "the bot gave a wrong answer" support tickets from a guessing game into a five-minute diagnosis.

### 11.3 Latency budget discipline

A naive RAG call chain — query rewrite (LLM call) → retrieval → rerank (model call) → generation (LLM call) → groundedness check (LLM call) — can easily stack to 4+ sequential LLM round-trips. Decide your latency budget up front and cut stages to fit it:

- **Chat/interactive UX**: skip query rewrite for follow-ups that don't need it (classify first, cheaply), skip groundedness checking synchronously (do it async, flag for review), stream the generation token-by-token so perceived latency is lower than actual latency.
- **Async/batch (e.g., nightly report generation)**: use the full pipeline including groundedness checks, since latency doesn't matter.

---

## 12. Advanced patterns

### 12.1 Corrective RAG (CRAG): self-grading retrieval before generation

Instead of trusting whatever the retriever returns, grade it first and take a different path depending on the grade:

```java
public class CorrectiveRagPipeline {

    public RagResponse answer(String question) {
        List<Document> retrieved = retriever.retrieve(new Query(question));
        RetrievalGrade grade = grader.grade(question, retrieved);

        return switch (grade) {
            case CORRECT -> generate(question, retrieved);
            case AMBIGUOUS -> {
                // Retrieved docs are borderline — supplement with a broader
                // web/secondary-source search or a query rewrite + retry
                List<Document> supplemented = fallbackRetriever.retrieve(new Query(question));
                yield generate(question, mergeDedupe(retrieved, supplemented));
            }
            case INCORRECT -> refuse(question); // §7's graceful degradation path
        };
    }
}
```

This is `§6` reranking generalized into a decision, not just a score — the grade determines the retrieval *strategy* for this specific query, not just which chunks survive.

### 12.2 Agentic RAG: let the model decide *whether* and *how* to retrieve

Naive RAG always retrieves, for every query, from one corpus, once. Real assistants often need to: decide retrieval isn't needed (small talk, a math question), decide which of several corpora to search (product docs vs. billing policy vs. code repo), or retrieve iteratively (search, read, realize you need a follow-up search, search again). Model retrieval as a tool the LLM calls rather than a fixed pre-generation step:

```java
@Tool(description = "Search the product documentation knowledge base for information relevant to the user's question")
public List<String> searchDocs(@ToolParam(description = "search query") String query) {
    return retriever.retrieve(new Query(query)).stream()
        .map(Document::getText)
        .toList();
}

@Tool(description = "Search billing and account policy documents")
public List<String> searchBillingPolicy(@ToolParam(description = "search query") String query) {
    return billingRetriever.retrieve(new Query(query)).stream()
        .map(Document::getText)
        .toList();
}
```

```java
String answer = chatClient.prompt()
    .tools(this)
    .user(question)
    .call()
    .content();
```

The model decides which tool(s) to call, with what query, and can call them multiple times in sequence (search → notice it needs a follow-up detail → search again) before answering. This trades determinism and latency predictability (you no longer know up front how many retrieval calls will happen) for flexibility across multi-corpus, multi-hop questions — a real cost worth weighing, not a strictly-better upgrade.

### 12.3 GraphRAG (when to reach for it)

For corpora where the *relationships between entities* matter more than any single document's text — org charts, regulatory dependency chains, codebases with deep call graphs — pure vector similarity struggles because it operates on text chunks, not on the graph of relationships between them. GraphRAG builds an entity/relationship graph (often via LLM-assisted extraction during ingestion) alongside or instead of the vector index, and answers multi-hop questions ("who approves changes affecting both team A and team B's shared service?") by traversing that graph. Spring AI doesn't have first-party support for this; it's typically built with Neo4j (Spring AI does support `Neo4jVectorStore`) plus custom graph-traversal logic. Reach for it only when you've confirmed — via your eval harness — that vector-only retrieval is specifically failing on relationship/multi-hop questions; it's meaningfully more engineering investment than everything above.

---

## 13. Reliability checklist

Before calling a RAG system production-ready:

- [ ] Chunking is structure-aware, not blind fixed-token splitting
- [ ] Retrieval uses both `topK` and `similarityThreshold`, tuned against real data
- [ ] Metadata filtering (tenant/ACL) is enforced server-side and covered by a test
- [ ] Hybrid (dense + sparse) retrieval with RRF fusion, not vector-only
- [ ] A reranking stage sits between retrieval and generation
- [ ] Empty/low-confidence retrieval triggers explicit refusal, not silent fallback to parametric knowledge
- [ ] Answers cite sources, and citations are checked against the actual retrieved set
- [ ] An automated eval harness (context precision/recall, faithfulness, relevancy) runs in CI on a real golden set
- [ ] Every request logs the transformed query, retrieved chunk IDs+scores, and post-processing decisions
- [ ] Re-ingestion/re-embedding is idempotent and has a defined process for corpus updates
- [ ] Latency budget is explicit, and expensive stages (groundedness checks, multi-query expansion) are gated by domain risk, not applied uniformly

---

## Where to go from here

Pick the **one** failure mode from §1's table that's actually hurting your current system, implement the corresponding section, and measure the effect with §10's harness before adding the next technique. Stacking every technique in this tutorial at once, untested, is how you end up with a slower, more expensive system that you can't debug when it's still wrong — the discipline of measuring before and after each change is the actual "advanced" skill here, more than any individual pattern.
