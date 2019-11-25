import React from 'react';
import PropTypes from 'prop-types';

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
        const response = await fetch("/api/vault");
        const content = await response.json();
        this.setState({loading: false, lastError: null, knownVaults: content});
        if(this.props.currentvault==="") this.props.vaultWasChanged(content[0].vaultId)
    }

    componentDidMount() {
        this.refresh();
    }

    render() {
        return <span>
            <label htmlFor="vaultsDropdown">Select vault: </label>
            <select id="vaultsDropdown" value={this.props.currentvault} onChange={evt=>this.props.vaultWasChanged(evt.target.value)}>
                {this.state.knownVaults.map(entry=><option value={entry.vaultId}>{entry.vaultId}</option>)}
            </select>
        </span>
    }
}

export default VaultSelector;