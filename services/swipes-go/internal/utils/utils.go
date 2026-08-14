package utils

import (
	"bytes"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"strconv"

	"tinder-clone/services/swipes-go/internal/model"
)

func ConstantTimeEqual(candidate []byte, expected []byte) bool {
	return len(candidate) == len(expected) && len(expected) > 0 && subtle.ConstantTimeCompare(candidate, expected) == 1
}

func BearerTokenBytes(header []byte) ([]byte, error) {
	value := bytes.TrimSpace(header)
	if len(value) < 8 || !bytes.EqualFold(value[:7], []byte("Bearer ")) {
		return nil, errors.New("missing bearer token")
	}
	token := bytes.TrimSpace(value[7:])
	if len(token) == 0 {
		return nil, errors.New("missing bearer token")
	}
	return token, nil
}

func DecodeSwipe(body []byte) (model.SwipeDTO, error) {
	var dto model.SwipeDTO
	if len(body) == 0 || json.Unmarshal(body, &dto) != nil {
		return model.SwipeDTO{}, errors.New("invalid swipe body")
	}
	return dto, nil
}

func EncodeEvent(event model.SwipeCreatedEvent, dst []byte) []byte {
	dst = append(dst, `{"eventId":"`...)
	dst = AppendUUID(dst, event.EventID)
	dst = append(dst, '"')
	dst = append(dst, `,"profile1Id":`...)
	dst = strconv.AppendQuote(dst, event.Profile1ID)
	dst = append(dst, `,"profile2Id":`...)
	dst = strconv.AppendQuote(dst, event.Profile2ID)
	dst = append(dst, `,"decision":`...)
	dst = strconv.AppendBool(dst, event.Decision)
	dst = append(dst, `,"isSuper":`...)
	dst = strconv.AppendBool(dst, event.IsSuper)
	dst = append(dst, `,"timestamp":`...)
	dst = strconv.AppendInt(dst, event.Timestamp, 10)
	return append(dst, '}')
}

func AppendUUID(dst []byte, value [16]byte) []byte {
	const hex = "0123456789abcdef"
	for i, b := range value {
		if i == 4 || i == 6 || i == 8 || i == 10 {
			dst = append(dst, '-')
		}
		dst = append(dst, hex[b>>4], hex[b&0x0f])
	}
	return dst
}
