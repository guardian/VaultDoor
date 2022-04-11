package main

import (
	"encoding/json"
	"io/ioutil"
	"log"
	"os"
	"tools/common"
)

/*
AsyncLoad opens the given report file, parses it as json and then streams each record onto the output
*/
func AsyncLoad(jsonReportFile string) chan common.DuplicateRecord {
	outputCh := make(chan common.DuplicateRecord, 100)

	go func() {
		f, err := os.Open(jsonReportFile)
		if err != nil {
			log.Printf("ERROR Could not open '%s': %s.", jsonReportFile, err)
			close(outputCh)
			return
		}

		defer f.Close()
		dataBytes, readErr := ioutil.ReadAll(f)
		if readErr != nil {
			log.Printf("ERROR Could not read '%s': %s.", jsonReportFile, readErr)
			close(outputCh)
			return
		}

		var content common.DuplicatesReport
		marshalErr := json.Unmarshal(dataBytes, &content)
		if marshalErr != nil {
			log.Printf("ERROR Could not unmarshal '%s': %s.", jsonReportFile, marshalErr)
			close(outputCh)
			return
		}

		log.Printf("INFO This vault has a total of %d items of which %d are duplicates (%0.0f%%)", content.ItemCount, content.DupesCount, (float64(content.DupesCount)/float64(content.ItemCount))*100.0)
		for _, rec := range content.Duplicates {
			outputCh <- rec
		}
		close(outputCh)
		return
	}()

	return outputCh
}
