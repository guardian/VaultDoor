import React from 'react';
import PropTypes from 'prop-types';
import VaultSelector from './VaultSelector.jsx';

class SearchBarFile extends React.Component {
    static propTypes = {
        filePath: PropTypes.string.isRequired,
        filePathUpdated: PropTypes.func.isRequired,
        selectedVault: PropTypes.string.isRequired,
        vaultSelectionChanged: PropTypes.func.isRequired
    };

    render(){
        return <div className="searchbar">
            <VaultSelector currentvault={this.props.selectedVault} vaultWasChanged={this.props.vaultSelectionChanged}/>
            <label htmlFor="filePathSearch">File path: </label>
            <input className="form-spacing" type="text" id="filePathSearch" onChange={evt=>this.props.filePathUpdated(evt.target.value)} value={this.props.filePath}/>
        </div>
    }
}

export default SearchBarFile;