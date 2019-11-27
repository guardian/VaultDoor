import React from 'react';
import PropTypes from 'prop-types';

class ContentHolder extends React.Component {
    static propTypes = {
        vaultId: PropTypes.string.isRequired,
        oid: PropTypes.string.isRequired,
        contentType: PropTypes.string.isRequired
    };

    render() {
        const mimeParts = this.props.contentType.split("/");
        switch (mimeParts[0]) {
            case "video":
                return <video src={"/api/vault/" + this.props.vaultId + "/" + this.props.oid}/>;
            case "audio":
                return <audio src={"/api/vault/" + this.props.vaultId + "/" + this.props.oid}/>;
            case "image":
                return <img src={"/api/vault/" + this.props.vaultId + "/" + this.props.oid} alt="image preview"/>;
            default:
                return <p className="centered">Don't know how to display a type of {this.props.contentType}.<br/>You could try to <a href={"/api/vault/" + this.props.vaultId + "/" + this.props.oid} target="_blank">download</a> it instead.</p>;
        }
    }
}

export default ContentHolder;