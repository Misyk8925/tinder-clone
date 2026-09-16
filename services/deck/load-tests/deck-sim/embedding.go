package main

import (
	"math"
	"math/rand"
)

// SwipeRecord is one observed decision, the only thing a real ranker could train on.
type SwipeRecord struct {
	Viewer    string
	Candidate string
	Liked     bool
}

// EmbeddingModel is a matrix factorisation over observed swipes:
//
//	P(viewer likes candidate) = sigmoid( bias[candidate] + factors[viewer] · factors[candidate] )
//
// The bias term is learned popularity; the dot product is learned taste. Both come from
// behaviour alone — no stated preference and nothing latent is an input.
//
// **This model shares its functional form with the simulator's own decision rule**, which
// also scores taste as a dot product of vectors. A screen of this arm therefore reports an
// upper bound on the approach rather than an expected gain: real taste is not a clean dot
// product, and a real model would not be handed its own generative form.
type EmbeddingModel struct {
	bias    map[string]float64
	factors map[string][]float64
	dims    int
}

type TrainingConfig struct {
	Dims        int
	Epochs      int
	Rate        float64
	Regulariser float64
	Seed        int64
}

func DefaultTrainingConfig() TrainingConfig {
	// Sixteen dimensions against the simulator's eight: matching them exactly would make
	// the circularity worse than it already is.
	return TrainingConfig{Dims: 16, Epochs: 12, Rate: 0.05, Regulariser: 0.02, Seed: 7}
}

func TrainEmbeddings(history []SwipeRecord, config TrainingConfig) *EmbeddingModel {
	random := rand.New(rand.NewSource(config.Seed))
	model := &EmbeddingModel{
		bias:    make(map[string]float64),
		factors: make(map[string][]float64),
		dims:    config.Dims,
	}

	for _, record := range history {
		model.ensure(record.Viewer, random)
		model.ensure(record.Candidate, random)
	}

	order := make([]int, len(history))
	for i := range order {
		order[i] = i
	}

	for epoch := 0; epoch < config.Epochs; epoch++ {
		random.Shuffle(len(order), func(a, b int) { order[a], order[b] = order[b], order[a] })
		for _, index := range order {
			model.step(history[index], config)
		}
	}
	return model
}

func (m *EmbeddingModel) ensure(id string, random *rand.Rand) {
	if _, known := m.factors[id]; known {
		return
	}
	vector := make([]float64, m.dims)
	for i := range vector {
		vector[i] = random.NormFloat64() * 0.1
	}
	m.factors[id] = vector
	m.bias[id] = 0
}

func (m *EmbeddingModel) step(record SwipeRecord, config TrainingConfig) {
	viewer := m.factors[record.Viewer]
	candidate := m.factors[record.Candidate]

	predicted := logistic(m.bias[record.Candidate] + dot(viewer, candidate))
	target := 0.0
	if record.Liked {
		target = 1.0
	}
	err := target - predicted

	m.bias[record.Candidate] += config.Rate * (err - config.Regulariser*m.bias[record.Candidate])
	for i := 0; i < m.dims; i++ {
		viewerValue := viewer[i]
		candidateValue := candidate[i]
		viewer[i] += config.Rate * (err*candidateValue - config.Regulariser*viewerValue)
		candidate[i] += config.Rate * (err*viewerValue - config.Regulariser*candidateValue)
	}
}

// Likelihood is the model's estimate that the viewer likes the candidate. An id the model
// never saw scores at the population's base rate rather than at zero, so a profile nobody
// has swiped on yet is not buried forever.
func (m *EmbeddingModel) Likelihood(viewerID, candidateID string) float64 {
	viewer, knownViewer := m.factors[viewerID]
	candidate, knownCandidate := m.factors[candidateID]
	if !knownViewer || !knownCandidate {
		return coldStartLikelihood
	}
	return logistic(m.bias[candidateID] + dot(viewer, candidate))
}

// Seen reports whether the model has any evidence about a profile at all — what the
// newcomer quota uses to find profiles that have never been shown.
func (m *EmbeddingModel) Seen(id string) bool {
	_, known := m.factors[id]
	return known
}

// coldStartLikelihood keeps an unseen profile mid-pack instead of last.
const coldStartLikelihood = 0.15

// embeddingScore ranks by the estimated chance of a *mutual* like, gated by the same stated
// fits the other arms use.
//
// The model replaces the popularity scalar, not the feasibility constraints. Dropping the
// age and distance terms and ranking on model output alone scored far worse — the model is
// an estimate of taste, and taste is worthless for someone four hundred kilometres outside
// the viewer's radius.
func embeddingScore(model *EmbeddingModel) func(Agent, Agent) float64 {
	return func(viewer, candidate Agent) float64 {
		distance := DistanceKm(viewer, candidate)
		forward := ageFit(candidate.Age, viewer.MinAge, viewer.MaxAge) *
			proximityFit(distance, float64(viewer.MaxRangeKm))
		backward := ageFit(viewer.Age, candidate.MinAge, candidate.MaxAge) *
			proximityFit(distance, float64(candidate.MaxRangeKm))
		mutual := model.Likelihood(viewer.ID, candidate.ID) * model.Likelihood(candidate.ID, viewer.ID)
		return mutual * forward * backward
	}
}

// Coverage reports how much evidence the model actually has: how many profiles it saw at
// all, and how many observations per profile on average. A factorisation with a handful of
// observations per item is fitting noise, and this is the number that says so.
func (m *EmbeddingModel) Coverage(history []SwipeRecord, profiles int) (seen int, perProfile float64) {
	appearances := make(map[string]int, len(m.factors))
	for _, record := range history {
		appearances[record.Candidate]++
	}
	total := 0
	for _, count := range appearances {
		total += count
	}
	if len(appearances) == 0 {
		return 0, 0
	}
	return len(appearances), float64(total) / float64(len(appearances))
}

// LikeRate is the smoothed share of a profile's impressions that turned into a like.
//
// A raw count answers "how many people liked them", which a profile can win purely by having
// been around longer. A rate answers "how often does someone who sees them like them", which
// tenure cannot inflate. The prior keeps a profile with three impressions from outranking
// everyone on a lucky streak, and gives an unseen profile the population's base rate rather
// than zero.
type LikeRate struct {
	Likes       Popularity
	Impressions Popularity
	PriorLikes  float64
	PriorShows  float64
}

func NewLikeRate(priorLikes, priorShows float64) *LikeRate {
	return &LikeRate{
		Likes: make(Popularity), Impressions: make(Popularity),
		PriorLikes: priorLikes, PriorShows: priorShows,
	}
}

func (r *LikeRate) Observe(profileID string, liked bool) {
	r.Impressions[profileID]++
	if liked {
		r.Likes[profileID]++
	}
}

func (r *LikeRate) Of(profileID string) float64 {
	return (r.Likes[profileID] + r.PriorLikes) / (r.Impressions[profileID] + r.PriorShows)
}

func (r *LikeRate) Unseen(profileID string) bool { return r.Impressions[profileID] == 0 }

// likeRateScore ranks by the estimated chance of a mutual like, with the candidate's like
// rate standing in for how much anyone wants them.
func likeRateScore(rate *LikeRate) func(Agent, Agent) float64 {
	return func(viewer, candidate Agent) float64 {
		distance := DistanceKm(viewer, candidate)
		forward := ageFit(candidate.Age, viewer.MinAge, viewer.MaxAge) *
			proximityFit(distance, float64(viewer.MaxRangeKm))
		backward := ageFit(viewer.Age, candidate.MinAge, candidate.MaxAge) *
			proximityFit(distance, float64(candidate.MaxRangeKm))
		return rate.Of(candidate.ID) * forward * backward
	}
}

// dampedPopularityScore is the arithmetic ranker with the two corrections the owner chose:
// popularity enters as log(1+likes) so the top cannot eclipse everything, and the stated
// fits still gate both directions.
func dampedPopularityScore(popularity Popularity) func(Agent, Agent) float64 {
	return func(viewer, candidate Agent) float64 {
		distance := DistanceKm(viewer, candidate)
		forward := ageFit(candidate.Age, viewer.MinAge, viewer.MaxAge) *
			proximityFit(distance, float64(viewer.MaxRangeKm))
		backward := ageFit(viewer.Age, candidate.MinAge, candidate.MaxAge) *
			proximityFit(distance, float64(candidate.MaxRangeKm))
		return math.Log1p(popularity[candidate.ID]) * forward * backward
	}
}
