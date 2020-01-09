import React from "react";
import ProjectLockerSearchBar from "./projectsearch/ProjectLockerSearchBar.jsx";
import { withRouter } from 'react-router-dom';
import ProjectContentSummary from "./projectsearch/ProjectContentSummary.jsx";
import VaultSelector from "./searchnbrowse/VaultSelector.jsx";

class ByProjectComponent extends React.Component {
    constructor(props){
        super(props);

        this.state = {
            loading: false,
            currentProjectSearch: "",
            projectLockerBaseUrl: "",
            plutoBaseUrl: "",
            lastError: null,
            vaultId: ""
        };
    }

    breakdownSearchParams() {
        const fullstring = this.props.location.search.slice(1);
        const parts = fullstring.split("&");
        const elems = parts.map(entry=>entry.split("="));
        return elems.reduce((acc,elem)=>{ acc[elem[0]]=elem[1]; return acc}, {});
    }

    async setupCurrentSearch(){
        const searchParams = this.breakdownSearchParams();
        console.log(searchParams);
        return new Promise((resolve, reject)=>{
            if(searchParams.hasOwnProperty("project")) {
                this.setState({currentProjectSearch: searchParams.project}, () => resolve());
            } else {
                resolve();
            }
        });
    }

    componentDidMount() {
        this.loadFrontendConfig().then(()=>{
            this.setupCurrentSearch()
        });
    }

    async loadFrontendConfig() {
        const response = await fetch("/api/config");
        if(response.ok){
            const content = await response.json();
            return new Promise((resolve, reject)=> {
                this.setState({projectLockerBaseUrl: content.projectLockerBaseUrl, plutoBaseUrl: content.plutoBaseUrl}, ()=>resolve())
            });
        } else {
            const content = await response.text();
            return new Promise((resolve, reject)=>{
                this.setState({lastError: content}, ()=>resolve())
            })
        }
    }

    componentDidUpdate(prevProps, prevState, snapshot) {
        if(prevState.currentProjectSearch!==this.state.currentProjectSearch){
            this.props.history.push("?project=" + this.state.currentProjectSearch);
        }
    }

    render() {
        return <div className="windowpanel">
                <div className="search-bar-element">
                    <VaultSelector currentvault={this.state.vaultId} vaultWasChanged={newVaultId=>this.setState({vaultId: newVaultId})}/>
                </div>
                <ProjectLockerSearchBar projectLockerBaseUrl={this.state.projectLockerBaseUrl}
                                        projectSelectionChanged={newProject=>this.setState({currentProjectSearch: newProject})}
                                        size={8}
                />
            <ProjectContentSummary vaultId={this.state.vaultId} projectId={this.state.currentProjectSearch} plutoBaseUrl={this.state.plutoBaseUrl}/>
        </div>
    }
}

export default withRouter(ByProjectComponent);