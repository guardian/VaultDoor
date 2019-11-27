import React from 'react';
import PropTypes from 'prop-types';
import EntrySummaryLi from './EntrySummaryLi.jsx';
import DetailsPanel from "./DetailsPanel.jsx";

class ResultsPanel extends React.Component {
    static propTypes = {
        entries: PropTypes.array.isRequired,
        previewRequestedCb: PropTypes.func.isRequired
    };

    constructor(props){
        super(props);

        this.state = {
            selectedEntry: null
        };

        this.entryClicked = this.entryClicked.bind(this);
    }
    entrySummary(){
        return "Found " + this.props.entries.length + " files";
    }

    entryClicked(selectedEntry){
        this.setState({selectedEntry: selectedEntry});
    }

    render(){
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
            <DetailsPanel entry={this.state.selectedEntry} previewRequestedCb={this.props.previewRequestedCb}/>
        </div>
    }
}

export default ResultsPanel;