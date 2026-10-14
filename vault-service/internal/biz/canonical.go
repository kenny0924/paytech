package biz

import (
	"encoding/binary"
	"math"
	"unicode/utf8"
)

var (
	stablePrefix  = []byte("vault-stable-v1")
	requestPrefix = []byte("vault-protect-request-v1")
	recordPrefix  = []byte("vault-record-v1")
)

func StableMaterial(dataType string, value []byte) ([]byte, error) {
	dataTypeBytes, err := canonicalString(dataType)
	if err != nil {
		return nil, err
	}
	if !utf8.Valid(value) || uint64(len(value)) > math.MaxUint32 {
		return nil, NewError(InvalidRequest)
	}
	material := make([]byte, 0, len(stablePrefix)+2+len(dataTypeBytes)+4+len(value))
	material = append(material, stablePrefix...)
	material = appendString(material, dataTypeBytes)
	return appendValue(material, value), nil
}

func RequestMaterial(caller string, input ProtectInput) ([]byte, error) {
	callerBytes, err := canonicalString(caller)
	if err != nil {
		return nil, err
	}
	dataTypeBytes, err := canonicalString(input.DataType)
	if err != nil {
		return nil, err
	}
	modeBytes, err := canonicalString(string(input.TokenMode))
	if err != nil {
		return nil, err
	}
	purposeBytes, err := canonicalString(input.Purpose)
	if err != nil {
		return nil, err
	}
	if !utf8.Valid(input.Value) || uint64(len(input.Value)) > math.MaxUint32 {
		return nil, NewError(InvalidRequest)
	}
	material := make([]byte, 0, len(requestPrefix)+2+len(callerBytes)+2+len(dataTypeBytes)+2+len(modeBytes)+4+len(input.Value)+2+len(purposeBytes))
	material = append(material, requestPrefix...)
	material = appendString(material, callerBytes)
	material = appendString(material, dataTypeBytes)
	material = appendString(material, modeBytes)
	material = appendValue(material, input.Value)
	return appendString(material, purposeBytes), nil
}

func RecordAAD(recordID int64, token, dataType string, mode TokenMode) ([]byte, error) {
	if recordID <= 0 {
		return nil, NewError(InternalError)
	}
	tokenBytes, err := canonicalString(token)
	if err != nil {
		return nil, err
	}
	dataTypeBytes, err := canonicalString(dataType)
	if err != nil {
		return nil, err
	}
	modeBytes, err := canonicalString(string(mode))
	if err != nil {
		return nil, err
	}
	material := make([]byte, 0, len(recordPrefix)+8+2+len(tokenBytes)+2+len(dataTypeBytes)+2+len(modeBytes))
	material = append(material, recordPrefix...)
	var recordIDBytes [8]byte
	binary.BigEndian.PutUint64(recordIDBytes[:], uint64(recordID))
	material = append(material, recordIDBytes[:]...)
	material = appendString(material, tokenBytes)
	material = appendString(material, dataTypeBytes)
	return appendString(material, modeBytes), nil
}

func canonicalString(value string) ([]byte, error) {
	if !utf8.ValidString(value) || len(value) > math.MaxUint16 {
		return nil, NewError(InvalidRequest)
	}
	return []byte(value), nil
}

func appendString(material, value []byte) []byte {
	var length [2]byte
	binary.BigEndian.PutUint16(length[:], uint16(len(value)))
	material = append(material, length[:]...)
	return append(material, value...)
}

func appendValue(material, value []byte) []byte {
	var length [4]byte
	binary.BigEndian.PutUint32(length[:], uint32(len(value)))
	material = append(material, length[:]...)
	return append(material, value...)
}
