import React from "react";
import PropTypes from "prop-types";

class MetadataTabView extends React.Component {
  static propTypes = {
    metaDataString: PropTypes.string,
    tabNames: PropTypes.array.isRequired,
    tabPrefixes: PropTypes.array.isRequired,
  };

  constructor(props) {
    super(props);

    this.state = {
      selectedTabIndex: 0,
    };
  }

  breakdownMetadataString() {
    return this.props.metaDataString
      .split(/\s*,\s*/)
      .map((elmt) => elmt.split("="))
      .reduce((acc, elem) => Object.assign(acc, { [elem[0]]: elem[1] }), {});
  }

  tableContent() {
    if (this.state.selectedTabIndex >= this.props.tabPrefixes.length) {
      console.error(
        "Requested tab " +
          this.state.selectedTabIndex +
          " but there are only " +
          this.props.tabPrefixes.length +
          " categories"
      );
      return (
        <table>
          <tbody>
            <tr>
              <td>
                <i>Not properly configured</i>
              </td>
            </tr>
          </tbody>
        </table>
      );
    }
    const metadata = this.breakdownMetadataString();

    return (
      <table className="metadata-view-table">
        {Object.keys(metadata)
          .sort()
          .filter((key) =>
            key.startsWith(this.props.tabPrefixes[this.state.selectedTabIndex])
          )
          .map((key, idx) => (
            <tr key={idx}>
              <td className="metadata-view-key">{key}</td>
              <td className="metadata-view-key">{metadata[key]}</td>
            </tr>
          ))}
      </table>
    );
  }

  render() {
    return (
      <div className="metadata-view-container">
        <span className="tab-header-row">
          {this.props.tabNames.map((name, idx) => (
            <span
              className={
                "tab-header" +
                (idx === this.props.selectedTabIndex ? " selected" : "")
              }
              onClick={() => this.setState({ selectedTabIndex: idx })}
              key={idx}
            >
              {name}
            </span>
          ))}
        </span>
        {this.tableContent()}
      </div>
    );
  }
}

export default MetadataTabView;
