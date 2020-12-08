import React from "react";
import PropTypes from "prop-types";
import LoadingIndicator from "../LoadingIndicator.jsx";

class DownloadFile extends React.Component {
    static propTypes = {
        oid: PropTypes.string.isRequired,
        vaultId: PropTypes.string.isRequired,
        fileName: PropTypes.string.isRequired
    }

    constructor(props) {
        super(props);
        this.state = {
            isDownloading: false,
            lastError: null,
            stage: "Downloading"
        }

        this.performDownload = this.performDownload.bind(this);
    }

    async performDownload() {
        this.setState({
            isDownloading: true,
            lastError: null,
            stage: "Downloading"
        });

        const downloadUri = `/api/vault/${this.props.vaultId}/${this.props.oid}`;
        const bearerToken = localStorage.getItem("vaultdoor:access-key");

        const result = await fetch(downloadUri, {
            headers: {
                Authorization: "Bearer " + bearerToken
            }
        });

        switch(result.status) {
            case 200:
                const blob = await result.blob();
                this.setState({stage: "Saving"});
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement("a");
                a.href = url;
                a.download = this.props.fileName;
                a.click();
                this.setState({isDownloading: false, lastError: null})
                break;

            case 401:
            case 403:
                await response.text();
                this.setState({isDownloading: false, lastError: "Permission denied, maybe your login has expired?"})
                break;
            case 404:
                await response.text();
                this.setState({isDownloading: false, lastError: "Content not found"})
                break;
            case 500:
                try {
                    const errorMsg = await response.json();
                    this.setState({isDownloading: false, lastError: errorMsg.detail ?? "Unknown error, see server logs"});
                } catch(e) {
                    console.error("Could not process server response: ", e);
                    this.setState({isDownloading: false, lastError: "Server error that could not be processed, see console"});
                }
                break;
            case 502:
            case 503:
            case 504:
                await response.text();
                this.setState({isDownloading: false, lastError: "Server is not responding, please try again later"})
                break;
            default:
                await response.text();
                this.setState({isDownloading: false, lastError: `Unrecognised server response ${response.status}`});
                break;
        }
    }

    render() {
        return <div>
            {
                this.state.lastError ? <p className="error">{this.state.lastError}</p> : <p/>
            }
            {
                this.state.isDownloading ? <LoadingIndicator messageText={this.state.stage}/> :
                    <button onClick={this.performDownload}>&gt;&nbsp;&nbsp;Download&nbsp;&nbsp;&lt;</button>
            }
        </div>
    }
}

export default DownloadFile;