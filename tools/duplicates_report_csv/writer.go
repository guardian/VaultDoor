package main

import (
	"encoding/csv"
	"log"
	"os"
)

func AsyncWriter(inputCh chan SummaryRecord, filename string) chan error {
	errCh := make(chan error, 1)
	go func() {
		f, err := os.OpenFile(filename, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0644)
		if err != nil {
			log.Printf("ERROR Could not open output file '%s': %s", filename, err)
			errCh <- err
			return
		}
		defer f.Close()

		writer := csv.NewWriter(f)

		writer.Write(SummaryRecordHeaderRow())

		for {
			rec, isOk := <-inputCh
			if !isOk {
				log.Printf("INFO AsyncWriter got end of stream, exiting")
				writer.Flush()
				errCh <- nil
				return
			}

			err := writer.Write(rec.toCSVArray())
			if err != nil {
				log.Printf("ERROR Could not write output: %s", err)
				writer.Flush()
				errCh <- err
				return
			}
		}
	}()

	return errCh
}
