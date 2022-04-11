package main

import (
	"fmt"
	"log"
	"sync"
	"tools/common"
)

type SummaryRecord struct {
	MXFSPath           string
	MaybeType          string
	MaybeProject       string
	DuplicateCount     int64
	IndividualFileSize int64
	TotalSize          int64
}

func SummaryRecordHeaderRow() []string {
	return []string{
		"File path",
		"Duplicates",
		"Project ID",
		"Media type",
		"Total duplicated size",
		"Individual file size",
	}
}

func (s SummaryRecord) toCSVArray() []string {
	return []string{
		s.MXFSPath,
		fmt.Sprintf("%d", s.DuplicateCount),
		s.MaybeProject,
		s.MaybeType,
		fmt.Sprintf("%d", s.TotalSize),
		fmt.Sprintf("%d", s.IndividualFileSize),
	}
}

type AsyncProcessor struct {
	waitGroup sync.WaitGroup
}

func NewAsyncProcessor() *AsyncProcessor {
	return &AsyncProcessor{waitGroup: sync.WaitGroup{}}
}

func findAType(records *[]common.DuplicateEntry) string {
	for _, entry := range *records {
		if entry.MaybeType != "" {
			return entry.MaybeType
		}
	}
	return ""
}

func findAProject(records *[]common.DuplicateEntry) string {
	for _, entry := range *records {
		if entry.MaybeProject != "" {
			return entry.MaybeProject
		}
	}
	return ""
}

func countTotalSize(records *[]common.DuplicateEntry) int64 {
	var totalSize int64 = 0
	for _, entry := range *records {
		totalSize += entry.ByteSize
	}
	return totalSize
}

func (p *AsyncProcessor) summaryThread(inputCh chan common.DuplicateRecord, outputCh chan SummaryRecord) {
	for {
		rec, isOk := <-inputCh
		if !isOk {
			log.Printf("INFO AsyncProcessor got end of stream, exiting")
			p.waitGroup.Done()
			return
		}

		var maybeFileSize int64 = 0
		if len(rec.DuplicatesData) > 0 {
			maybeFileSize = rec.DuplicatesData[0].ByteSize
		}

		output := SummaryRecord{
			MXFSPath:           rec.MXFSPath,
			MaybeType:          findAType(&rec.DuplicatesData),
			MaybeProject:       findAProject(&rec.DuplicatesData),
			DuplicateCount:     rec.DuplicateCount,
			IndividualFileSize: maybeFileSize,
			TotalSize:          countTotalSize(&rec.DuplicatesData),
		}
		outputCh <- output
	}
}
func (p *AsyncProcessor) GenerateSummary(inputCh chan common.DuplicateRecord, paralellism int) chan SummaryRecord {
	outputCh := make(chan SummaryRecord, 100)

	for i := 0; i < paralellism; i++ {
		p.waitGroup.Add(1)
		go p.summaryThread(inputCh, outputCh)
	}

	//close off the output stream when all of the threads are done
	go func() {
		p.waitGroup.Wait()
		close(outputCh)
	}()

	return outputCh
}
