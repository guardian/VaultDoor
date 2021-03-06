import React from "react";
import PropTypes from "prop-types";
import PathView, { TRUNC_END, TRUNC_MIDDLE, TRUNC_START } from "./PathView";
import CommissionProjectView from "../metadata/CommissionProjectView.jsx";
import MetadataTabView from "../metadata/MetadataTabView.jsx";
import DownloadButton from "../common/DownloadButton";
import SearchComponentContext from "./SearchComponentContext";

class DetailsPanel extends React.Component {
  static propTypes = {
    entry: PropTypes.object,
    previewRequestedCb: PropTypes.func.isRequired,
    projectClicked: PropTypes.func,
    className: PropTypes.string,
  };

  static mdTabNames = ["GNM metadata", "Media Info", "MXFS metadata", "All"];

  static mdTabPrefixes = ["GNM_", "_mediainfo", "MXFS_", ""];

  constructor(props) {
    super(props);
    this.projectClicked = this.projectClicked.bind(this);
  }

  projectClicked() {
    const entry = this.props.entry;
    if (!entry) return;

    if (
      this.props.projectClicked &&
      entry.gnmMetadata &&
      entry.gnmMetadata.projectId
    )
      this.props.projectClicked(entry.gnmMetadata.projectId);
  }

  render() {
    const entry = this.props.entry;
    if (!entry) {
      return (
        <div className={this.props.className}>
          <span className="centered filename semilarge">Nothing selected</span>
        </div>
      );
    }
    const pathParts = entry.attributes.name
      .split("/")
      .filter((entry) => entry.length > 0);
    const fileName =
      pathParts.length > 0 ? pathParts[pathParts.length - 1] : "(no name)";

    return (
      <div className={this.props.className}>
        <span className="centered filename semilarge">{fileName}</span>
        <span className="centered">
          {entry.gnmMetadata
            ? entry.gnmMetadata.type
            : "(no filetype)"}
        </span>
        {this.context.vaultId && entry ? (
          <span className="centered">
            <DownloadButton
              vaultId={this.context.vaultId}
              oid={this.props.entry.oid}
              fileName={fileName}
            />
          </span>
        ) : null}
        {/*
            "preview" link is currently hidden until proper proxy playback is implemented
            */}
        <a
          style={{ display: "none" }}
          className="centered clickable"
          onClick={() => this.props.previewRequestedCb(entry.oid)}
        >
          &gt;&gt; Preview &lt;&lt;
        </a>
        <CommissionProjectView
          entry={this.props.entry}
          clickable={true}
          onProjectClicked={this.projectClicked}
        />
        <PathView
          pathParts={pathParts.slice(0, -1)}
          truncateMode={TRUNC_MIDDLE}
          limit={5}
          stripStart={5}
        />
        <MetadataTabView
          tabNames={DetailsPanel.mdTabNames}
          tabPrefixes={DetailsPanel.mdTabPrefixes}
          metaDataString={entry.customMeta}
        />
      </div>
    );
  }
}

DetailsPanel.contextType = SearchComponentContext;

export default DetailsPanel;
