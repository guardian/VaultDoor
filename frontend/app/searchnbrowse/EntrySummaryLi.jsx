import React from 'react';
import PropTypes from 'prop-types';
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";

/**
 * render an entry for a list summary
 */
class EntrySummaryLi extends React.Component {
    static propTypes = {
        entry: PropTypes.object.isRequired,
        entryClickedCb: PropTypes.func.isRequired
    };

    render() {
        const entry = this.props.entry;
        const pathParts = entry.attributes.name.split("/");
        const fileName = pathParts.length>0 ? pathParts[pathParts.length - 1] : "(no name)";
        const isInTrash = entry.metadata.includes("MXFS_INTRASH=true")

        return <li key={entry.oid} className="clickable" onClick={()=>this.props.entryClickedCb(entry)}>
            <p className="filename">{fileName}</p>
            <p className="supplementary">{entry.attributes.size} { isInTrash ? <FontAwesomeIcon icon="trash-alt" style={{marginLeft: "1em", height: "1em"}}/> : null}</p>
            <p className="supplementary">{entry.attributes.ctime}</p>
        </li>
    }
}

export default EntrySummaryLi;