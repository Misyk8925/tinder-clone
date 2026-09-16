package main

import "math"

const earthRadiusKm = 6371.0

// Weights of the latent decision. They were not guessed: they are set so that the most
// wanted tenth of candidates receives roughly 35% of all like probability — a strong but
// not absurd popularity skew, verified by TestInterestIsConcentrated.
//
// Unlike the asymmetry parameter these are constants rather than a swept axis, which is a
// residual assumption of the whole experiment: a steeper skew would make reciprocity
// matter more, a flatter one less. The sweep in NFR-7 does not cover this dimension.
const (
	tasteWeight       = 2.4
	appealWeight      = 7.0
	selectivityWeight = 2.0
)

// LikeProbability is how a simulated viewer decides on a candidate it was shown.
//
// It deliberately sits outside the model class the ranker can express. `baseline` sums an
// age curve and a distance curve; `reciprocal` adds their mirrors. Neither can represent a
// latent taste vector, a heavy-tailed appeal or a per-agent selectivity, so no variant can
// recover this function exactly and the comparison is a fair contest rather than a
// restatement of the generator.
//
// Stated preferences only bend the outcome, they do not gate it: a window is a soft
// penalty, not a filter. Making stated preferences perfectly predictive would hand the
// heuristics a result the real world does not give them.
func LikeProbability(viewer, candidate Agent) float64 {
	affinity := dot(viewer.Taste, candidate.Taste)
	score := tasteWeight*affinity + appealWeight*candidate.Appeal - appealWeight/2 - selectivityWeight*viewer.Selectivity

	probability := logistic(score)
	probability *= agePenalty(viewer, candidate)
	probability *= distancePenalty(viewer, candidate)

	return clamp(probability)
}

// MutualProbability is what the bench-only `oracle` variant ranks by: a match needs both
// sides, so the quantity worth maximising is the product, not the viewer's own liking.
func MutualProbability(viewer, candidate Agent) float64 {
	return LikeProbability(viewer, candidate) * LikeProbability(candidate, viewer)
}

// agePenalty decays outside the stated window instead of cutting at its edge.
func agePenalty(viewer, candidate Agent) float64 {
	outside := 0.0
	if candidate.Age < viewer.MinAge {
		outside = float64(viewer.MinAge - candidate.Age)
	} else if candidate.Age > viewer.MaxAge {
		outside = float64(candidate.Age - viewer.MaxAge)
	}
	return math.Exp(-outside / 8.0)
}

func distancePenalty(viewer, candidate Agent) float64 {
	distance := DistanceKm(viewer, candidate)
	radius := float64(viewer.MaxRangeKm)
	if distance <= radius {
		return 1.0
	}
	return math.Exp(-(distance - radius) / math.Max(radius, 1))
}

// AcceptsStated reports whether the candidate falls inside the viewer's stated window and
// radius. It is the symmetry probe used by the control population, not a decision rule.
func AcceptsStated(viewer, candidate Agent) bool {
	if candidate.Age < viewer.MinAge || candidate.Age > viewer.MaxAge {
		return false
	}
	return DistanceKm(viewer, candidate) <= float64(viewer.MaxRangeKm)
}

func DistanceKm(a, b Agent) float64 {
	lat1 := radians(a.Latitude)
	lat2 := radians(b.Latitude)
	deltaLat := radians(b.Latitude - a.Latitude)
	deltaLon := radians(b.Longitude - a.Longitude)

	h := math.Sin(deltaLat/2)*math.Sin(deltaLat/2) +
		math.Cos(lat1)*math.Cos(lat2)*math.Sin(deltaLon/2)*math.Sin(deltaLon/2)
	return earthRadiusKm * 2 * math.Atan2(math.Sqrt(h), math.Sqrt(1-h))
}

func radians(degrees float64) float64 { return degrees * math.Pi / 180 }

func logistic(x float64) float64 { return 1 / (1 + math.Exp(-x)) }

func dot(a, b []float64) float64 {
	size := len(a)
	if len(b) < size {
		size = len(b)
	}
	sum := 0.0
	for i := 0; i < size; i++ {
		sum += a[i] * b[i]
	}
	return sum
}

func clamp(value float64) float64 {
	if value < 0 {
		return 0
	}
	if value > 1 {
		return 1
	}
	return value
}

// pairDraw is the deterministic uniform draw a viewer uses when deciding on a candidate.
//
// It is derived from the pair and the run seed rather than from a shared random source, so
// two runs of the same population produce the same swipes no matter how many workers ran
// in parallel or in what order they finished. Without that, comparing two ranking variants
// would also be comparing two different sets of coin flips.
func pairDraw(viewerID, candidateID string, seed int64) float64 {
	hash := fnv1a64(viewerID)
	hash = fnv1a64WithSeed(candidateID, hash)
	hash ^= uint64(seed) * 0x9e3779b97f4a7c15
	// Mix so neighbouring ids do not produce neighbouring draws.
	hash ^= hash >> 33
	hash *= 0xff51afd7ed558ccd
	hash ^= hash >> 33
	return float64(hash>>11) / float64(uint64(1)<<53)
}

func fnv1a64(value string) uint64 {
	return fnv1a64WithSeed(value, 0xcbf29ce484222325)
}

func fnv1a64WithSeed(value string, seed uint64) uint64 {
	hash := seed
	for i := 0; i < len(value); i++ {
		hash ^= uint64(value[i])
		hash *= 0x100000001b3
	}
	return hash
}
