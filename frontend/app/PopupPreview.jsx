import React from "react";
import PropTypes from "prop-types";
import { authenticatedFetch } from "./auth";
import DownloadButton from "./common/DownloadButton";
import { IconButton } from "@material-ui/core";
import { Close } from "@material-ui/icons";
import SearchComponentContext from "./searchnbrowse/SearchComponentContext";

class PopupPreview extends React.Component {
  static PREVIEW_IMAGE = 1;
  static PREVIEW_VIDEO = 2;
  static PREVIEW_AUDIO = 3;

  static propTypes = {
    oid: PropTypes.string.isRequired,
    dialogClose: PropTypes.func.isRequired,
  };

  constructor(props) {
    super(props);

    this.state = {
      loading: false,
      size: -1,
      type: "application/unknown",
      lastError: null,
    };

    this.loadup = this.loadup.bind(this);
  }

  setStatePromise(newState) {
    return new Promise((resolve, reject) => {
      try {
        this.setState(newState, () => resolve());
      } catch (err) {
        reject(err);
      }
    });
  }

  async loadup() {
    await this.setStatePromise({ loading: true });

    const url = "/api/vault/" + this.context.vaultId + "/" + this.props.oid;
    const response = await authenticatedFetch(url, { method: "HEAD" });
    if (response.ok) {
      const maybeContentType = response.headers.get("content-type");
      await this.setStatePromise({
        size: response.headers.get("content-length"),
        type: maybeContentType ? maybeContentType : "application/unknown",
        loading: false,
      });
    } else {
      console.error("Could not load: ", response.error());
      await this.setStatePromise({
        loading: false,
        lastError: response.error(),
      });
    }
  }

  componentDidMount() {
    this.loadup();
  }

  renderDialogContent() {
    if (this.state.loading) {
      return <p className="centered large">Loading...</p>;
    }
    return (
      <div>
        <table>
          <tbody>
            <tr>
              <td>Type</td>
              <td>{this.state.type}</td>
            </tr>
            <tr>
              <td>Size</td>
              <td>{this.state.size}</td>
            </tr>
          </tbody>
        </table>
        <DownloadButton
          oid={this.props.oid}
          fileName="changeme.dat"
        />
      </div>
    );
  }

  render() {
    return (
      <div className="popup">
        <div className="popup_inner">
          {/*<span style={{float: "right"}} className="clickable" onClick={this.props.dialogClose}>*/}
          {/*    <FontAwesomeIcon icon="times" size="2x" style={{padding: "5px"}}/>*/}
          {/*</span>*/}
          <IconButton onClick={this.props.dialogClose}>
            <Close />
          </IconButton>
          {this.renderDialogContent()}
        </div>
      </div>
    );
  }
}

PopupPreview.contextType = SearchComponentContext;

export default PopupPreview;
