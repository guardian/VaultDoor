import React from 'react';
import PropTypes from 'prop-types';
import PathView from "./PathView.jsx";

class DetailsPanel extends React.Component {
    static propTypes = {
        entry: PropTypes.object.isRequired
    };

    render(){
        const entry = this.props.entry;
        if(!entry){
            return <div className="results-subpanel">
                <span className="centered filename semilarge">Nothing selected</span>
            </div>
        }
        const pathParts = entry.attributes.name.split("/");
        const fileName = pathParts.length>0 ? pathParts[pathParts.length - 1] : "(no name)";

        return <div className="results-subpanel">
            <span className="centered filename semilarge">{fileName}</span>
            <PathView pathParts={pathParts.slice(0,-1)} truncateMode={PathView.TRUNC_END} limit={5}/>
        </div>
    }
}

export default DetailsPanel;