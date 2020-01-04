import React from "react";
import ProjectLockerSearchBar from "./projectsearch/ProjectLockerSearchBar.jsx";

class ByProjectComponent extends React.Component {
    constructor(props){
        super(props);

        this.state = {
            loading: false,
            currentProjectSearch: "",
            projectLockerBaseUrl: "",
            lastError: null
        };
    }

    componentDidMount() {
        this.loadFrontendConfig();
    }

    async loadFrontendConfig() {
        const response = await fetch("/api/config");
        if(response.ok){
            const content = await response.json();
            return new Promise((resolve, reject)=> {
                this.setState({projectLockerBaseUrl: content.projectLockerBaseUrl}, ()=>resolve())
            });
        } else {
            const content = await response.text();
            return new Promise((resolve, reject)=>{
                this.setState({lastError: content}, ()=>resolve())
            })
        }
    }

    render() {
        return <div className="windowpanel">
            <ProjectLockerSearchBar projectLockerBaseUrl={this.state.projectLockerBaseUrl}
                                    projectSelectionChanged={newProject=>this.setState({currentProjectSearch: newProject})}
                                    size={15}/>
        </div>
    }
}

export default ByProjectComponent;