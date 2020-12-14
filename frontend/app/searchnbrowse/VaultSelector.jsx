import React from 'react';
import PropTypes from 'prop-types';
import {authenticatedFetch} from "../auth";

class VaultSelector extends React.Component {
    static propTypes = {
        currentvault: PropTypes.string.isRequired,
        vaultWasChanged: PropTypes.func.isRequired
    };

    constructor(props){
        super(props);

        this.state = {
            loading: false,
            lastError: null,
            knownVaults: []
        }
    }

    async refresh() {
        const response = await authenticatedFetch("/api/vault");
        const content = await response.json();
        this.setState({loading: false, lastError: null, knownVaults: content});
        if(this.props.currentvault==="") this.props.vaultWasChanged(content[0].vaultId)
    }

    componentDidMount() {
        this.refresh();
    }

    render() {
        return <span className="form-spacing">
            <label htmlFor="vaultsDropdown">Select vault: </label>
            <select  id="vaultsDropdown" value={this.props.currentvault} onChange={evt=>this.props.vaultWasChanged(evt.target.value)}>
                {this.state.knownVaults.map(entry=><option value={entry.vaultId} key={entry.vaultId}>{entry.name}</option>)}
            </select>
        </span>
    }
}

export default VaultSelector;