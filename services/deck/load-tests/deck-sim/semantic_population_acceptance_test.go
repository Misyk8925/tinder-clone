package main

import (
	"encoding/json"
	"testing"
)

// Given a fixed synthetic seed, when the semantic population is generated,
// then its public profile material contains a bio and non-empty selected hobbies.
// This is red until the first semantic-shadow implementation slice extends the corpus.
func TestSemanticPopulationCarriesBioAndHobbies(t *testing.T) {
	params := DefaultParams()
	params.Size = 100
	params.Seed = 42
	agents := generate(t, params)
	for _, agent := range agents {
		encoded, err := json.Marshal(agent)
		if err != nil {
			t.Fatal(err)
		}
		var profile map[string]any
		if err := json.Unmarshal(encoded, &profile); err != nil {
			t.Fatal(err)
		}
		bio, _ := profile["bio"].(string)
		hobbies, _ := profile["hobbies"].([]any)
		if bio == "" || len(hobbies) == 0 {
			t.Fatalf("synthetic profile %s lacks semantic identity data", agent.ID)
		}
	}
}
