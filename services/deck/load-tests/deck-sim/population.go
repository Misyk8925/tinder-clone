package main

import (
	"fmt"
	"math"
	"math/rand"
)

// Params controls the simulated population.
//
// Asymmetry is the axis of the whole experiment. The cost of one-directional ranking is
// not a fixed number: it is entirely determined by how much viewers' preference windows
// disagree with each other. Sweeping this parameter is what stops the result from being a
// restatement of a constant chosen here.
type Params struct {
	Size      int
	Asymmetry float64 // 0 = symmetric control population, 1 = widest disagreement
	Seed      int64
	Cities    int
	TasteDims int
}

// DefaultParams matches NFR-4: below roughly a hundred thousand profiles neither the
// popularity skew nor the geography shows up.
func DefaultParams() Params {
	return Params{Size: 100_000, Asymmetry: 0.5, Seed: 1, Cities: 12, TasteDims: 8}
}

func (p Params) validate() error {
	if p.Size <= 0 {
		return fmt.Errorf("size must be positive, got %d", p.Size)
	}
	if p.Asymmetry < 0 || p.Asymmetry > 1 {
		return fmt.Errorf("asymmetry must be within [0,1], got %v", p.Asymmetry)
	}
	if p.Cities <= 0 || p.TasteDims <= 0 {
		return fmt.Errorf("cities and taste dimensions must be positive")
	}
	return nil
}

// Agent is one simulated person. The first block is what the platform can see; the second
// is latent and exists only inside the simulation.
type Agent struct {
	ID         string  `json:"id"`
	Age        int     `json:"age"`
	Latitude   float64 `json:"latitude"`
	Longitude  float64 `json:"longitude"`
	MinAge     int     `json:"minAge"`
	MaxAge     int     `json:"maxAge"`
	MaxRangeKm int     `json:"maxRangeKm"`

	// Latent. Never an input to an outcome metric — see FR-14. Only the bench-only
	// `oracle` variant is allowed to read these.
	Taste       []float64 `json:"taste"`
	Appeal      float64   `json:"appeal"`
	Selectivity float64   `json:"selectivity"`
	Patience    int       `json:"patience"`
}

// cityCentre is a rough population cluster: geography has to be lumpy, or every profile
// has the same number of neighbours and the reverse index never fans out unevenly.
type cityCentre struct {
	latitude  float64
	longitude float64
	weight    float64
}

// Generate builds a reproducible population. The same params always produce the same
// agents, so a sweep can be re-run and compared.
func Generate(params Params) ([]Agent, error) {
	if err := params.validate(); err != nil {
		return nil, err
	}
	random := rand.New(rand.NewSource(params.Seed))

	centres := make([]cityCentre, params.Cities)
	total := 0.0
	for i := range centres {
		centres[i] = cityCentre{
			latitude:  35 + random.Float64()*25,
			longitude: -10 + random.Float64()*45,
			// Heavy-tailed city sizes: a few metropolises, many small towns.
			weight: math.Exp(random.NormFloat64()),
		}
		total += centres[i].weight
	}

	agents := make([]Agent, params.Size)
	for i := range agents {
		agents[i] = generateAgent(random, params, centres, total)
	}
	return agents, nil
}

func generateAgent(random *rand.Rand, params Params, centres []cityCentre, totalWeight float64) Agent {
	age := sampleAge(random)
	latitude, longitude := sampleLocation(random, centres, totalWeight)

	minAge, maxAge, maxRange := samplePreferences(random, params.Asymmetry, age)

	return Agent{
		ID:          randomUUID(random),
		Age:         age,
		Latitude:    latitude,
		Longitude:   longitude,
		MinAge:      minAge,
		MaxAge:      maxAge,
		MaxRangeKm:  maxRange,
		Taste:       sampleUnitVector(random, params.TasteDims),
		Appeal:      sampleAppeal(random),
		Selectivity: 0.2 + random.Float64()*0.6,
		Patience:    5 + random.Intn(41),
	}
}

// samplePreferences is where the asymmetry axis acts.
//
// At asymmetry 0 every agent gets the same window width around their own age and the same
// radius. That is what makes the control population genuinely symmetric: A accepts B
// exactly when B accepts A. An identical *absolute* window — everyone wants 25..35 —
// would not be symmetric at all, because acceptance would then depend only on the
// candidate's age.
func samplePreferences(random *rand.Rand, asymmetry float64, age int) (int, int, int) {
	halfWidth := 5.0 + asymmetry*random.Float64()*25.0
	offset := asymmetry * random.NormFloat64() * 8.0
	radius := 50.0 * math.Exp(asymmetry*random.NormFloat64())

	centre := float64(age) + offset
	minAge := int(math.Round(centre - halfWidth))
	maxAge := int(math.Round(centre + halfWidth))

	if minAge < 18 {
		minAge = 18
	}
	if maxAge > 99 {
		maxAge = 99
	}
	if maxAge < minAge {
		maxAge = minAge
	}

	maxRange := int(math.Round(radius))
	if maxRange < 1 {
		maxRange = 1
	}
	if maxRange > 500 {
		maxRange = 500
	}
	return minAge, maxAge, maxRange
}

func sampleAge(random *rand.Rand) int {
	// Right-skewed towards the late twenties, which is where dating populations sit.
	age := int(math.Round(18 + math.Abs(random.NormFloat64())*9))
	if age > 70 {
		age = 70
	}
	return age
}

func sampleLocation(random *rand.Rand, centres []cityCentre, totalWeight float64) (float64, float64) {
	target := random.Float64() * totalWeight
	cumulative := 0.0
	chosen := centres[len(centres)-1]
	for _, centre := range centres {
		cumulative += centre.weight
		if target < cumulative {
			chosen = centre
			break
		}
	}
	return chosen.latitude + random.NormFloat64()*0.35, chosen.longitude + random.NormFloat64()*0.35
}

// sampleAppeal is deliberately heavy-tailed: a small share of profiles collects most of
// the interest. A uniform population hides exactly the fan-out this experiment is about.
func sampleAppeal(random *rand.Rand) float64 {
	value := math.Exp(random.NormFloat64()*1.2 - 0.3)
	return value / (1 + value)
}

func sampleUnitVector(random *rand.Rand, dimensions int) []float64 {
	vector := make([]float64, dimensions)
	norm := 0.0
	for i := range vector {
		vector[i] = random.NormFloat64()
		norm += vector[i] * vector[i]
	}
	norm = math.Sqrt(norm)
	if norm == 0 {
		vector[0] = 1
		return vector
	}
	for i := range vector {
		vector[i] /= norm
	}
	return vector
}

func randomUUID(random *rand.Rand) string {
	bytes := make([]byte, 16)
	for i := range bytes {
		bytes[i] = byte(random.Intn(256))
	}
	bytes[6] = (bytes[6] & 0x0f) | 0x40
	bytes[8] = (bytes[8] & 0x3f) | 0x80
	return fmt.Sprintf("%x-%x-%x-%x-%x", bytes[0:4], bytes[4:6], bytes[6:8], bytes[8:10], bytes[10:16])
}
