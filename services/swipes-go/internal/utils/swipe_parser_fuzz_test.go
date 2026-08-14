package utils

import (
	"encoding/json"
	"reflect"
	"testing"

	"tinder-clone/services/swipes-go/internal/model"
)

func FuzzDecodeSwipeMatchesEncodingJSON(f *testing.F) {
	for _, seed := range [][]byte{
		benchmarkSwipe,
		[]byte(`{"profile2Id":"b","profile1Id":"a","decision":null,"isSuper":null}`),
		[]byte(" { \n \"profile1Id\" : \"a\\u0062\", \"profile2Id\" : \"c\" } "),
		[]byte(`{"profile1Id":"a","profile2Id":"b","isSuper":"true"}`),
		[]byte(`{"profile1Id":"a","profile2Id":"b"} trailing`),
	} {
		f.Add(seed)
	}
	f.Fuzz(func(t *testing.T, body []byte) {
		var reference model.SwipeDTO
		referenceErr := json.Unmarshal(body, &reference)
		actual, actualErr := DecodeSwipe(body)
		if (referenceErr == nil) != (actualErr == nil) {
			t.Fatalf("error mismatch body=%q reference=%v actual=%v", body, referenceErr, actualErr)
		}
		if actualErr == nil && !reflect.DeepEqual(actual, reference) {
			t.Fatalf("value mismatch body=%q reference=%+v actual=%+v", body, reference, actual)
		}
	})
}
