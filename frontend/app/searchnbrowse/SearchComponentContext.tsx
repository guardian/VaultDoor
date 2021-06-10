import React from "react";

interface SearchComponentContextContents {
    vaultId?: string;
    vaultIdUpdated: (newValue:string)=>void;
}

const SearchComponentContext = React.createContext<SearchComponentContextContents>({
    vaultId: undefined,
    vaultIdUpdated: ()=>{}
});

export type {SearchComponentContextContents};

export default SearchComponentContext;