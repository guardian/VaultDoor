import React from 'react';
import PropTypes from 'prop-types';
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";

class PathView extends React.Component {
    static TRUNC_START=1;       //truncate by removing the start
    static TRUNC_MIDDLE=2;      //truncate by removing the middle
    static TRUNC_END=3;         //truncate by removing the end

    static propTypes = {
        pathParts: PropTypes.array.isRequired,
        limit: PropTypes.number,
        truncateMode: PropTypes.number.isRequired,       //should be one of the TRUNC_ constants above
        stripStart: PropTypes.number
    };

    truncateParts() {
        const pathParts = this.props.stripStart ? this.props.pathParts.slice(this.props.stripStart) : this.props.pathParts;

        switch(this.props.truncateMode){
            case PathView.TRUNC_START:
                const truncateFrom = this.props.limit;
                return ["..."].concat(pathParts.slice(truncateFrom));
            case PathView.TRUNC_END:
                const truncateTo = this.props.limit;
                return pathParts.slice(0, truncateTo).concat(["..."]);
            default:
                const sectionLength = this.props.limit / 2;
                const firstPart = pathParts.slice(0, sectionLength+1);
                const lastPart = ["..."].concat(this.props.pathParts.slice(this.props.pathParts.length - sectionLength +1));
                return firstPart.concat(lastPart);
        }
    }

    render(){
        if(this.props.pathParts==null) return;
        const truncated = this.props.pathParts.length>this.props.limit;

        const visibleParts = truncated ? this.truncateParts() : this.props.pathParts;

        return <ul className="pathview">{
            visibleParts.map((part,idx)=>{
                const indent = (idx * 0.25) + 1;
                return <li className="pathview" style={{paddingLeft: indent + "em"}}><FontAwesomeIcon icon="folder-open"/>{part}</li>
            })
        }</ul>
    }
}

export default PathView;