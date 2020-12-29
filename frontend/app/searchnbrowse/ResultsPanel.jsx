import React from 'react';
import PropTypes from 'prop-types';
import EntrySummaryLi from './EntrySummaryLi';
import DetailsPanel from "./DetailsPanel.jsx";

class ResultsPanel extends React.Component {
    static propTypes = {
        entries: PropTypes.array.isRequired,
        previewRequestedCb: PropTypes.func.isRequired,
        projectClicked: PropTypes.func,
        vaultId: PropTypes.string
    };

    constructor(props){
        super(props);

        this.state = {
            selectedEntry: null,
            internalError: null
        };

        this.entryClicked = this.entryClicked.bind(this);
    }

    static getDerivedStateFromError(err) {
        return {
            internalError: err.toString()
        }
    }

    componentDidCatch(error, errorInfo) {
        console.error("The following error occurred in ResultsPanel:")
        console.error(error, errorInfo);
    }

    entrySummary(){
        return "Found " + this.props.entries.length + " files";
    }

    entryClicked(selectedEntry){
        this.setState({selectedEntry: selectedEntry});
    }

    render(){
        if(this.state.internalError) {
            return <div className="results-panel">
                <div className="results-subpanel-wide">
                    <p className="error">The results panel failed: {this.state.internalError}</p>
                    <p className="error">Please reload the page</p>
                </div>
            </div>
        }

        return <div className="results-panel">
            <div className="results-subpanel-wide">
                <span className="centered large">{this.entrySummary()}</span>
            </div>
            <div className="results-subpanel">
                <ul className="silent-list">
                    {
                        this.props.entries.map(entry=><EntrySummaryLi entry={entry} entryClickedCb={this.entryClicked}/>)
                    }
                </ul>
            </div>
            <DetailsPanel entry={this.state.selectedEntry}
                          previewRequestedCb={this.props.previewRequestedCb}
                          projectClicked={this.props.projectClicked}
                          vaultId={this.props.vaultId}
            />
        </div>
    }
}

export default ResultsPanel;