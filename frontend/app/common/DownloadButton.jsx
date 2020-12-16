import React from "react";
import PropTypes from "prop-types";
import {authenticatedFetch} from "../auth";
import LoadingIndicator from "../LoadingIndicator.jsx";

class DownloadButton extends React.Component {
    static propTypes = {
        vaultId: PropTypes.string.isRequired,
        oid: PropTypes.string.isRequired,
        fileName: PropTypes.string.isRequired
    }

    constructor(props) {
        super(props);

        this.state = {
            lastError: null,
            downloading: false,
            succeeded: false,
        };

        this.performDownload = this.performDownload.bind(this);
    }

    async performDownload() {
        this.setState({downloading: true, lastError: null});
        const result = await authenticatedFetch(`/api/vault/${this.props.vaultId}/${this.props.oid}/token`);
        switch(result.status) {
            case 200:
                const serverData = await result.json();
                const a = document.createElement("a");
                console.log("token download uri is ", serverData.uri);
                a.href = serverData.uri;
                a.download = this.props.fileName;
                a.target = "_blank";
                a.click();
                this.setState({downloading: false, succeeded: true, lastError: null});
                break;
            case 404:
                this.setState({downloading: false, succeeded: false, lastError: "This item does not exist on the server or download token expired"});
                break;
            case 502:
            case 503:
            case 504:
                this.setState({downloading: false, succeeded: false, lastError: "Server is not responding, will retry in 3s..."},
                    ()=>window.setTimeout(()=>this.performDownload(), 3000));
                break;
            default:
                this.setState({downloading: false, succeeded: false, lastError: "Server error, see logs"})
        }
    }

    render() {
        return <div>
            {
                this.state.downloading ? <LoadingIndicator messageText="Downloading..."/> :
                    <button onClick={this.performDownload}>&gt;&nbsp;&nbsp;Download&nbsp;&nbsp;&lt;</button>
            }
            {
                this.state.succeeded ? <p>Download running, please check your browser downloads</p> : null
            }
            {
                this.state.lastError ? <p className="error">{this.lastError}</p> : null
            }
        </div>
    }
}

export default DownloadButton;