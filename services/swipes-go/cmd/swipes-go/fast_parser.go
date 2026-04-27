package main

import (
	"bytes"
	"unicode"
)

var (
	compactProfile1Prefix = []byte(`{"profile1Id":"`)
	compactProfile2Sep    = []byte(`","profile2Id":"`)
	compactDecisionSep    = []byte(`","decision":`)
	compactIsSuperSep     = []byte(`,"isSuper":`)
)

func parseTrustedSwipe(body []byte) (TrustedSwipe, error) {
	body = bytes.TrimSpace(body)
	if len(body) == 0 {
		return TrustedSwipe{}, badRequest("Swipe body is required")
	}

	if swipe, ok, err := parseTrustedSwipeCompact(body); ok {
		return swipe, err
	}

	return parseTrustedSwipeFlexible(body)
}

func parseTrustedSwipeCompact(body []byte) (TrustedSwipe, bool, error) {
	if !bytes.HasPrefix(body, compactProfile1Prefix) {
		return TrustedSwipe{}, false, nil
	}

	cursor := len(compactProfile1Prefix)
	profile1End := bytes.IndexByte(body[cursor:], '"')
	if profile1End < 0 {
		return TrustedSwipe{}, true, badRequest("Invalid field: profile1Id")
	}
	profile1ID := string(body[cursor : cursor+profile1End])
	if isBlank(profile1ID) {
		return TrustedSwipe{}, true, badRequest("Missing field: profile1Id")
	}
	cursor += profile1End

	if !bytes.HasPrefix(body[cursor:], compactProfile2Sep) {
		return TrustedSwipe{}, false, nil
	}
	cursor += len(compactProfile2Sep)
	profile2End := bytes.IndexByte(body[cursor:], '"')
	if profile2End < 0 {
		return TrustedSwipe{}, true, badRequest("Invalid field: profile2Id")
	}
	profile2ID := string(body[cursor : cursor+profile2End])
	if isBlank(profile2ID) {
		return TrustedSwipe{}, true, badRequest("Missing field: profile2Id")
	}
	cursor += profile2End

	if !bytes.HasPrefix(body[cursor:], compactDecisionSep) {
		return TrustedSwipe{}, false, nil
	}
	cursor += len(compactDecisionSep)
	decision, width, ok := parseCompactBool(body[cursor:])
	if !ok {
		return TrustedSwipe{}, true, badRequest("Invalid field: decision")
	}
	cursor += width

	if !bytes.HasPrefix(body[cursor:], compactIsSuperSep) {
		return TrustedSwipe{}, false, nil
	}
	cursor += len(compactIsSuperSep)
	isSuper, width, ok := parseCompactBool(body[cursor:])
	if !ok {
		return TrustedSwipe{}, true, badRequest("Invalid field: isSuper")
	}
	cursor += width
	if cursor >= len(body) || body[cursor] != '}' {
		return TrustedSwipe{}, false, nil
	}

	return TrustedSwipe{
		Profile1ID: profile1ID,
		Profile2ID: profile2ID,
		Decision:   decision,
		IsSuper:    isSuper,
	}, true, nil
}

func parseCompactBool(body []byte) (bool, int, bool) {
	if len(body) >= 4 && body[0] == 't' && body[1] == 'r' && body[2] == 'u' && body[3] == 'e' {
		return true, 4, true
	}
	if len(body) >= 5 && body[0] == 'f' && body[1] == 'a' && body[2] == 'l' && body[3] == 's' && body[4] == 'e' {
		return false, 5, true
	}
	return false, 0, false
}

func parseTrustedSwipeFlexible(body []byte) (TrustedSwipe, error) {
	profile1ID, err := extractString(body, "profile1Id")
	if err != nil {
		return TrustedSwipe{}, err
	}
	profile2ID, err := extractString(body, "profile2Id")
	if err != nil {
		return TrustedSwipe{}, err
	}
	decision, err := extractBool(body, "decision", false)
	if err != nil {
		return TrustedSwipe{}, err
	}
	isSuper, err := extractBool(body, "isSuper", false)
	if err != nil {
		return TrustedSwipe{}, err
	}
	return TrustedSwipe{
		Profile1ID: profile1ID,
		Profile2ID: profile2ID,
		Decision:   decision,
		IsSuper:    isSuper,
	}, nil
}

func extractString(body []byte, fieldName string) (string, error) {
	fieldStart := bytes.Index(body, []byte(`"`+fieldName+`"`))
	if fieldStart < 0 {
		return "", badRequest("Missing field: " + fieldName)
	}
	colon := bytes.IndexByte(body[fieldStart+len(fieldName)+2:], ':')
	if colon < 0 {
		return "", badRequest("Invalid field: " + fieldName)
	}
	valueStart := skipWhitespace(body, fieldStart+len(fieldName)+2+colon+1)
	if valueStart >= len(body) || body[valueStart] != '"' {
		return "", badRequest("Invalid field: " + fieldName)
	}
	valueEnd := bytes.IndexByte(body[valueStart+1:], '"')
	if valueEnd < 0 {
		return "", badRequest("Invalid field: " + fieldName)
	}
	value := string(body[valueStart+1 : valueStart+1+valueEnd])
	if isBlank(value) {
		return "", badRequest("Missing field: " + fieldName)
	}
	return value, nil
}

func extractBool(body []byte, fieldName string, defaultValue bool) (bool, error) {
	fieldStart := bytes.Index(body, []byte(`"`+fieldName+`"`))
	if fieldStart < 0 {
		return defaultValue, nil
	}
	colon := bytes.IndexByte(body[fieldStart+len(fieldName)+2:], ':')
	if colon < 0 {
		return false, badRequest("Invalid field: " + fieldName)
	}
	valueStart := skipWhitespace(body, fieldStart+len(fieldName)+2+colon+1)
	if bytes.HasPrefix(body[valueStart:], []byte("true")) {
		return true, nil
	}
	if bytes.HasPrefix(body[valueStart:], []byte("false")) {
		return false, nil
	}
	return false, badRequest("Invalid field: " + fieldName)
}

func skipWhitespace(value []byte, index int) int {
	current := index
	for current < len(value) && unicode.IsSpace(rune(value[current])) {
		current++
	}
	return current
}

func isBlank(value string) bool {
	for _, r := range value {
		if !unicode.IsSpace(r) {
			return false
		}
	}
	return true
}
