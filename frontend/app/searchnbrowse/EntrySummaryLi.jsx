import React from 'react';
import PropTypes from 'prop-types';

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


        return <li key={entry.oid} className="clickable" onClick={()=>this.props.entryClickedCb(entry)}>
            <p className="filename">{fileName}</p>
            <p className="supplementary">{entry.attributes.size}</p>
            <p className="supplementary">{entry.attributes.ctime}</p>
        </li>
    }
}

export default EntrySummaryLi;