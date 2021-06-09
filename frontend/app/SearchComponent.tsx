import React from "react";
import SearchBarFile from "./searchnbrowse/SearchBarFile";
import ndjsonStream from "can-ndjson-stream";
import ResultsPanel from "./searchnbrowse/ResultsPanel";
import PopupPreview from "./PopupPreview.jsx";
import {RouteComponentProps, withRouter} from "react-router-dom";
import { authenticatedFetch } from "./auth";
import SearchComponentContext from "./searchnbrowse/SearchComponentContext";

interface SearchComponentState {
  searching?: boolean;
  fileEntries?: FileEntry[];
  requestedPreview?: any;
  currentReader?: ReadableStreamReader<FileEntry>;
  currentAbort?: any;
  vaultId?: string;
}

class SearchComponent extends React.Component<RouteComponentProps, SearchComponentState> {
  static resultsLimit = 100;

  constructor(props:RouteComponentProps) {
    super(props);

    this.state = {
      searching: false,
      fileEntries: [],
      requestedPreview: undefined,
      currentReader: undefined,
      currentAbort: undefined,
      vaultId: undefined
    };

    this.asyncDownload = this.asyncDownload.bind(this);

    this.previewRequested = this.previewRequested.bind(this);
    this.previewClosed = this.previewClosed.bind(this);

    this.projectClicked = this.projectClicked.bind(this);

    this.vaultIdUpdated = this.vaultIdUpdated.bind(this);
  }

  setStatePromise(newState:SearchComponentState) {
    return new Promise<void>((resolve, reject) => {
      try {
        this.setState(newState, () => resolve());
      } catch (err) {
        reject(err);
      }
    });
  }

  async asyncDownload(url:string) {
    const abortController = new AbortController();

    const response = await authenticatedFetch(url, {
      signal: abortController.signal,
    });
    if (response.status !== 200) {
      console.error(`Could not load data: server error ${response.status}`);
      const rawData = await response.text();
      console.error(`Server said ${rawData}`);

      return;
    }

    const stream = await ndjsonStream<Uint8Array,FileEntry>(response.body);
    const reader = stream.getReader();

    await this.setStatePromise({
      currentReader: reader,
      currentAbort: abortController,
    });

    const parentComponent = this;

    const readNextChunk = (reader:ReadableStreamReader<FileEntry>) => {
      reader.read().then(({ done, value }) => {

        if (value) {
          parentComponent.setState(
            (oldState) => {
              return {
                fileEntries: oldState.fileEntries ? oldState.fileEntries.concat([value]) : [value],
                searching: !done,
              };
            },
            () => {
              if (
                  parentComponent.state.fileEntries &&
                  parentComponent.state.fileEntries.length >= SearchComponent.resultsLimit
              ) {
                console.log("Reached limit, stopping");
                reader.cancel();
              }
            }
          );
        } else {
          console.warn("Got no data");
        }
        if (done) {
          parentComponent.setState({ searching: false });
        } else {
          readNextChunk(reader);
        }
      });
    }
    //readNextChunk = readNextChunk.bind(this);
    readNextChunk(reader);
  }

  abortReadInProgress() {
    const myRef = this;
    return new Promise<void>((resolve, reject) => {
      if (!myRef.state.currentReader) {
        resolve();
        return;
      }
      //if(myRef.state.currentAbort) myRef.state.currentAbort.abort();
      myRef.state.currentReader.cancel().then((_:void) => {
        function waitForSearch() {
          if (myRef.state.searching) {
            console.log("Waiting for search to cancel...");
            window.setTimeout(waitForSearch, 500);
          } else {
            console.log("Search cancelled");
            resolve();
          }
        }
        waitForSearch();
      });
    });
  }

  newSearch(url:string) {
    this.abortReadInProgress().then((_) =>
      this.setState({ searching: true, fileEntries: [] }, () =>
        this.asyncDownload(url).catch((err) => {
          console.error(err);
          this.setState({ searching: false });
        })
      )
    );
  }

  previewRequested(oid:string) {
    console.log("preview requested: ", oid);
    this.setState({ requestedPreview: oid });
  }

  previewClosed() {
    this.setState({ requestedPreview: null });
  }

  projectClicked(projectId:string) {
    this.props.history.push("/byproject?project=" + projectId);
  }

  vaultIdUpdated(newValue:string) {
    this.setState({vaultId: newValue});
  }

  render() {
    return (
      <div className="windowpanel">
        <SearchComponentContext.Provider value={{ vaultId: this.state.vaultId, vaultIdUpdated: this.vaultIdUpdated}}>
          <SearchBarFile
            searchUrlChanged={(newUrl) => {
              if (!newUrl.includes("/undefined/")) {
                this.newSearch(newUrl);
              }
            }}
          />
          <span
            style={{
              float: "right",
              marginRight: "2em",
              display: this.state.searching ? "inline-block" : "none",
            }}
          >
            Loaded {this.state.fileEntries?.length}...
          </span>
          <ResultsPanel
            entries={this.state.fileEntries ?? []}
            previewRequestedCb={this.previewRequested}
            projectClicked={this.projectClicked}
          />
          {this.state.requestedPreview ? (
            <PopupPreview
              oid={this.state.requestedPreview}
              dialogClose={this.previewClosed}
            />
          ) : (
            ""
          )}
        </SearchComponentContext.Provider>
      </div>
    );
  }
}

export default withRouter(SearchComponent);
