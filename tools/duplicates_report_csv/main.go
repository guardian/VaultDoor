package main

import "flag"

func main() {
	inputFile := flag.String("in", "", "Name of the .json report to start with")
	outputFile := flag.String("out", "duplicates.csv", "Name of the .csv report to output")
	flag.Parse()

	recordsCh := AsyncLoad(*inputFile)
	processor := NewAsyncProcessor()
	processedCh := processor.GenerateSummary(recordsCh, 10)
	errCh := AsyncWriter(processedCh, *outputFile)

	//we must wait for errCh because otherwise we terminate right away!
	<-errCh
}
