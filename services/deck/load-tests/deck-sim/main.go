package main

import (
	"bufio"
	"encoding/json"
	"flag"
	"fmt"
	"math/rand"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"
)

// publicProfile is everything the platform is allowed to know about an agent.
type publicProfile struct {
	ID         string  `json:"id"`
	Age        int     `json:"age"`
	Latitude   float64 `json:"latitude"`
	Longitude  float64 `json:"longitude"`
	MinAge     int     `json:"minAge"`
	MaxAge     int     `json:"maxAge"`
	MaxRangeKm int     `json:"maxRangeKm"`
}

// latentProfile is what only the simulation and the bench-only `oracle` may read. It is
// written to a separate file on purpose: an outcome metric that reads latent state is a
// leak, and separate files make that leak visible instead of accidental.
type latentProfile struct {
	ID          string    `json:"id"`
	Taste       []float64 `json:"taste"`
	Appeal      float64   `json:"appeal"`
	Selectivity float64   `json:"selectivity"`
	Patience    int       `json:"patience"`
}

func main() {
	if len(os.Args) < 2 {
		usage()
		os.Exit(64)
	}
	switch os.Args[1] {
	case "generate":
		generateCommand(os.Args[2:])
	case "drive":
		driveCommand(os.Args[2:])
	case "jwks":
		jwksCommand(os.Args[2:])
	case "screen":
		screenCommand(os.Args[2:])
	case "quota":
		quotaCommand(os.Args[2:])
	case "decay":
		decayCommand(os.Args[2:])
	case "seed":
		seedCommand(os.Args[2:])
	default:
		usage()
		os.Exit(64)
	}
}

func usage() {
	fmt.Fprintln(os.Stderr, "deck-sim generate  — write a population")
	fmt.Fprintln(os.Stderr, "deck-sim jwks      — print the JWKS a bench service should verify against")
	fmt.Fprintln(os.Stderr, "deck-sim drive     — walk viewers through their decks and swipe")
	fmt.Fprintln(os.Stderr, "deck-sim screen    — offline: is there room between baseline and the ceiling?")
	fmt.Fprintln(os.Stderr, "deck-sim quota     — offline: what newcomer quota keeps the ranker from eating itself?")
	fmt.Fprintln(os.Stderr, "deck-sim decay     — offline: how fast should the popularity counter forget?")
	fmt.Fprintln(os.Stderr, "deck-sim seed      — SQL that puts a population into profiles_db")
}

func generateCommand(args []string) {
	params := DefaultParams()
	outputDir := "."

	flags := flag.NewFlagSet("generate", flag.ExitOnError)
	flags.IntVar(&params.Size, "size", params.Size, "number of profiles")
	flags.Float64Var(&params.Asymmetry, "asymmetry", params.Asymmetry,
		"preference disagreement, 0 = symmetric control population, 1 = widest")
	flags.Int64Var(&params.Seed, "seed", params.Seed, "random seed; the same seed reproduces the population")
	flags.IntVar(&params.Cities, "cities", params.Cities, "number of population clusters")
	flags.IntVar(&params.TasteDims, "taste-dims", params.TasteDims, "dimensions of the latent taste vector")
	flags.StringVar(&outputDir, "out", outputDir, "directory for profiles.jsonl and latent.jsonl")
	_ = flags.Parse(args)

	agents, err := Generate(params)
	if err != nil {
		fail(64, err)
	}
	if err := write(filepath.Join(outputDir, "profiles.jsonl"), agents, publicOf); err != nil {
		fail(74, err)
	}
	if err := write(filepath.Join(outputDir, "latent.jsonl"), agents, latentOf); err != nil {
		fail(74, err)
	}
	report(params, agents)
}

// screenCommand answers NFR-10 before any money is spent: it sweeps preference asymmetry
// and reports how much of the achievable ceiling today's ranking already reaches.
func screenCommand(args []string) {
	params := DefaultParams()
	config := DefaultScreenConfig()
	var sweep string

	flags := flag.NewFlagSet("screen", flag.ExitOnError)
	flags.IntVar(&params.Size, "size", params.Size, "population per point")
	flags.Int64Var(&params.Seed, "seed", params.Seed, "population seed")
	flags.IntVar(&config.Viewers, "viewers", config.Viewers, "agents evaluated per point")
	flags.IntVar(&config.Window, "window", config.Window, "cards materialised per deck")
	flags.StringVar(&sweep, "asymmetry", "0,0.25,0.5,0.75,1", "comma-separated asymmetry values")
	flags.IntVar(&config.TrainViewers, "train-viewers", config.TrainViewers,
		"agents whose baseline pass becomes the training history")
	flags.Float64Var(&config.NewcomerQuota, "newcomer-quota", config.NewcomerQuota,
		"share of each learned deck reserved for profiles with no evidence")
	_ = flags.Parse(args)

	// Every flag has to be registered before Parse, and `sweep` read after it. Reading it
	// first silently ignored -asymmetry and swept the default five points instead.
	points, err := parseFloats(sweep)
	if err != nil {
		fail(64, err)
	}

	fmt.Printf("%-10s %8s %9s %11s %8s %10s %8s   %s\n",
		"asymmetry", "random", "baseline", "reciprocal", "learned", "embedding", "oracle",
		"share of ceiling")

	exhausted := true
	for _, asymmetry := range points {
		params.Asymmetry = asymmetry
		agents, err := Generate(params)
		if err != nil {
			fail(64, err)
		}
		results := Screen(agents, config)
		if Headroom(results, VariantBaseline) < 0.95 {
			exhausted = false
		}

		fmt.Printf("%-10.2f %8.3f %9.3f %11.3f %8.3f %10.3f %8.3f   base %.0f%% / learned %.0f%% / emb %.0f%%\n",
			asymmetry,
			rateOf(results, VariantRandom), rateOf(results, VariantBaseline),
			rateOf(results, VariantReciprocal), rateOf(results, VariantLearned),
			rateOf(results, VariantEmbedding), rateOf(results, VariantOracle),
			Headroom(results, VariantBaseline)*100,
			Headroom(results, VariantLearned)*100,
			Headroom(results, VariantEmbedding)*100)
		fmt.Printf("%-10s training evidence: %d profiles seen, %.1f observations each\n",
			"", LastCoverageSeen, LastCoveragePerProfile)
	}

	fmt.Println()
	if exhausted {
		fmt.Println("NFR-10 fires: baseline reaches 95% of the ceiling at every point.")
		fmt.Println("The ranking branch is exhausted; the paid run has nothing to find.")
	} else {
		fmt.Println("There is room between baseline and the ceiling. The paid run can find something.")
	}
}

func parseFloats(list string) ([]float64, error) {
	values := []float64{}
	for _, part := range strings.Split(list, ",") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		value, err := strconv.ParseFloat(part, 64)
		if err != nil {
			return nil, fmt.Errorf("asymmetry %q is not a number", part)
		}
		values = append(values, value)
	}
	if len(values) == 0 {
		return nil, fmt.Errorf("no asymmetry values given")
	}
	return values, nil
}

// quotaCommand measures the newcomer quota over several rounds of the ranker training on
// its own output, because one round shows only what the quota costs and never what it buys.
func quotaCommand(args []string) {
	params := DefaultParams()
	config := DefaultScreenConfig()
	var sweep string
	rounds := 5

	flags := flag.NewFlagSet("quota", flag.ExitOnError)
	flags.IntVar(&params.Size, "size", 20_000, "population")
	flags.Float64Var(&params.Asymmetry, "asymmetry", 0.5, "preference disagreement")
	flags.Int64Var(&params.Seed, "seed", params.Seed, "population seed")
	flags.IntVar(&config.Viewers, "viewers", 4_000, "agents browsing each round")
	flags.IntVar(&rounds, "rounds", rounds, "cycles of rank, observe, learn")
	flags.StringVar(&sweep, "quota", "0,0.02,0.05,0.10,0.20", "comma-separated newcomer quotas")
	_ = flags.Parse(args)

	quotas, err := parseFloats(sweep)
	if err != nil {
		fail(64, err)
	}
	agents, err := Generate(params)
	if err != nil {
		fail(64, err)
	}

	fmt.Printf("%d profiles, %d viewers per round, %d rounds, asymmetry %.2f\n\n",
		params.Size, config.Viewers, rounds, params.Asymmetry)
	fmt.Printf("%-8s %-7s %10s %10s %16s\n",
		"quota", "round", "matches", "covered", "exposure top 10%")

	for _, quota := range quotas {
		results := SimulateRounds(agents, config, quota, rounds)
		for _, result := range results {
			if result.Round != 1 && result.Round != rounds {
				continue
			}
			fmt.Printf("%-8.0f%% %-7d %10.3f %9.1f%% %15.1f%%\n",
				quota*100, result.Round, result.MatchesPer100Swipes,
				result.Covered*100, result.ExposureTopDecile*100)
		}
		fmt.Println()
	}
}

// decayCommand measures the counter's half-life against arriving cohorts. Without arrivals
// a counter that never forgets is trivially best, which would be true of a static world and
// false of this one.
func decayCommand(args []string) {
	params := DefaultParams()
	config := DefaultScreenConfig()
	var sweep string
	rounds := 8
	quota := 0.12
	arrivals := 0.4

	flags := flag.NewFlagSet("decay", flag.ExitOnError)
	flags.IntVar(&params.Size, "size", 20_000, "population")
	flags.Float64Var(&params.Asymmetry, "asymmetry", 0.5, "preference disagreement")
	flags.Int64Var(&params.Seed, "seed", params.Seed, "population seed")
	flags.IntVar(&config.Viewers, "viewers", 4_000, "agents browsing each round")
	flags.IntVar(&rounds, "rounds", rounds, "cycles of rank, observe, learn")
	flags.Float64Var(&quota, "newcomer-quota", quota, "newcomer quota to hold fixed")
	flags.Float64Var(&arrivals, "arrivals", arrivals, "share of the population arriving after round 1")
	flags.StringVar(&sweep, "half-life", "0,1,2,4,8,16", "half-lives in rounds; 0 means never forget")
	_ = flags.Parse(args)

	halfLives, err := parseFloats(sweep)
	if err != nil {
		fail(64, err)
	}
	agents, err := Generate(params)
	if err != nil {
		fail(64, err)
	}

	fmt.Printf("%d profiles, %d viewers per round, %d rounds, %.0f%% arriving later, quota %.0f%%\n\n",
		params.Size, config.Viewers, rounds, arrivals*100, quota*100)
	fmt.Printf("%-14s %10s %14s %18s\n",
		"half-life", "matches", "appeal recall", "newcomer exposure")

	for _, halfLife := range halfLives {
		result := SimulateDecay(agents, config, quota, rounds, arrivals, halfLife)
		label := "never"
		if halfLife > 0 {
			label = fmt.Sprintf("%.0f rounds", halfLife)
		}
		fmt.Printf("%-14s %10.3f %13.1f%% %17.1f%%\n",
			label, result.MatchesPer100Swipes, result.AppealRecall*100, result.NewcomerExposure*100)
	}

	// The alternative the decay sweep points at: a rate needs no half-life to be free of
	// tenure, because tenure is in its denominator.
	rateResult := SimulateLikeRate(agents, config, quota, rounds, arrivals)
	fmt.Printf("%-14s %10.3f %13.1f%% %17.1f%%\n",
		"like rate", rateResult.MatchesPer100Swipes, rateResult.AppealRecall*100,
		rateResult.NewcomerExposure*100)
}

// seedCommand writes the SQL to put a generated population into the profiles database, so
// the driver has real profiles to read decks for.
func seedCommand(args []string) {
	var profilesPath string
	batch := 1000

	flags := flag.NewFlagSet("seed", flag.ExitOnError)
	flags.StringVar(&profilesPath, "profiles", "profiles.jsonl", "public profiles from `generate`")
	flags.IntVar(&batch, "batch", batch, "rows per INSERT statement")
	_ = flags.Parse(args)

	profiles, err := readJSONL[publicProfile](profilesPath)
	if err != nil {
		fail(66, err)
	}
	agents := make([]Agent, 0, len(profiles))
	for _, profile := range profiles {
		agents = append(agents, Agent{
			ID: profile.ID, Age: profile.Age, Latitude: profile.Latitude, Longitude: profile.Longitude,
			MinAge: profile.MinAge, MaxAge: profile.MaxAge, MaxRangeKm: profile.MaxRangeKm,
		})
	}

	if err := WriteSeedSQL(os.Stdout, agents, batch); err != nil {
		fail(74, err)
	}
	fmt.Fprintf(os.Stderr, "seeded %d profiles\n", len(agents))
}

func jwksCommand(args []string) {
	var keyPath, issuer, audience string
	flags := flag.NewFlagSet("jwks", flag.ExitOnError)
	flags.StringVar(&keyPath, "key", "signing-key.pem", "signing key; created if missing")
	flags.StringVar(&issuer, "issuer", "http://deck-sim:8099/realms/bench", "iss claim")
	flags.StringVar(&audience, "audience", "tinder-client", "aud claim")
	_ = flags.Parse(args)

	signer, err := LoadOrCreateIssuer(keyPath, issuer, audience, time.Hour)
	if err != nil {
		fail(74, err)
	}
	document, err := signer.JWKS()
	if err != nil {
		fail(74, err)
	}
	fmt.Println(string(document))
}

func driveCommand(args []string) {
	var profilesPath, latentPath, outputPath, keyPath, issuer, audience, jwksAddr string
	var credentialsPath, tokenURL, clientID string
	config := DriverConfig{PageLimit: 20, Concurrency: 32, BuildRetry: 2 * time.Second, BuildWaits: 5, Seed: 1}
	viewerCount := 2_000

	flags := flag.NewFlagSet("drive", flag.ExitOnError)
	flags.StringVar(&profilesPath, "profiles", "profiles.jsonl", "public profiles from `generate`")
	flags.StringVar(&latentPath, "latent", "latent.jsonl", "latent state from `generate`")
	flags.StringVar(&outputPath, "out", "outcomes.jsonl", "per-viewer outcome counts")
	flags.StringVar(&keyPath, "key", "signing-key.pem", "signing key; created if missing")
	flags.StringVar(&issuer, "issuer", "http://deck-sim:8099/realms/bench", "iss claim; must match the bench service configuration")
	flags.StringVar(&audience, "audience", "tinder-client", "aud claim")
	flags.StringVar(&jwksAddr, "jwks-addr", "", "serve the JWKS on this address, e.g. :8099")
	flags.StringVar(&credentialsPath, "credentials", "",
		"JSONL of {profileId,username,password}; when set, tokens come from Keycloak instead of being minted")
	flags.StringVar(&tokenURL, "token-url",
		"http://localhost:9080/realms/spring/protocol/openid-connect/token", "Keycloak token endpoint")
	flags.StringVar(&clientID, "client-id", "tinder-client", "public client with the direct access grant")
	flags.StringVar(&config.BaseURL, "base-url", "http://gateway:8222", "gateway base URL")
	flags.IntVar(&viewerCount, "viewers", viewerCount, "how many agents actually browse")
	flags.IntVar(&config.PageLimit, "limit", config.PageLimit, "deck page size")
	flags.IntVar(&config.Concurrency, "concurrency", config.Concurrency, "parallel viewers")
	flags.Int64Var(&config.Seed, "seed", config.Seed, "decision seed; the same seed reproduces the swipes")
	_ = flags.Parse(args)

	agents, err := loadAgents(profilesPath, latentPath)
	if err != nil {
		fail(66, err)
	}
	signer, err := LoadOrCreateIssuer(keyPath, issuer, audience, time.Hour)
	if err != nil {
		fail(74, err)
	}
	if jwksAddr != "" {
		serveJWKS(jwksAddr, signer)
	}

	var tokens TokenSource = MintedTokens{signer}
	viewers := SelectViewers(agents, viewerCount)

	if credentialsPath != "" {
		credentials, err := readJSONL[Credential](credentialsPath)
		if err != nil {
			fail(66, err)
		}
		keycloak := NewKeycloakTokens(tokenURL, clientID, credentials)
		tokens = keycloak
		// Only profiles with a Keycloak login can be driven; everyone else stays in the
		// candidate pool, which is where a large population belongs anyway.
		viewers = onlyViewers(agents, keycloak.Viewers())
		fmt.Printf("tokens    Keycloak, %d viewers with credentials\n", len(viewers))
	}

	driver := NewDriver(config, tokens, agents)

	started := time.Now()
	outcomes, runErr := driver.Run(viewers)
	elapsed := time.Since(started)

	if err := writeOutcomes(outputPath, outcomes); err != nil {
		fail(74, err)
	}
	requests, swipes := driver.Stats()
	shown, liked := 0, 0
	for _, outcome := range outcomes {
		shown += outcome.Shown
		liked += outcome.Liked
	}

	fmt.Printf("viewers   %d\n", len(viewers))
	fmt.Printf("shown     %d\n", shown)
	fmt.Printf("liked     %d\n", liked)
	fmt.Printf("swipes    %d\n", swipes)
	fmt.Printf("requests  %d in %s\n", requests, elapsed.Round(time.Millisecond))
	if runErr != nil {
		fail(70, runErr)
	}
}

// onlyViewers keeps the agents a token source can actually speak for, in a stable order.
func onlyViewers(agents []Agent, allowed map[string]bool) []Agent {
	viewers := make([]Agent, 0, len(allowed))
	for _, agent := range agents {
		if allowed[agent.ID] {
			viewers = append(viewers, agent)
		}
	}
	return viewers
}

func serveJWKS(address string, signer *Issuer) {
	document, err := signer.JWKS()
	if err != nil {
		fail(74, err)
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/jwks.json", func(writer http.ResponseWriter, _ *http.Request) {
		writer.Header().Set("Content-Type", "application/json")
		_, _ = writer.Write(document)
	})
	server := &http.Server{Addr: address, Handler: mux, ReadHeaderTimeout: 5 * time.Second}
	go func() {
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			fmt.Fprintln(os.Stderr, "jwks server:", err)
		}
	}()
	fmt.Printf("jwks      http://%s/jwks.json (kid %s)\n", address, signer.KeyID())
}

func fail(code int, err error) {
	fmt.Fprintln(os.Stderr, err)
	os.Exit(code)
}

func publicOf(agent Agent) any {
	return publicProfile{agent.ID, agent.Age, agent.Latitude, agent.Longitude,
		agent.MinAge, agent.MaxAge, agent.MaxRangeKm}
}

func latentOf(agent Agent) any {
	return latentProfile{agent.ID, agent.Taste, agent.Appeal, agent.Selectivity, agent.Patience}
}

func write(path string, agents []Agent, project func(Agent) any) error {
	file, err := os.Create(path)
	if err != nil {
		return fmt.Errorf("create %s: %w", path, err)
	}
	defer file.Close()

	writer := bufio.NewWriter(file)
	encoder := json.NewEncoder(writer)
	for _, agent := range agents {
		if err := encoder.Encode(project(agent)); err != nil {
			return fmt.Errorf("write %s: %w", path, err)
		}
	}
	return writer.Flush()
}

// report prints the numbers that say whether this population is worth running: how often
// acceptance is one-sided, and how concentrated the appeal is.
func report(params Params, agents []Agent) {
	oneSided, mutual := AcceptanceRates(agents, 200_000, params.Seed+1)

	fmt.Printf("profiles           %d\n", len(agents))
	fmt.Printf("asymmetry          %.2f\n", params.Asymmetry)
	fmt.Printf("seed               %d\n", params.Seed)
	fmt.Printf("one-sided pairs    %.4f  (A wants B, B does not want A)\n", oneSided)
	fmt.Printf("mutual pairs       %.4f\n", mutual)
	fmt.Printf("interest top decile %.4f of all like probability\n", InterestConcentration(agents, 2_000, 200, params.Seed+2))
}

// AcceptanceRates samples random pairs and reports how many are one-sided and how many
// are mutual, using stated preferences only. One-sided share is the observable meaning of
// the asymmetry parameter: it is what one-directional ranking is blind to.
func AcceptanceRates(agents []Agent, samples int, seed int64) (oneSided float64, mutual float64) {
	if len(agents) < 2 || samples <= 0 {
		return 0, 0
	}
	random := rand.New(rand.NewSource(seed))
	oneSidedCount, mutualCount := 0, 0

	for i := 0; i < samples; i++ {
		a := agents[random.Intn(len(agents))]
		b := agents[random.Intn(len(agents))]
		if a.ID == b.ID {
			continue
		}
		forward := AcceptsStated(a, b)
		backward := AcceptsStated(b, a)
		switch {
		case forward && backward:
			mutualCount++
		case forward != backward:
			oneSidedCount++
		}
	}
	return float64(oneSidedCount) / float64(samples), float64(mutualCount) / float64(samples)
}

// InterestConcentration is the share of all like probability that the most wanted tenth of
// candidates receives.
//
// It deliberately measures received interest rather than the appeal parameter. Appeal
// enters the decision inside a logistic, so a mild spread in the parameter can still
// produce a steep spread in outcomes — and it is the outcome skew that the reverse index
// and the deck have to survive.
func InterestConcentration(agents []Agent, candidates, viewers int, seed int64) float64 {
	if len(agents) < 2 || candidates <= 0 || viewers <= 0 {
		return 0
	}
	random := rand.New(rand.NewSource(seed))

	received := make([]float64, 0, candidates)
	total := 0.0
	for i := 0; i < candidates; i++ {
		candidate := agents[random.Intn(len(agents))]
		interest := 0.0
		for j := 0; j < viewers; j++ {
			interest += LikeProbability(agents[random.Intn(len(agents))], candidate)
		}
		received = append(received, interest)
		total += interest
	}
	if total == 0 {
		return 0
	}

	threshold := quantile(received, 0.9)
	top := 0.0
	for _, interest := range received {
		if interest >= threshold {
			top += interest
		}
	}
	return top / total
}

func quantile(values []float64, q float64) float64 {
	sorted := append([]float64(nil), values...)
	sort.Float64s(sorted)
	index := int(float64(len(sorted)-1) * q)
	return sorted[index]
}

// loadAgents rejoins the two files `generate` wrote. They are separate on disk so an
// outcome metric physically cannot read latent state; the driver is the one component
// allowed to hold both, because a simulated viewer decides with its own preferences.
func loadAgents(profilesPath, latentPath string) ([]Agent, error) {
	profiles, err := readJSONL[publicProfile](profilesPath)
	if err != nil {
		return nil, err
	}
	latent, err := readJSONL[latentProfile](latentPath)
	if err != nil {
		return nil, err
	}
	if len(profiles) != len(latent) {
		return nil, fmt.Errorf("%s has %d rows but %s has %d; they are from different runs",
			profilesPath, len(profiles), latentPath, len(latent))
	}

	latentByID := make(map[string]latentProfile, len(latent))
	for _, row := range latent {
		latentByID[row.ID] = row
	}

	agents := make([]Agent, 0, len(profiles))
	for _, profile := range profiles {
		state, ok := latentByID[profile.ID]
		if !ok {
			return nil, fmt.Errorf("profile %s has no latent state", profile.ID)
		}
		agents = append(agents, Agent{
			ID: profile.ID, Age: profile.Age, Latitude: profile.Latitude, Longitude: profile.Longitude,
			MinAge: profile.MinAge, MaxAge: profile.MaxAge, MaxRangeKm: profile.MaxRangeKm,
			Taste: state.Taste, Appeal: state.Appeal, Selectivity: state.Selectivity, Patience: state.Patience,
		})
	}
	return agents, nil
}

func readJSONL[T any](path string) ([]T, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, fmt.Errorf("open %s: %w", path, err)
	}
	defer file.Close()

	var rows []T
	decoder := json.NewDecoder(bufio.NewReader(file))
	for decoder.More() {
		var row T
		if err := decoder.Decode(&row); err != nil {
			return nil, fmt.Errorf("decode %s: %w", path, err)
		}
		rows = append(rows, row)
	}
	return rows, nil
}

func writeOutcomes(path string, outcomes []Outcome) error {
	file, err := os.Create(path)
	if err != nil {
		return fmt.Errorf("create %s: %w", path, err)
	}
	defer file.Close()

	writer := bufio.NewWriter(file)
	encoder := json.NewEncoder(writer)
	for _, outcome := range outcomes {
		if err := encoder.Encode(outcome); err != nil {
			return fmt.Errorf("write %s: %w", path, err)
		}
	}
	return writer.Flush()
}
