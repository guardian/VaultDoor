import React from 'react';
import {DeleteOutline} from "@material-ui/icons";

interface EntrySummaryLiProps {
    entry:FileEntry;
    entryClickedCb: (entry:FileEntry)=>void;
}

/**
 * render an entry for a list summary
 */
const EntrySummaryLi:React.FC<EntrySummaryLiProps> = (props) => {
    const pathParts = props.entry.attributes.name.split("/");
    const fileName = pathParts.length>0 ? pathParts[pathParts.length - 1] : "(no name)";
    const isInTrash = props.entry.metadata ? props.entry.metadata.includes("MXFS_INTRASH=true") : false;

    return <li key={props.entry.oid} className="clickable" onClick={()=>props.entryClickedCb(props.entry)}>
        <p className="filename">{fileName}</p>
        <p className="supplementary">{props.entry.attributes.size} { isInTrash ? <DeleteOutline id="delete-icon" style={{marginLeft: "1em", height: "1em"}}/> : null}</p>
        <p className="supplementary">{props.entry.attributes.ctime}</p>
    </li>
}

export default EntrySummaryLi;