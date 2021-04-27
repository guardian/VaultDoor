import React from "react";
import PropTypes from "prop-types";
import EntrySummaryLi from "./EntrySummaryLi";
import DetailsPanel from "./DetailsPanel.jsx";
import { withStyles } from "@material-ui/core/styles";

const styles = {
  container: {
    marginLeft: "auto",
    marginRight: "auto",
    overflow: "hidden",
    width: "90vw",
    marginTop: "2em",
    height: "80vh",
    display: "grid",
    borderStyle: "dashed",
    paddingBottom: "1em",
    gridTemplateColumns: "50% 50%",
    gridTemplateRows: "3.4em auto",
  },
  titleRow: {
    gridColumnStart: 1,
    gridColumnEnd: 3,
    gridRowStart: 1,
    gridRowEnd: 2,
    borderBottomStyle: "dashed",
    borderBottomWidth: "thin",
    padding: "0.5em",
  },
  subPanel: {
    overflowX: "hidden",
    overflowY: "auto",
  },
  leftPanel: {
    borderRightStyle: "dashed",
    borderRightWidth: "thin",
    gridColumnStart: 1,
    gridColumnEnd: 2,
    gridRowStart: 2,
    gridRowEnd: 3,
  },
  rightPanel: {
    gridColumnStart: 2,
    gridColumnEnd: 3,
    gridRowStart: 2,
    gridRowEnd: 3,
  },
};

class ResultsPanel extends React.Component {
  static propTypes = {
    entries: PropTypes.array.isRequired,
    previewRequestedCb: PropTypes.func.isRequired,
    projectClicked: PropTypes.func,
    vaultId: PropTypes.string,
  };

  constructor(props) {
    super(props);

    this.state = {
      selectedEntry: null,
      internalError: null,
    };

    this.entryClicked = this.entryClicked.bind(this);
  }

  static getDerivedStateFromError(err) {
    return {
      internalError: err.toString(),
    };
  }

  componentDidCatch(error, errorInfo) {
    console.error("The following error occurred in ResultsPanel:");
    console.error(error, errorInfo);
  }

  entrySummary() {
    return "Found " + this.props.entries.length + " files";
  }

  entryClicked(selectedEntry) {
    this.setState({ selectedEntry: selectedEntry });
  }

  render() {
    if (this.state.internalError) {
      return (
        <div className="results-panel">
          <div className="results-subpanel-wide">
            <p className="error">
              The results panel failed: {this.state.internalError}
            </p>
            <p className="error">Please reload the page</p>
          </div>
        </div>
      );
    }

    return (
      <div className={this.props.classes.container}>
        <div className={this.props.classes.titleRow}>
          <span className="centered large">{this.entrySummary()}</span>
        </div>
        <div
          className={`${this.props.classes.subPanel} ${this.props.classes.leftPanel}`}
        >
          <ul className="silent-list">
            {this.props.entries.map((entry) => (
              <EntrySummaryLi
                entry={entry}
                entryClickedCb={this.entryClicked}
              />
            ))}
          </ul>
        </div>
        <DetailsPanel
          className={`${this.props.classes.subPanel} ${this.props.classes.rightPanel}`}
          entry={this.state.selectedEntry}
          previewRequestedCb={this.props.previewRequestedCb}
          projectClicked={this.props.projectClicked}
          vaultId={this.props.vaultId}
        />
      </div>
    );
  }
}

export default withStyles(styles)(ResultsPanel);
