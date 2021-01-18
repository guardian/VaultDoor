import React from "react";
import PropTypes from "prop-types";
import { authenticatedFetch } from "../auth";

class VaultSelector extends React.Component {
  static propTypes = {
    currentvault: PropTypes.string.isRequired,
    vaultWasChanged: PropTypes.func.isRequired,
  };

  constructor(props) {
    super(props);

    this.state = {
      loading: false,
      lastError: null,
      knownVaults: [],
    };
  }

  async refresh() {
    const response = await authenticatedFetch("/api/vault");
    switch (response.status) {
      case 200:
        const content = await response.json();
        if (Array.isArray(content)) {
          const reversed = content.reverse();
          this.setState({
            loading: false,
            lastError: null,
            knownVaults: reversed,
          });
          if (this.props.currentvault === "")
            this.props.vaultWasChanged(reversed[0].vaultId);
        } else {
          console.error(
            "Expected server response to be an array, got ",
            content
          );
          this.setState({ lastError: "Could not understand server response" });
        }
        break;
      default:
        const errorContent = await response.text();
        console.error(errorContent);
        this.setState({ lastError: `Server error ${response.status}` });
        break;
    }
  }

  componentDidMount() {
    this.refresh();
  }

  render() {
    return (
      <span className="form-spacing">
        {this.state.lastError ? (
          <p className="error">{this.state.lastError}</p>
        ) : (
          <>
            <label htmlFor="vaultsDropdown">Select vault: </label>
            <select
              id="vaultsDropdown"
              value={this.props.currentvault}
              onChange={(evt) => this.props.vaultWasChanged(evt.target.value)}
            >
              {this.state.knownVaults.map((entry) => (
                <option value={entry.vaultId}>{entry.name}</option>
              ))}
            </select>
          </>
        )}
      </span>
    );
  }
}

export default VaultSelector;
