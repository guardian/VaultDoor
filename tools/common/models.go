package common

type DuplicateEntry struct {
	OID              string `json:"oid"`
	MXFSPath         string `json:"mxfsPath"`
	MXFSFilename     string `json:"mxfsFilename"`
	MaybeAssetFolder string `json:"maybeAssetFolder"`
	MaybeType        string `json:"maybeType"`
	MaybeProject     string `json:"maybeProject"`
	MaybeChecksum    string `json:"checkSum"`
	ByteSize         int64  `json:"byteSize"`
}

type DuplicateRecord struct {
	MXFSPath       string `json:"mxfsPath"`
	DuplicateCount int64  `json:"duplicateNumber"`
	DuplicatesData []DuplicateEntry
}

type DuplicatesReport struct {
	DupesCount int64 `json:"dupes_count"`
	ItemCount  int64 `json:"item_count"`
	Duplicates []DuplicateRecord
}
