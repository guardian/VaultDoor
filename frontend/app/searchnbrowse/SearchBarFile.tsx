import React, {useEffect, useState} from "react";
import {Grid, Input, MenuItem, Select} from "@material-ui/core";
import VaultSelector from "./NewVaultSelector";
import SortSelector from "../common/SortSelector";

interface SearchBarFileProps {
    searchUrlChanged: (newUrl:string)=>void;
}

const SearchBarFile:React.FC<SearchBarFileProps> = (props) => {
    const [internalError, setInternalError] = useState<string|undefined>(undefined);
    const [filePath, setFilePath] = useState("");
    const [selectedVault, setSelectedVault] = useState<string|undefined>(undefined);
    const [sortField, setSortField] = useState("MXFS_ARCHIVE_TIME");
    const [sortOrder, setSortOrder] = useState("Descending");

    /**
     * rebuild the target url whenever the paramers change
     */
    useEffect(() => {
        const urlBase = "/api/vault/" + selectedVault + "/list";

        const params:Record<string, string> = {
            sortField: sortField,
            sortDir: sortOrder,
            forFile: filePath,
        };

        const queryTerms = Object.keys(params)
            .filter((fieldName) => params[fieldName] !== "")
            .map((fieldName) => `${fieldName}=${params[fieldName]}`);

        const url = queryTerms.length > 0
            ? urlBase + "?" + queryTerms.join("&")
            : urlBase;
        props.searchUrlChanged(url);

    }, [filePath, selectedVault, sortField, sortOrder])

    if (internalError) {
        return (
            <div className="searchbar">
                <p className="error">
                    The search bar component failed: {internalError}
                </p>
                <p className="error">Please reload the page</p>
            </div>
        );
    }

    return (
        <Grid direction="row" container spacing={3} justify="space-around" alignItems="flex-end" style={{paddingLeft:"1em", paddingRight:"1em"}}>
            <Grid item>
                <VaultSelector
                    currentvault={selectedVault ?? ""}
                    vaultWasChanged={(newVault) =>
                        setSelectedVault(newVault)
                    }
                />
            </Grid>
            <Grid item>
                <label htmlFor="filePathSearch">File path: </label>
                <Input
                    type="text"
                    id="filePathSearch"
                    onChange={(evt) => setFilePath(evt.target.value)}
                    value={filePath}
                />
            </Grid>
            <Grid item>
                <label htmlFor="sort-field-selector">Sort by:</label>
                <Select
                    id="sort-field-selector"
                    value={sortField}
                    style={{paddingRight: "0.5em"}}
                    onChange={(evt)=>setSortField(evt.target.value as string)}
                >
                    //see FileListController
                    <MenuItem value="MXFS_ARCHIVE_TIME">Archive time</MenuItem>
                    <MenuItem value="MXFS_CREATION_TIME">Created time</MenuItem>
                    <MenuItem value="MXFS_MODIFICATION_TIME">Modified time</MenuItem>
                    <MenuItem value="MXFS_FILEEXT">File type</MenuItem>
                    <MenuItem value="MXFS_FILENAME">File name</MenuItem>
                    <MenuItem value="DPSP_SIZE">File size</MenuItem>
                </Select>
                <Select
                    id="sort-dir-selector"
                    value={sortOrder}
                    onChange={(evt)=>setSortOrder(evt.target.value as string)}
                >
                    <MenuItem value="Descending">
                        {sortField?.includes("TIME")
                            ? "Most recent first"
                            : "Descending"}
                    </MenuItem>
                    <MenuItem value="Ascending">
                        {sortField?.includes("TIME")
                            ? "Oldest first"
                            : "Ascending"}
                    </MenuItem>
                </Select>
            </Grid>
        </Grid>
    );
}

export default SearchBarFile;