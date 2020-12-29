import React, {useState, useEffect} from "react";
import {authenticatedFetch} from "../auth";
import {makeStyles, MenuItem, Select} from "@material-ui/core";

interface VaultSelectorProps {
    currentvault: string;
    vaultWasChanged: (newVault:string)=>void;
}

const useStyles = makeStyles({
    vaultSelectorDropdown: {
        width: "70%"
    }
})

const VaultSelector:React.FC<VaultSelectorProps> = (props) => {
    const [loading, setLoading] = useState<boolean>(false);
    const [lastError, setLastError] = useState<string|undefined>(undefined);
    const [knownVaults, setKnownVaults] = useState<Array<VaultDescription>>([]);

    const styles = useStyles();

    const refresh = async ()=> {
        const response = await authenticatedFetch("/api/vault", {});
        switch(response.status) {
            case 200:
                const content = await response.json() as Array<VaultDescription>;
                if(Array.isArray(content)) {
                    const reversed = content.reverse();
                    //this.setState({loading: false, lastError: null, knownVaults: reversed});
                    setLoading(false);
                    setLastError(undefined);
                    setKnownVaults(reversed);
                    if (props.currentvault === "") props.vaultWasChanged(reversed[0].vaultId);
                } else {
                    console.error("Expected server response to be an array, got ", content);
                    setLastError("Could not understand server response");
                    setLoading(false);
                }
                break;
            default:
                const errorContent = await response.text();
                console.error(errorContent);
                setLoading(false);
                setLastError(`Server error ${response.status}`);
                break;
        }
    }

    useEffect(()=>{
        refresh();
    }, []);

    return lastError ? <p className="error">{lastError}</p> : <>
                <label htmlFor="vaultsDropdown">Select vault: </label>
                <Select labelId="vaults-dropdown-label" id="vaultsDropdown"
                        className={styles.vaultSelectorDropdown}
                        value={props.currentvault}
                        onChange={evt => props.vaultWasChanged(evt.target.value as string)}>
                    {knownVaults.map((entry,idx) => <MenuItem value={entry.vaultId} key={idx}>{entry.name}</MenuItem>)}
                </Select>
            </>
}

export default VaultSelector;