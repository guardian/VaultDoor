import React from 'react';
import PropTypes from 'prop-types';
import PathView from "./PathView.jsx";
import CommissionProjectView from "../metadata/CommissionProjectView.jsx";
import MetadataTabView from "../metadata/MetadataTabView.jsx";

class DetailsPanel extends React.Component {
    static propTypes = {
        entry: PropTypes.object,
        previewRequestedCb: PropTypes.func.isRequired
    };

    static mdTabNames = [
        "GNM metadata",
        "Media Info",
        "MXFS metadata",
        "All"
    ];

    static mdTabPrefixes = [
        "GNM_",
        "_mediainfo",
        "MXFS_",
        ""
    ];

    render(){
        const entry = this.props.entry;
        if(!entry){
            return <div className="results-subpanel">
                <span className="centered filename semilarge">Nothing selected</span>
            </div>
        }
        const pathParts = entry.attributes.name.split("/").filter(entry=>entry.length>0);
        const fileName = pathParts.length>0 ? pathParts[pathParts.length - 1] : "(no name)";

        return <div className="results-subpanel">
            <span className="centered filename semilarge">{fileName}</span>
            <span className="centered">{this.props.entry.gnmMetadata ? this.props.entry.gnmMetadata.type : "(no filetype)"}</span>
            <a className="centered clickable" onClick={()=>this.props.previewRequestedCb(entry.oid)}>&gt;&gt; Preview &lt;&lt;</a>
            <CommissionProjectView entry={this.props.entry}/>
            <PathView pathParts={pathParts.slice(0,-1)} truncateMode={PathView.TRUNC_MIDDLE} limit={5}/>
            <MetadataTabView tabNames={DetailsPanel.mdTabNames} tabPrefixes={DetailsPanel.mdTabPrefixes} metaDataString={entry.customMeta} />
        </div>
    }
}

export default DetailsPanel;