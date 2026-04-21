# SpeedBoost Plugin: Technical Design Document

## Table of Contents
1. [Executive Summary](#1-executive-summary)
2. [Problem Statement & Requirements](#2-problem-statement--requirements)
3. [High-Level Architecture](#3-high-level-architecture)
4. [Detailed Design](#4-detailed-design)
5. [Evolution of Design & Challenges](#5-evolution-of-design--challenges)
6. [Final Production Design](#6-final-production-design)
7. [Instrumentation & Observability](#7-instrumentation--observability)
8. [Performance Analysis](#8-performance-analysis)
9. [Interview Questions & Answers](#9-interview-questions--answers)
10. [Deep Dive Topics](#10-deep-dive-topics)

---

## 1. Executive Summary

**SpeedBoost** is a custom Solr ValueSource plugin that dynamically adjusts document ranking scores based on:
- **Delivery Speed Tiers** (e.g., Mnow, D0, D1, D2) - products deliverable within specific timeframes
- **Personalization (P13N) Scores** - user-specific relevance scores
- **Cohort Scores** - popularity/trending scores for non-personalized users

The plugin intercepts Solr's scoring pipeline and applies multiplicative boost factors to ensure faster-deliverable products rank higher while maintaining personalization relevance.

### Key Metrics Achieved
- **Query Latency**: Reduced from ~150ms to ~20ms (P99)
- **Throughput**: 10x improvement in documents scored per second
- **Memory**: Efficient segment-level caching with minimal GC pressure

---

## 2. Problem Statement & Requirements

### 2.1 Business Context

In e-commerce search, customers expect:
1. **Relevant results** - Products matching their preferences
2. **Fast delivery** - Products that can be delivered quickly should rank higher
3. **Real-time updates** - Delivery availability changes based on inventory, location, time

### 2.2 Technical Requirements

| Requirement | Description | Constraint |
|-------------|-------------|------------|
| **Real-time Tier Assignment** | Assign delivery tier to each document at query time | Cannot pre-index (changes per query based on user location/time) |
| **High Cardinality** | Each document has 500-1500 hashcodes (warehouse+pincode combinations) | Memory and CPU efficient |
| **Low Latency** | Must not add >5ms to query latency | P99 < 25ms for scoring |
| **High Throughput** | Score 50,000+ documents per query | Solr scores top-N after filtering |
| **Configurable Boost** | Different boost factors per tier, per scoring type | Runtime configuration via proto |

### 2.3 Input Data Structure

```protobuf
message SpeedBoostConfig {
    bool enabled = 1;
    P13NConfig p13n = 2;           // Personalization scores map
    CohortConfig cohort = 3;        // Cohort normalization params
    SpeedBoostTierConfig tierConfig = 4;
}

message SpeedBoostTierConfig {
    string fieldKey = 1;            // Solr field: "hashcodes"
    map<string, DeliveryTierBoostConfig> tiers = 2;  // tier -> config
    repeated string tierPriorityOrder = 3;           // ["mnow", "D0", "D1", "D2"]
    BoostFactors nonSpeedBoostFactors = 4;
}

message DeliveryTierBoostConfig {
    repeated string hashCodes = 1;  // 100-600 hashcodes per tier
    BoostFactors boostFactors = 2;
}
```

### 2.4 Scoring Formula

```
For each document:
1. Detect highest-priority tier where doc.hashcodes ∩ tier.hashcodes ≠ ∅
2. Get normalized score:
   - P13N user: normalizedScore = minNorm + (span * rawP13nScore / maxNorm)
   - Cohort user: normalizedScore = maxNorm * (log10(rawCohort + 1) / logDivisor)
3. Apply boost:
   - If tier matched: finalScore = normalizedScore * tier.boostFactor
   - Else: finalScore = normalizedScore * nonSpeedBoostFactor
```

---

## 3. High-Level Architecture

### 3.1 System Context

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Search Request Flow                          │
└─────────────────────────────────────────────────────────────────────┘

  Client Request
       │
       ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   QRC API    │───▶│  Solr Cloud  │───▶│   Shards     │
│  (Gateway)   │    │ (Distributed)│    │  (Workers)   │
└──────────────┘    └──────────────┘    └──────────────┘
       │                   │                    │
       │                   │                    ▼
       │                   │           ┌──────────────────┐
       │                   │           │ SpeedBoost Plugin│
       │                   │           │  (ValueSource)   │
       │                   │           └──────────────────┘
       │                   │                    │
       ▼                   ▼                    ▼
┌──────────────────────────────────────────────────────────────┐
│                    Query Processing Pipeline                   │
│                                                                │
│  1. Parse Query ──▶ 2. Filter ──▶ 3. Score ──▶ 4. Sort/Return │
│                                      ▲                         │
│                                      │                         │
│                              SpeedBoost injects                │
│                              custom scoring here               │
└──────────────────────────────────────────────────────────────┘
```

### 3.2 Plugin Integration Point

```java
// Solr's function query mechanism
// Query: bf=speedboost(BASE64_CONFIG)

public class SpeedBoostFunctionParser extends ValueSourceParser {
    @Override
    public ValueSource parse(FunctionQParser fqp) {
        // 1. Decode base64 proto config
        // 2. Create SpeedBoostFunction (ValueSource)
        // 3. Return to Solr's scoring pipeline
    }
}

public class SpeedBoostFunction extends ValueSource {
    @Override
    public FunctionValues getValues(Map context, LeafReaderContext readerContext) {
        // Called once per segment
        // Returns DoubleDocValues that scores each document
    }
}
```

### 3.3 Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    Per-Query Data Flow                           │
└─────────────────────────────────────────────────────────────────┘

  SpeedBoostConfig (Proto)
         │
         ▼
  ┌──────────────────┐
  │ Base64 Decode    │  ◄── FunctionParser.parse()
  │ Proto Parse      │
  └────────┬─────────┘
           │
           ▼
  ┌──────────────────┐
  │ Build Tier Data  │  ◄── SpeedBoostFunction constructor
  │ Structures       │      - hashcodeToTier map
  │                  │      - tierPriority list
  │                  │      - boostFactors map
  └────────┬─────────┘
           │
           ▼
  ┌──────────────────┐
  │ Per-Segment      │  ◄── getValues() - called per segment
  │ Cache Resolution │      - Check SolrCache
  │                  │      - Build ordinal cache if miss
  └────────┬─────────┘
           │
           ▼
  ┌──────────────────┐
  │ Per-Document     │  ◄── doubleVal(doc) - called per document
  │ Scoring          │      - Tier detection
  │                  │      - Score normalization
  │                  │      - Boost application
  └──────────────────┘
```

---

## 4. Detailed Design

### 4.1 Core Components

#### 4.1.1 SpeedBoostFunctionParser

**Responsibility**: Entry point for Solr's function query mechanism.

```java
public class SpeedBoostFunctionParser extends ValueSourceParser {
    
    private volatile SpeedBoostMetrics metrics;  // Singleton per core
    
    @Override
    public ValueSource parse(FunctionQParser fqp) throws SyntaxError {
        // OPTIMIZATION: Skip scoring on coordinator node
        boolean isShard = fqp.getReq().getParams().getBool("isShard", false);
        if (!isShard && isDistributed) {
            return new DoubleConstValueSource(0.0);  // No-op on coordinator
        }
        
        // 1. Decode and parse proto config
        byte[] configBytes = Base64.getUrlDecoder().decode(base64Payload);
        SpeedBoostConfig config = SpeedBoostConfig.parseFrom(configBytes);
        
        // 2. Get segment-level cache from Solr
        SolrCache<Object, SpeedBoostOrdinalCache> cache = 
            fqp.getReq().getSearcher().getCache("speedBoostCache");
        
        // 3. Create function with all dependencies
        return new SpeedBoostFunction(config, schema, cache, metrics);
    }
}
```

**Key Design Decisions**:
- **Coordinator Skip**: Distributed queries hit coordinator first, then shards. Scoring only happens on shards.
- **Lazy Metrics Init**: Double-checked locking for thread-safe singleton initialization.
- **Cache Injection**: SolrCache reference passed to function for segment caching.

#### 4.1.2 SpeedBoostFunction (Main Scoring Logic)

**Constructor - Runs Once Per Query**:

```java
public SpeedBoostFunction(SpeedBoostConfig config, IndexSchema schema,
                          SolrCache<Object, SpeedBoostOrdinalCache> cache,
                          SpeedBoostMetrics metrics) {
    
    // 1. Create ValueSources for field access
    this.styleIdVS = schema.getField(styleIdField).getType().getValueSource(...);
    this.cohortVS = schema.getField(cohortField).getType().getValueSource(...);
    
    // 2. Build tier data structures
    this.hashcodeToTier = new HashMap<>();      // BytesRef -> tierName
    this.tierToHashcodes = new HashMap<>();     // tierName -> List<hashcode>
    this.tierBoostFactorsMap = new HashMap<>(); // tierName -> BoostFactors
    this.tierPriority = new ArrayList<>();      // Ordered: [mnow, D0, D1, D2]
    
    buildTierDataStructures();  // Populate above maps
    
    // 3. Pre-compute normalization factors (avoid division per document)
    this.p13nScaleFactor = p13nNormSpan / p13nMaxNorm;
    this.cohortScaleFactor = cohortMaxNorm / cohortLogDivisor;
    
    // 4. Compute fingerprint for cache invalidation
    this.tierHashcodeFingerprint = computeFingerprint(hashcodeToTier);
}
```

**getValues() - Runs Once Per Segment**:

```java
@Override
public FunctionValues getValues(Map context, LeafReaderContext readerContext) {
    
    // 1. Get DocValues iterators for this segment
    FunctionValues styleIdValues = styleIdVS.getValues(context, readerContext);
    FunctionValues cohortValues = cohortVS.getValues(context, readerContext);
    SortedSetDocValues hashcodeDocValues = 
        readerContext.reader().getSortedSetDocValues(hashcodeFieldName);
    
    // 2. Resolve or build segment-level ordinal cache
    SpeedBoostOrdinalCache ordinalCache = resolveOrdinalCache(readerContext, hashcodeDocValues);
    
    // 3. Return DoubleDocValues for per-document scoring
    return new DoubleDocValues(this) {
        @Override
        public double doubleVal(int doc) {
            // Per-document scoring logic
        }
    };
}
```

**doubleVal() - Runs Per Document (HOT PATH)**:

```java
@Override
public double doubleVal(int doc) throws IOException {
    // 1. Extract document data
    String styleId = styleIdValues.strVal(doc);
    double rawCohortScore = cohortValues.doubleVal(doc);
    
    // 2. Check P13N
    Float rawP13nScore = p13nScoresMap.get(styleId);
    boolean isP13n = (rawP13nScore != null && rawP13nScore > 0);
    
    // 3. Detect tier (CRITICAL - most expensive operation)
    detectSpeedTier(doc, hashcodeDocValues, ordinalCache);
    String matchedTier = detectedTierName;
    
    // 4. Normalize scores
    double normalizedP13n = isP13n 
        ? Math.min(p13nMinNorm + (p13nScaleFactor * rawP13nScore), 1.0) 
        : 0.0;
    double normalizedCohort = rawCohortScore > 0
        ? Math.min(cohortScaleFactor * Math.log10(rawCohortScore + 1.0), cohortMaxNorm)
        : 0.0;
    
    // 5. Apply boost
    return calculateFinalScore(isP13n, matchedTier, normalizedP13n, normalizedCohort);
}
```

#### 4.1.3 Tier Detection Strategies

**Strategy 1: FixedBitSet Cache (Primary - O(numTiers) per document)**

```java
private void detectUsingOrdinalCache(int doc, SortedSetDocValues docValues,
                                     SpeedBoostOrdinalCache cache) {
    if (!docValues.advanceExact(doc)) return;
    
    // Collect document's ordinals into temp array
    int count = 0;
    long ord;
    while ((ord = docValues.nextOrd()) != NO_MORE_ORDS) {
        tempOrdinals[count++] = (int) ord;
    }
    
    // Use cache to find best tier
    detectedTierName = cache.findBestTier(tempOrdinals, count);
}

// SpeedBoostOrdinalCache.findBestTier()
public String findBestTier(int[] ordinals, int count) {
    // Step 1: Filter to only speed ordinals (quick elimination)
    int speedCount = 0;
    for (int i = 0; i < count; i++) {
        if (anySpeedOrdinal.get(ordinals[i])) {
            ordinals[speedCount++] = ordinals[i];
        }
    }
    if (speedCount == 0) return null;
    
    // Step 2: Check each tier in priority order
    for (int t = 0; t < tierBitsets.length; t++) {
        FixedBitSet bitset = tierBitsets[t];
        for (int i = 0; i < speedCount; i++) {
            if (bitset.get(ordinals[i])) {
                return tierNames[t];  // First match wins
            }
        }
    }
    return null;
}
```

**Strategy 2: HashMap Fallback (O(D) where D = doc ordinals)**

```java
private void detectUsingHashMap(int doc, SortedSetDocValues docValues,
                                Map<BytesRef, String> hcToTier) {
    if (!docValues.advanceExact(doc)) return;
    
    int bestPriority = Integer.MAX_VALUE;
    long ord;
    while ((ord = docValues.nextOrd()) != NO_MORE_ORDS) {
        BytesRef docHash = docValues.lookupOrd(ord);  // String lookup
        String tier = hcToTier.get(docHash);
        if (tier != null) {
            int priority = tierPriorityIndex.get(tier);
            if (priority < bestPriority) {
                bestPriority = priority;
                detectedTierName = tier;
                if (bestPriority == 0) return;  // Can't do better than mnow
            }
        }
    }
}
```

#### 4.1.4 Segment-Level Caching

```java
private SpeedBoostOrdinalCache resolveOrdinalCache(LeafReaderContext readerContext,
                                                   SortedSetDocValues docValues) {
    if (speedBoostCache == null) return null;
    
    // Cache key: (segment identity, hashcode fingerprint)
    Object segmentKey = readerContext.reader().getCoreCacheHelper().getKey();
    Object cacheKey = Arrays.asList(segmentKey, tierHashcodeFingerprint);
    
    // Check cache
    SpeedBoostOrdinalCache cached = speedBoostCache.get(cacheKey);
    if (cached != null) {
        metrics.getOrdinalCacheReuse().inc();
        return cached;
    }
    
    // Build and cache
    SpeedBoostOrdinalCache built = buildOrdinalCache(docValues);
    speedBoostCache.put(cacheKey, built);
    metrics.getOrdinalCacheBuilds().inc();
    return built;
}

private SpeedBoostOrdinalCache buildOrdinalCache(SortedSetDocValues docValues) {
    long valueCount = docValues.getValueCount();
    int maxOrd = (int) valueCount;
    
    // Create bitsets for each tier
    FixedBitSet anySpeedOrdinal = new FixedBitSet(maxOrd);
    FixedBitSet[] tierBitsets = new FixedBitSet[numTiers];
    
    for (int ti = 0; ti < numTiers; ti++) {
        tierBitsets[ti] = new FixedBitSet(maxOrd);
        for (String hashcode : tierToHashcodes.get(tierNames[ti])) {
            long ord = docValues.lookupTerm(new BytesRef(hashcode));
            if (ord >= 0) {
                tierBitsets[ti].set((int) ord);
                anySpeedOrdinal.set((int) ord);
            }
        }
    }
    
    return new SpeedBoostOrdinalCache(anySpeedOrdinal, tierBitsets, tierNames);
}
```

---

## 5. Evolution of Design & Challenges

### 5.1 Version 1: Naive HashMap Approach

**Design**:
```java
// Per document: iterate all doc hashcodes, lookup in HashMap
for each ordinal in document:
    String hashcode = docValues.lookupOrd(ord).utf8ToString();  // ALLOCATION!
    String tier = hashcodeToTier.get(hashcode);                 // HashMap lookup
    if (tier != null && priority < bestPriority):
        update bestMatch
```

**Problems**:
| Issue | Impact | Root Cause |
|-------|--------|------------|
| String allocation per ordinal | High GC pressure | `utf8ToString()` creates new String |
| HashMap lookup overhead | CPU cache misses | Random memory access pattern |
| No early termination | Wasted computation | Always processes all ordinals |

**Metrics**:
- Latency: ~150ms P99
- GC: Young gen collections every few queries
- Memory: Heap pressure from temporary Strings

### 5.2 Version 2: Ordinal-Based Detection (segment-optimised)

**Design**:
```java
// Pre-compute ordinal -> tierPriority map once per segment
Map<Long, Integer> ordinalToTierPriority = new HashMap<>();
for (tier, hashcodes) in tierConfig:
    for hashcode in hashcodes:
        long ord = docValues.lookupTerm(new BytesRef(hashcode));
        ordinalToTierPriority.put(ord, tierPriority);

// Per document: direct ordinal lookup (no string conversion!)
for each ordinal in document:
    Integer priority = ordinalToTierPriority.get(ord);  // Ordinal lookup
    if (priority != null && priority < bestPriority):
        update bestMatch
```

**Improvements**:
- ✅ Eliminated String allocations in hot path
- ✅ Ordinal-based lookup (Long vs String)
- ❌ Still rebuilds map every query (not cached)
- ❌ HashMap boxing overhead (Long objects)

**Metrics**:
- Latency: ~80ms P99
- GC: Reduced but still significant

### 5.3 Version 3: Inverted Index Approach (segment-inv_index)

**Design**:
```java
// Pre-compute per-segment: Walk posting lists instead of doc values
int[] docTierPriority = new int[maxDoc];
Arrays.fill(docTierPriority, Integer.MAX_VALUE);

for (tierIdx, tierHashcodes) in enumerate(tierConfig):
    for hashcode in tierHashcodes:
        if (termsEnum.seekExact(new BytesRef(hashcode))):
            PostingsEnum postings = termsEnum.postings(null);
            while ((docId = postings.nextDoc()) != NO_MORE_DOCS):
                if (tierIdx < docTierPriority[docId]):
                    docTierPriority[docId] = tierIdx;

// Per document: O(1) array lookup!
int tierIdx = docTierPriority[doc];
String tier = tierIdx < MAX ? tierPriority.get(tierIdx) : null;
```

**Improvements**:
- ✅ O(1) per-document lookup
- ✅ No per-document iteration
- ❌ Large array allocation (maxDoc integers)
- ❌ Still rebuilds every query
- ❌ Memory proportional to segment size, not query size

### 5.4 Version 4: FixedBitSet with SolrCache (Final)

**Design**:
```java
// Cache key includes segment identity + hashcode fingerprint
// Cache survives across queries until:
//   1. Segment changes (reindex/merge)
//   2. Hashcode config changes (different fingerprint)

SpeedBoostOrdinalCache cache = speedBoostCache.get(cacheKey);
if (cache == null) {
    cache = buildOrdinalCache();  // Build FixedBitSet arrays
    speedBoostCache.put(cacheKey, cache);
}

// Per document: Check bitsets in tier priority order
for (tier in tierPriority):
    for (ordinal in docOrdinals):
        if (tierBitset[tier].get(ordinal)):
            return tier;  // First match in priority order
```

**Final Improvements**:
- ✅ Cache persists across queries
- ✅ FixedBitSet: memory-efficient, CPU cache-friendly
- ✅ Fingerprint-based invalidation
- ✅ Graceful fallback to HashMap if cache unavailable

### 5.5 Challenge Summary

| Challenge | Root Cause | Solution |
|-----------|------------|----------|
| High GC pressure | String allocation per ordinal | Ordinal-based lookup |
| Repeated computation | Rebuilding data structures per query | SolrCache integration |
| Memory overhead | Large arrays per segment | FixedBitSet (1 bit per ordinal) |
| Cache invalidation | Config changes between queries | Fingerprint-based keys |
| Cold start latency | First query builds cache | Async warming (future) |

---

## 6. Final Production Design

### 6.1 Class Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        SpeedBoost Plugin                             │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────┐       ┌─────────────────────────┐
│ SpeedBoostFunctionParser│       │    SpeedBoostMetrics    │
│  «ValueSourceParser»    │──────▶│                         │
│                         │       │ - parseTimer            │
│ + parse(FunctionQParser)│       │ - initTimer             │
│ + getOrInitMetrics()    │       │ - getValuesTimer        │
│ + validateConfig()      │       │ - queryCount            │
└───────────┬─────────────┘       │ - tierHits/Misses       │
            │                     │ - p13nHits/Misses       │
            │ creates             │ - ordinalCacheBuilds    │
            ▼                     └─────────────────────────┘
┌─────────────────────────┐
│   SpeedBoostFunction    │       ┌─────────────────────────┐
│     «ValueSource»       │──────▶│  SpeedBoostOrdinalCache │
│                         │       │                         │
│ - config: SpeedBoostConfig      │ - anySpeedOrdinal       │
│ - hashcodeToTier: Map   │       │ - tierBitsets[]         │
│ - tierPriority: List    │       │ - tierNames[]           │
│ - speedBoostCache       │       │                         │
│                         │       │ + findBestTier()        │
│ + getValues()           │       └─────────────────────────┘
│ + resolveOrdinalCache() │
│ + buildOrdinalCache()   │
└─────────────────────────┘
            │
            │ returns
            ▼
┌─────────────────────────┐
│    DoubleDocValues      │
│   «FunctionValues»      │
│                         │
│ + doubleVal(doc)        │
│ - detectSpeedTier()     │
│ - calculateFinalScore() │
└─────────────────────────┘
```

### 6.2 Sequence Diagram

```
┌──────┐ ┌──────────┐ ┌─────────────┐ ┌───────────────┐ ┌─────────────┐
│Client│ │  Solr    │ │   Parser    │ │SpeedBoostFunc │ │OrdinalCache │
└──┬───┘ └────┬─────┘ └──────┬──────┘ └───────┬───────┘ └──────┬──────┘
   │          │              │                │                │
   │ query    │              │                │                │
   │─────────▶│              │                │                │
   │          │ parse()      │                │                │
   │          │─────────────▶│                │                │
   │          │              │ decode proto   │                │
   │          │              │───────┐        │                │
   │          │              │       │        │                │
   │          │              │◀──────┘        │                │
   │          │              │                │                │
   │          │              │ new SpeedBoostFunction()        │
   │          │              │───────────────▶│                │
   │          │              │                │ build tier maps│
   │          │              │                │───────┐        │
   │          │              │                │       │        │
   │          │              │                │◀──────┘        │
   │          │              │◀───────────────│                │
   │          │◀─────────────│                │                │
   │          │              │                │                │
   │          │ getValues(segment)            │                │
   │          │──────────────────────────────▶│                │
   │          │              │                │ check cache    │
   │          │              │                │───────────────▶│
   │          │              │                │     miss       │
   │          │              │                │◀───────────────│
   │          │              │                │ build cache    │
   │          │              │                │───────────────▶│
   │          │              │                │◀───────────────│
   │          │◀─────────────────────────────────────────────────
   │          │              │                │                │
   │          │ doubleVal(doc1)               │                │
   │          │──────────────────────────────▶│                │
   │          │              │                │ findBestTier() │
   │          │              │                │───────────────▶│
   │          │              │                │◀───────────────│
   │          │◀──────────────────────────────│                │
   │          │              │                │                │
   │          │ doubleVal(doc2..N)            │                │
   │          │──────────────────────────────▶│                │
   │          │◀──────────────────────────────│                │
   │          │              │                │                │
   │◀─────────│              │                │                │
   │ response │              │                │                │
```

### 6.3 Memory Layout

```
┌─────────────────────────────────────────────────────────────────────┐
│                     SpeedBoostOrdinalCache                           │
│                     (Per Segment, Cached)                            │
└─────────────────────────────────────────────────────────────────────┘

anySpeedOrdinal (FixedBitSet):
┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐
│ 0 │ 1 │ 0 │ 0 │ 1 │ 0 │ 1 │ 0 │ 0 │ 1 │ 0 │ 0 │ 0 │ 1 │ 0 │...│
└───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘
  0   1   2   3   4   5   6   7   8   9  10  11  12  13  14  ...

tierBitsets[0] (mnow):
┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐
│ 0 │ 1 │ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │ 1 │ 0 │ 0 │ 0 │ 0 │ 0 │...│
└───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘

tierBitsets[1] (D0):
┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐
│ 0 │ 0 │ 0 │ 0 │ 1 │ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │ 1 │ 0 │...│
└───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘

tierBitsets[2] (D1):
┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐
│ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │ 1 │ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │ 0 │...│
└───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───┘

Memory: ~(numOrdinals / 8) bytes per bitset
For 100K ordinals, 4 tiers: ~50KB total (extremely efficient!)
```

---

## 7. Instrumentation & Observability

### 7.1 Why Instrumentation?

| Need | Metric Type | Use Case |
|------|-------------|----------|
| **Latency Monitoring** | Timer | Identify slow queries, P99 tracking |
| **Throughput** | Counter | Capacity planning, load testing |
| **Cache Efficiency** | Counter | Tune cache size, detect thrashing |
| **Feature Usage** | Counter | P13N vs Cohort distribution |
| **Configuration Health** | Gauge | Detect config issues |

### 7.2 Metrics Implementation

```java
public final class SpeedBoostMetrics {
    
    // Timers (measure latency distribution)
    private final Timer parseTimer;      // Proto decode + validation
    private final Timer initTimer;       // SpeedBoostFunction constructor
    private final Timer getValuesTimer;  // Segment setup + cache resolution
    
    // Counters (monotonically increasing)
    private final Counter queryCount;         // Total queries
    private final Counter docsScored;         // Documents processed
    private final Counter tierHits;           // Docs with tier match
    private final Counter tierMisses;         // Docs without tier match
    private final Counter p13nHits;           // P13N users
    private final Counter p13nMisses;         // Cohort users
    private final Counter ordinalCacheBuilds; // Cache misses
    private final Counter ordinalCacheReuse;  // Cache hits
    private final Counter parseErrors;        // Config parse failures
    
    // Gauges (point-in-time values)
    private final AtomicLong latestTierCount;     // Tiers in config
    private final AtomicLong latestHashcodeCount; // Total hashcodes
    private final AtomicLong latestP13nEntryCount;// P13N map size
}
```

### 7.3 Integration with Solr Metrics

```java
// Register with Solr's MetricRegistry
private MetricRegistry resolveMetricRegistry(FunctionQParser fqp) {
    SolrCore core = fqp.getReq().getCore();
    SolrMetricManager metricManager = core.getCoreContainer().getMetricManager();
    String registryName = "solr.core." + core.getName();
    return metricManager.registry(registryName);
}

// Metrics are automatically exposed via:
// 1. /admin/metrics endpoint
// 2. StatsD export (configured in solr.xml)
// 3. Prometheus via solr-exporter
```

### 7.4 Usage in Code

```java
// In Parser
Timer.Context parseCtx = metrics.getParseTimer().time();
try {
    // Parse proto
} finally {
    parseCtx.stop();
}
metrics.getQueryCount().inc();

// In getValues()
Timer.Context gvCtx = metrics.getGetValuesTimer().time();
// ... setup segment data ...
gvCtx.stop();

// In doubleVal()
metrics.getDocsScored().inc();
if (matchedTier != null) {
    metrics.getTierHits().inc();
} else {
    metrics.getTierMisses().inc();
}
```

### 7.5 Grafana Dashboard Queries

```promql
# Query latency P99
histogram_quantile(0.99, 
  rate(solr_core_SPEEDBOOST_getValues_time_bucket[5m]))

# Cache hit ratio
rate(solr_core_SPEEDBOOST_ordinalCache_reuse[5m]) / 
(rate(solr_core_SPEEDBOOST_ordinalCache_reuse[5m]) + 
 rate(solr_core_SPEEDBOOST_ordinalCache_builds[5m]))

# Tier match ratio
rate(solr_core_SPEEDBOOST_tier_hits[5m]) / 
rate(solr_core_SPEEDBOOST_docs_scored[5m])

# P13N user ratio
rate(solr_core_SPEEDBOOST_p13n_hits[5m]) / 
rate(solr_core_SPEEDBOOST_docs_scored[5m])
```

---

## 8. Performance Analysis

### 8.1 Complexity Analysis

| Operation | Time Complexity | Space Complexity |
|-----------|-----------------|------------------|
| **Constructor** | O(T × H) | O(T × H) |
| **buildOrdinalCache** | O(T × H) | O(V) where V = vocabulary size |
| **getValues** (cache hit) | O(1) | O(1) |
| **getValues** (cache miss) | O(T × H) | O(V) |
| **doubleVal** (with cache) | O(D × T) | O(D) where D = doc ordinals |
| **doubleVal** (HashMap) | O(D) | O(1) |

Where:
- T = number of tiers (typically 4-5)
- H = hashcodes per tier (typically 100-600)
- V = vocabulary size in segment (typically 10K-100K)
- D = ordinals per document (typically 5-15)

### 8.2 Benchmarks

```
Environment: 8-core, 32GB RAM, SSD
Index: 10M documents, 50K unique hashcodes
Query: 50,000 documents scored per query

┌────────────────────────┬───────────┬───────────┬───────────┐
│ Approach               │ P50 (ms)  │ P99 (ms)  │ Throughput│
├────────────────────────┼───────────┼───────────┼───────────┤
│ HashMap (v1)           │    85     │   150     │  330 qps  │
│ Ordinal Map (v2)       │    45     │    80     │  700 qps  │
│ Inverted Index (v3)    │    25     │    45     │ 1200 qps  │
│ FixedBitSet+Cache (v4) │    12     │    22     │ 2500 qps  │
└────────────────────────┴───────────┴───────────┴───────────┘
```

### 8.3 Memory Usage

```
Per Query (transient):
- SpeedBoostFunction object: ~2KB
- Tier data structures: ~50KB (for 2000 total hashcodes)
- tempOrdinals array: ~60 bytes per thread

Per Segment (cached):
- SpeedBoostOrdinalCache: ~(numOrdinals / 8) * (numTiers + 1) bytes
- For 100K ordinals, 4 tiers: ~62.5KB

Total for 10 segments: ~625KB cached data
```

---

## 9. Interview Questions & Answers

### 9.1 Design & Architecture

**Q1: Why did you choose to implement this as a Solr ValueSource plugin instead of a custom query parser or search component?**

**A**: ValueSource was chosen because:
1. **Integration Point**: ValueSource plugs directly into Solr's scoring pipeline via function queries (`bf=speedboost(...)`). This allows combining our boost with other scoring factors.
2. **Per-Document Access**: ValueSource provides the `doubleVal(doc)` contract which is exactly what we need - compute a score for each document.
3. **Segment Awareness**: The `getValues(LeafReaderContext)` method gives us segment-level hooks, enabling our caching strategy.
4. **Minimal Invasion**: No core Solr modifications needed; the plugin is self-contained and can be deployed as a JAR.

Alternative approaches considered:
- **Custom QParser**: Would work but harder to combine with other boosts
- **SearchComponent**: Runs after scoring, too late to influence ranking
- **Rescorer**: Possible but adds another pass over results

---

**Q2: Explain your caching strategy. Why use SolrCache instead of a simple ConcurrentHashMap?**

**A**: SolrCache was chosen for several reasons:

1. **Lifecycle Management**: SolrCache is tied to the SolrIndexSearcher lifecycle. When a new searcher opens (after commit/reindex), old caches are automatically invalidated. A manual ConcurrentHashMap would require us to detect and handle searcher changes.

2. **Eviction Policies**: SolrCache supports LRU/LFU eviction with configurable size limits. This prevents unbounded memory growth.

3. **Warming**: SolrCache supports autowarming - pre-populating the new cache from the old one during searcher transitions.

4. **Monitoring**: SolrCache automatically exposes metrics (hits, misses, evictions) via Solr's admin interface.

The cache key design `(segmentKey, hashcodeFingerprint)` ensures:
- Same segment + same hashcodes = cache hit
- Segment change (reindex) = automatic miss (segmentKey changes)
- Config change = miss (fingerprint changes)

---

**Q3: How do you handle the case where hashcode configuration changes between queries?**

**A**: We compute a **fingerprint** of the hashcode-to-tier mapping:

```java
private static long computeFingerprint(Map<BytesRef, String> hashcodeToTier) {
    long sum = 0L;
    long xor = 0L;
    for (BytesRef br : hashcodeToTier.keySet()) {
        int h = br.hashCode();
        sum += h;
        xor ^= h;
    }
    return (sum * 0x9E3779B97F4A7C15L) ^ (xor * 0x517CC1B727220A95L) ^ size;
}
```

This fingerprint is included in the cache key. If hashcodes change:
- Different fingerprint generated
- Cache lookup misses
- New cache entry built with updated config

The fingerprint combines sum, XOR, and size to be sensitive to:
- Added/removed hashcodes
- Order changes (via position-dependent sum)
- Collision resistance (via XOR with different multipliers)

---

**Q4: Walk me through what happens when a document has hashcodes matching multiple tiers.**

**A**: The system always returns the **highest priority tier** (lowest index in tierPriority list).

Algorithm with FixedBitSet cache:
```java
for (int t = 0; t < tierBitsets.length; t++) {  // Priority order
    for (int i = 0; i < docOrdinalCount; i++) {
        if (tierBitsets[t].get(docOrdinals[i])) {
            return tierNames[t];  // First match wins
        }
    }
}
```

Key insight: We iterate tiers in priority order (mnow → D0 → D1 → D2), so the first match is automatically the highest priority.

With HashMap fallback, we track `bestPriority` and only update when we find a better (lower) priority:
```java
if (priority < bestPriority) {
    bestPriority = priority;
    detectedTierName = tier;
    if (bestPriority == 0) return;  // Can't do better than mnow
}
```

---

### 9.2 Performance & Optimization

**Q5: What was the biggest performance bottleneck and how did you solve it?**

**A**: The biggest bottleneck was **per-document String allocation** in the naive implementation.

**Problem**:
```java
// Hot path - called 50,000+ times per query
BytesRef bytes = docValues.lookupOrd(ord);
String hashcode = bytes.utf8ToString();  // NEW STRING EVERY TIME!
String tier = hashcodeToTier.get(hashcode);
```

This created ~500K temporary String objects per query (50K docs × 10 ordinals), causing:
- Young generation GC every few queries
- ~50ms GC pauses
- CPU cache thrashing

**Solution**: Ordinal-based lookup
```java
// Pre-compute: hashcode ordinal -> tier (once per segment)
long ord = docValues.lookupTerm(new BytesRef(hashcode));
ordinalToTier.put(ord, tierIndex);

// Hot path: direct ordinal lookup (no String!)
Integer tier = ordinalToTier.get(docOrdinal);
```

Further optimized with FixedBitSet:
```java
// Even faster: bit test instead of HashMap lookup
if (tierBitset.get(ordinal)) return tier;
```

**Result**: 10x latency reduction, eliminated GC pressure.

---

**Q6: Why FixedBitSet instead of HashSet<Integer> for ordinal tracking?**

**A**: FixedBitSet has several advantages:

| Aspect | FixedBitSet | HashSet<Integer> |
|--------|-------------|------------------|
| **Memory** | 1 bit per ordinal | 32+ bytes per entry (Integer object + node overhead) |
| **Lookup** | O(1) bit test, no hashing | O(1) average, but hash computation |
| **CPU Cache** | Sequential memory, cache-friendly | Random access, cache misses |
| **GC** | Zero allocations after construction | Integer boxing creates garbage |
| **Construction** | Single array allocation | Multiple allocations as set grows |

For 100K ordinals:
- FixedBitSet: ~12.5KB
- HashSet<Integer> with 1000 entries: ~50KB+ (including Integer objects)

The bit test `bitset.get(ordinal)` compiles to a single array access + bit mask operation - extremely fast.

---

**Q7: Explain the pre-computed scale factors optimization.**

**A**: Score normalization requires division:
```java
// Original (division per document - expensive)
normalizedP13n = minNorm + ((1.0 - minNorm) * rawScore / maxNorm);
```

Division is 15-30x slower than multiplication on modern CPUs. We pre-compute the divisor once:
```java
// Constructor (once per query)
this.p13nScaleFactor = (1.0 - minNorm) / maxNorm;

// Hot path (multiplication only)
normalizedP13n = minNorm + (p13nScaleFactor * rawScore);
```

For 50K documents, this saves ~50K division operations per query.

---

### 9.3 System Design

**Q8: How would you scale this system for 10x more hashcodes per tier?**

**A**: Several strategies:

1. **Bloom Filter Pre-filter**:
   ```java
   // Quick "definitely not in tier" check before BitSet
   BloomFilter<BytesRef> tierBloom = BloomFilter.create(funnel, expectedSize, 0.01);
   if (!tierBloom.mightContain(hashcode)) {
       continue;  // Skip BitSet check
   }
   ```

2. **Hierarchical Caching**:
    - L1: Thread-local cache for frequently accessed ordinals
    - L2: Segment-level SolrCache (current)
    - L3: Cross-segment shared data for common hashcodes

3. **Compression**:
    - Use Roaring Bitmaps instead of FixedBitSet for sparse ordinal sets
    - Run-length encoding for clustered ordinals

4. **Parallel Tier Detection**:
   ```java
   // For very high cardinality, parallelize tier checks
   return tierBitsets.parallelStream()
       .filter(bitset -> matchesAny(bitset, docOrdinals))
       .findFirst()
       .map(idx -> tierNames[idx]);
   ```

5. **Early Termination Optimization**:
    - Sort tierPriority by frequency (most common first)
    - Track hit rates and reorder dynamically

---

**Q9: How do you ensure thread safety in this plugin?**

**A**: Thread safety is achieved through:

1. **Immutable Per-Query State**:
   ```java
   // SpeedBoostFunction fields are final
   protected final Map<String, Float> p13nScoresMap;
   protected final List<String> tierPriority;
   // ... all final fields
   ```

2. **Thread-Local Mutable State**:
   ```java
   // Inside DoubleDocValues (anonymous class)
   private String detectedTierName;  // Per-thread instance
   private int[] tempOrdinals = new int[32];  // Per-thread buffer
   ```

   Each thread gets its own `DoubleDocValues` instance from `getValues()`, so these fields don't need synchronization.

3. **SolrCache Thread Safety**:
    - SolrCache implementations (LRUCache, FastLRUCache) are thread-safe
    - Concurrent reads/writes handled internally

4. **Metrics Thread Safety**:
    - Codahale Timer/Counter use atomics internally
    - Gauge values stored in AtomicLong

5. **Double-Checked Locking for Singleton**:
   ```java
   private volatile SpeedBoostMetrics metrics;
   
   private SpeedBoostMetrics getOrInitMetrics() {
       SpeedBoostMetrics local = metrics;
       if (local != null) return local;
       synchronized (this) {
           if (metrics != null) return metrics;
           metrics = new SpeedBoostMetrics(registry);
           return metrics;
       }
   }
   ```

---

### 9.4 Troubleshooting & Operations

**Q10: A query is taking 500ms in production. How would you debug it?**

**A**: Systematic debugging approach:

1. **Check Metrics Dashboard**:
   ```promql
   # Is it SpeedBoost specifically?
   histogram_quantile(0.99, rate(SPEEDBOOST_getValues_time_bucket[5m]))
   
   # Cache efficiency
   rate(SPEEDBOOST_ordinalCache_builds[5m])  # High = cache thrashing
   ```

2. **Enable Debug Logging** (temporarily):
   ```xml
   <logger name="com.company.sprod.function" level="DEBUG"/>
   ```

   Look for:
    - Large hashcode counts in config
    - Cache misses on every query
    - Unusually high tier detection times

3. **Query Analysis**:
   ```bash
   # Check query parameters
   curl "solr/select?debugQuery=true&bf=speedboost(...)"
   ```

   Look for:
    - Very large result sets (numFound)
    - Complex filters increasing doc count

4. **Segment Analysis**:
   ```java
   // Check if specific segments are slow
   log.info("Segment {} ordinal count: {}", segmentName, docValues.getValueCount());
   ```

   Large segments with high vocabulary = slower cache builds.

5. **Config Analysis**:
   ```java
   // Log config size
   log.info("Tiers: {}, Total hashcodes: {}, P13N entries: {}",
       tierCount, hashcodeCount, p13nMapSize);
   ```

   Sudden increase in hashcodes = config issue.

6. **GC Analysis**:
   ```bash
   # Check for GC pauses during query
   jstat -gcutil <pid> 1000
   ```

**Common Issues**:
| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| Consistent slowness | Cache disabled/thrashing | Check cache config |
| Periodic spikes | GC pauses | Tune heap, reduce allocations |
| Slow after reindex | Cache warming | Enable autowarming |
| Single query slow | Large config payload | Compress proto |

---

### 9.5 Deep Technical Questions

**Q11: Explain the trade-offs between your inverted index approach vs the FixedBitSet approach.**

**A**:

| Aspect | Inverted Index (Posting Lists) | FixedBitSet per Tier |
|--------|-------------------------------|---------------------|
| **Pre-computation** | Walk posting lists for all hashcodes | Build bitsets from term lookups |
| **Memory (per segment)** | O(maxDoc) int array | O(vocabulary × numTiers) bits |
| **Per-doc lookup** | O(1) array access | O(D × T) bit tests |
| **Best for** | Dense tier coverage (most docs match) | Sparse tier coverage (few docs match) |
| **Caching** | Hard to cache (config-dependent) | Easy to cache (segment-dependent) |

**Inverted Index** is better when:
- Most documents match some tier
- Tier hashcodes cover large portion of vocabulary
- Single query (no caching benefit)

**FixedBitSet** is better when:
- Sparse tier coverage
- Repeated queries with same config (caching)
- Multiple tiers need to be checked per doc

We chose FixedBitSet because:
1. Real-world tier coverage is sparse (~10% of docs match)
2. Same user searches multiple times (cache reuse)
3. Memory is bounded by vocabulary, not document count

---

**Q12: How would you implement A/B testing for different scoring strategies?**

**A**: Several approaches:

1. **Config-Driven Strategy Selection**:
   ```protobuf
   message SpeedBoostConfig {
       enum ScoringStrategy {
           BITSET_CACHED = 0;
           HASHMAP = 1;
           INVERTED_INDEX = 2;
       }
       ScoringStrategy strategy = 10;
   }
   ```

   ```java
   // In detectSpeedTier()
   switch (config.getStrategy()) {
       case BITSET_CACHED:
           return detectUsingOrdinalCache(...);
       case HASHMAP:
           return detectUsingHashMap(...);
       // ...
   }
   ```

2. **Request Parameter Override**:
   ```java
   boolean useExperimental = fqp.getReq().getParams()
       .getBool("speedboost.experimental", false);
   ```

3. **Percentage-Based Rollout**:
   ```java
   // Consistent hashing on user/session ID
   int bucket = Math.abs(userId.hashCode()) % 100;
   boolean useNewStrategy = bucket < rolloutPercentage;
   ```

4. **Metrics Comparison**:
   ```java
   // Instrument both paths
   if (useNewStrategy) {
       metrics.getNewStrategyTimer().time(() -> newDetection());
   } else {
       metrics.getOldStrategyTimer().time(() -> oldDetection());
   }
   ```

---

## 10. Deep Dive Topics

### Topics Worth Exploring Further

1. **Lucene DocValues Internals**
    - How SortedSetDocValues stores multi-valued fields
    - Ordinal encoding and dictionary compression
    - `advanceExact()` vs `advance()` performance

2. **Solr Caching Architecture**
    - FilterCache, QueryResultCache, DocumentCache
    - Cache warming and autowarming strategies
    - LRU vs LFU eviction policies

3. **JVM Performance**
    - Escape analysis and scalar replacement
    - Loop unrolling and vectorization
    - JIT compilation of hot methods

4. **Bitset Operations**
    - SIMD vectorization of bit operations
    - Roaring Bitmaps for sparse sets
    - Rank/Select operations

5. **Distributed Search**
    - Coordinator vs shard scoring
    - Score normalization across shards
    - Two-phase retrieval

6. **Metrics & Observability**
    - Codahale metrics internals (HDR Histogram)
    - Prometheus scraping and cardinality
    - SLO/SLA definition for search

### Recommended Reading

1. **Lucene in Action** - Chapters on scoring and indexing
2. **Solr in Action** - Chapter on function queries
3. **High Performance Java** - Chapters on GC and memory
4. **Designing Data-Intensive Applications** - Chapters on indexing
5. **Google SRE Book** - Chapters on monitoring and alerting

### Code to Study

1. `org.apache.lucene.index.SortedSetDocValues` - DocValues API
2. `org.apache.lucene.util.FixedBitSet` - Bit operations
3. `org.apache.solr.search.LRUCache` - Solr caching
4. `com.codahale.metrics.Timer` - Metrics implementation
5. `org.apache.solr.search.FunctionQParser` - Function query parsing

---

## Appendix A: Configuration Reference

### solrconfig.xml Cache Configuration

```xml
<cache name="speedBoostCache"
       class="solr.LRUCache"
       size="1000"
       initialSize="100"
       autowarmCount="50"
       regenerator="solr.NoOpRegenerator"/>
```

### Metrics Export Configuration

```xml
<!-- solr.xml -->
<metrics>
  <reporter name="statsd" class="org.apache.solr.metrics.reporters.SolrStatsdReporter">
    <str name="host">statsd.internal</str>
    <int name="port">8125</int>
    <str name="prefix">solr</str>
  </reporter>
</metrics>
```

---

## Appendix B: Glossary

| Term | Definition |
|------|------------|
| **DocValues** | Column-oriented storage for field values, optimized for sorting/faceting |
| **Ordinal** | Integer ID assigned to each unique term in a field's dictionary |
| **Segment** | Immutable chunk of the Lucene index; searches scan all segments |
| **ValueSource** | Solr/Lucene abstraction for computing per-document values |
| **FixedBitSet** | Dense bit array with O(1) get/set operations |
| **Posting List** | List of documents containing a specific term |
| **P13N** | Personalization - user-specific relevance scores |
| **Cohort** | Group-based popularity scores for non-personalized users |

---

*Document Version: 1.0*
*Last Updated: January 2026*
*Author: Search Platform Team*
