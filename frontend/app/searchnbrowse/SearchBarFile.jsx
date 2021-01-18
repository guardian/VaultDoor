import React from "react";
import PropTypes from "prop-types";
import VaultSelector from "./NewVaultSelector";
import { Grid, Input } from "@material-ui/core";

class SearchBarFile extends React.Component {
  static propTypes = {
    filePath: PropTypes.string.isRequired,
    filePathUpdated: PropTypes.func.isRequired,
    selectedVault: PropTypes.string.isRequired,
    vaultSelectionChanged: PropTypes.func.isRequired,
  };

  constructor(props) {
    super(props);

    this.state = {
      internalError: null,
    };
  }

  static getDerivedStateFromError(err) {
    return {
      internalError: err.toString(),
    };
  }

  componentDidCatch(error, errorInfo) {
    console.error("The following error occurred in SearchBarFile:");
    console.error(error, errorInfo);
  }

  render() {
    if (this.state.internalError) {
      return (
        <div className="searchbar">
          <p className="error">
            The search bar component failed: {this.state.internalError}
          </p>
          <p className="error">Please reload the page</p>
        </div>
      );
    }
    return (
      <Grid direction="row" container>
        <Grid item xs="3">
          <VaultSelector
            currentvault={this.props.selectedVault}
            vaultWasChanged={this.props.vaultSelectionChanged}
          />
        </Grid>
        <Grid item xs="3">
          <label htmlFor="filePathSearch">File path: </label>
          <Input
            type="text"
            id="filePathSearch"
            onChange={(evt) => this.props.filePathUpdated(evt.target.value)}
            value={this.props.filePath}
          />
        </Grid>
      </Grid>
    );
  }
}

export default SearchBarFile;
